// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
/** Process-wide clipboard bridged to the active SDL desktop clipboard when a window is open. */
public class ClipboardManager {
 public interface OnPrimaryClipChangedListener{void onPrimaryClipChanged();}
 private static ClipData primary;
 private static final ArrayList<OnPrimaryClipChangedListener> listeners=new ArrayList<OnPrimaryClipChangedListener>();
 private static String hostText(){
  try{
   byte[] utf8=shiraz.runtime.NativeSurface.getClipboardText();
   if(utf8==null||utf8.length==0)return null;
   return new String(utf8,StandardCharsets.UTF_8);
  }catch(UnsatisfiedLinkError|RuntimeException unavailable){return null;}
 }
 private static void pushHostText(ClipData clip){
  if(clip==null||clip.getItemCount()==0)return;
  CharSequence text=clip.getItemAt(0).getText();
  if(text==null)return;
  String value=text.toString().replace('\0',' ');
  try{shiraz.runtime.NativeSurface.setClipboardText(value.getBytes(StandardCharsets.UTF_8));}
  catch(UnsatisfiedLinkError|RuntimeException unavailable){}
 }
 private static synchronized void syncFromHost(){
  String host=hostText();
  if(host==null)return;
  CharSequence current=primary!=null&&primary.getItemCount()>0?primary.getItemAt(0).getText():null;
  if(current==null||!host.equals(current.toString())){primary=ClipData.newPlainText("host",host);primary.getDescription().setTimestamp(System.currentTimeMillis());}
 }
 public void setPrimaryClip(ClipData clip){
  if(clip==null)throw new NullPointerException("clip");
  OnPrimaryClipChangedListener[] snapshot;
  synchronized(ClipboardManager.class){primary=new ClipData(clip);primary.getDescription().setTimestamp(System.currentTimeMillis());snapshot=listeners.toArray(new OnPrimaryClipChangedListener[0]);}
  pushHostText(clip);
  for(OnPrimaryClipChangedListener listener:snapshot)listener.onPrimaryClipChanged();
 }
 public synchronized ClipData getPrimaryClip(){
  synchronized(ClipboardManager.class){
   syncFromHost();
   return primary==null?null:new ClipData(primary);
  }
 }
 public synchronized ClipDescription getPrimaryClipDescription(){
  synchronized(ClipboardManager.class){
   syncFromHost();
   return primary==null?null:primary.getDescription();
  }
 }
 public synchronized boolean hasPrimaryClip(){
  synchronized(ClipboardManager.class){
   syncFromHost();
   return primary!=null;
  }
 }
 public void clearPrimaryClip(){
  synchronized(ClipboardManager.class){primary=null;}
  try{shiraz.runtime.NativeSurface.setClipboardText(new byte[0]);}
  catch(UnsatisfiedLinkError|RuntimeException unavailable){}
 }
 public void addPrimaryClipChangedListener(OnPrimaryClipChangedListener listener){if(listener==null)throw new NullPointerException("listener");synchronized(ClipboardManager.class){if(!listeners.contains(listener))listeners.add(listener);}}
 public void removePrimaryClipChangedListener(OnPrimaryClipChangedListener listener){synchronized(ClipboardManager.class){listeners.remove(listener);}}
 public boolean hasText(){ClipData clip=getPrimaryClip();return clip!=null&&clip.getItemCount()>0&&clip.getItemAt(0).getText()!=null;}
 public CharSequence getText(){ClipData clip=getPrimaryClip();return clip==null||clip.getItemCount()==0?null:clip.getItemAt(0).getText();}
 public void setText(CharSequence text){setPrimaryClip(ClipData.newPlainText(null,text));}
}
