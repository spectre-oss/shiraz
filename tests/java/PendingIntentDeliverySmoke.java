// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.Intent;
import java.nio.charset.StandardCharsets;
public final class PendingIntentDeliverySmoke {
 static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
 static String encoded(Intent i){return java.util.Base64.getEncoder().encodeToString(HostPendingIntents.snapshot(i));}
 public static void main(String[] args)throws Exception{
  String token="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  String first="{\"op\":\"pending_intent_deliver\",\"token\":\""+token+"\",\"package\":\"test.owner\",\"sha256\":\""+token+"\",\"uid\":10000,\"kind\":1,\"flags\":0,\"payload\":\""+encoded(new Intent("original").putExtra("value","old"))+"\"}";
  String fill="{\"fill\":\""+encoded(new Intent("replacement").putExtra("value","new"))+"\"}";
  HostPendingIntents.Delivery result=HostPendingIntents.decode(bytes(first),bytes(fill));
  if(!result.intent.getAction().equals("original")||!result.intent.getStringExtra("value").equals("new"))throw new AssertionError("Mutable completion extras");
  for(String bad:new String[]{first+" {}",first.replace("\"kind\":1","\"kind\":1,\"kind\":1"),first.replace("\"uid\":10000","\"uid\":\"10000\""),first.replace("\"flags\":0","\"flags\":67108864")}){
   try{HostPendingIntents.decode(bytes(bad),bytes(fill));throw new AssertionError("Invalid delivery accepted");}catch(java.io.IOException|IllegalArgumentException expected){}
  }
  android.os.Parcel p=android.os.Parcel.obtain();try{
   new Intent("original").writeToParcel(p,0);p.writeInt(77);
   String trailing=java.util.Base64.getEncoder().encodeToString(p.marshall());
   String malformed=first.replace(encoded(new Intent("original").putExtra("value","old")),trailing);
   try{HostPendingIntents.decode(bytes(malformed),bytes("{\"fill\":null}"));throw new AssertionError("Trailing parcel accepted");}catch(IllegalArgumentException expected){}
  }finally{p.recycle();}
  System.out.println("PENDING_INTENT_DELIVERY_OK");
 }
}
