// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;
import android.content.Context;
import android.text.Html;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
/** Placeholder view type: no web engine; HTML payloads are shown as plain text. */
public class WebView extends FrameLayout {
 public interface FindListener { void onFindResultReceived(int activeMatchOrdinal,int numberOfMatches,boolean isDoneCounting); }
 public class PrivateAccess {}
 private final WebSettings settings=new WebSettings();
 private final PrivateAccess privateAccess=new PrivateAccess();
 private final TextView body;
 private String url="";
 private int progress;
 private String title;
 private FindListener findListener;
 private WebViewClient client;
 private WebChromeClient chrome;
 public PrivateAccess shirazPrivateAccess(){return privateAccess;}
 public WebView(Context context){this(context,null);}
 public WebView(Context context,AttributeSet attrs){this(context,attrs,0);}
 public WebView(Context context,AttributeSet attrs,int defStyleAttr){
  super(context,attrs,defStyleAttr);
  body=new TextView(context);
  body.setPadding(24,24,24,24);
  ScrollView scroll=new ScrollView(context);
  scroll.addView(body,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
  addView(scroll,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
 }
 /** Disabling debugging is valid without an engine; enabling it is unsupported. */
 public static void setWebContentsDebuggingEnabled(boolean enabled){
  if(enabled)throw new UnsupportedOperationException("WebView remote debugging requires a web engine");
 }
 public static WebViewFactoryProvider getFactory(){return WebViewFactory.getProvider();}
 public WebSettings getSettings(){return settings;}
 public void loadUrl(String url,java.util.Map<String,String> additionalHttpHeaders){
  if(additionalHttpHeaders!=null&&!additionalHttpHeaders.isEmpty()&&url!=null&&
      (url.regionMatches(true,0,"http:",0,5)||url.regionMatches(true,0,"https:",0,6)))
   throw new UnsupportedOperationException("WebView HTTP requests require a web engine");
  loadUrl(url);
 }
 public void loadUrl(String url){
  this.url=url==null?"":url;
  progress=0;
  title=null;
  if(client!=null)client.onPageStarted(this,this.url,null);
  CharSequence page=fetch(this.url);
  show(page);
  progress=100;
  if(client!=null)client.onPageFinished(this,this.url);
  if(chrome!=null){chrome.onProgressChanged(this,100);if(title!=null)chrome.onReceivedTitle(this,title);}
 }
 public void loadData(String data,String mimeType,String encoding){loadDataWithBaseURL(null,data,mimeType,encoding,null);}
 public void loadDataWithBaseURL(String baseUrl,String data,String mimeType,String encoding,String historyUrl){
  url=historyUrl==null?(baseUrl==null?"":baseUrl):historyUrl;
  progress=0;
  title=null;
  if(client!=null)client.onPageStarted(this,url,null);
  show(htmlContent(data));
  progress=100;
  if(client!=null)client.onPageFinished(this,url);
  if(chrome!=null){chrome.onProgressChanged(this,100);if(title!=null)chrome.onReceivedTitle(this,title);}
 }
 /** Static HTML metadata only; script-driven document titles require a web engine. */
 private CharSequence htmlContent(String html){
  if(html!=null){
   String metadata=html.replaceAll("(?is)<!--.*?-->|<script\\b[^>]*>.*?</script\\s*>|<style\\b[^>]*>.*?</style\\s*>","");
   java.util.regex.Matcher match=java.util.regex.Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title\\s*>").matcher(metadata);
   if(match.find())title=plain(match.group(1)).toString().replaceAll("[\\t\\n\\r\\f ]+"," ").trim();
  }
  return plain(html);
 }
 private void show(CharSequence text){body.setText(text==null?"":text);}
 private CharSequence fetch(String target){
  if(target==null||target.isEmpty())return "";
  String asset=assetPath(target);
  if(asset!=null){
   try{
    byte[] bytes=readAsset(asset);
    Charset charset=charsetOf(null);
    return htmlContent(new String(bytes,charset));
   }catch(Exception error){
    System.err.println("SHIRAZ_WEBVIEW_ASSET_FAILED "+asset+" "+error.getMessage());
    return target;
   }
  }
  if(target.startsWith("file:")){
   try{
    java.net.URI uri=new java.net.URI(target);
    if(uri.getAuthority()!=null&&!uri.getAuthority().isEmpty())throw new java.io.IOException("Remote file authority");
    java.io.File file=new java.io.File(uri.getPath()).getCanonicalFile();
    java.io.File files=getContext().getFilesDir().getCanonicalFile();
    java.io.File cache=getContext().getCacheDir().getCanonicalFile();
    if(!within(file,files)&&!within(file,cache))throw new java.io.IOException("File outside app storage");
    try(InputStream in=new java.io.FileInputStream(file)){
     return htmlContent(new String(readContent(in),StandardCharsets.UTF_8));
    }
   }catch(Exception error){throw new IllegalArgumentException("Cannot load app-local WebView content",error);}
  }
  if(target.startsWith("data:")){
   int comma=target.indexOf(',');
   if(comma>5){
    String meta=target.substring(5,comma);
    String payload=target.substring(comma+1);
    try{
     byte[] bytes=meta.toLowerCase().contains(";base64")?java.util.Base64.getDecoder().decode(payload):payload.getBytes(StandardCharsets.UTF_8);
     return htmlContent(new String(bytes,charsetOf(meta)));
    }catch(RuntimeException error){return target;}
   }
  }
  return target;
 }
 private static boolean within(java.io.File file,java.io.File directory){
  return file.getPath().startsWith(directory.getPath()+java.io.File.separator);
 }
 private byte[] readAsset(String path)throws Exception{
  try(InputStream in=getContext().getAssets().open(path)){return readContent(in);}
 }
 private static byte[] readContent(InputStream in)throws java.io.IOException{
  try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[8192];int n;
   while((n=in.read(buffer))!=-1){
    if(n>4*1024*1024-out.size())throw new java.io.IOException("WebView content too large");
    out.write(buffer,0,n);
   }
   return out.toByteArray();
  }
 }
 private static String assetPath(String target){
  final String prefix="file:///android_asset/";
  if(!target.startsWith(prefix))return null;
  String path=target.substring(prefix.length());
  int cut=path.length();
  for(int i=0;i<path.length();i++){char c=path.charAt(i);if(c=='?'||c=='#'){cut=i;break;}}
  path=path.substring(0,cut);
  if(path.isEmpty()||path.charAt(0)=='/'||path.indexOf('\0')>=0||path.indexOf('\\')>=0)return null;
  for(String part:path.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))return null;
  return path;
 }
 private static Charset charsetOf(String meta){
  if(meta!=null){
   String lower=meta.toLowerCase();
   int at=lower.indexOf("charset=");
   if(at>=0){
    String name=meta.substring(at+8).trim();
    int end=name.length();
    for(int i=0;i<name.length();i++){char c=name.charAt(i);if(c==';'||c==' '||c==','){end=i;break;}}
    try{return Charset.forName(name.substring(0,end));}catch(RuntimeException ignored){}
   }
  }
  return StandardCharsets.UTF_8;
 }
 private static CharSequence plain(String html){
  if(html==null||html.isEmpty())return "";
  try{
   CharSequence spanned=Html.fromHtml(html);
   if(spanned.length()<=32000)return spanned;
  }catch(IllegalArgumentException ignored){}
  String text=strip(html);
  if(text.length()>32000)text=text.substring(0,32000)+"\n…";
  return text;
 }
 private static String strip(String html){
  StringBuilder out=new StringBuilder(Math.min(html.length(),32000));
  String skip=null;boolean entity=false;StringBuilder ent=new StringBuilder();
  for(int i=0;i<html.length();i++){
   char c=html.charAt(i);
   if(skip!=null){
    if(c=='<'){int end=html.indexOf('>',i+1);if(end<0)break;String body=html.substring(i+1,end).trim().toLowerCase();i=end;if(body.startsWith("/")&&body.substring(1).trim().startsWith(skip))skip=null;}
    continue;
   }
   if(entity){
    if(c==';'){out.append(decode(ent.toString()));ent.setLength(0);entity=false;}
    else if(ent.length()>16){out.append('&').append(ent).append(c);ent.setLength(0);entity=false;}
    else ent.append(c);
    continue;
   }
   if(c=='<'){
    int end=html.indexOf('>',i+1);if(end<0){out.append(html.substring(i));break;}
    String body=html.substring(i+1,end).trim();i=end;
    if(body.startsWith("!--")){int close=html.indexOf("-->",end);if(close>=0)i=close+2;continue;}
    boolean closing=body.startsWith("/");if(closing)body=body.substring(1).trim();
    boolean self=body.endsWith("/");if(self)body=body.substring(0,body.length()-1).trim();
    int split=0;while(split<body.length()&&!Character.isWhitespace(body.charAt(split)))split++;
    String tag=body.substring(0,split).toLowerCase();
    if(!closing&&(tag.equals("script")||tag.equals("style"))&&!self){skip=tag;continue;}
    if(tag.equals("br")||tag.equals("p")||tag.equals("div")||tag.equals("li")||tag.equals("tr")||tag.equals("h1")||tag.equals("h2")||tag.equals("h3")||tag.equals("h4")||tag.equals("h5")||tag.equals("h6")||tag.equals("blockquote")||tag.equals("ul")||tag.equals("ol")){
     if(out.length()>0&&out.charAt(out.length()-1)!='\n')out.append('\n');
     if(!closing&&tag.equals("li"))out.append("• ");
    }
    continue;
   }
   if(c=='&'){entity=true;continue;}
   out.append(c);
   if(out.length()>32000)break;
  }
  if(entity)out.append('&').append(ent);
  return out.toString().replace('\u00a0',' ').trim();
 }
 private static String decode(String name){
  if("amp".equals(name)||"#38".equals(name))return "&";
  if("lt".equals(name)||"#60".equals(name))return "<";
  if("gt".equals(name)||"#62".equals(name))return ">";
  if("quot".equals(name)||"#34".equals(name))return "\"";
  if("apos".equals(name)||"#39".equals(name))return "'";
  if("nbsp".equals(name)||"#160".equals(name))return " ";
  if(name.startsWith("#x")||name.startsWith("#X"))try{return new String(Character.toChars(Integer.parseInt(name.substring(2),16)));}catch(RuntimeException error){return "&"+name+";";}
  if(name.startsWith("#"))try{return new String(Character.toChars(Integer.parseInt(name.substring(1))));}catch(RuntimeException error){return "&"+name+";";}
  return "&"+name+";";
 }
 public String getTitle(){return title;}
 public int getProgress(){return progress;}
 public String getUrl(){return url;}
 public void stopLoading(){}
 public void reload(){}
 public boolean canGoBack(){return false;}
 public boolean canGoForward(){return false;}
 public void goBack(){}
 public void goForward(){}
 public void clearCache(boolean includeDiskFiles){}
 public void clearHistory(){}
 public void clearFormData(){}
 public void setWebViewClient(WebViewClient client){this.client=client;}
 public void setWebChromeClient(WebChromeClient client){chrome=client;}
 public void addJavascriptInterface(Object object,String name){}
 public void removeJavascriptInterface(String name){}
 public void evaluateJavascript(String script,ValueCallback<String> resultCallback){if(resultCallback!=null)resultCallback.onReceiveValue(null);}
 public void setDownloadListener(DownloadListener listener){}
 public void setFindListener(FindListener listener){findListener=listener;}
 public void findAllAsync(String find){if(findListener!=null)findListener.onFindResultReceived(0,0,true);}
 public void clearMatches(){}
 public void setNetworkAvailable(boolean networkUp){}
 public void onPause(){}
 public void onResume(){}
 public void pauseTimers(){}
 public void resumeTimers(){}
 public void freeMemory(){}
 public void destroy(){removeAllViews();}
}
