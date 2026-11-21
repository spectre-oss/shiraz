// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.io.*;
import java.nio.*;
import java.util.*;
import android.content.pm.Signature;

/** Bounded signer certificate metadata. This is not APK authenticity verification. */
final class ApkCertificates {
 private static Signature[] readV1(String path)throws IOException {
  try(java.util.jar.JarFile jar=new java.util.jar.JarFile(path,true)){
   long signingBytes=0;int entries=0;java.util.Enumeration<java.util.jar.JarEntry> all=jar.entries();
   while(all.hasMoreElements()){
    java.util.jar.JarEntry entry=all.nextElement();if(++entries>65536)throw new IOException("APK entry limit");
    if(entry.getName().toUpperCase(java.util.Locale.ROOT).startsWith("META-INF/")){
     long size=entry.getSize();if(size<0||size>4*1024*1024||(signingBytes+=size)>16*1024*1024)throw new IOException("APK signing metadata limit");
    }
   }
   java.util.jar.JarEntry manifest=jar.getJarEntry("AndroidManifest.xml");
   if(manifest==null)throw new IOException("Missing Android manifest");
   if(manifest.getSize()>16*1024*1024)throw new IOException("Manifest certificate-read limit");
   try(InputStream input=jar.getInputStream(manifest)){
    byte[] buffer=new byte[8192];int count,total=0;
    while((count=input.read(buffer))!=-1){total+=count;if(total>16*1024*1024)throw new IOException("Manifest certificate-read limit");}
   }
   java.security.CodeSigner[] signers=manifest.getCodeSigners();
   if(signers==null)return new Signature[0];
   if(signers.length>32)throw new IOException("Too many APK signers");
   Signature[] result=new Signature[signers.length];
   for(int i=0;i<signers.length;i++){
    java.util.List<? extends java.security.cert.Certificate> chain=signers[i].getSignerCertPath().getCertificates();
    if(chain.isEmpty())throw new IOException("Empty signer certificate path");
    try{byte[] encoded=chain.get(0).getEncoded();if(encoded.length==0||encoded.length>1024*1024)throw new IOException("Signer certificate size");result[i]=new Signature(encoded);}
    catch(java.security.cert.CertificateEncodingException error){throw new IOException("Cannot encode signer certificate",error);}
   }
   return result;
  }
 }
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
   if(foot.getLong(8)!=0x20676953204b5041L||foot.getLong(16)!=0x3234206b636f6c42L)return readV1(path);
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
