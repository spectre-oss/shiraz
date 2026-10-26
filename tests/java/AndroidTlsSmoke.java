// SPDX-License-Identifier: GPL-3.0-only
import java.io.*;
import java.net.*;
import java.security.*;
import javax.net.ssl.*;
import java.security.cert.CertificateFactory;
import com.android.org.conscrypt.*;
public final class AndroidTlsSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static OpenSSLSocketImpl socket(SSLContext context,String host,int port)throws Exception{
  Socket plain=new Socket();plain.connect(new InetSocketAddress("127.0.0.1",port),3000);
  OpenSSLSocketImpl socket=(OpenSSLSocketImpl)context.getSocketFactory().createSocket(plain,host,port,true);socket.setSoTimeout(3000);
  SSLParameters params=socket.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");socket.setSSLParameters(params);socket.setHostname(host);return socket;
 }
 static String apkRequest(ClassLoader loader,Object client,String url)throws Exception{
  Class<?> requestClass=loader.loadClass("okhttp3.Request"),builderClass=loader.loadClass("okhttp3.Request$Builder");Object builder=builderClass.newInstance();builderClass.getMethod("url",String.class).invoke(builder,url);Object request=builderClass.getMethod("build").invoke(builder);
  Object call=loader.loadClass("okhttp3.OkHttpClient").getMethod("newCall",requestClass).invoke(client,request);
  Object response=loader.loadClass("okhttp3.Call").getMethod("execute").invoke(call);
  try{Object body=loader.loadClass("okhttp3.Response").getMethod("body").invoke(response);long length=(Long)loader.loadClass("okhttp3.ResponseBody").getMethod("contentLength").invoke(body);check(length>=0&&length<=4096,"APK response bound");return new String((byte[])loader.loadClass("okhttp3.ResponseBody").getMethod("bytes").invoke(body),"UTF-8");}
  finally{loader.loadClass("okhttp3.Response").getMethod("close").invoke(response);}
 }
 public static void main(String[] args)throws Exception{
  if(args.length==1&&args[0].equals("custom")){
   SSLContext custom=SSLContext.getInstance("TLS","wolfJSSE");custom.init(null,null,null);SSLContext.setDefault(custom);SSLSocketFactory customFactory=custom.getSocketFactory();HttpsURLConnection.setDefaultSSLSocketFactory(customFactory);
   shiraz.runtime.HostTlsProvider.install();check(SSLContext.getDefault()==custom&&HttpsURLConnection.getDefaultSSLSocketFactory()==customFactory,"explicit custom defaults preserved");System.out.println("CUSTOM_TLS_DEFAULTS_PRESERVED");return;
  }
  // Exercise cached defaults too, before installation.
  SSLSocketFactory.getDefault();HttpsURLConnection.getDefaultSSLSocketFactory();
  shiraz.runtime.HostTlsProvider.install();
  check(SSLContext.getDefault().getSocketFactory() instanceof OpenSSLSocketFactoryImpl,"default context bridge");
  check(SSLSocketFactory.getDefault() instanceof OpenSSLSocketFactoryImpl,"cached default socket factory bridge");
  check(HttpsURLConnection.getDefaultSSLSocketFactory() instanceof OpenSSLSocketFactoryImpl,"cached HTTPS bridge");
  SSLContext defaultContext=SSLContext.getInstance("TLS");defaultContext.init(null,null,null);int port=Integer.parseInt(args[0]);
  try(OpenSSLSocketImpl socket=socket(defaultContext,"localhost",port)){try{socket.startHandshake();throw new AssertionError("Untrusted certificate accepted");}catch(SSLException expected){}}
  KeyStore trust=KeyStore.getInstance("BKS");trust.load(null,null);
  try(InputStream input=new FileInputStream(args[1])){trust.setCertificateEntry("fixture",CertificateFactory.getInstance("X.509").generateCertificate(input));}
  TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(trust);
  SSLContext context=SSLContext.getInstance("TLS");TrustManager[] managers=factory.getTrustManagers();context.init(null,managers,null);
  java.lang.reflect.Field field=OpenSSLSocketFactoryImpl.class.getDeclaredField("sslParameters");field.setAccessible(true);
  Object parameters=field.get(context.getSocketFactory());java.lang.reflect.Field manager=SSLParametersImpl.class.getDeclaredField("x509TrustManager");manager.setAccessible(true);check(manager.get(parameters)==managers[0],"actual trust manager exposed");
  try(OpenSSLSocketImpl socket=socket(context,"localhost",port)){
   socket.setAlpnProtocols(new byte[]{2,'h','2',8,'h','t','t','p','/','1','.','1'});socket.setUseSessionTickets(true);
   socket.addHandshakeCompletedListener(new HandshakeCompletedListener(){public void handshakeCompleted(HandshakeCompletedEvent event){try{event.getSession().getSessionContext().setSessionCacheSize(1);throw new AssertionError("Handshake event exposed raw context");}catch(UnsupportedOperationException expected){}}});
   socket.startHandshake();try{socket.getSession().getSessionContext().setSessionCacheSize(1);throw new AssertionError("Socket session exposed raw context");}catch(UnsupportedOperationException expected){}
   check("http/1.1".equals(new String(socket.getAlpnSelectedProtocol(),"UTF-8")),"negotiated ALPN");
   socket.getOutputStream().write("GET /body HTTP/1.0\r\nHost: localhost\r\n\r\n".getBytes("UTF-8"));
   BufferedReader input=new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));
   check(input.readLine().contains("200"),"HTTP status");String line;int length=-1;while(!(line=input.readLine()).isEmpty())if(line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());
   check(length==19,"bounded response length");char[] body=new char[length];int offset=0;while(offset<length){int n=input.read(body,offset,length-offset);check(n>0,"complete response");offset+=n;}check(new String(body).equals("shiraz-network-body"),"trusted response body");
   socket.close();try{socket.startHandshake();throw new AssertionError("Closed handshake accepted");}catch(SocketException expected){}
  }
  try(OpenSSLSocketImpl socket=socket(context,"localhost",port)){socket.startHandshake();byte[] alpn=socket.getAlpnSelectedProtocol();check(alpn==null||alpn.length==0,"no protocol invented without ALPN");}
  try(OpenSSLSocketImpl socket=socket(context,"wrong.example",port)){try{socket.startHandshake();throw new AssertionError("Wrong host accepted");}catch(SSLHandshakeException expected){check(socket.isClosed(),"failed endpoint closes transport");}}
  try(OpenSSLSocketImpl socket=socket(context,"wrong.example",port)){try{socket.getOutputStream().write("GET /forbidden HTTP/1.0\r\n\r\n".getBytes("UTF-8"));throw new AssertionError("Implicit wrong host write accepted");}catch(SSLHandshakeException expected){}}
  try(OpenSSLSocketImpl socket=socket(context,"wrong.example",port)){try{socket.getInputStream().read();throw new AssertionError("Implicit wrong host read accepted");}catch(SSLHandshakeException expected){}}
  try(OpenSSLSocketImpl socket=socket(context,"wrong.example",port)){SSLSession session=socket.getSession();check(!session.isValid()&&session.getCipherSuite().equals("SSL_NULL_WITH_NULL_NULL"),"implicit session failure remains invalid");check(socket.isClosed(),"session failure closes transport");}
  if(args.length>2){
   ClassLoader loader=(ClassLoader)Class.forName("dalvik.system.PathClassLoader").getConstructor(String.class,ClassLoader.class).newInstance(args[2],AndroidTlsSmoke.class.getClassLoader());
   Class<?> platform=loader.loadClass("okhttp3.internal.platform.Platform");Object companion=platform.getField("Companion").get(null);Object platformInstance=companion.getClass().getMethod("get").invoke(companion);check(platformInstance!=null,"exact APK platform initialized");
   Class<?> builderClass=loader.loadClass("okhttp3.OkHttpClient$Builder");Object defaultBuilder=builderClass.newInstance();Object defaultClient=builderClass.getMethod("build").invoke(defaultBuilder);try{apkRequest(loader,defaultClient,"https://localhost:"+port+"/forbidden");throw new AssertionError("Exact APK untrusted certificate accepted");}catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof SSLException,"exact APK untrusted rejection");}
   Object builder=builderClass.newInstance();builderClass.getMethod("sslSocketFactory",SSLSocketFactory.class,X509TrustManager.class).invoke(builder,context.getSocketFactory(),managers[0]);
   builderClass.getMethod("connectTimeout",long.class,java.util.concurrent.TimeUnit.class).invoke(builder,3L,java.util.concurrent.TimeUnit.SECONDS);builderClass.getMethod("readTimeout",long.class,java.util.concurrent.TimeUnit.class).invoke(builder,3L,java.util.concurrent.TimeUnit.SECONDS);
   Object client=builderClass.getMethod("build").invoke(builder);
   check(apkRequest(loader,client,"https://localhost:"+port+"/body").equals("shiraz-network-body"),"exact APK verified TLS body");
   try{apkRequest(loader,client,"https://127.0.0.1:"+port+"/forbidden");throw new AssertionError("Exact APK wrong hostname accepted");}catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof SSLException,"exact APK hostname rejection");}
   System.out.println("EXACT_APK_TLS_VERIFIED");
  }
  check(!context.getClientSessionContext().getIds().hasMoreElements(),"no client session is cached after mixed ALPN connections");
  try{context.getClientSessionContext().setSessionCacheSize(1);throw new AssertionError("Unsafe resumption reenabled");}catch(UnsupportedOperationException expected){}
  System.out.println("ANDROID_TLS_BRIDGE_VERIFIED");
 }
}
