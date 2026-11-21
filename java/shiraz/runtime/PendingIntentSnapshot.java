// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Intent;
import android.os.Parcel;

/** Immutable, bounded creator-owned data. Binder references remain live capabilities. */
final class PendingIntentSnapshot implements java.io.Closeable {
 private Parcel stored;
 private PendingIntentSnapshot(Parcel stored){this.stored=stored;}
 static PendingIntentSnapshot capture(Intent intent){
  Parcel value=Parcel.obtain();boolean owned=false;
  try{
   intent.writeToParcel(value,0);
   if(value.dataSize()>32768)throw new IllegalArgumentException("PendingIntent snapshot limit");
   if(value.hasFileDescriptors())throw new IllegalArgumentException("PendingIntent must not contain file descriptors");
   PendingIntentSnapshot result=new PendingIntentSnapshot(value);owned=true;return result;
  }finally{if(!owned)value.recycle();}
 }
 synchronized byte[] bytes(){
  if(stored==null)throw new IllegalStateException("PendingIntent snapshot closed");
  return stored.hasBinders()?null:stored.marshall();
 }
 synchronized Intent read(){
  if(stored==null)throw new IllegalStateException("PendingIntent snapshot closed");
  Parcel value=Parcel.obtain();try{
   value.appendFrom(stored,0,stored.dataSize());value.setDataPosition(0);
   Intent result=Intent.CREATOR.createFromParcel(value);value.enforceNoDataAvail();return result;
  }finally{value.recycle();}
 }
 public synchronized void close(){if(stored!=null){stored.recycle();stored=null;}}
}
