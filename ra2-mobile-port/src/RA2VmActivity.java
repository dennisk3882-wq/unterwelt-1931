package com.winlator;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

/** Original executable inside RA2 VM's offline browser-based Win32 runtime. */
public final class RA2VmActivity extends AppCompatActivity {
    private WebView browser;
    private TextView notice;
    private RA2VmFiles files;
    private final ArrayDeque<String> log=new ArrayDeque<>();
    private ValueCallback<android.net.Uri[]> pendingFiles;
    private static final int PICK_RESOURCE=8824;
    private String lastFailure="";

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        files=new RA2VmFiles(this);
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        browser=new WebView(this);
        browser.setBackgroundColor(Color.rgb(7,9,16));
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setDatabaseEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(true);
        browser.getSettings().setMediaPlaybackRequiresUserGesture(false);
        browser.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        browser.getSettings().setLoadWithOverviewMode(true);
        browser.getSettings().setUseWideViewPort(true);
        browser.setLayerType(View.LAYER_TYPE_HARDWARE,null);
        browser.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
                return files.serve(request);
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest req,android.webkit.WebResourceError error) {
                String url=req.getUrl().toString();
                // Missing Windows loose files can legitimately return 404.
                if(req.isForMainFrame()) showFailure("WebView-Start fehlgeschlagen: "+error.getDescription());
                append("network-error "+url+": "+error.getDescription());
            }
            @Override public void onPageFinished(WebView view,String url) {
                if(lastFailure.isEmpty()) {
                    notice.setText(files.gameAvailable()
                        ? "Spieldaten erkannt: "+(files.ra2Available()?"RA2 ":"")+
                          (files.yuriAvailable()?"Yuri":"")+". In der Engine „SPIELDATEIEN LADEN“ wählen."
                        : "Kein vollständiger RA2-/Yuri-Spielordner erkannt. „LOG KOPIEREN“ zeigt den geprüften Pfad und die vorhandenen Dateien.");
                }
            }
            @Override public boolean onRenderProcessGone(WebView view,android.webkit.RenderProcessGoneDetail detail) {
                showFailure("Android-WebView-Prozess beendet. Vermutlich Speicher- oder Enginefehler. Bitte Diagnose kopieren.");
                append("render-gone didCrash="+(detail!=null && detail.didCrash()));
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
                    showFailure("Dateiauswahl nicht verfügbar: "+e.getMessage());
                    return false;
                }
            }
        });
        root.addView(browser,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xd91b2736);
        notice=new TextView(this);
        notice.setTextColor(Color.WHITE);
        notice.setTextSize(12);
        notice.setPadding(12,6,10,6);
        notice.setText("RA2 VM wird gestartet …");
        bar.addView(notice,new LinearLayout.LayoutParams(0,-2,1f));
        Button copy=new Button(this);
        copy.setText("Log kopieren");copy.setTextSize(11);
        copy.setOnClickListener(v->copyLog());
        bar.addView(copy,new LinearLayout.LayoutParams(-2,-2));
        Button restart=new Button(this);
        restart.setText("Neustart");restart.setTextSize(11);
        restart.setOnClickListener(v->{lastFailure="";browser.reload();notice.setText("RA2 VM wird neu geladen …");});
        bar.addView(restart,new LinearLayout.LayoutParams(-2,-2));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        root.addView(bar,p);
        setContentView(root);
        append("Engine=RA2 VM (GPL-3.0), origin="+RA2VmFiles.URL);
        append("DataDir="+files.originalDirectory()+" exists="+files.gameAvailable());
        append("GameDiscovery="+files.discoveryStatus());
        append("RA2ready="+files.ra2Available()+" YuriReady="+files.yuriAvailable());
        browser.loadUrl(RA2VmFiles.URL);
    }
    private void showFailure(String error) {
        lastFailure=error;
        runOnUiThread(()->{if(notice!=null){notice.setText(error);notice.setTextColor(0xffffc7a1);}});
    }
    private void append(String line){
        Log.d("RA2Vm",line);
        synchronized(log){if(log.size()>=240)log.removeFirst();log.addLast(line);}
    }
    private void copyLog(){
        StringBuilder result=new StringBuilder("RA2 Android VM - Diagnose\n");
        synchronized(log){for(String line:log)result.append(line).append('\n');}
        ClipboardManager clip=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clip!=null)clip.setPrimaryClip(ClipData.newPlainText("RA2 VM Diagnose",result.toString()));
        Toast.makeText(this,"RA2-VM-Protokoll kopiert",Toast.LENGTH_LONG).show();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_RESOURCE && pendingFiles!=null) {
            ValueCallback<android.net.Uri[]> callback=pendingFiles;pendingFiles=null;
            callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode,data));
        }
    }
    @Override protected void onDestroy() {
        if(browser!=null){browser.stopLoading();browser.destroy();browser=null;}
        super.onDestroy();
    }
}