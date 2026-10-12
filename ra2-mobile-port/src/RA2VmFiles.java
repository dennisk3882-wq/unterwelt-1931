package com.winlator;
import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import com.winlator.core.WineRegistryEditor;
import java.io.*;
import java.util.*;
public final class RA2VmFiles {
  public static final String HOST="ra2.android.local";
  public static final String URL="https://"+HOST+"/index.html";
  private final Context ctx;
  private final File base;
  private final RA2VmGameDiscovery.Result discovery;
  private final RA2VmMediaOverlay media;
  public RA2VmFiles(Context context) {
    ctx=context.getApplicationContext();
    ArrayList<File> roots=new ArrayList<>();
    int requested=ctx.getSharedPreferences("ra2_mobile",Context.MODE_PRIVATE).getInt("container_id",1);
    try {
      com.winlator.container.ContainerManager manager=new com.winlator.container.ContainerManager(ctx);
      com.winlator.container.Container preferred=manager.getContainerById(requested);
      if(preferred!=null)roots.add(preferred.getRootDir());
      for(com.winlator.container.Container candidate:manager.getContainers())
        if(preferred==null || candidate.id!=preferred.id)roots.add(candidate.getRootDir());
    } catch (Exception ignored) {
      // Legacy install metadata may be unavailable before the first container load.
    }
    roots.add(new File(ctx.getFilesDir(),"imagefs/home/xuser-"+requested));
    roots.add(new File(ctx.getFilesDir(),"imagefs/home/xuser-1"));
    discovery=RA2VmGameDiscovery.find(roots);
    base=discovery.directory;
    media=new RA2VmMediaOverlay(base);
  }
  public File originalDirectory(){return base;}
  public boolean gameAvailable(){return discovery.ra2Ready || discovery.yuriReady;}
  public String discoveryStatus(){return discovery.status;}
  public boolean ra2Available(){return discovery.ra2Ready;}
  public boolean yuriAvailable(){return discovery.yuriReady;}
  public boolean sovietCdReady(){return media.ra2DiscReady();}
  public String mediaStatus(){return "RA2-CD2="+media.ra2DiscReady()+" cdFiles="+media.discFileCount()+" cdPath="+media.ra2Disc().getAbsolutePath();}
  public WebResourceResponse serve(WebResourceRequest request) {
    Uri uri=request.getUrl();
    if(!HOST.equalsIgnoreCase(uri.getHost()) || !"https".equals(uri.getScheme()))return null;
    String path=uri.getPath();
    try {
      if(path==null || path.equals("/"))path="/index.html";
      if(path.contains("..") || path.indexOf('\\')>=0)return text(403,"Invalid path");
      if(path.equals("/android-installation.json")) return installationProfile();
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
  /**
   * Exposes the locally registered original CD credentials only to the APK's
   * private https://ra2.android.local WebView origin. The response is never
   * stored, logged, cached, sent to GitHub, or bundled with the APK.
   * The worker uses this to reproduce the legitimate Windows installation
   * registry, not to synthesize a license or accept a placeholder key.
   */
  private WebResourceResponse installationProfile() throws IOException {
    android.content.SharedPreferences pref=ctx.getSharedPreferences("ra2_mobile",Context.MODE_PRIVATE);
    String ra2=validatedKey(pref.getString("original_cd_key_ra2",""));
    String yuri=validatedKey(pref.getString("original_cd_key_yuri",""));
    if(ra2.isEmpty()||yuri.isEmpty()) {
      File driveC=base.getParentFile()==null?null:base.getParentFile().getParentFile();
      File wine=driveC==null?null:driveC.getParentFile();
      if(wine!=null) {
        File registryFile=new File(wine,"system.reg");
        if(registryFile.isFile()) {
          try (WineRegistryEditor registry=new WineRegistryEditor(registryFile)) {
            if(ra2.isEmpty())ra2=validatedKey(
              firstPresent(registry.getStringValue("Software\\Wow6432Node\\Westwood\\Red Alert 2","Serial"),
                           registry.getStringValue("Software\\Westwood\\Red Alert 2","Serial")));
            if(yuri.isEmpty())yuri=validatedKey(
              firstPresent(registry.getStringValue("Software\\Wow6432Node\\Westwood\\Yuri's Revenge","Serial"),
                           registry.getStringValue("Software\\Westwood\\Yuri's Revenge","Serial")));
          } catch(Exception ignored) {
            // Do not block the engine if the original registry is absent.
          }
        }
      }
    }
    JSONObject json=new JSONObject();
    try {
      json.put("ra2Serial",ra2);
      json.put("yuriSerial",yuri);
      json.put("ra2Cd2Ready",media.ra2DiscReady());
      json.put("cdEmulationEnabled",pref.getBoolean("vm_virtual_cd_enabled",true));
      json.put("ra2Valid",!ra2.isEmpty());
      json.put("yuriValid",!yuri.isEmpty());
    } catch(org.json.JSONException failure){throw new IOException("Cannot prepare registration metadata",failure);}
    byte[] bytes=json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Map<String,String> headers=new HashMap<>();
    headers.put("Cache-Control","no-store, private");
    headers.put("X-Content-Type-Options","nosniff");
    headers.put("Cross-Origin-Resource-Policy","same-origin");
    return new WebResourceResponse("application/json","UTF-8",200,"OK",headers,new ByteArrayInputStream(bytes));
  }
  private static String firstPresent(String first,String second){
    return RA2SerialPolicy.acceptableInput(first)?first:second;
  }
  private static String validatedKey(String input){
    return RA2SerialPolicy.acceptableInput(input)?RA2SerialPolicy.normalize(input):"";
  }

  private File resolve(String path)throws IOException {
    // Original game and extracted ISO media share one read-only virtual namespace.
    return media.resolve(path);
  }
  private WebResourceResponse game(Uri uri,String path,WebResourceRequest request)throws IOException {
    if(path.startsWith("ra2/"))path=path.substring(4);
    if(path.startsWith("yr/"))path=path.substring(3);
    if(path.equals(".list")){
      String dir=uri.getQueryParameter("dir");
      if(dir==null || dir.equals("ra2")||dir.equals("yr"))dir="";
      JSONArray files=new JSONArray();
      for(String name:media.listing(dir))files.put(name);
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
