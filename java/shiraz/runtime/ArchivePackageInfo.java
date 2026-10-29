// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.content.pm.*;
import java.io.*;
import java.util.zip.*;

/** Read-only, bounded APK identity inspection. Does not install or execute code. */
public final class ArchivePackageInfo {
 private ArchivePackageInfo(){}
 private static String string(BinaryXml.Value value){return value!=null&&value.type==3?value.text:null;}
 private static int integer(BinaryXml.Value value,int fallback){return value!=null&&value.type>=16&&value.type<=31?value.data:fallback;}
 private static int resource(BinaryXml.Value value){return value!=null&&value.type==1?value.data:0;}
 private static String qualify(String pkg,String name){return name==null?null:name.startsWith(".")?pkg+name:name.indexOf('.')<0?pkg+"."+name:name;}
 public static PackageInfo read(String path,int flags){
  int supported=PackageManager.GET_SIGNATURES|PackageManager.GET_SIGNING_CERTIFICATES;
  if((flags&~supported)!=0)throw new UnsupportedOperationException("Archive query flags: "+flags);
  if(path==null)return null;
  android.os.StrictMode.ThreadPolicy old=android.os.StrictMode.allowThreadDiskReads();
  try(ZipFile zip=new ZipFile(path)){
   if(zip.size()>100000)return null;
   ZipEntry entry=null;
   java.util.Enumeration<? extends ZipEntry> entries=zip.entries();
   while(entries.hasMoreElements()){ZipEntry candidate=entries.nextElement();if(candidate.getName().equals("AndroidManifest.xml")){if(entry!=null)return null;entry=candidate;}}
   if(entry==null||entry.isDirectory()||entry.getSize()>4*1024*1024)return null;
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();
   try(InputStream in=zip.getInputStream(entry)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>4*1024*1024)return null;bytes.write(buffer,0,n);}}
   BinaryXml.Node root=BinaryXml.parse(bytes.toByteArray());
   if(!root.name.equals("manifest"))return null;
   String pkg=null;for(BinaryXml.Attribute attribute:root.attributes)if(attribute.namespace.isEmpty()&&attribute.name.equals("package"))pkg=string(attribute.value);
   if(pkg==null||pkg.length()>1024||!pkg.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))return null;
   PackageInfo info=new PackageInfo();info.packageName=pkg;
   info.setLongVersionCode(((long)integer(root.android("versionCodeMajor"),0)<<32)|(integer(root.android("versionCode"),0)&0xffffffffL));
   info.versionName=string(root.android("versionName"));
   int versionResource=resource(root.android("versionName"));
   if(versionResource!=0){android.content.res.Resources resources=android.content.res.Resources.shirazOpen(path,360,640);try{info.versionName=resources.getString(versionResource);}finally{resources.shirazClose();}}
   info.sharedUserId=string(root.android("sharedUserId"));info.sharedUserLabel=resource(root.android("sharedUserLabel"));
   info.baseRevisionCode=integer(root.android("revisionCode"),0);info.installLocation=integer(root.android("installLocation"),-1);
   ApplicationInfo app=new ApplicationInfo();app.packageName=pkg;app.sourceDir=new File(path).getAbsolutePath();app.publicSourceDir=app.sourceDir;app.uid=-1;app.storageUuid=android.os.storage.StorageManager.UUID_DEFAULT;app.minSdkVersion=1;app.targetSdkVersion=1;
   BinaryXml.Node application=null;
   for(BinaryXml.Node node:root.children){
    if(node.name.equals("uses-sdk")){app.minSdkVersion=integer(node.android("minSdkVersion"),1);app.targetSdkVersion=integer(node.android("targetSdkVersion"),app.minSdkVersion);}
    if(node.name.equals("application")){if(application!=null)return null;application=node;}
   }
   if(application==null)return null;
   app.className=qualify(pkg,string(application.android("name")));app.name=app.className;
   app.labelRes=resource(application.android("label"));app.nonLocalizedLabel=string(application.android("label"));
   app.icon=resource(application.android("icon"));app.logo=resource(application.android("logo"));app.banner=resource(application.android("banner"));app.theme=resource(application.android("theme"));
   app.enabled=integer(application.android("enabled"),1)!=0;
   app.flags=(integer(application.android("hasCode"),1)!=0?ApplicationInfo.FLAG_HAS_CODE:0)|(integer(application.android("debuggable"),0)!=0?ApplicationInfo.FLAG_DEBUGGABLE:0);
   info.applicationInfo=app;
   if((flags&PackageManager.GET_SIGNATURES)!=0)info.signatures=ApkCertificates.read(path);
   if((flags&PackageManager.GET_SIGNING_CERTIFICATES)!=0)info.signingInfo=new SigningInfo(ApkCertificates.read(path));
   return info;
  }catch(IOException|IllegalArgumentException error){return null;}
  finally{android.os.StrictMode.setThreadPolicy(old);}
 }
}
