package com.winlator;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
/** CAB extraction in Android, with validated staging and atomic replacement. */
public final class CabExtractor {
 static { System.loadLibrary("ra2cab"); }
 private CabExtractor() {}
 private static native long extractFile(String archive,String output,String name) throws IOException;
 public interface Progress {void update(String name);}
 public static String extractRequired(File archives,File destination,String[] names,Progress progress) throws IOException {
  File[] cabs=archives.listFiles(f->f.isFile()&&f.getName().toLowerCase(Locale.ROOT).endsWith(".cab"));
  if(cabs==null||cabs.length==0)throw new IOException("Keine CAB-Dateien in "+archives.getName());
  Arrays.sort(cabs,(a,b)->a.getName().compareToIgnoreCase(b.getName()));
  if(!destination.isDirectory()&&!destination.mkdirs())throw new IOException("Zielordner nicht beschreibbar");
  StringBuilder report=new StringBuilder();
  for(String name:names){
   if(!name.matches("[A-Za-z0-9_.-]+"))throw new IOException("Ungültiger Dateiname");
   File target=new File(destination,name);File[] existing=destination.listFiles();
   if(existing!=null)for(File f:existing)if(f.isFile()&&f.getName().equalsIgnoreCase(name)){target=f;break;}
   File stage=new File(destination,name+".extracting");boolean done=false;StringBuilder failures=new StringBuilder();
   for(File cab:cabs){
    if(Thread.currentThread().isInterrupted())throw new IOException("Einrichtung unterbrochen");
    progress.update(cab.getName()+" → "+name);
    try{
     long size=extractFile(cab.getAbsolutePath(),stage.getAbsolutePath(),name);if(size<0)continue;
     if(size==0||!stage.isFile()||stage.length()!=size)throw new IOException("Entpackte Dateigröße stimmt nicht: "+name);
     if(!stage.renameTo(target))throw new IOException("Datei konnte nicht übernommen werden: "+name);
     report.append(cab.getName()).append(" -> ").append(name).append(" ").append(size).append(" bytes\n");done=true;break;
    }catch(IOException e){failures.append(cab.getName()).append(": ").append(e.getMessage()).append("; ");}
    finally{if(stage.exists())stage.delete();}
   }
   if(!done)throw new IOException(name+" konnte nicht entpackt werden. "+failures);
  }
  return report.toString();
 }
}
