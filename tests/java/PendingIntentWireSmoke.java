// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.nio.charset.StandardCharsets;
public final class PendingIntentWireSmoke {
 static PendingIntentCapability parse(String text)throws Exception{return PendingIntentWire.reply(text.getBytes(StandardCharsets.UTF_8));}
 public static void main(String[] args)throws Exception{
  String token="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  String facts="{\"token\":\""+token+"\",\"package\":\"test.owner\",\"sha256\":\""+token+"\",\"uid\":10000,\"kind\":0,\"flags\":67108864}";
  String valid="{\"ok\":true,\"pending_intent\":"+facts+"}";
  PendingIntentCapability cap=parse(valid);
  if(!cap.token.equals(token)||!cap.packageName.equals("test.owner")||cap.uid!=10000||cap.flags!=67108864)throw new AssertionError("Host facts lost");
  if(parse("{\"ok\":true,\"pending_intent\":null}")!=null)throw new AssertionError("No-create result");
  for(String bad:new String[]{valid+" {}",valid.replace("10000","9999"),valid.replace("67108864","1"),valid.replace("test.owner","../owner"),valid.replace("\"kind\":0","\"kind\":0,\"kind\":1"),"{\"ok\":true}","{\"ok\":true,\"canceled\":false,\"pending_intent\":null}","{\"ok\":false,\"pending_intent\":null,\"error\":\"denied\"}"}){
   try{parse(bad);throw new AssertionError("Malformed capability accepted");}catch(java.io.IOException|IllegalArgumentException expected){}
  }
  try{parse("{\"ok\":false,\"canceled\":true,\"error\":\"unavailable\"}");throw new AssertionError("Canceled token accepted");}catch(ServiceBrokerClient.PendingIntentCanceledException expected){}
  try{parse("{\"ok\":false,\"error\":\"denied\"}");throw new AssertionError("Denial ignored");}catch(SecurityException expected){}
  String request=PendingIntentWire.request("pending_intent_obtain",null,0,-1,0,new byte[]{1},new byte[]{2});
  if(request.contains("uid")||request.contains("package")||!request.contains("AQ==")||!request.contains("Ag=="))throw new AssertionError("Wrong authority in request");
  try{PendingIntentWire.request("pending_intent_obtain",null,0,0,0,new byte[1],new byte[32769]);throw new AssertionError("Oversize snapshot");}catch(IllegalArgumentException expected){}
  if(PendingIntentWire.status("{\"ok\":true}".getBytes(StandardCharsets.UTF_8),false)!=0)throw new AssertionError();
  if(PendingIntentWire.status("{\"ok\":false,\"canceled\":false,\"uncertain\":true}".getBytes(StandardCharsets.UTF_8),true)!=3)throw new AssertionError();
  for(String bad:new String[]{"{\"ok\":true,\"uncertain\":false}","{\"ok\":false}","{\"ok\":false,\"canceled\":true,\"uncertain\":true}","{\"ok\":true,\"ok\":true}","{\"ok\":true} {}"}){
   try{PendingIntentWire.status(bad.getBytes(StandardCharsets.UTF_8),true);throw new AssertionError("Invalid dispatch status accepted");}catch(java.io.IOException expected){}
  }
  System.out.println("PENDING_INTENT_WIRE_OK");
 }
}
