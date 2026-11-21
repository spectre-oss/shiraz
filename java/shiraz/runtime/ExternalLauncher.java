// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Intent;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/** Hands plain web, mail and phone links to the desktop opener; everything else stays inside the loaded APK. */
public final class ExternalLauncher {
 public interface Runner {void run(List<String> command);}
 private static final int MAX_URI=4096;
 private static Runner runner=new Runner(){public void run(List<String> command){
  try{
   ProcessBuilder builder=new ProcessBuilder(command);
   Map<String,String> environment=builder.environment();// The opener is a host program: drop loader overrides meant for the runtime.
   for(String key:new ArrayList<String>(environment.keySet()))if(key.startsWith("LD_")||key.startsWith("DYLD_"))environment.remove(key);
   builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.to(new java.io.File("/dev/null")));
   builder.start();
  }catch(java.io.IOException failed){System.err.println("SHIRAZ_EXTERNAL_OPEN_FAILED "+failed.getMessage());}
 }};
 private ExternalLauncher(){}
 public static void setRunner(Runner replacement){if(replacement==null)throw new NullPointerException("runner");runner=replacement;}
 private static boolean browserSelector(Intent intent){
  if(!Intent.ACTION_MAIN.equals(intent.getAction())||intent.getType()!=null)return false;
  java.util.Set<String> outer=intent.getCategories();
  if(outer==null||outer.size()!=1||!outer.contains(Intent.CATEGORY_LAUNCHER))return false;
  Intent selector=intent.getSelector();
  if(selector==null||!Intent.ACTION_MAIN.equals(selector.getAction())||selector.getComponent()!=null||selector.getPackage()!=null||selector.getSelector()!=null||selector.getData()!=null||selector.getType()!=null)return false;
  java.util.Set<String> categories=selector.getCategories();
  return categories!=null&&categories.size()==1&&categories.contains(Intent.CATEGORY_APP_BROWSER);
 }
 public static String externalTarget(Intent intent){
  if(intent==null||intent.getComponent()!=null||intent.getPackage()!=null)return null;
  boolean browser=browserSelector(intent);
  // A selector, when present, chooses the recipient. Do not discard its
  // package, component or filter by treating the outer VIEW as an open link.
  if(!browser&&(!Intent.ACTION_VIEW.equals(intent.getAction())||intent.getSelector()!=null))return null;
  Uri data=intent.getData();if(data==null)return null;
  String scheme=data.getScheme();
  if(scheme==null)return null;
  scheme=scheme.toLowerCase(java.util.Locale.ROOT);
  if(browser&&!scheme.equals("http")&&!scheme.equals("https"))return null;
  if(!scheme.equals("http")&&!scheme.equals("https")&&!scheme.equals("mailto")&&!scheme.equals("tel"))return null;
  String text=data.toString();
  if(text.length()>MAX_URI)return null;
  for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c<=' '||c==127)return null;}
  return text;
 }
 /** LaunchServices on macOS (default browser, Mail, FaceTime for tel:), the XDG opener elsewhere. */
 public static String opener(){return HostOs.darwin()?"/usr/bin/open":"xdg-open";}
 public static boolean open(Intent intent){
  String target=externalTarget(intent);
  if(target==null)return false;
  ArrayList<String> command=new ArrayList<String>();command.add(opener());command.add(target);
  runner.run(command);
  return true;
 }
}
