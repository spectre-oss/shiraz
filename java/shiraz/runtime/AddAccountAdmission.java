// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Version-one account-add admission on the private host/service control channel. */
final class AddAccountAdmission {
 final int uid;final String caller,type;
 private AddAccountAdmission(int uid,String caller,String type){this.uid=uid;this.caller=caller;this.type=type;}
 private static String string(ByteBuffer input)throws IOException{
  int size=input.getInt();if(size<1||size>4096||size>input.remaining())throw new IOException("Account admission string length");
  byte[] bytes=new byte[size];input.get(bytes);String value=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
  if(value.length()>1024||value.indexOf(0)>=0)throw new IOException("Invalid account admission string");return value;
 }
 static AddAccountAdmission decode(byte[] bytes)throws IOException{
  if(bytes.length>8220)throw new IOException("Account admission frame limit");
  try{
   ByteBuffer input=ByteBuffer.wrap(bytes);
   if(input.getInt()!=4||input.getInt()!=1)throw new IOException("Account admission operation/version");
   int uid=input.getInt();String caller=string(input),type=string(input);
   if(uid<10000||uid>19999||!caller.matches("[A-Za-z_][A-Za-z0-9_.]*")||input.hasRemaining())throw new IOException("Invalid account admission identity");
   return new AddAccountAdmission(uid,caller,type);
  }catch(java.nio.BufferUnderflowException error){throw new IOException("Truncated account admission");}
 }
}
