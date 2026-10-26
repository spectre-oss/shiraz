// SPDX-License-Identifier: GPL-3.0-only
import java.net.*;
import java.io.*;
import java.security.*;
import javax.net.ssl.*;
import java.security.cert.CertificateFactory;
public final class NetworkSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}
 static String readTls(String url)throws Exception{
  HttpsURLConnection c=(HttpsURLConnection)new URL(url+"/body").openConnection();c.setConnectTimeout(3000);c.setReadTimeout(3000);
  try{check(c.getResponseCode()==200,"HTTPS status");ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[64];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return out.toString("UTF-8");}finally{c.disconnect();}
 }
 static void concurrentClose(SSLContext context, String url)throws Exception {
  URL address=new URL(url);
  for(int attempt=0;attempt<20;attempt++){
   final SSLSocket socket=(SSLSocket)context.getSocketFactory().createSocket("localhost",address.getPort());
   socket.setSoTimeout(2000);socket.startHandshake();
   socket.getOutputStream().write("GET /slow HTTP/1.0\r\nHost: localhost\r\n\r\n".getBytes("US-ASCII"));
   final InputStream input=socket.getInputStream();
   int matched=0;byte[] delimiter={13,10,13,10};
   while(matched<4){int b=input.read();check(b>=0,"slow response headers");matched=b==delimiter[matched]?matched+1:0;}
   check(input.read()=='a',"slow response first byte");
   final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1);
   final java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
   Thread reader=new Thread(new Runnable(){public void run(){entered.countDown();try{input.read();}catch(IOException expected){}catch(Throwable t){failure.set(t);}}},"tls-close-reader");
   reader.start();entered.await();Thread.sleep(20);socket.close();reader.join(3000);
   check(!reader.isAlive(),"close left TLS reader blocked");check(failure.get()==null,"TLS close reader failure: "+failure.get());
   try{input.read();throw new AssertionError("read after TLS close accepted");}catch(IOException expected){}
   socket.close();
  }
 }
 public static void main(String[] args)throws Exception{
  check(hex(MessageDigest.getInstance("MD5").digest("abc".getBytes("UTF-8"))).equals("900150983cd24fb0d6963f7d28e17f72"),"MD5 vector");
  check(hex(MessageDigest.getInstance("SHA-256").digest("abc".getBytes("UTF-8"))).equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),"SHA256 vector");
  try{MessageDigest.getInstance("missing-digest");throw new AssertionError("unknown algorithm accepted");}catch(NoSuchAlgorithmException expected){}
  HttpURLConnection c=(HttpURLConnection)new URL(args[0]+"/redirect").openConnection();c.setConnectTimeout(3000);c.setReadTimeout(3000);
  check(c.getResponseCode()==200,"HTTP redirect status");check("text/plain".equals(c.getContentType()),"HTTP headers");
  ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[32];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}finally{c.disconnect();}
  check("shiraz-network-body".equals(out.toString("UTF-8")),"HTTP response body");
  c=(HttpURLConnection)new URL(args[0]+"/missing").openConnection();c.setConnectTimeout(3000);c.setReadTimeout(3000);check(c.getResponseCode()==404,"HTTP error status");try{c.getInputStream();throw new AssertionError("missing response accepted");}catch(FileNotFoundException expected){}finally{c.disconnect();}
  String tls=args[1];
  try{readTls(tls);throw new AssertionError("Untrusted certificate accepted");}catch(SSLException expected){}
  KeyStore trust=KeyStore.getInstance("BKS");trust.load(null,null);
  try(InputStream cert=new FileInputStream(args[2])){trust.setCertificateEntry("fixture",CertificateFactory.getInstance("X.509").generateCertificate(cert));}
  TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(trust);
  SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(null,tm.getTrustManagers(),null);
  HttpsURLConnection.setDefaultSSLSocketFactory(ssl.getSocketFactory());
  check("shiraz-network-body".equals(readTls(tls)),"trusted HTTPS body");
  try{readTls(tls.replace("localhost","127.0.0.1"));throw new AssertionError("Hostname mismatch accepted");}catch(SSLException expected){}
  concurrentClose(ssl,tls);
  ssl.getClientSessionContext().setSessionTimeout(2);
  check(ssl.getClientSessionContext().getSessionTimeout()==2,"closed session timeout update");
  System.out.println("NETWORK_TLS_CONCURRENT_CLOSE_OK");
  System.out.println("NETWORK_HTTP_TLS_DIGEST_OK");
 }
}
