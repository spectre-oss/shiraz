// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.io.*;
import java.nio.*;
import java.util.*;
import android.content.pm.Signature;

/** Bounded signer certificate metadata. This is not APK authenticity verification. */
final class ApkCertificates {
 private static ByteBuffer take(ByteBuffer source,int length)throws IOException {
  if(length<0||length>source.remaining())throw new IOException("Invalid signing block length");
  ByteBuffer result=source.slice().order(ByteOrder.LITTLE_ENDIAN);result.limit(length);source.position(source.position()+length);return result;
 }
 private static ByteBuffer field(ByteBuffer source)throws IOException {
  if(source.remaining()<4)throw new IOException("Truncated signing field");return take(source,source.getInt());
 }
 static Signature[] read(String path) {
  try(RandomAccessFile file=new RandomAccessFile(path,"r")) {
   long length=file.length();byte[] tail=new byte[(int)Math.min(length,65557)];file.seek(length-tail.length);file.readFully(tail);
   ByteBuffer end=ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN);int offset=-1;
   for(int i=tail.length-22;i>=0;i--)if(end.getInt(i)==0x06054b50&&i+22+(end.getShort(i+20)&65535)==tail.length){offset=i;break;}
   if(offset<0)throw new IOException("Missing ZIP end");
   long directory=end.getInt(offset+16)&0xffffffffL;
   if(directory<32||directory>length-tail.length+offset)throw new IOException("Invalid ZIP directory");
   byte[] footer=new byte[24];file.seek(directory-24);file.readFully(footer);ByteBuffer foot=ByteBuffer.wrap(footer).order(ByteOrder.LITTLE_ENDIAN);
   if(foot.getLong(8)!=0x20676953204b5041L||foot.getLong(16)!=0x3234206b636f6c42L)return new Signature[0];
   long size=foot.getLong(0);if(size<24||size>16*1024*1024||size+8>directory)throw new IOException("Invalid APK signing size");
   byte[] data=new byte[(int)size+8];file.seek(directory-size-8);file.readFully(data);ByteBuffer block=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
   if(block.getLong()!=size)throw new IOException("APK signing size mismatch");block.limit(data.length-24);
   while(block.hasRemaining()) {
    if(block.remaining()<12)throw new IOException("Truncated APK signing entry");long count=block.getLong();if(count<4||count>block.remaining())throw new IOException("APK signing entry size");
    ByteBuffer entry=take(block,(int)count);int id=entry.getInt();if(id!=0x7109871a)continue;
    ByteBuffer signers=field(entry);ArrayList<Signature> result=new ArrayList<Signature>();
    while(signers.hasRemaining()) {
     if(result.size()>=32)throw new IOException("Too many APK signers");ByteBuffer signed=field(field(signers));field(signed);ByteBuffer certificates=field(signed);ByteBuffer certificate=field(certificates);
     byte[] encoded=new byte[certificate.remaining()];certificate.get(encoded);if(encoded.length==0)throw new IOException("Empty APK certificate");result.add(new Signature(encoded));
    }
    return result.toArray(new Signature[result.size()]);
   }
   return new Signature[0];
  }catch(IOException|RuntimeException error){throw new IllegalStateException("Cannot read APK signer metadata",error);}
 }
}
