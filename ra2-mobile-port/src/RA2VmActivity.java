package com.winlator;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayDeque;

/** Distraction-free RA2/Yuri fullscreen, local GPL VM engine, touch settings on demand. */
public final class RA2VmActivity extends AppCompatActivity {
    private WebView browser;
    private RA2VmFiles files;
    private final ArrayDeque<String> log = new ArrayDeque<>();
    private ValueCallback<android.net.Uri[]> pendingFiles;
    private static final int PICK_RESOURCE = 8824;
    private boolean menuOpen;
    private boolean settingsOpen;
    private boolean touchOpen;
    private LinearLayout menu;
    private android.widget.ScrollView menuScroll;
    private TextView gear;
    private String lastFailure = "";
    private long engineOpenedAt;

    private int dp(float units) {
        return Math.round(getResources().getDisplayMetrics().density * units);
    }

    private void immersive() {
        // Android 16: hide system navigation/status bars but allow transient edge swipes.
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    private GradientDrawable background(int color, int stroke, float radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) bg.setStroke(dp(1),stroke);
        return bg;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engineOpenedAt=android.os.SystemClock.elapsedRealtime();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        files = new RA2VmFiles(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        browser = new WebView(this);
        browser.setBackgroundColor(Color.BLACK);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setDatabaseEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(true);
        browser.getSettings().setMediaPlaybackRequiresUserGesture(false);
        browser.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        browser.getSettings().setLoadWithOverviewMode(true);
        browser.getSettings().setUseWideViewPort(true);
        browser.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        browser.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return files.serve(request);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, android.webkit.WebResourceError error) {
                append("network-error "+req.getUrl()+": "+error.getDescription());
                if (req.isForMainFrame()) showFailure("VM-Start: "+error.getDescription());
            }
            @Override public void onPageFinished(WebView view, String url) {
                applyEngineUi();
                append("Ready: RA2="+files.ra2Available()+" Yuri="+files.yuriAvailable()+
                    " CD2="+files.sovietCdReady());
                if (!files.gameAvailable()) showFailure("Spieldateien nicht gefunden. ⚙ → Protokoll kopieren.");
            }
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                append("render-process-gone crashed="+(detail!=null&&detail.didCrash()));
                showFailure("Spielengine wurde von Android beendet. ⚙ → Protokoll kopieren.");
                return true;
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage msg) {
                append("JS "+msg.messageLevel()+" "+msg.message()+" @"+msg.sourceId()+":"+msg.lineNumber());
                if(msg.messageLevel()==ConsoleMessage.MessageLevel.ERROR)
                    showFailure("Engine-Fehler: "+msg.message());
                return true;
            }
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<android.net.Uri[]> callback,
                                                         FileChooserParams params) {
                if(pendingFiles!=null)pendingFiles.onReceiveValue(null);
                pendingFiles=callback;
                try {
                    Intent pick=params.createIntent();
                    pick.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(pick,PICK_RESOURCE);
                    return true;
                } catch(Exception e) {
                    pendingFiles.onReceiveValue(null);pendingFiles=null;
                    showFailure("Dateiauswahl: "+e.getMessage());
                    return false;
                }
            }
        });
        root.addView(browser,new FrameLayout.LayoutParams(-1,-1));

        // One small floating gear replaces the permanent diagnostic banner.
        gear = new TextView(this);
        gear.setText("⚙");
        gear.setTextSize(23);
        gear.setTextColor(0xffe3d2a3);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("RA2 Einstellungen öffnen");
        gear.setBackground(background(0x92212630,0x88a8a49a,10));
        FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(dp(36),dp(36),Gravity.TOP|Gravity.RIGHT);
        gp.topMargin=dp(6);
        gp.rightMargin=dp(8);
        root.addView(gear,gp);

        menu=new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(8),dp(8),dp(8),dp(8));
        menu.setBackground(background(0xed11171e,0xff6b6050,9));
        // Fully visible buttons, with the most important troubleshooting action first.
        addMenuButton("🤖 GEFECHTSTEST – 2 MINUTEN", () -> {
            closeMenu();
            runAutonomousBattleTest();
        });
        addMenuButton("⚙ Einstellungen", () -> {
            settingsOpen=!settingsOpen;
            applyEngineUi();
            closeMenu();
        });
        addMenuButton("🔑 Original-CD-Schlüssel", () -> {
            closeMenu();
            editOriginalKeys();
        });
        addMenuButton("📀 CD-Laufwerk D: ändern", () -> {
            closeMenu();
            android.content.SharedPreferences pref =
                getSharedPreferences("ra2_mobile",Context.MODE_PRIVATE);
            boolean enabled = pref.getBoolean("vm_virtual_cd_enabled",true);
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("RA2 Original-CD-Prüfmodus")
                .setMessage("Das Original-Sowjet-CD-Abbild ist " +
                    (files.sovietCdReady()?"vorhanden.":"nicht vorhanden.") +
                    "\nVirtuelles CD-Laufwerk D: ist derzeit " + (enabled?"EIN.":"AUS.") +
                    "\n\nWenn das Gefecht weiterhin automatisch endet oder der Start " +
                    "nach dieser Änderung eine CD verlangt, kannst du hier zurückschalten.")
                .setNegativeButton("Abbrechen",null)
                .setPositiveButton(enabled?"D: deaktivieren":"D: aktivieren",(d,which)->{
                    pref.edit().putBoolean("vm_virtual_cd_enabled",!enabled).apply();
                    append("CD-Pruefmodus D: "+(!enabled?"EIN":"AUS")+
                        " (originale Medien bleiben unveraendert)");
                    if(browser!=null)browser.reload();
                }).show();
        });
        addMenuButton("⌨ Tasten / Joystick", () -> {
            touchOpen=!touchOpen;
            applyEngineUi();
            closeMenu();
        });
        addMenuButton("🧪 Testlabor & Aufzeichnungen", () -> {
            closeMenu();
            openTestLabMenu();
        });
        addMenuButton("📋 Analyse und Protokoll", () -> {
            closeMenu();
            copyLog();
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Automatische Ursachenanalyse")
                .setMessage(buildReasonedAnalysis())
                .setPositiveButton("OK",null).show();
        });
        addMenuButton("💥 Niederlage markieren", () -> {
            append("PLAYER_EVENT=UNITS_EXPLODED_DEFEAT");
            copyLog();
            closeMenu();
        });
        addMenuButton("Protokoll kopieren", () -> {copyLog();closeMenu();});
        addMenuButton("Spiel neu starten", () -> {
            closeMenu();
            lastFailure="";
            if(browser!=null)browser.reload();
        });
        addMenuButton("Spiel verlassen", () -> finish());
        // Use actual landscape bounds; long labels should never collapse to just an emoji.
        int screenWidth=getResources().getDisplayMetrics().widthPixels;
        int screenHeight=getResources().getDisplayMetrics().heightPixels;
        int menuWidth=Math.min(dp(305),Math.max(dp(200),screenWidth-dp(86)));
        int menuHeight=Math.max(dp(160),screenHeight-dp(70));
        menuScroll=new android.widget.ScrollView(this);
        menuScroll.setFillViewport(false);
        menuScroll.setVerticalScrollBarEnabled(true);
        menuScroll.setBackground(background(0xed11171e,0xff6b6050,9));
        menuScroll.setVisibility(View.GONE);
        menuScroll.addView(menu,new android.widget.ScrollView.LayoutParams(-1,-2));
        FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(menuWidth,menuHeight,Gravity.TOP|Gravity.RIGHT);
        mp.topMargin=dp(47);mp.rightMargin=dp(8);
        root.addView(menuScroll,mp);
        gear.setOnClickListener(v->{
            menuOpen=!menuOpen;
            menuScroll.setVisibility(menuOpen?View.VISIBLE:View.GONE);
            gear.setAlpha(menuOpen?1f:0.65f);
        });
        setContentView(root);
        append("Engine=RA2 VM, origin="+RA2VmFiles.URL);
        append("DataDir="+files.originalDirectory()+" exists="+files.gameAvailable());
        append("GameDiscovery="+files.discoveryStatus());
        append("RA2ready="+files.ra2Available()+" YuriReady="+files.yuriAvailable());
        append("VirtualCD="+files.mediaStatus());
        append("FileFingerprint="+files.gameFingerprint());
        append("CD-Guest-Modus-D="+(getSharedPreferences("ra2_mobile",Context.MODE_PRIVATE)
            .getBoolean("vm_virtual_cd_enabled",true)?"EIN":"AUS")+" (falls CD2 vorhanden)");
        browser.loadUrl(RA2VmFiles.URL);
    }

    private void addMenuButton(String label, Runnable action) {
        Button b=new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setMaxLines(2);
        b.setSingleLine(false);
        b.setPadding(dp(8),0,dp(8),0);
        b.setContentDescription(label);
        b.setBackground(background(0xff303b43,0xff54565b,6));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));
        lp.bottomMargin=dp(6);
        menu.addView(b,lp);
        b.setOnClickListener(v->action.run());
    }


    /** Plain German test button – kept separate from the general settings menu. */
    private void runAutonomousBattleTest() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("2-Minuten-Gefechts-Autotest")
            .setMessage("Die RA2-Engine startet mit deinen Originaldaten ein Gefecht und " +
                "überwacht zwei Minuten lang Spielzustand, Einheiten und Testbefehle. " +
                "Falls eine Auswahl erscheint, Red Alert 2 auswählen. " +
                "Dies ist ein Funktionstest, kein eigenständig gewinnender KI-Spieler. " +
                "Nicht gespeicherter Spielfortschritt geht beim Neustart verloren.")
            .setNegativeButton("Abbrechen",null)
            .setPositiveButton("2-Minuten-Test starten",(dialog,which)->{
                append("AUTOTEST angefordert – RA2 Gefecht mit Originaldateien");
                if(browser!=null)browser.loadUrl(RA2VmFiles.URL+"?start-page=battle&ra2-autotest=1");
            }).show();
    }

    private void openTestLabMenu() {
        final String[] actions={
            "🤖 2-Minuten-Gefechts-Autotest",
            "⏺ Spielablauf aufzeichnen",
            "⏹ Aufzeichnung speichern",
            "▶ Aufzeichnung wiederholen",
            "📋 Intelligente Analyse & Log",
            "💥 Niederlage markieren & Log"
        };
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("RA2 – Testlabor")
            .setItems(actions,(dialog,which)->{
                switch(which) {
                    case 0:
                        runAutonomousBattleTest();
                        break;
                    case 1:
                        if(browser!=null)browser.evaluateJavascript(
                            "String(window.ra2AndroidLab?.startRecording?.() ?? false)",
                            result->Toast.makeText(this,
                                result.contains("true")?"Spielablauf wird aufgezeichnet.":
                                "Zuerst RA2 in der VM starten.",Toast.LENGTH_LONG).show());
                        break;
                    case 2:
                        if(browser!=null)browser.evaluateJavascript(
                            "String(window.ra2AndroidLab?.stopRecording?.() ?? 'Kein Spiel geladen')",
                            result->Toast.makeText(this,result,Toast.LENGTH_LONG).show());
                        break;
                    case 3:
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Aufgezeichneten Spielablauf wiederholen")
                            .setMessage("Die Engine startet neu und führt die zuletzt " +
                                "aufgezeichneten Befehle aus. Die gleiche Karte und " +
                                "Auflösung verbessern die Vergleichbarkeit.")
                            .setNegativeButton("Abbrechen",null)
                            .setPositiveButton("Wiederholen",(d,w)->{
                                if(browser!=null)browser.loadUrl(RA2VmFiles.URL+"?ra2-replay=1");
                            }).show();
                        break;
                    case 4:
                        copyLog();
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Intelligente Ursachenanalyse")
                            .setMessage(buildReasonedAnalysis())
                            .setPositiveButton("OK",null).show();
                        break;
                    case 5:
                        append("PLAYER_EVENT=UNITS_EXPLODED_DEFEAT");
                        copyLog();
                        break;
                    default: break;
                }
            })
            .setNegativeButton("Schließen",null)
            .show();
    }

    private void closeMenu() {
        menuOpen=false;
        if(menuScroll!=null)menuScroll.setVisibility(View.GONE);
        if(gear!=null)gear.setAlpha(0.65f);
    }

    /**
     * Keep original serials only in the existing per-app preferences.
     * RA2VmFiles exposes the selected game's serial to the local private VM
     * origin at next startup; never bundle keys in the publicly distributed APK.
     */
    private void editOriginalKeys() {
        final android.content.SharedPreferences prefs =
            getSharedPreferences("ra2_mobile", Context.MODE_PRIVATE);
        final String[] prefKeys = {"original_cd_key_ra2", "original_cd_key_yuri"};
        final String[] gameNames = {"Alarmstufe Rot 2", "Yuri’s Rache"};
        final LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(12), dp(16), dp(8));
        form.setBackgroundColor(0xff182027);

        TextView intro = new TextView(this);
        intro.setText("Gib deinen Original-CD-Schlüssel ein. Bereits gespeicherte Schlüssel " +
            "bleiben erhalten, wenn das Feld leer bleibt. Die Prüfung kontrolliert " +
            "nur das Format, nicht die Echtheit. Deine Schlüssel werden nicht " +
            "in die APK eingebaut oder im Protokoll ausgegeben.");
        intro.setTextColor(Color.WHITE);
        intro.setTextSize(14);
        form.addView(intro, new LinearLayout.LayoutParams(-1, -2));

        final android.widget.EditText[] inputs = new android.widget.EditText[2];
        for (int i=0; i<2; i++) {
            TextView label = new TextView(this);
            boolean saved = RA2SerialPolicy.acceptableInput(prefs.getString(prefKeys[i], ""));
            label.setText(gameNames[i] + (saved ? " – hinterlegt (Format OK)" : " – noch nicht hinterlegt"));
            label.setTextColor(0xffe3d2a3);
            label.setTextSize(14);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1,-2);
            labelParams.topMargin = dp(14);
            form.addView(label, labelParams);

            android.widget.EditText input = new android.widget.EditText(this);
            input.setSingleLine(true);
            input.setTextColor(Color.WHITE);
            input.setHintTextColor(0xffaab4bb);
            input.setHint("Originalschlüssel eingeben (optional)");
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setFilters(new android.text.InputFilter[] {
                new android.text.InputFilter.LengthFilter(80)
            });
            inputs[i] = input;
            form.addView(input, new LinearLayout.LayoutParams(-1,-2));
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setBackgroundColor(0xff182027);
        scroll.addView(form);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Original-CD-Schlüssel")
            .setView(scroll)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern", null)
            .create();
        final boolean hadSecure = (getWindow().getAttributes().flags &
            WindowManager.LayoutParams.FLAG_SECURE) != 0;
        dialog.setOnDismissListener(d -> {
            if (!hadSecure) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        });
        dialog.setOnShowListener(d -> {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String ra2 = inputs[0].getText().toString().trim();
                String yuri = inputs[1].getText().toString().trim();
                String[] values = {ra2, yuri};
                for(int i=0; i<2; i++) {
                    if (!values[i].isEmpty() && !RA2SerialPolicy.acceptableInput(values[i])) {
                        inputs[i].setError("Ungültiges Format: vollständigen Originalschlüssel eingeben.");
                        inputs[i].requestFocus();
                        return;
                    }
                }
                if (ra2.isEmpty() && yuri.isEmpty()) {
                    dialog.dismiss();
                    Toast.makeText(this,"Keine neuen Schlüssel eingegeben.",Toast.LENGTH_SHORT).show();
                    return;
                }
                android.content.SharedPreferences.Editor edit = prefs.edit();
                if(!ra2.isEmpty()) edit.putString(prefKeys[0], RA2SerialPolicy.normalize(ra2));
                if(!yuri.isEmpty()) edit.putString(prefKeys[1], RA2SerialPolicy.normalize(yuri));
                if (!edit.commit()) {
                    Toast.makeText(this,"Schlüssel konnten nicht gespeichert werden.",Toast.LENGTH_LONG).show();
                    return;
                }
                dialog.dismiss();
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Schlüssel hinterlegt")
                    .setMessage("Die Schlüssel werden bei einem Neustart automatisch an die " +
                        "lokale RA2-/Yuri-Engine übergeben. Spiel jetzt neu starten? " +
                        "Nicht gespeicherter Spielfortschritt geht verloren.")
                    .setNegativeButton("Später",null)
                    .setPositiveButton("Jetzt neu starten",(ignore,which)->{
                        if(browser!=null)browser.reload();
                    }).show();
            });
        });
        dialog.show();
    }

    private void applyEngineUi() {
        if(browser==null)return;
        // Static class names, no untrusted string is interpolated into JavaScript.
        browser.evaluateJavascript(
            "document.documentElement.classList.add('ra2-android');" +
            "document.documentElement.classList.toggle('ra2-settings-open',"+settingsOpen+");" +
            "document.documentElement.classList.toggle('ra2-touch-open',"+touchOpen+");", null);
    }

    private void showFailure(String error) {
        lastFailure=error;
        append("ERROR "+error);
        runOnUiThread(()->Toast.makeText(this,error,Toast.LENGTH_LONG).show());
    }

    private void append(String line) {
        String stamped="[t+"+(android.os.SystemClock.elapsedRealtime()-engineOpenedAt)+"ms] "+line;
        Log.d("RA2Vm",stamped);
        synchronized(log) {if(log.size()>=300)log.removeFirst();log.addLast(stamped);}
    }

    private String buildReasonedAnalysis() {
        StringBuilder capture=new StringBuilder();
        synchronized(log){for(String line:log)capture.append(line).append('\n');}
        String value=capture.toString();
        boolean mediaOk=value.contains("CD2=true") || value.contains("RA2-CD2=true");
        boolean guestCD=value.contains("GetDriveTypeA D:=CDROM");
        boolean registry=value.contains("Original installation registration connected");
        boolean registryRead=value.contains("RegQuery") || value.contains("RegOpen");
        boolean houseDeath=value.contains("ALARM=SPIELER_TOT");
        boolean noUnits=value.contains("ALARM=KEINE_EINHEITEN");
        boolean houseObserved=value.contains("[RA2-HOUSE] Battle");
        boolean autoStarted=value.contains("AUTOTEST gestartet");
        boolean autoFinished=value.contains("AUTOTEST ENDE");
        boolean matchEnded=value.contains("AUTOTEST ENDE: GEFECHT_BEENDET") ||
            value.contains("AUTOTEST GEFECHT BEENDET");
        boolean autoFailed=value.contains("AUTOTEST FEHLER") || value.contains("AUTOTEST ABBRUCH");
        boolean stale=value.contains("AUTOTEST ENDE: ENGINE_ANHALT");
        StringBuilder out=new StringBuilder();
        out.append("Original-CD erkannt: ").append(mediaOk?"JA":"NOCH NICHT BESTAETIGT").append('\n');
        out.append("CD-ROM im Windows-Gast: ").append(guestCD?"JA":"NOCH NICHT BESTAETIGT").append('\n');
        out.append("Registrierung uebertragen: ").append(registry?"JA":"NOCH NICHT BESTAETIGT").append('\n');
        out.append("Windows-Registry im Spiel abgefragt: ").append(registryRead?"JA":"NICHT BEOBACHTET").append('\n');
        out.append("House-Speicherzustand lesbar: ").append(houseObserved?"JA":"NICHT BESTAETIGT").append('\n');
        out.append("Gefechts-Autotest: ").append(matchEnded?"GEFECHT_BEENDET":
            autoFailed?"START/LAUFZEITFEHLER":
            autoFinished?"BEENDET":autoStarted?"LAEUFT":"NICHT GESTARTET").append("\n\n");
        if(houseDeath) out.append("BERECHNETES ERGEBNIS: RA2 markiert den lokalen Spieler als tot. " +
            "Die Niederlage ist in der Spielsimulation messbar; die ausloesende Routine muss noch untersucht werden.");
        else if(noUnits) out.append("VERDACHT: Der lokale Spieler hat keine Einheiten mehr. " +
            "Bitte Zeitpunkt zur Niederlage vergleichen, da auch normaler Spielverlauf moeglich ist.");
        else if(matchEnded) out.append("GEFECHT BEENDET: Die Ergebnisseite wurde erreicht. " +
            "Ein anschliessend unveraenderter Framezaehler ist KEIN Engine-Absturz. " +
            "Auf den genauen Zeitpunkt der Einheitenverluste und fehlende Registry-Werte achten."); 
        else if(stale) out.append("VERDACHT: Die native Spiellogik/Frameausgabe stoppt, " +
            "ohne dass eine Ergebnisseite erkannt wurde.");
        else if(!houseObserved) out.append("DATENLUECKE: Keine gueltige House-Speicherprobe. " +
            "Moeglicherweise abweichende GAME.EXE-Version oder Spiel noch nicht auf dem Schlachtfeld.");
        else out.append("Bisher kein gemessener Uebergang zum Verliererstatus. " +
            "Automatischen Test laufen lassen und nach der Explosion direkt markieren.");
        if(value.contains("RegQuery") && value.contains("Ergebnis=FEHLT"))
            out.append("\nHINWEIS: Mindestens eine Win32-Registry-Abfrage ist fehlgeschlagen. " +
                "Erst mit dem abgefragten Wertnamen ist ihre Bedeutung bestimmbar.");
        if(value.contains("STATUS=NICHT_LESBAR"))
            out.append("\nHINWEIS: House-Daten der vorhandenen GAME.EXE sind nicht sicher lesbar.");
        out.append("\nKein CD-Schluessel oder Spielinhalt wird fuer diese Analyse hochgeladen.");
        return out.toString();
    }

    private void copyLog() {
        StringBuilder result=new StringBuilder("RA2 Android VM – Diagnose\n");
        result.append("Automatische Ursachenanalyse:\n").append(buildReasonedAnalysis()).append("\n\n");
        synchronized(log) {for(String line:log)result.append(line).append('\n');}
        if(!lastFailure.isEmpty())result.append("LastFailure: ").append(lastFailure).append('\n');
        ClipboardManager clip=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clip!=null)clip.setPrimaryClip(ClipData.newPlainText("RA2 VM Diagnose",result.toString()));
        Toast.makeText(this,"VM-Protokoll kopiert",Toast.LENGTH_SHORT).show();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_RESOURCE&&pendingFiles!=null) {
            ValueCallback<android.net.Uri[]> callback=pendingFiles;pendingFiles=null;
            callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode,data));
        }
    }

    @Override public void onBackPressed() {
        if(menuOpen) {closeMenu();return;}
        if(settingsOpen||touchOpen){
            settingsOpen=false;touchOpen=false;applyEngineUi();return;
        }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if(browser!=null){browser.stopLoading();browser.destroy();browser=null;}
        super.onDestroy();
    }
}