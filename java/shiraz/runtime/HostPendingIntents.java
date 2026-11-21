// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.util.JsonReader;
import android.util.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;

/** Host-owned capabilities; creator callbacks use the ordinary Context dispatch path. */
public final class HostPendingIntents {
 private static ParcelFileDescriptor callback;
 private static final java.util.HashMap<String,PendingIntentSnapshot> snapshots=new java.util.HashMap<String,PendingIntentSnapshot>();
 private static final byte[] LIVE_PREFIX="SHIRAZ-LIVE-PI\0".getBytes(StandardCharsets.US_ASCII);
 private HostPendingIntents(){}
 public static boolean available(){return RuntimeBroker.client()!=null;}
 private static ServiceBrokerClient broker(){ServiceBrokerClient b=RuntimeBroker.client();if(b==null)throw new IllegalStateException("PendingIntent broker unavailable");return b;}
 public static byte[] snapshot(Intent intent){
  Parcel p=Parcel.obtain();try{intent.writeToParcel(p,0);byte[] bytes=p.marshall();if(bytes.length>32768)throw new IllegalArgumentException("PendingIntent snapshot limit");return bytes;}finally{p.recycle();}
 }
 public static synchronized PendingIntentCapability obtain(Context context,int kind,int code,int flags,Intent intent){
  Intent filter=new Intent(intent.getAction()).setDataAndType(intent.getData(),intent.getType()).setPackage(intent.getPackage()).setComponent(intent.getComponent());
  if(intent.getCategories()!=null)for(String category:new java.util.TreeSet<String>(intent.getCategories()))filter.addCategory(category);
  byte[] identity=snapshot(filter);
  PendingIntentSnapshot owned=PendingIntentSnapshot.capture(intent);boolean retained=false;
  try{
   ensureHost(context);collect();byte[] payload=owned.bytes();
   if(payload!=null)return broker().obtainPendingIntent(kind,code,flags,identity,payload);
   if(snapshots.size()>=289)throw new IllegalStateException("PendingIntent retained snapshot limit");
   String id=java.util.UUID.randomUUID().toString().replace("-","")+java.util.UUID.randomUUID().toString().replace("-","");
   snapshots.put(id,owned);retained=true;
   PendingIntentCapability result=broker().obtainLivePendingIntent(kind,code,flags,identity,id);
   collect();return result;
  }catch(IOException e){throw new IllegalStateException("PendingIntent registration failed",e);}
  finally{if(!retained)owned.close();}
 }
 private static synchronized void collect()throws IOException{
  if(snapshots.isEmpty())return;
  java.util.Set<String> live=broker().pendingIntentReferences();
  java.util.Iterator<java.util.Map.Entry<String,PendingIntentSnapshot>> it=snapshots.entrySet().iterator();
  while(it.hasNext()){java.util.Map.Entry<String,PendingIntentSnapshot> entry=it.next();if(!live.contains(entry.getKey())){entry.getValue().close();it.remove();}}
 }
 private static synchronized Intent retained(String id)throws IOException{
  PendingIntentSnapshot value=snapshots.get(id);if(value==null)throw new IOException("PendingIntent creator snapshot expired");return value.read();
 }
 public static PendingIntentCapability inspect(String token){try{return broker().inspectPendingIntent(token);}catch(IOException e){throw new IllegalStateException("PendingIntent lookup failed",e);}}
 public static void cancel(String token){try{broker().cancelPendingIntent(token);}catch(ServiceBrokerClient.PendingIntentCanceledException ignored){}catch(IOException e){throw new IllegalStateException("PendingIntent cancel failed",e);}}
 public static void send(String token,Intent fill)throws android.app.PendingIntent.CanceledException{
  try{broker().sendPendingIntent(token,fill==null?null:snapshot(fill));}
  catch(ServiceBrokerClient.PendingIntentCanceledException e){throw new android.app.PendingIntent.CanceledException();}
  catch(IOException e){throw new IllegalStateException("PendingIntent delivery failed",e);}
 }
 private static synchronized void ensureHost(Context context)throws IOException{
  if(callback!=null)return;
  final Context owner=context.getApplicationContext()==null?context:context.getApplicationContext();
  final ServiceBrokerClient client=broker();
  final ServiceBrokerClient.PackageIdentity identity=client.lookupPackage(owner.getPackageName());
  if(identity==null)throw new SecurityException("PendingIntent creator not enrolled");
  final ParcelFileDescriptor channel=client.registerPendingIntentHost();callback=channel;
  Thread thread=new Thread(new Runnable(){public void run(){
   try{
    for(;;){
     Delivery value;
     try(NativeIpc.Packet first=NativeIpc.receive(channel.getFd());NativeIpc.Packet second=NativeIpc.receive(channel.getFd())){
      if(first.descriptorCount()!=0||second.descriptorCount()!=0)throw new IOException("PendingIntent callback descriptors");
      value=decode(first.bytes,second.bytes);
     }
     if(!value.cap.packageName.equals(identity.packageName)||value.cap.uid!=identity.uid)throw new SecurityException("PendingIntent creator mismatch");
     Intent intent=value.intent;
     intent.setExtrasClassLoader(owner.getClassLoader());
     switch(value.cap.kind){case 1:owner.sendBroadcast(intent);break;case 2:owner.startService(intent);break;case 3:owner.startForegroundService(intent);break;default:owner.startActivity(intent);}
     NativeIpc.send(channel.getFd(),"{\"ok\":true}".getBytes(StandardCharsets.UTF_8),new int[0]);
    }
   }catch(IOException|RuntimeException failure){System.err.println("SHIRAZ_PENDING_INTENT_CALLBACK_CLOSED: "+failure.getClass().getSimpleName());}
   finally{NativeIpc.shutdown(channel.getFd());try{channel.close();}catch(IOException ignored){}synchronized(HostPendingIntents.class){if(callback==channel){callback=null;for(PendingIntentSnapshot snapshot:snapshots.values())snapshot.close();snapshots.clear();}}}
  }},"pending-intent-creator");thread.setDaemon(true);thread.start();
  Thread retirement=new Thread(new Runnable(){public void run(){
   try{for(;;){Thread.sleep(1000);synchronized(HostPendingIntents.class){if(callback!=channel)return;collect();}}}
   catch(InterruptedException stopped){Thread.currentThread().interrupt();}
   catch(IOException|RuntimeException failure){System.err.println("SHIRAZ_PENDING_INTENT_RETIREMENT_FAILED");NativeIpc.shutdown(channel.getFd());}
  }},"pending-intent-retirement");retirement.setDaemon(true);retirement.start();
 }
 static final class Delivery {final PendingIntentCapability cap;final Intent intent;Delivery(PendingIntentCapability c,Intent i){cap=c;intent=i;}}
 private static JsonReader reader(byte[] bytes)throws IOException{
  if(bytes.length<1||bytes.length>65536)throw new IOException("PendingIntent callback limit");
  return new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()));
 }
 private static Intent intent(String encoded)throws IOException{return intent(encoded,false);}
 private static Intent intent(String encoded,boolean ownerPayload)throws IOException{
  if(encoded==null||encoded.length()>43692)throw new IOException("PendingIntent payload limit");
  byte[] bytes=java.util.Base64.getDecoder().decode(encoded);
  if(bytes.length<1||bytes.length>32768||!java.util.Base64.getEncoder().encodeToString(bytes).equals(encoded))throw new IOException("PendingIntent payload encoding");
  if(bytes.length>=LIVE_PREFIX.length&&java.util.Arrays.equals(java.util.Arrays.copyOf(bytes,LIVE_PREFIX.length),LIVE_PREFIX)){
   if(!ownerPayload)throw new IOException("Live snapshot in fill-in Intent");
   String reference=new String(bytes,LIVE_PREFIX.length,bytes.length-LIVE_PREFIX.length,StandardCharsets.US_ASCII);
   if(!reference.matches("[0-9a-f]{64}:[0-9a-f]{64}"))throw new IOException("Invalid live snapshot reference");
   return retained(reference.substring(65));
  }
  Parcel p=Parcel.obtain();try{p.unmarshall(bytes,0,bytes.length);p.setDataPosition(0);Intent result=Intent.CREATOR.createFromParcel(p);p.enforceNoDataAvail();return result;}finally{p.recycle();}
 }
 static Delivery decode(byte[] first,byte[] second)throws IOException{
  JsonReader in=reader(first);String op=null,token=null,pkg=null,sha=null,payload=null;Integer uid=null,kind=null,flags=null;
  HashSet<String> keys=new HashSet<String>();in.beginObject();while(in.hasNext()){
   String key=in.nextName();if(!keys.add(key))throw new IOException("Duplicate PendingIntent callback field");
   if(key.equals("op"))op=in.nextString();else if(key.equals("token"))token=in.nextString();else if(key.equals("package"))pkg=in.nextString();else if(key.equals("sha256"))sha=in.nextString();else if(key.equals("payload"))payload=in.nextString();
   else if(key.equals("uid")||key.equals("kind")||key.equals("flags")){
    if(in.peek()!=JsonToken.NUMBER)throw new IOException("Invalid PendingIntent number");int n=in.nextInt();if(key.equals("uid"))uid=n;else if(key.equals("kind"))kind=n;else flags=n;
   }else throw new IOException("Unknown PendingIntent callback field");
  }in.endObject();if(in.peek()!=JsonToken.END_DOCUMENT||!"pending_intent_deliver".equals(op)||uid==null||kind==null||flags==null)throw new IOException("Invalid PendingIntent callback");in.close();
  PendingIntentCapability cap=new PendingIntentCapability(token,pkg,sha,uid,kind,flags);Intent value=intent(payload,true);
  in=reader(second);in.beginObject();if(!in.hasNext()||!in.nextName().equals("fill"))throw new IOException("Missing PendingIntent fill");
  String fill=null;if(in.peek()==JsonToken.NULL)in.nextNull();else fill=in.nextString();
  if(in.hasNext())throw new IOException("Unknown PendingIntent fill field");in.endObject();if(in.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Trailing PendingIntent fill");in.close();
  if(fill!=null){if((flags&0x04000000)!=0)throw new IOException("Immutable PendingIntent fill");Intent extra=intent(fill);if(extra.getExtras()!=null)value.putExtras(extra.getExtras());}
  return new Delivery(cap,value);
 }
 static synchronized void shutdown(){
  if(callback!=null){NativeIpc.shutdown(callback.getFd());callback=null;}
  for(PendingIntentSnapshot snapshot:snapshots.values())snapshot.close();snapshots.clear();
 }
}
