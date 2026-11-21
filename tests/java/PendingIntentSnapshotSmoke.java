// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.IAccountAuthenticatorResponse;
import android.content.Intent;
import android.os.*;
public final class PendingIntentSnapshotSmoke {
 public static void main(String[] args)throws Exception{
  final int[] replies={0};
  IAccountAuthenticatorResponse endpoint=new IAccountAuthenticatorResponse.Stub(){
   public void onResult(Bundle result){if(!"result".equals(result.getString("value")))throw new AssertionError();replies[0]++;}
   public void onRequestContinued(){}public void onError(int code,String text){throw new AssertionError();}
  };
  Intent original=new Intent("snapshot").putExtra("reply",new AccountAuthenticatorResponse(endpoint)).putExtra("value","original");
  PendingIntentSnapshot snapshot=PendingIntentSnapshot.capture(original);
  original.putExtra("value","changed");
  Intent first=snapshot.read();first.putExtra("value","first-mutated");
  Intent second=snapshot.read();
  if(!"original".equals(second.getStringExtra("value")))throw new AssertionError("Snapshot mutable");
  snapshot.close();
  Bundle result=new Bundle();result.putString("value","result");
  ((AccountAuthenticatorResponse)second.getParcelableExtra("reply")).onResult(result);
  if(replies[0]!=1)throw new AssertionError("Binder callback lost after snapshot release");
  try{snapshot.read();throw new AssertionError("Closed snapshot readable");}catch(IllegalStateException expected){}
  Parcel source=Parcel.obtain(),copy=Parcel.obtain();Binder binder=new Binder();
  try{
   source.writeInt(17);source.writeStrongBinder(binder);source.writeInt(29);
   copy.writeInt(5);copy.appendFrom(source,4,8);
   if(source.dataPosition()!=12)throw new AssertionError("Source position changed");
   source.recycle();copy.setDataPosition(0);
   if(copy.readInt()!=5||copy.readStrongBinder()!=binder||copy.readInt()!=29)throw new AssertionError("Attachment offsets");
   try{copy.marshall();throw new AssertionError("Attachment serialized as bytes");}catch(IllegalStateException expected){}
   int size=copy.dataSize();
   try{copy.appendFrom(copy,5,3);throw new AssertionError("Partial attachment accepted");}catch(IllegalArgumentException expected){}
   if(copy.dataSize()!=size)throw new AssertionError("Partial copy mutated destination");
   copy.appendFrom(copy,5,0);
   copy.appendFrom(copy,4,4);copy.setDataPosition(size);if(copy.readStrongBinder()!=binder)throw new AssertionError("Self copy attachment lost");
  }finally{source.recycle();copy.recycle();}
  System.out.println("PENDING_INTENT_SNAPSHOT_OK");
 }
}
