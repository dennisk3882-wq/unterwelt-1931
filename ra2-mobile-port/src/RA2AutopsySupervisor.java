package com.winlator;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Autonomous, strictly on-device RA2 autopsy coordinator.
 * Compares a zero-input baseline with a reproducible scripted-input run.
 * Records selected actual WebView frames and diagnoses a deterministic failure.
 * WebView must stay foreground: Android may suspend its renderer in background.
 */
public final class RA2AutopsySupervisor {
    public interface Reporter { void write(String line); void finished(String result); }
    private final Activity activity;
    private final Reporter reporter;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService disk=Executors.newSingleThreadExecutor();
    private final ArrayList<String> events=new ArrayList<>();
    private final ArrayList<File> captures=new ArrayList<>();
    private final ArrayList<Double> visualDistances=new ArrayList<>();
    private File sessionDir;
    private Bitmap previousSmall;
    private WebView web;
    private boolean running;
    private boolean finished;
    private String scenario="";
    private String passiveResult="";
    private String activeResult="";
    private long lastCaptureAt;
    private int images;

    public RA2AutopsySupervisor(Activity activity,Reporter reporter){
        this.activity=activity;
        this.reporter=reporter;
    }
    public boolean isRunning(){return running;}
    public File latestArchive(){
        File dir=new File(activity.getFilesDir(),"ra2-autopsy");
        File[] all=dir.listFiles(f->f.getName().endsWith(".zip"));
        if(all==null||all.length==0)return null;
        File result=all[0];
        for(File f:all)if(f.lastModified()>result.lastModified())result=f;
        return result;
    }
    public void start(WebView view){
        if(running)return;
        running=true;finished=false;
        web=view;images=0;passiveResult="";activeResult="";lastCaptureAt=0;
        events.clear();captures.clear();visualDistances.clear();previousSmall=null;
        File dir=new File(activity.getFilesDir(),"ra2-autopsy");
        if(!dir.exists()&&!dir.mkdirs()){running=false;reporter.write("AUTOPSY FILE_ERROR");return;}
        sessionDir=new File(dir,"case_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date()));
        if(!sessionDir.mkdirs()){running=false;reporter.write("AUTOPSY STORAGE_ERROR");return;}
        record("AUTOPSY SESSION_STARTED mode=two_scenarios max=2x180s");
        load("passive");
    }
    private void load(String mode){
        if(!running||web==null)return;
        scenario=mode;
        lastCaptureAt=0;
        record("AUTOPSY SCENARIO_BEGIN="+mode);
        web.loadUrl(RA2VmFiles.URL+"?start-page=battle&ra2-superdiag=1&scenario="+mode);
    }
    public void observe(String msg){
        if(!running||!msg.startsWith("[RA2-AUTO] "))return;
        final String event=msg.substring("[RA2-AUTO] ".length());
        record(event);
        if(event.startsWith("FIRST_FRAME")||event.startsWith("BATTLEFIELD_REACHED"))
            ui.postDelayed(()->snapshot("battle"),450);
        if(event.startsWith("PAGE")&&(event.contains("MultiplayerScore")||event.contains("GameOver")))
            ui.postDelayed(()->snapshot("score"),500);
        if(event.startsWith("SAMPLE")){
            long now=android.os.SystemClock.elapsedRealtime();
            if(now-lastCaptureAt>=12500){lastCaptureAt=now;ui.post(()->snapshot("sampling"));}
        }
        if(event.startsWith("END ")){
            final String endedMode=scenario;
            if("passive".equals(endedMode))passiveResult=event;
            else activeResult=event;
            ui.postDelayed(()->{
                snapshot("final_"+endedMode);
                if("passive".equals(endedMode)) {
                    record("AUTOPSY BASELINE_DONE next=active");
                    load("active");
                } else finalizeSession();
            },1250);
        }
    }
    /** Take small private screenshots, never exfiltrate them or expose key inputs. */
    private void snapshot(String stage){
        if(!running || web==null || sessionDir==null || images>=16)return;
        if(web.getWidth()<100||web.getHeight()<100)return;
        int width=900;
        int height=Math.max(260,Math.min(650,Math.round(900f*web.getHeight()/web.getWidth())));
        Bitmap bitmap=null;
        try{
            bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.RGB_565);
            Canvas canvas=new Canvas(bitmap);
            canvas.drawColor(android.graphics.Color.BLACK);
            canvas.scale((float)width/web.getWidth(),(float)height/web.getHeight());
            web.draw(canvas);
            final Bitmap small=Bitmap.createScaledBitmap(bitmap,64,36,true);
            if(previousSmall!=null){
                long diff=0;
                for(int y=0;y<36;y+=3)for(int x=0;x<64;x+=3){
                    int a=small.getPixel(x,y),b=previousSmall.getPixel(x,y);
                    diff+=Math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b));
                    diff+=Math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b));
                    diff+=Math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b));
                }
                double score=Math.round(1000.0*diff/(22*12*3*255))/10.0;
                visualDistances.add(score);
                record("AUTOPSY FRAME_DIFF stage="+stage+" percentApprox="+score);
                previousSmall.recycle();
            }
            previousSmall=small;
            final String safe=stage.replaceAll("[^A-Za-z0-9_]","_");
            File jpg=new File(sessionDir,String.format(Locale.ROOT,"%02d_%s_%s.jpg",
                ++images,scenario,safe));
            try(FileOutputStream out=new FileOutputStream(jpg)){
                if(!bitmap.compress(Bitmap.CompressFormat.JPEG,76,out))
                    throw new IllegalStateException("Failed to encode visual sample");
            }
            captures.add(jpg);
            record("AUTOPSY SCREENSHOT saved="+jpg.getName()+
                " bytes="+jpg.length()+" shape="+width+"x"+height);
        }catch(Exception e){record("AUTOPSY FRAME_CAPTURE_ERROR type="+e.getClass().getSimpleName());}
        finally{if(bitmap!=null)bitmap.recycle();}
    }
    private void record(String event){
        String stamped="t="+android.os.SystemClock.elapsedRealtime()+" "+event;
        if(events.size()<1800)events.add(stamped);
        reporter.write("[RA2-BLACKBOX] "+event);
    }
    private static String token(String event,String name){
        for(String part:event.split(" "))if(part.startsWith(name+"="))return part.substring(name.length()+1);
        return "";
    }
    private String conclusions(){
        String p=token(passiveResult,"verdict"),a=token(activeResult,"verdict");
        StringBuilder out=new StringBuilder();
        out.append("RA2 AUTOPSIE – AUTOMATISCHER ABSCHLUSSBERICHT\n");
        out.append("Durchlauf 1 OHNE Spielereingriffe: ").append(p.isEmpty()?"NICHT ABGESCHLOSSEN":p).append("\n");
        out.append("Durchlauf 2 MIT Steuerbefehlen: ").append(a.isEmpty()?"NICHT ABGESCHLOSSEN":a).append("\n");
        out.append("PASSIVE: ").append(passiveResult).append("\n");
        out.append("ACTIVE: ").append(activeResult).append("\n");
        if("MATCH_ENDED".equals(p)&&"MATCH_ENDED".equals(a)){
            long t1=parseDuration(passiveResult),t2=parseDuration(activeResult);
            long diff=Math.abs(t1-t2);
            out.append("Befund: Beide Gefechte enden bereits in der Testphase.\n");
            if(t1>0&&t2>0&&diff<15000)
                out.append("Hoher Verdacht auf reproduzierbare Installations-/Engine-Pruefung; "+
                    "Bedieneingaben sind als Hauptursache unwahrscheinlich. "+
                    "Kopierschutz nicht bewiesen.\n");
        }else if("MATCH_ENDED".equals(p)&&!"MATCH_ENDED".equals(a))
            out.append("Befund: Aktiver und passiver Verlauf weichen ab; Spielaktionen koennen relevant sein.\n");
        else if("MATCH_ENDED".equals(a)&&!"MATCH_ENDED".equals(p))
            out.append("Befund: Nur aktiver Verlauf erreicht Ergebnisseite. Eingaben/Initialisierung untersuchen.\n");
        else
            out.append("Befund: Kein reproduzierbarer normaler Spielabschluss in beiden Laeufen nachgewiesen.\n");
        boolean missing=false,drive=false,house=false;
        java.util.LinkedHashSet<String> missingValues=new java.util.LinkedHashSet<>();
        for(String s:events){
            if(s.contains("RegQuery")&&s.contains("FEHLT")){
                missing=true;
                int pos=s.indexOf("Wertname=");
                if(pos>=0){
                    String value=s.substring(pos+9).split(" ")[0];
                    if(missingValues.size()<16)missingValues.add(value);
                }
            }
            if(s.contains("D:=CDROM"))drive=true;
            if(s.contains("[RA2-HOUSE]"))house=true;
        }
        out.append("Fehlende Win32-Registrywerte: ").append(missing?"JA":"NICHT IN BLACKBOX BESTAETIGT");
        if(!missingValues.isEmpty())out.append(" ").append(missingValues);
        out.append("\n");
        out.append("CD-Laufwerk D: als CD-ROM in bisheriger Diagnose: ").append(drive?"JA":"NICHT GEPRUEFT").append("\n");
        out.append("Bilder: ").append(images).append(" | Vergleichswerte: ").append(visualDistances.size()).append("\n");
        out.append("Schutzstatus/Originalschluessel werden nicht getestet oder offengelegt.\n");
        out.append("WICHTIG: Dies ist eine deterministische Software-Forensik, keine "+
            "generative Bild-KI oder ein strategisch vollstaendiger Spieler.\n");
        return out.toString();
    }
    private static long parseDuration(String s){
        try{return Long.parseLong(token(s,"durationMs"));}catch(Exception ignored){return -1;}
    }
    private void finalizeSession(){
        if(!running||finished)return;
        finished=true;running=false;
        String verdict=conclusions();
        File summary=new File(sessionDir,"Analyse.txt");
        File timeline=new File(sessionDir,"Timeline.txt");
        try{
            try(FileOutputStream a=new FileOutputStream(summary)){
                a.write(verdict.getBytes(StandardCharsets.UTF_8));
            }
            try(FileOutputStream b=new FileOutputStream(timeline)){
                for(String e:events){b.write(e.getBytes(StandardCharsets.UTF_8));b.write('\n');}
            }
        }catch(Exception e){reporter.write("AUTOPSY write-error="+e.getClass().getSimpleName());}
        // Keep only the latest three .zip reports; no unbounded storage accumulation.
        final File zip=new File(sessionDir.getParentFile(),sessionDir.getName()+".zip");
        disk.execute(()->{
            try(ZipOutputStream out=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))){
                ArrayList<File> all=new ArrayList<>(captures);all.add(summary);all.add(timeline);
                for(File file:all)if(file.exists()){
                    out.putNextEntry(new ZipEntry(file.getName()));
                    try(java.io.FileInputStream src=new java.io.FileInputStream(file)){
                        byte[] data=new byte[16384];int n;
                        while((n=src.read(data))!=-1)out.write(data,0,n);
                    }
                    out.closeEntry();
                }
            }catch(Exception e){Log.w("RA2Autopsy","zip write failed",e);}
            File[] zipFiles=sessionDir.getParentFile().listFiles(f->f.getName().endsWith(".zip"));
            if(zipFiles!=null&&zipFiles.length>3){
                java.util.Arrays.sort(zipFiles,java.util.Comparator.comparingLong(File::lastModified));
                for(int i=0;i<zipFiles.length-3;i++)zipFiles[i].delete();
            }
            activity.runOnUiThread(()->{
                ClipboardManager clip=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if(clip!=null)clip.setPrimaryClip(ClipData.newPlainText("RA2 Auto-Analyse",verdict));
                reporter.finished(verdict);
                Toast.makeText(activity,"Automatische Analyse abgeschlossen – Bericht kopiert",Toast.LENGTH_LONG).show();
            });
        });
    }
    public void cancel(){running=false;finished=true;reporter.write("AUTOPSY USER_STOPPED");}
    public void close(){
        cancel();ui.removeCallbacksAndMessages(null);
        disk.shutdown();
        if(previousSmall!=null){previousSmall.recycle();previousSmall=null;}
    }
}
