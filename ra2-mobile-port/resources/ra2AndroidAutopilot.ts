import type { VmShell } from '../../../adapter/vmShell';

/**
 * Android RA2 autopsy: an autonomous bounded in-game fault reproduction.
 * Uses the original executable, never overwrites game data or changes protection.
 * Two deterministic scenarios are run by the Android host (passive, then active).
 * A browser worker sees guest frames/Win32 calls; its Android parent stores results.
 */
export function installRa2AndroidAutopilot(vm: VmShell, canvas: HTMLCanvasElement,
  isCurrent: () => boolean): void {
  if (location.hostname !== 'ra2.android.local') return;
  const args = new URLSearchParams(location.search);
  if (args.get('ra2-superdiag') !== '1') return;
  const scenario = args.get('scenario') === 'active' ? 'active' : 'passive';
  const MAX_RUNTIME_MS = 180_000;
  let stopped = false;
  let emitted = false;
  let activeFrameSeen = false;
  let firstFrameAt = 0;
  let lastFrame = -1;
  let firstBattleAt = 0;
  let lastShell = '';
  let noFrameSince = 0;
  let orders = 0;
  let count = 0;
  let warned = false;
  const originAt = performance.now();
  const sleep = (ms: number): Promise<void> => new Promise(ok => setTimeout(ok, ms));
  const now = () => Math.round(performance.now() - originAt);
  const log = (message: string) => console.info('[RA2-AUTO] '+message);
  const shell = () => canvas.dataset.shellPage || '';
  const phase = () => {
    try { return (JSON.parse(canvas.dataset.vmStatus || '{}') as {phase?: string}).phase || 'UNKNOWN'; }
    catch { return 'UNKNOWN'; }
  };
  const runEnd = (kind: string, reason: string) => {
    if (emitted) return;
    emitted = true;
    stopped = true;
    log('END scenario='+scenario+' verdict='+kind+' reason='+reason.replace(/[^a-zA-Z0-9_:-]/g,'_')+
      ' durationMs='+now()+' firstBattleMs='+firstBattleAt+
      ' firstFrameMs='+firstFrameAt+' lastFrame='+lastFrame+
      ' orders='+orders+' samples='+count);
  };
  const send = (key: number,down: boolean) => {
    vm.setKeyState(key,down);
    vm.postMessage(down ? 0x0100 : 0x0101,key,down ? 1 : 0xc0000001);
  };
  const rightClick = (x: number,y: number) => {
    const point=((y & 65535)<<16)|(x & 65535);
    vm.setCursorPosition(x,y);
    vm.postMessage(0x0200,0,point);
    vm.postMessage(0x0204,2,point);
    vm.postMessage(0x0205,0,point);
  };
  const sendDeterministicOrder = () => {
    const match=/^(\d+)x(\d+)$/.exec(canvas.dataset.vmResolution ?? '');
    const width=Math.max(640,Math.min(3000, Number(match?.[1])||canvas.width||640));
    const height=Math.max(480,Math.min(1500, Number(match?.[2])||canvas.height||480));
    send(0x51,true);send(0x51,false);
    const x=Math.floor(width*(orders%2===0 ? 0.54 : 0.65));
    const y=Math.floor(height*(orders%2===0 ? 0.48 : 0.59));
    rightClick(x,y);
    orders++;
    log('ORDER scenario=active index='+orders+' x='+x+' y='+y);
  };
  log('START scenario='+scenario+' fullAuto=1 maxRuntimeMs='+MAX_RUNTIME_MS);
  const alive = () => !stopped && isCurrent();
  const periodic = async () => {
    while (alive() && now()<MAX_RUNTIME_MS) {
      count++;
      const p = phase();
      const page = shell();
      if (page !== lastShell) {
        lastShell = page;
        log('PAGE tMs='+now()+' title='+(page||'BATTLEFIELD'));
      }
      // Score page signals a *finished match*, not a hung VM.
      if (/multiplayerscore|gameover|defeat|victory/i.test(page)) {
        runEnd('MATCH_ENDED','SCORE_PAGE');
        break;
      }
      if (['error','exited','blocked'].includes(p.toLowerCase())) {
        runEnd('ENGINE_FAILURE',p);
        break;
      }
      const frame = (await vm.getGamePerformance().catch(() => null))?.frame ?? -1;
      if (!alive()) break;
      if (frame > 0 && !activeFrameSeen) {
        activeFrameSeen=true;
        firstFrameAt=now();
        log('FIRST_FRAME tMs='+firstFrameAt+' nativeFrame='+frame);
      }
      if (page === '' && activeFrameSeen && firstBattleAt === 0) {
        firstBattleAt = now();
        log('BATTLEFIELD_REACHED tMs='+firstBattleAt);
      }
      if (frame >= 0 && frame !== lastFrame) noFrameSince=0;
      else noFrameSince += 1000;
      if (count===1 || count%5===0 || firstBattleAt && count%2===0)
        log('SAMPLE tMs='+now()+' phase='+p+
          ' frame='+frame+' shell='+(page||'BATTLE')+
          ' deltaFrame='+(lastFrame>=0?frame-lastFrame:-1)+
          ' staleMs='+noFrameSince);
      // Important: no move commands in passive test; compare to active test.
      if (scenario==='active' && firstBattleAt>0 && now()-firstBattleAt>=6000 &&
          count%6===0 && orders<20 && !page) {
        sendDeterministicOrder();
      }
      if (firstBattleAt>0 && noFrameSince>=15000) {
        if(!warned) { warned=true;log('WARNING nativeFramesStationary='+noFrameSince+'ms phase='+p); }
        // no early "crash" conclusion on rendered menu/score; report a timeout
      }
      lastFrame=frame;
      await sleep(1000);
    }
    if (!emitted) runEnd(now()>=MAX_RUNTIME_MS ? 'TIME_LIMIT':'INTERRUPTED',
      'GAME_STILL_RUNNING_OR_NOT_READY');
  };
  void periodic().catch(e => {
    log('EXCEPTION category=TEST_CONTROLLER');
    runEnd('TEST_FAILURE','SCRIPT_ERROR');
  });
}
