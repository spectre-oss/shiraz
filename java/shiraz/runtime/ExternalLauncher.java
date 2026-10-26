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
   Map<String,String> environment=builder.environment();environment.remove("LD_LIBRARY_PATH");environment.remove("LD_PRELOAD");
   builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.to(new java.io.File("/dev/null")));
   builder.start();
  }catch(java.io.IOException failed){System.err.println("SHIRAZ_EXTERNAL_OPEN_FAILED "+failed.getMessage());}
 }};
 private ExternalLauncher(){}
 public static void setRunner(Runner replacement){if(replacement==null)throw new NullPointerException("runner");runner=replacement;}
 public static String externalTarget(Intent intent){
  if(intent==null||!Intent.ACTION_VIEW.equals(intent.getAction())||intent.getComponent()!=null||intent.getPackage()!=null)return null;
  Uri data=intent.getData();if(data==null)return null;
  String scheme=data.getScheme();
  if(scheme==null)return null;
  scheme=scheme.toLowerCase();
  if(!scheme.equals("http")&&!scheme.equals("https")&&!scheme.equals("mailto")&&!scheme.equals("tel"))return null;
  String text=data.toString();
  if(text.length()>MAX_URI)return null;
  for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c<=' '||c==127)return null;}
  return text;
 }
 public static boolean open(Intent intent){
  String target=externalTarget(intent);
  if(target==null)return false;
  ArrayList<String> command=new ArrayList<String>();command.add("xdg-open");command.add(target);
  runner.run(command);
  return true;
 }
}
