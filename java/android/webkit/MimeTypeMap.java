// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;
import java.util.HashMap;
import java.util.Locale;
public class MimeTypeMap {
 private static final MimeTypeMap INSTANCE=new MimeTypeMap();
 private static final HashMap<String,String> EXT_TO_MIME=new HashMap<String,String>();
 private static final HashMap<String,String> MIME_TO_EXT=new HashMap<String,String>();
 private static void map(String ext,String mime){
  EXT_TO_MIME.put(ext,mime);
  if(!MIME_TO_EXT.containsKey(mime))MIME_TO_EXT.put(mime,ext);
 }
 static {
  map("jpg","image/jpeg");map("jpeg","image/jpeg");map("png","image/png");map("gif","image/gif");
  map("webp","image/webp");map("bmp","image/bmp");map("svg","image/svg+xml");map("ico","image/x-icon");
  map("mp4","video/mp4");map("webm","video/webm");map("mkv","video/x-matroska");map("3gp","video/3gpp");
  map("mp3","audio/mpeg");map("ogg","audio/ogg");map("opus","audio/ogg");map("wav","audio/x-wav");
  map("m4a","audio/mp4");map("aac","audio/aac");map("flac","audio/flac");
  map("txt","text/plain");map("html","text/html");map("htm","text/html");map("css","text/css");
  map("csv","text/csv");map("xml","text/xml");map("vcf","text/x-vcard");map("ics","text/calendar");
  map("json","application/json");map("pdf","application/pdf");map("zip","application/zip");
  map("gz","application/gzip");map("tar","application/x-tar");map("apk","application/vnd.android.package-archive");
  map("xdc","application/webxdc+zip");
 }
 private MimeTypeMap(){}
 public static MimeTypeMap getSingleton(){return INSTANCE;}
 public static String getFileExtensionFromUrl(String url){
  if(url==null||url.isEmpty())return "";
  int fragment=url.lastIndexOf('#');if(fragment>0)url=url.substring(0,fragment);
  int query=url.lastIndexOf('?');if(query>0)url=url.substring(0,query);
  int slash=url.lastIndexOf('/');String filename=slash>=0?url.substring(slash+1):url;
  if(filename.isEmpty())return "";
  for(int i=0;i<filename.length();i++){
   char c=filename.charAt(i);
   if(!Character.isLetterOrDigit(c)&&c!='+'&&c!='.'&&c!='_'&&c!='-')return "";
  }
  int dot=filename.lastIndexOf('.');
  return dot>=0?filename.substring(dot+1):"";
 }
 public boolean hasMimeType(String mimeType){
  return mimeType!=null&&!mimeType.isEmpty()&&MIME_TO_EXT.containsKey(mimeType.toLowerCase(Locale.ROOT));
 }
 public String getMimeTypeFromExtension(String extension){
  return extension==null||extension.isEmpty()?null:EXT_TO_MIME.get(extension.toLowerCase(Locale.ROOT));
 }
 public boolean hasExtension(String extension){
  return extension!=null&&!extension.isEmpty()&&EXT_TO_MIME.containsKey(extension.toLowerCase(Locale.ROOT));
 }
 public String getExtensionFromMimeType(String mimeType){
  return mimeType==null||mimeType.isEmpty()?null:MIME_TO_EXT.get(mimeType.toLowerCase(Locale.ROOT));
 }
}
