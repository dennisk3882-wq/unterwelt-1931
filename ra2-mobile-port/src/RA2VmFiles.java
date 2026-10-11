package com.winlator;
import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import org.json.JSONArray;
import java.io.*;
import java.util.*;
public final class RA2VmFiles {
  public static final String HOST="ra2.android.local";
  public static final String URL="https://"+HOST+"/index.html";
  private final Context ctx;
  private final File base;
  public RA2VmFiles(Context context) {
    ctx=context.getApplicationContext();
    base=new File(context.getFilesDir(),"imagefs/home/xuser-1/.wine/drive_c/Westwood/RA2");
  }
  public File originalDirectory(){return base;}
  public boolean gameAvailable(){return new File(base,"game.exe").isFile();}
  public WebResourceResponse serve(WebResourceRequest request) {
    Uri uri=request.getUrl();
    if(!HOST.equalsIgnoreCase(uri.getHost()) || !"https".equals(uri.getScheme()))return null;
    String path=uri.getPath();
    try {
      if(path==null || path.equals("/"))path="/index.html";
      if(path.contains("..") || path.indexOf('\\')>=0)return text(403,"Invalid path");
      if(path.startsWith("/game/"))return game(uri,path.substring(6),request);
      InputStream stream=ctx.getAssets().open("ra2vm/"+path.substring(1));
      String mime="application/octet-stream";
      if(path.endsWith(".html"))mime="text/html";
      else if(path.endsWith(".js") || path.endsWith(".mjs"))mime="text/javascript";
      else if(path.endsWith(".css"))mime="text/css";
      else if(path.endsWith(".json"))mime="application/json";
      else if(path.endsWith(".wasm"))mime="application/wasm";
      else if(path.endsWith(".png"))mime="image/png";
      else if(path.endsWith(".svg"))mime="image/svg+xml";
      return reply(200,mime,stream,new HashMap<>());
    } catch(FileNotFoundException e){return text(404,"File not found: "+path);}
      catch(Exception e){return text(500,e.toString());}
  }
  private File resolve(String path)throws IOException {
    File current=base;
    for(String part:path.split("/")){
      if(part.isEmpty() || part.equals("."))continue;
      if(part.equals(".."))throw new IOException("Invalid path");
      File[] items=current.listFiles(); File found=null;
      if(items!=null)for(File f:items)if(f.getName().equalsIgnoreCase(part)){found=f;break;}
      current=found!=null?found:new File(current,part);
    }
    String root=base.getCanonicalPath(),actual=current.getCanonicalPath();
    if(!actual.equals(root)&&!actual.startsWith(root+File.separator))throw new IOException("Outside game directory");
    return current;
  }
  private WebResourceResponse game(Uri uri,String path,WebResourceRequest request)throws IOException {
    if(path.startsWith("ra2/"))path=path.substring(4);
    if(path.startsWith("yr/"))path=path.substring(3);
    if(path.equals(".list")){
      String dir=uri.getQueryParameter("dir");
      if(dir==null || dir.equals("ra2")||dir.equals("yr"))dir="";
      File folder=resolve(dir);
      if(!folder.isDirectory())return text(404,"[]");
      JSONArray files=new JSONArray();
      File[] entries=folder.listFiles();
      if(entries!=null)for(File f:entries)if(!f.getName().startsWith("."))files.put(f.getName());
      return reply(200,"application/json",new ByteArrayInputStream(files.toString().getBytes("UTF-8")),new HashMap<>());
    }
    File file=resolve(path);
    if(!file.isFile())return text(404,"Not found");
    long size=file.length(),start=0,end=size-1;
    String range=request.getRequestHeaders().get("Range");
    boolean partial=false;
    if(range!=null && range.startsWith("bytes=")){
      String[] p=range.substring(6).split("-",2);
      try{
        start=Long.parseLong(p[0]);
        if(p.length>1&&!p[1].isEmpty())end=Math.min(end,Long.parseLong(p[1]));
        if(start<0||start>=size||end<start)return text(416,"Invalid range");
        partial=true;
      }catch(NumberFormatException ignored){start=0;end=size-1;}
    }
    long remaining=partial?end-start+1:size;
    FileInputStream in=new FileInputStream(file);
    try{
      long skip=start;
      while(skip>0){long n=in.skip(skip);if(n<=0)throw new IOException("Seek failed");skip-=n;}
    }catch(IOException e){in.close();throw e;}
    Map<String,String> extra=new HashMap<>();
    extra.put("Content-Length",String.valueOf(remaining));
    extra.put("Accept-Ranges","bytes");
    if(partial)extra.put("Content-Range","bytes "+start+"-"+end+"/"+size);
    return reply(partial?206:200,"application/octet-stream",new FilterInputStream(in){
      long left=remaining;
      @Override public int read()throws IOException{if(left<=0)return -1;int n=super.read();if(n>=0)left--;return n;}
      @Override public int read(byte[] b,int o,int c)throws IOException{if(left<=0)return -1;int n=super.read(b,o,(int)Math.min(left,c));if(n>0)left-=n;return n;}
    },extra);
  }
  private WebResourceResponse text(int code,String body){
    return reply(code,"text/plain",new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)),new HashMap<>());
  }
  private WebResourceResponse reply(int code,String mime,InputStream stream,Map<String,String> extras){
    Map<String,String> h=new HashMap<>();
    h.put("Cross-Origin-Opener-Policy","same-origin");
    h.put("Cross-Origin-Embedder-Policy","require-corp");
    h.put("Cross-Origin-Resource-Policy","same-origin");
    h.put("Cache-Control","no-store");
    h.putAll(extras);
    return new WebResourceResponse(mime,null,code,code==206?"Partial Content":code==200?"OK":"Error",h,stream);
  }
}
