import type { VmShell } from '../../../adapter/vmShell';

/** A bounded, on-device RA2 test runner. No game binaries, keys or user data leave the WebView. */
type RecordedEvent =
  | { t: number; k: 'm'; a: number; b: number; c: number }
  | { t: number; k: 'k'; a: number; b: number }
  | { t: number; k: 'p'; a: number; b: number };
type Recording = { version: 1; startPage: string; width: number; height: number; events: RecordedEvent[] };
const STORAGE = 'ra2-android-diagnostic-trace-v1';
const RUN_MS = 120_000;
const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));
const labWindow = window as Window & { ra2AndroidLab?: {
  startRecording(): boolean; stopRecording(): string; replay(): void;
  stopAutotest(): void; status(): string;
} };

export function installAndroidAutotest(vm: VmShell, canvas: HTMLCanvasElement,
  current: () => boolean): void {
  if (window.location.hostname !== 'ra2.android.local') return;
  let recording = false;
  let running = false;
  let cancelled = false;
  let recordStart = 0;
  let events: RecordedEvent[] = [];
  let captureFrame = { width: 800, height: 600 };
  let reportEvents = 0;
  const fresh = () => current() && !cancelled;
  const log = (line: string) => {
    if (reportEvents++ < 160) console.info('[RA2-LAB] ' + line);
  };
  const time = () => Math.round(performance.now() - recordStart);
  const add = (event: RecordedEvent) => {
    if (!recording) return;
    if (events.length >= 8000 || time() > 10 * 60_000) {
      recording = false;
      log('Aufzeichnungslimit erreicht; bitte Aufzeichnung beenden und sichern');
      return;
    }
    events.push(event);
  };
  const actualPost = vm.postMessage.bind(vm);
  const actualKey = vm.setKeyState.bind(vm);
  const actualCursor = vm.setCursorPosition.bind(vm);
  vm.postMessage = (message, wp = 0, lp = 0) => {
    // Limit mouse move traffic while preserving all button/key edges for deterministic replay.
    if (message !== 0x0200 || events.length === 0 ||
        time() - events[events.length - 1]!.t >= 90)
      add({ t: time(), k: 'm', a: message, b: wp, c: lp });
    actualPost(message, wp, lp);
  };
  vm.setKeyState = (key, down) => {
    add({ t: time(), k: 'k', a: key, b: down ? 1 : 0 });
    actualKey(key, down);
  };
  vm.setCursorPosition = (x, y) => {
    if (!events.length || time() - events[events.length - 1]!.t >= 100)
      add({ t: time(), k: 'p', a: x, b: y });
    actualCursor(x, y);
  };

  const shell = () => canvas.dataset.shellPage ?? '';
  const phase = () => {
    try { return (JSON.parse(canvas.dataset.vmStatus ?? '{}') as { phase?: string }).phase ?? 'unbekannt'; }
    catch { return 'unbekannt'; }
  };
  const frameSize = () => canvas.dataset.vmResolution ?? canvas.width + 'x' + canvas.height;
  const ready = async (battle: boolean, deadline = 90_000): Promise<boolean> => {
    const until = performance.now() + deadline;
    let stable = 0;
    let sawSkirmish = false;
    while (performance.now() < until && fresh()) {
      const currentPhase = phase();
      const page = shell().toLowerCase();
      if (page.includes('skirmish')) sawSkirmish = true;
      // A blank shell during early initialization is NOT an active battlefield.
      const nativeFrame = battle ? (await vm.getGamePerformance().catch(() => null))?.frame ?? 0 : 1;
      const ok = battle
        ? currentPhase === 'running' && !page && nativeFrame > 0 && sawSkirmish
        : currentPhase === 'running' && page.includes('mainmenu');
      if (ok && ++stable >= 3) return true;
      if (!ok) stable = 0;
      if (['error', 'exited', 'blocked'].includes(currentPhase)) return false;
      await delay(300);
    }
    return false;
  };
  const input = (event: RecordedEvent) => {
    if (event.k === 'm') actualPost(event.a, event.b, event.c);
    else if (event.k === 'k') actualKey(event.a, !!event.b);
    else actualCursor(event.a, event.b);
  };
  const press = (key: number) => {
    actualKey(key, true);
    actualPost(0x0100, key, 1);
    actualKey(key, false);
    actualPost(0x0101, key, 0xc0000001);
  };
  const mouseClick = (x: number, y: number, right: boolean) => {
    const lParam = ((y & 0xffff) << 16) | (x & 0xffff);
    actualCursor(x, y);
    actualPost(0x0200, 0, lParam);
    const down = right ? 0x0204 : 0x0201;
    const up = right ? 0x0205 : 0x0202;
    const flag = right ? 2 : 1;
    actualKey(right ? 2 : 1, true);
    actualPost(down, flag, lParam);
    actualKey(right ? 2 : 1, false);
    actualPost(up, 0, lParam);
  };

  async function replay(): Promise<void> {
    if (running) return;
    let saved: Recording | null = null;
    try { saved = JSON.parse(localStorage.getItem(STORAGE) ?? 'null') as Recording | null; }
    catch { /* Reject corrupt local recordings. */ }
    if (!saved || saved.version !== 1 || !Array.isArray(saved.events) || saved.events.length > 8000) {
      log('WIEDERGABE: Kein gueltiger gespeicherter Spielablauf vorhanden'); return;
    }
    running = true;
    cancelled = false;
    recording = false;
    log('WIEDERGABE: ' + saved.events.length + ' echte Eingaben, Startseite=' + saved.startPage);
    const ok = await ready(saved.startPage === 'battle');
    if (!ok) { log('WIEDERGABE: Spiel wurde nicht rechtzeitig bereit'); running = false; return; }
    let previous = 0, sent = 0;
    const started = performance.now();
    for (const e of saved.events) {
      if (!fresh()) break;
      if (typeof e.t !== 'number' || !Number.isFinite(e.t) || e.t < previous ||
          !['m','k','p'].includes(e.k)) break;
      const wait = Math.max(0, e.t - (performance.now() - started));
      if (wait) await delay(Math.min(wait, 20_000));
      if (!fresh()) break;
      input(e);
      previous = e.t;
      sent++;
    }
    log('WIEDERGABE beendet: ' + sent + '/' + saved.events.length +
      ' Eingaben, Endphase=' + phase() + ' Seite=' + shell());
    running = false;
  }

  async function autotest(): Promise<void> {
    if (running) return;
    running = true;
    cancelled = false;
    recording = false;
    log('AUTOTEST gestartet: 120s Gefecht, Originaldateien unveraendert');
    if (!(await ready(true))) {
      log('AUTOTEST ABBRUCH: Schlachtfeld fehlt oder VM haengt beim Start; Phase=' + phase() +
        ' Shell=' + shell()); running = false; return;
    }
    const started = performance.now();
    let lastFrame = -1;
    let stale = 0;
    let samples = 0;
    let orders = 0;
    let failed = false;
    let scoreReached = false;
    while (fresh() && performance.now() - started < RUN_MS) {
      const elapsed = Math.round((performance.now() - started) / 1000);
      const state = phase();
      const page = shell();
      const perf = await vm.getGamePerformance().catch(() => null);
      const frame = perf?.frame ?? -1;
      samples++;
      if (frame === lastFrame && frame >= 0) stale++; else stale = 0;
      if (samples <= 3 || samples % 4 === 0 || stale === 4)
        log('AUTO STATUS t=' + elapsed + 's VM=' + state + ' nativeFrame=' + frame +
          ' Aufloesung=' + frameSize() + ' Shell=' + (page || 'Gefecht'));
      if (/multiplayerscore|gameover|victory|defeat/i.test(page)) {
        scoreReached = true;
        log('AUTOTEST GEFECHT BEENDET: Ergebnisfenster erreicht nach ' + elapsed +
          's; letzte nativeFrame=' + frame + '. Keine weiteren Befehle.');
        break;
      }
      if (['error','blocked','exited'].includes(state) || stale >= 5) {
        failed = true;
        log('AUTOTEST FEHLER: Engine oder Gefecht pausiert, t=' + elapsed +
          's, Phase=' + state + ' Shell=' + (page || 'Gefecht'));
        break;
      }
      // Every 15s issue harmless native unit-selection/move orders.
      // This tests real input propagation but cannot guarantee an entire strategic match.
      if (elapsed >= 5 && samples % 3 === 0) {
        const width = Math.max(400, Math.min(4000, Number(frameSize().split('x')[0]) || 800));
        const height = Math.max(300, Math.min(3000, Number(frameSize().split('x')[1]) || 600));
        press(0x51); // Native RA2 'Q' group selection hotkey.
        mouseClick(Math.floor(width * (orders % 2 ? 0.48 : 0.62)),
                   Math.floor(height * (orders % 2 ? 0.55 : 0.45)), true);
        orders++;
        log('AUTO BEFEHL #' + orders + ' Einheiten waehlen/Bewegen');
      }
      lastFrame = frame;
      await delay(5000);
    }
    const verdict = scoreReached ? 'GEFECHT_BEENDET' :
      failed ? 'ENGINE_ANHALT' :
      !fresh() ? 'MANUELL_GESTOPPT' : '120s_DURCHGELAUFEN';
    log('AUTOTEST ENDE: ' + verdict +
      ' nativeAbfragen=' + samples + ' Spielbefehle=' + orders +
      ' Phase=' + phase() + ' Shell=' + (shell() || 'Gefecht') +
      ' (Ergebnisseite allein beweist keinen Kopierschutzfehler)');
    running = false;
  }

  labWindow.ra2AndroidLab = {
    startRecording() {
      if (!fresh() || running) return false;
      recording = true;
      recordStart = performance.now();
      captureFrame = (() => {
        const [width, height] = frameSize().split('x').map(Number);
        return { width: width || 800, height: height || 600 };
      })();
      events = [];
      log('AUFZEICHNUNG START: ' + shell() + ' bei ' + frameSize());
      return true;
    },
    stopRecording() {
      if (!recording && events.length === 0) return 'Keine laufende Aufzeichnung.';
      recording = false;
      const record: Recording = {
        version: 1, startPage: new URLSearchParams(location.search).get('start-page') ?? 'mainmenu',
        width: captureFrame.width, height: captureFrame.height, events,
      };
      try { localStorage.setItem(STORAGE, JSON.stringify(record)); }
      catch { return 'Kein Platz zum Speichern der Aufzeichnung.'; }
      const note = 'Gespeichert: ' + events.length + ' Eingaben, ' + time() + 'ms';
      log('AUFZEICHNUNG STOP: ' + note);
      return note;
    },
    replay() { void replay(); },
    stopAutotest() { cancelled = true; log('TEST manuell gestoppt'); },
    status() { return (recording ? 'AUFNAHME ' + events.length : running ? 'TEST_LAEUFT' : 'BEREIT') +
      ' Phase=' + phase(); },
  };
  const params = new URLSearchParams(window.location.search);
  if (params.get('ra2-autotest') === '1') void autotest();
  else if (params.get('ra2-replay') === '1') void replay();
}
