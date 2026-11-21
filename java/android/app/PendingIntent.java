// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.content.*;
import android.os.*;
/** Host-owned intent capability, with a local fallback outside registered sessions. */
public final class PendingIntent implements Parcelable {
 public static final int FLAG_ONE_SHOT=0x40000000,FLAG_NO_CREATE=0x20000000,FLAG_CANCEL_CURRENT=0x10000000,FLAG_UPDATE_CURRENT=0x08000000,FLAG_IMMUTABLE=0x04000000,FLAG_MUTABLE=0x02000000;
 private shiraz.runtime.PendingIntentCapability capability;
 private final String pkg;private Intent intent;private final Context context;private static final int ACTIVITY=0,BROADCAST=1,SERVICE=2,FOREGROUND_SERVICE=3;private final int kind;private final int flags;private boolean canceled;
 private static final java.util.WeakHashMap<Context,java.util.LinkedHashMap<java.util.List<Object>,PendingIntent>> registry=new java.util.WeakHashMap<Context,java.util.LinkedHashMap<java.util.List<Object>,PendingIntent>>();
 private PendingIntent(Context context,Intent intent,int flags,int kind){
  if(context==null||intent==null)throw new NullPointerException();
  if((flags&~(FLAG_UPDATE_CURRENT|FLAG_IMMUTABLE|FLAG_MUTABLE|FLAG_ONE_SHOT|FLAG_NO_CREATE|FLAG_CANCEL_CURRENT))!=0)throw new UnsupportedOperationException("PendingIntent flags");
  if((flags&(FLAG_MUTABLE|FLAG_IMMUTABLE))==(FLAG_MUTABLE|FLAG_IMMUTABLE))throw new IllegalArgumentException("Conflicting mutability flags");
  this.context=context;this.kind=kind;this.flags=flags;pkg=context.getPackageName();ComponentName component=intent.getComponent();
  if(intent.getPackage()!=null&&!pkg.equals(intent.getPackage()))throw new UnsupportedOperationException("External or implicit PendingIntent");
  if(component!=null){if(!pkg.equals(component.getPackageName()))throw new UnsupportedOperationException("External or implicit PendingIntent");}
  // Activity resolution is deferred until send, through the creator's normal launch path.
  else if(kind!=BROADCAST&&kind!=ACTIVITY)throw new UnsupportedOperationException("Implicit service PendingIntent");
  this.intent=intent.shirazBroadcastCopy();
 }
 private PendingIntent(shiraz.runtime.PendingIntentCapability capability){this.capability=capability;pkg=capability.packageName;kind=capability.kind;flags=capability.flags;context=null;}
 private static synchronized PendingIntent obtain(Context context,int code,Intent intent,int flags,int kind){
  PendingIntent checked=new PendingIntent(context,intent,flags,kind);Context owner=context.getApplicationContext();if(owner==null)owner=context;
  if(shiraz.runtime.HostPendingIntents.available()){
   shiraz.runtime.PendingIntentCapability cap=shiraz.runtime.HostPendingIntents.obtain(owner,kind,code,flags,checked.intent);
   return cap==null?null:new PendingIntent(cap);
  }
  java.util.Set<String> categories=intent.getCategories();
  java.util.List<Object> key=java.util.Arrays.<Object>asList(kind,code,flags&(FLAG_ONE_SHOT|FLAG_IMMUTABLE|FLAG_MUTABLE),intent.getAction(),intent.getDataString(),intent.getType(),intent.getPackage(),intent.getComponent(),categories==null?null:new java.util.TreeSet<String>(categories));
  java.util.LinkedHashMap<java.util.List<Object>,PendingIntent> tokens=registry.get(owner);if(tokens==null){if((flags&FLAG_NO_CREATE)!=0)return null;tokens=new java.util.LinkedHashMap<java.util.List<Object>,PendingIntent>();registry.put(owner,tokens);}
  PendingIntent old=tokens.get(key);if(old!=null&&old.canceled){tokens.remove(key);old=null;}if((flags&FLAG_CANCEL_CURRENT)!=0){tokens.remove(key);if(old!=null)old.canceled=true;old=null;}
  if(old!=null){if((flags&FLAG_UPDATE_CURRENT)!=0)old.intent=checked.intent;return old;}
  if((flags&FLAG_NO_CREATE)!=0)return null;if(tokens.size()>=256)throw new IllegalStateException("PendingIntent token limit");tokens.put(key,checked);return checked;
 }
 public static PendingIntent getActivity(Context context,int code,Intent intent,int flags){return obtain(context,code,intent,flags,ACTIVITY);}
 public static PendingIntent getBroadcast(Context context,int code,Intent intent,int flags){return obtain(context,code,intent,flags,BROADCAST);}
 public static PendingIntent getService(Context context,int code,Intent intent,int flags){return obtain(context,code,intent,flags,SERVICE);}
 public static PendingIntent getForegroundService(Context context,int code,Intent intent,int flags){return obtain(context,code,intent,flags,FOREGROUND_SERVICE);}
 public IntentSender getIntentSender(){return new IntentSender(this);}
 public void send() throws CanceledException {shirazSend(null);}
 public synchronized void shirazSend(Intent fill) throws CanceledException {
  if(canceled)throw new CanceledException();
  if(capability!=null){shiraz.runtime.HostPendingIntents.send(capability.token,fill);return;}
  Intent value=intent.shirazBroadcastCopy();
  if(fill!=null&&(flags&FLAG_IMMUTABLE)==0&&fill.getExtras()!=null)value.putExtras(fill.getExtras());
  if((flags&FLAG_ONE_SHOT)!=0)canceled=true;
  switch(kind){
   case BROADCAST:context.sendBroadcast(value);break;
   case SERVICE:context.startService(value);break;
   case FOREGROUND_SERVICE:context.startForegroundService(value);break;
   default:context.startActivity(value);
  }
 }
 public void cancel(){if(capability!=null)shiraz.runtime.HostPendingIntents.cancel(capability.token);canceled=true;}
 public static class CanceledException extends android.util.AndroidException {public CanceledException(){super("Pending intent canceled");}}
 public String getCreatorPackage(){return pkg;}public String getTargetPackage(){return pkg;}
 public int getCreatorUid(){return capability==null?android.os.Process.myUid():capability.uid;}
 public boolean isImmutable(){return (flags&FLAG_IMMUTABLE)!=0;}
 public interface OnFinished {
  void onSendFinished(PendingIntent pendingIntent, Intent intent, int resultCode, String resultData, Bundle resultExtras);
 }
 public void send(int code) throws CanceledException {send(null,code,null,null,null,null,null);}
 public void send(Context context,int code,Intent intent) throws CanceledException {send(context,code,intent,null,null,null,null);}
 public void send(int code,OnFinished onFinished,Handler handler) throws CanceledException {send(null,code,null,onFinished,handler,null,null);}
 public void send(Context context,int code,Intent intent,OnFinished onFinished,Handler handler) throws CanceledException {send(context,code,intent,onFinished,handler,null,null);}
 public void send(Context context,int code,Intent intent,OnFinished onFinished,Handler handler,String requiredPermission) throws CanceledException {send(context,code,intent,onFinished,handler,requiredPermission,null);}
 public void send(Context context,final int code,final Intent intent,final OnFinished onFinished,Handler handler,String requiredPermission,Bundle options) throws CanceledException {
  shirazSend(intent);
  if(onFinished!=null){
   if(handler!=null){handler.post(new Runnable(){@Override public void run(){onFinished.onSendFinished(PendingIntent.this,intent,code,null,null);}});}
   else{onFinished.onSendFinished(this,intent,code,null,null);}
  }
 }
 public static void writePendingIntentOrNullToParcel(PendingIntent sender,Parcel out){
  out.writeInt(sender!=null?1:0);
  if(sender!=null)sender.writeToParcel(out,0);
 }
 public static PendingIntent readPendingIntentOrNullFromParcel(Parcel in){
  return in.readInt()!=0?CREATOR.createFromParcel(in):null;
 }
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel out,int flags){
  if(capability==null)throw new UnsupportedOperationException("PendingIntent IPC requires a host session");
  out.writeInt(1);out.writeString(capability.token);
 }
 public static final Parcelable.Creator<PendingIntent> CREATOR=new Parcelable.Creator<PendingIntent>(){
  public PendingIntent createFromParcel(Parcel in){if(in.readInt()!=1)throw new IllegalArgumentException("PendingIntent parcel version");return new PendingIntent(shiraz.runtime.HostPendingIntents.inspect(in.readString()));}
  public PendingIntent[] newArray(int size){return new PendingIntent[size];}
 };
 @Override public boolean equals(Object other){if(this==other)return true;return capability!=null&&other instanceof PendingIntent&&((PendingIntent)other).capability!=null&&capability.token.equals(((PendingIntent)other).capability.token);}
 @Override public int hashCode(){return capability==null?System.identityHashCode(this):capability.token.hashCode();}
}
