// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * macOS install boundary: the Shiraz launcher owns the app library, and an app
 * that may install packages talks to it through a request directory.
 *
 * The launcher sets SHIRAZ_LAUNCHER_APPS (its library, read-only here) and a
 * per-session SHIRAZ_LAUNCHER_INBOX for every run. It decides per request:
 * installs need REQUEST_INSTALL_PACKAGES, removals REQUEST_DELETE_PACKAGES in
 * the requesting app's manifest. A request is {@code <id>.request} (properties) plus,
 * for installs, {@code <id>.apk}; both appear by atomic rename, the request last.
 * The launcher answers with {@code <id>.result} (status=ok|failed, package,
 * message) and deletes the request files.
 */
final class LauncherInbox {
 private LauncherInbox(){}
 private static final long POLL_MILLIS=200;

 private static File directory(String name){
  String path=System.getenv(name);if(path==null||path.isEmpty())return null;
  File file=new File(path);return file.isAbsolute()&&file.isDirectory()?file:null;
 }
 /** The launcher library's apps/ directory, or null outside the launcher. */
 static File apps(){return HostOs.darwin()?directory("SHIRAZ_LAUNCHER_APPS"):null;}
 static File inbox(){return HostOs.darwin()?directory("SHIRAZ_LAUNCHER_INBOX"):null;}
 static boolean active(){return apps()!=null;}

 /** Library facts: apps/<pkg>/package.properties next to apps/<pkg>/app.apk. */
 static Properties metadata(String pkg){
  File apps=apps();if(apps==null)return null;
  File dir=new File(apps,pkg),file=new File(dir,"package.properties");
  if(!file.isFile()||file.length()>4*1024*1024||!new File(dir,"app.apk").isFile())return null;
  try{Properties facts=new Properties();try(Reader in=new InputStreamReader(new FileInputStream(file),"UTF-8")){facts.load(in);}
   return pkg.equals(facts.getProperty("package"))?facts:null;}
  catch(IOException e){throw new IllegalStateException("Launcher package metadata",e);}
 }
 static String[] packages(){File apps=apps();String[] names=apps==null?null:apps.list();return names==null?new String[0]:names;}

 /** Hand an APK to the launcher and wait for it to be added to the library. */
 static String install(File apk,String expected,String installer,long timeoutMillis)throws IOException{
  File inbox=require();String id=UUID.randomUUID().toString();
  File part=new File(inbox,id+".apk.part"),staged=new File(inbox,id+".apk");
  try{Files.copy(apk.toPath(),part.toPath(),StandardCopyOption.REPLACE_EXISTING);move(part,staged);}
  catch(IOException e){part.delete();staged.delete();throw e;}
  Properties request=new Properties();request.setProperty("kind","install");request.setProperty("installer",installer);
  if(expected!=null&&!expected.isEmpty())request.setProperty("expected",expected);
  try{return await(inbox,id,request,timeoutMillis);}finally{staged.delete();}
 }
 /** Ask the launcher to start an app from its library. */
 static void launch(String pkg,String installer)throws IOException{
  Properties request=new Properties();request.setProperty("kind","launch");request.setProperty("package",pkg);request.setProperty("installer",installer);
  await(require(),UUID.randomUUID().toString(),request,15000);
 }
 /** Ask the launcher to remove an app. The launcher asks the user, so this can wait minutes. */
 static void remove(String pkg,String installer)throws IOException{
  Properties request=new Properties();request.setProperty("kind","remove");request.setProperty("package",pkg);request.setProperty("installer",installer);
  await(require(),UUID.randomUUID().toString(),request,10*60*1000L);
 }

 private static File require()throws IOException{
  File inbox=inbox();if(inbox==null)throw new IOException("Installing and opening other apps needs the Shiraz launcher");
  return inbox;
 }
 private static void move(File from,File to)throws IOException{Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE);}

 private static String await(File inbox,String id,Properties request,long timeoutMillis)throws IOException{
  File part=new File(inbox,id+".request.part"),file=new File(inbox,id+".request"),result=new File(inbox,id+".result");
  try(Writer out=new OutputStreamWriter(new FileOutputStream(part),"UTF-8")){request.store(out,null);}
  move(part,file);
  long deadline=System.currentTimeMillis()+timeoutMillis;
  try{
   while(!result.isFile()){
    if(System.currentTimeMillis()>deadline)throw new IOException("The Shiraz launcher did not answer the "+request.getProperty("kind")+" request");
    try{Thread.sleep(POLL_MILLIS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("Interrupted");}
   }
   if(result.length()>65536)throw new IOException("Launcher answer is too large");
   Properties answer=new Properties();try(Reader in=new InputStreamReader(new FileInputStream(result),"UTF-8")){answer.load(in);}
   if(!"ok".equals(answer.getProperty("status")))throw new IOException(answer.getProperty("message","The launcher refused the request"));
   return answer.getProperty("package");
  }finally{file.delete();result.delete();}
 }
}
