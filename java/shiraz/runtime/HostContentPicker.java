// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Intent;
import android.net.Uri;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
/** Desktop file selection for ACTION_GET_CONTENT / OPEN_DOCUMENT / PICK when no APK gallery exists. */
public final class HostContentPicker {
 public interface Chooser {String choose(String mimeType);}
 private static Chooser chooser=new Chooser(){public String choose(String mimeType){return desktopChoose(mimeType);}};
 private HostContentPicker(){}
 public static void setChooser(Chooser replacement){if(replacement==null)throw new NullPointerException("chooser");chooser=replacement;}
 public static Intent unwrap(Intent intent){
  if(intent==null)return null;
  if(Intent.ACTION_CHOOSER.equals(intent.getAction())){
   Intent inner=intent.getParcelableExtra(Intent.EXTRA_INTENT);
   return inner==null?intent:inner;
  }
  return intent;
 }
 public static boolean handles(Intent intent){
  Intent target=unwrap(intent);
  if(target==null||target.getComponent()!=null||target.getPackage()!=null)return false;
  String action=target.getAction();
  return Intent.ACTION_GET_CONTENT.equals(action)||Intent.ACTION_OPEN_DOCUMENT.equals(action)||Intent.ACTION_PICK.equals(action);
 }
 public static Uri pick(Intent intent){
  Intent target=unwrap(intent);
  if(!handles(target))return null;
  String mime=target.getType();
  if(mime==null||mime.isEmpty())mime="*/*";
  String env=System.getenv("SHIRAZ_PICK_FILE");
  if(env!=null&&!env.isEmpty()){
   File file=new File(env);
   if(file.isFile()&&file.canRead())return Uri.fromFile(file.getAbsoluteFile());
   System.err.println("SHIRAZ_PICK_FILE_UNAVAILABLE "+env);
   return null;
  }
  String path=chooser.choose(mime);
  if(path==null||path.isEmpty())return null;
  File file=new File(path);
  if(!file.isFile()||!file.canRead()){System.err.println("SHIRAZ_PICK_UNREADABLE "+path);return null;}
  return Uri.fromFile(file.getAbsoluteFile());
 }
 private static String desktopChoose(String mimeType){
  ArrayList<String> command=new ArrayList<String>();
  command.add("zenity");
  command.add("--file-selection");
  command.add("--title=Select file");
  if(mimeType!=null&&mimeType.startsWith("image/"))command.add("--file-filter=Images | *.png *.jpg *.jpeg *.webp *.gif *.bmp");
  try{
   ProcessBuilder builder=new ProcessBuilder(command);
   Map<String,String> environment=builder.environment();environment.remove("LD_LIBRARY_PATH");environment.remove("LD_PRELOAD");
   builder.redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));
   Process process=builder.start();
   String line;
   try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
    line=reader.readLine();
   }
   int code=process.waitFor();
   if(code!=0||line==null||line.isEmpty())return null;
   return line.trim();
  }catch(Exception error){
   System.err.println("SHIRAZ_PICK_FAILED "+error.getMessage());
   return null;
  }
 }
}
