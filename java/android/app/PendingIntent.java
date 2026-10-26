// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.content.*;
import android.os.*;
/** Process-local intent token with explicit mutable completion extras. */
public final class PendingIntent implements Parcelable {
 public static final int FLAG_ONE_SHOT=0x40000000,FLAG_NO_CREATE=0x20000000,FLAG_CANCEL_CURRENT=0x10000000,FLAG_UPDATE_CURRENT=0x08000000,FLAG_IMMUTABLE=0x04000000,FLAG_MUTABLE=0x02000000;
 private final String pkg;private Intent intent;private final Context context;private final boolean broadcast;private final int flags;private boolean canceled;
 private static final java.util.WeakHashMap<Context,java.util.LinkedHashMap<java.util.List<Object>,PendingIntent>> registry=new java.util.WeakHashMap<Context,java.util.LinkedHashMap<java.util.List<Object>,PendingIntent>>();
 private PendingIntent(Context context,Intent intent,int flags,boolean broadcast){
  if(context==null||intent==null)throw new NullPointerException();
  if((flags&~(FLAG_UPDATE_CURRENT|FLAG_IMMUTABLE|FLAG_MUTABLE|FLAG_ONE_SHOT|FLAG_NO_CREATE|FLAG_CANCEL_CURRENT))!=0)throw new UnsupportedOperationException("PendingIntent flags");
  if((flags&(FLAG_MUTABLE|FLAG_IMMUTABLE))==(FLAG_MUTABLE|FLAG_IMMUTABLE))throw new IllegalArgumentException("Conflicting mutability flags");
  this.context=context;this.broadcast=broadcast;this.flags=flags;pkg=context.getPackageName();ComponentName component=intent.getComponent();
  if(intent.getPackage()!=null&&!pkg.equals(intent.getPackage()))throw new UnsupportedOperationException("External or implicit PendingIntent");
  if(component!=null){if(!pkg.equals(component.getPackageName()))throw new UnsupportedOperationException("External or implicit PendingIntent");}
  else if(!broadcast)throw new UnsupportedOperationException("External or implicit PendingIntent");
  this.intent=intent.shirazBroadcastCopy();
 }
 private static synchronized PendingIntent obtain(Context context,int code,Intent intent,int flags,boolean broadcast){
  PendingIntent checked=new PendingIntent(context,intent,flags,broadcast);Context owner=context.getApplicationContext();if(owner==null)owner=context;
  java.util.Set<String> categories=intent.getCategories();
  java.util.List<Object> key=java.util.Arrays.<Object>asList(broadcast,code,flags&(FLAG_ONE_SHOT|FLAG_IMMUTABLE|FLAG_MUTABLE),intent.getAction(),intent.getDataString(),intent.getType(),intent.getPackage(),intent.getComponent(),categories==null?null:new java.util.TreeSet<String>(categories));
  java.util.LinkedHashMap<java.util.List<Object>,PendingIntent> tokens=registry.get(owner);if(tokens==null){if((flags&FLAG_NO_CREATE)!=0)return null;tokens=new java.util.LinkedHashMap<java.util.List<Object>,PendingIntent>();registry.put(owner,tokens);}
  PendingIntent old=tokens.get(key);if(old!=null&&old.canceled){tokens.remove(key);old=null;}if((flags&FLAG_CANCEL_CURRENT)!=0){tokens.remove(key);if(old!=null)old.canceled=true;old=null;}
  if(old!=null){if((flags&FLAG_UPDATE_CURRENT)!=0)old.intent=checked.intent;return old;}
  if((flags&FLAG_NO_CREATE)!=0)return null;if(tokens.size()>=256)throw new IllegalStateException("PendingIntent token limit");tokens.put(key,checked);return checked;
 }
 public static PendingIntent getActivity(Context context,int code,Intent intent,int flags){return obtain(context,code,intent,flags,false);}
 public static PendingIntent getBroadcast(Context context,int code,Intent intent,int flags){return obtain(context,code,intent,flags,true);}
 public static PendingIntent getService(Context context,int code,Intent intent,int flags){return obtain(context,code,intent,flags,false);}
 public static PendingIntent getForegroundService(Context context,int code,Intent intent,int flags){return getService(context,code,intent,flags);}
 public IntentSender getIntentSender(){return new IntentSender(this);}
 public void send() throws CanceledException {shirazSend(null);}
 public synchronized void shirazSend(Intent fill) throws CanceledException {
  if(canceled)throw new CanceledException();
  Intent value=intent.shirazBroadcastCopy();
  if(fill!=null&&(flags&FLAG_IMMUTABLE)==0&&fill.getExtras()!=null)value.putExtras(fill.getExtras());
  if((flags&FLAG_ONE_SHOT)!=0)canceled=true;
  if(broadcast)context.sendBroadcast(value);else context.startActivity(value);
 }
 public void cancel(){canceled=true;}
 public static class CanceledException extends android.util.AndroidException {public CanceledException(){super("Pending intent canceled");}}
 public String getCreatorPackage(){return pkg;}public String getTargetPackage(){return pkg;}
 public int describeContents(){return 0;}public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("PendingIntent IPC");}
}
