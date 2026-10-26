// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import android.content.*;
/** Host AppImage installation boundary and XDG installed-package registry. */
public final class HostAppInstaller {
 private HostAppInstaller(){}
 public static File base(String name,String fallback){
  String path=System.getenv("SHIRAZ_HOST_"+name);if(path==null)path=System.getenv(name);
  if(path!=null&&!path.isEmpty()&&new File(path).isAbsolute())return new File(path);
  String home=System.getenv("HOME");if(home==null||!new File(home).isAbsolute())throw new IllegalStateException("Absolute HOME or XDG directory required");
  return new File(home,fallback);
 }
 private static File registry(){return new File(base("XDG_DATA_HOME",".local/share"),"shiraz/installed");}
 private static boolean valid(String pkg){return pkg!=null&&pkg.length()<=1024&&pkg.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");}
 public static Properties metadata(String pkg){
  if(!valid(pkg))return null;File dir=new File(registry(),pkg),file=new File(dir,"package.properties");
  if(!file.isFile()||file.length()>4*1024*1024||!new File(dir,"application.AppImage").isFile())return null;
  try{Properties facts=new Properties();try(Reader in=new InputStreamReader(new FileInputStream(file),"UTF-8")){facts.load(in);}if(!pkg.equals(facts.getProperty("package")))return null;return facts;}
  catch(IOException e){throw new IllegalStateException("Installed package metadata",e);}
 }
 public static List<String> packages(){ArrayList<String> result=new ArrayList<>();String[] files=registry().list();if(files!=null){if(files.length>4096)throw new IllegalStateException("Installed package registry limit");for(String pkg:files)if(metadata(pkg)!=null)result.add(pkg);}return result;}
 public static Intent launchIntent(String pkg){Properties facts=metadata(pkg);if(facts==null)return null;String activity=facts.getProperty("launchActivity");return activity==null?null:new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(new ComponentName(pkg,activity)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);}
 private static void cleanEnvironment(Map<String,String> env){
  for(String key:new ArrayList<String>(env.keySet()))if(key.startsWith("SHIRAZ_LIVE_")||key.equals("SHIRAZ_PRESENT_SNAPSHOT")||key.equals("SHIRAZ_INPUT_SCRIPT")||key.startsWith("APPIMAGE")||key.equals("APPDIR")||key.equals("OWD")||key.equals("LD_LIBRARY_PATH")||key.equals("LD_PRELOAD")||key.equals("PYTHONPATH")||key.equals("PYTHONHOME"))env.remove(key);
  for(String key:new String[]{"XDG_DATA_HOME","XDG_CACHE_HOME","XDG_STATE_HOME"}){String value=env.get("SHIRAZ_HOST_"+key);if(value!=null)env.put(key,value);}
 }
 public static boolean open(Intent intent,String ownPackage){
  ComponentName component=intent.getComponent();if(component==null||component.getPackageName().equals(ownPackage))return false;
  Properties facts=metadata(component.getPackageName());if(facts==null)return false;
  if(!component.getClassName().equals(facts.getProperty("launchActivity")))throw new android.content.ActivityNotFoundException("Only installed launch activities are supported");
  File file=new File(new File(registry(),component.getPackageName()),"application.AppImage");
  try{File state=new File(base("XDG_STATE_HOME",".local/state"),"shiraz");state.mkdirs();
   ProcessBuilder builder=new ProcessBuilder(file.getAbsolutePath());cleanEnvironment(builder.environment());builder.environment().put("APPIMAGE_EXTRACT_AND_RUN","1");
   builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(new File(state,"app-launch.log")));builder.start();
   audit("opened",component.getPackageName());return true;
  }catch(IOException e){throw new android.content.ActivityNotFoundException("Cannot launch installed AppImage: "+e);}
 }
 public static void recordAccess(String operation){try{audit("access",operation);}catch(IOException e){throw new IllegalStateException("Cannot record access",e);}}
 private static synchronized void audit(String event,String pkg)throws IOException{
  File state=new File(base("XDG_STATE_HOME",".local/state"),"shiraz");state.mkdirs();
  try(FileWriter out=new FileWriter(new File(state,"access-history.log"),true)){out.write(System.currentTimeMillis()+" "+event+" "+pkg+"\n");}
 }
 public interface RemovalResult {void complete(boolean removed);}
 public static boolean handlesRemoval(Intent intent){return intent!=null&&intent.getComponent()==null&&("android.intent.action.DELETE".equals(intent.getAction())||"android.intent.action.UNINSTALL_PACKAGE".equals(intent.getAction()))&&intent.getData()!=null&&"package".equals(intent.getData().getScheme());}
 public static void confirmRemoval(final android.app.Activity activity,Intent intent,final RemovalResult result){
  final String pkg=intent.getData().getSchemeSpecificPart();final Properties facts=metadata(pkg);
  if(facts==null||pkg.equals(activity.getPackageName()))throw new ActivityNotFoundException("Installed package cannot be removed here");
  final android.app.AlertDialog dialog=new android.app.AlertDialog(activity);
  String label=facts.getProperty("label",pkg);if(label.startsWith("@")||label.trim().isEmpty())label=pkg;
  dialog.setTitle("Uninstall "+label+"?");
  dialog.setMessage("The installed app will be removed. Saved app data is kept.");
  dialog.setButton(android.content.DialogInterface.BUTTON_NEGATIVE,"Cancel",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int which){result.complete(false);}});
  dialog.setOnCancelListener(new android.content.DialogInterface.OnCancelListener(){public void onCancel(android.content.DialogInterface d){result.complete(false);}});
  dialog.setButton(android.content.DialogInterface.BUTTON_POSITIVE,"Uninstall",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int which){
   final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
   Thread worker=new Thread(new Runnable(){public void run(){boolean success=false;try{remove(activity,pkg);success=true;}catch(IOException error){System.err.println("SHIRAZ_UNINSTALL_FAILED "+error.getClass().getSimpleName());}final boolean removed=success;main.post(new Runnable(){public void run(){if(removed)activity.sendBroadcast(new Intent("android.intent.action.PACKAGE_REMOVED").setData(android.net.Uri.parse("package:"+pkg)).putExtra("android.intent.extra.REPLACING",false).putExtra("android.intent.extra.DATA_REMOVED",false));else{android.app.AlertDialog failure=new android.app.AlertDialog(activity);failure.setTitle("Uninstall failed");failure.setMessage("The app could not be removed. See the installation log for details.");failure.setButton(android.content.DialogInterface.BUTTON_POSITIVE,"OK",(android.content.DialogInterface.OnClickListener)null);failure.show();}result.complete(removed);}});}},"Shiraz uninstall");worker.setDaemon(true);worker.start();
  }});dialog.show();
 }
 private static void remove(Context context,String pkg)throws IOException{
  String root=System.getenv("SHIRAZ_APPDIR");if(root==null)throw new IOException("Uninstall requires packaged runtime");
  File template=new File(root),tools=new File(template,"usr/share/shiraz/installer"),state=new File(base("XDG_STATE_HOME",".local/state"),"shiraz");state.mkdirs();
  String loader=System.getProperty("os.arch","").contains("aarch64")?"ld-linux-aarch64.so.1":"ld-linux-x86-64.so.2";
  ProcessBuilder builder=new ProcessBuilder(new File(template,"usr/lib/"+loader).getPath(),"--library-path",new File(template,"usr/lib").getPath(),new File(tools,"python").getPath(),"-s","-m","shiraz.installed","--uninstall",pkg,"--installer",context.getPackageName());
  cleanEnvironment(builder.environment());builder.environment().put("PYTHONHOME",tools.getPath());builder.environment().put("PYTHONPATH",tools.getPath());builder.environment().put("PYTHONDONTWRITEBYTECODE","1");builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(new File(state,"install-build.log")));
  try{Process process=builder.start();if(!process.waitFor(60,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Uninstall timed out");}if(process.exitValue()!=0)throw new IOException("Uninstall failed");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException(error);}
 }
 public static String install(Context context,File apk,String expected)throws IOException {
  String root=System.getenv("SHIRAZ_APPDIR");if(root==null)throw new IOException("Install from the packaged Shiraz AppImage");
  File template=new File(root),tools=new File(template,"usr/share/shiraz/installer"),result=File.createTempFile("install-result-",".properties",context.getCacheDir());
  File state=new File(base("XDG_STATE_HOME",".local/state"),"shiraz");if(!state.isDirectory()&&!state.mkdirs())throw new IOException("Cannot create install history directory");
  String loader=System.getProperty("os.arch").contains("aarch64")?"ld-linux-aarch64.so.1":"ld-linux-x86-64.so.2";
  ProcessBuilder builder=new ProcessBuilder(new File(template,"usr/lib/"+loader).getPath(),"--library-path",new File(template,"usr/lib").getPath(),new File(tools,"python").getPath(),"-s","-m","shiraz.installed","--template",root,"--apk",apk.getPath(),"--expected",expected==null?"":expected,"--installer",context.getPackageName(),"--result",result.getPath());
  cleanEnvironment(builder.environment());builder.environment().put("PYTHONHOME",tools.getPath());builder.environment().put("PYTHONPATH",tools.getPath());builder.environment().put("PYTHONDONTWRITEBYTECODE","1");
  builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(new File(state,"install-build.log")));
  try{Process process=builder.start();if(!process.waitFor(700,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("AppImage build timed out");}
   if(process.exitValue()!=0)throw new IOException("AppImage build failed; see "+new File(state,"install-build.log"));
   Properties values=new Properties();try(Reader in=new FileReader(result)){values.load(in);}String pkg=values.getProperty("package");if(metadata(pkg)==null)throw new IOException("Installer did not publish a package");
   context.sendBroadcast(new Intent("android.intent.action.PACKAGE_ADDED").setData(android.net.Uri.parse("package:"+pkg)).putExtra("android.intent.extra.REPLACING",false));return pkg;
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}finally{result.delete();}
 }
}
