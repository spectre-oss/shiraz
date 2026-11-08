// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import javax.net.ssl.*;
import android.net.http.X509TrustManagerExtensions;
import shiraz.runtime.*;

/** Real sockets and engines use the same default trust path as applications. */
public final class ApplicationTrustSmoke {
    static String apk;static int port;static int passed;
    static Properties facts(String config,boolean debug){Properties p=new Properties();p.setProperty("package","org.shiraz.networktrust");p.setProperty("targetSdk","35");p.setProperty("sourceDir",apk);p.setProperty("networkSecurityConfig","@xml/"+config);p.setProperty("debuggable",Boolean.toString(debug));return p;}
    static void policy(String config)throws Exception{HostNetworkPolicy.install(facts(config,false));}
    static SSLContext context()throws Exception{SSLContext c=SSLContext.getInstance("TLS");c.init(null,null,null);return c;}
    static void request(SSLSocketFactory factory,String host,boolean rejected)throws Exception{
        Socket raw=new Socket();raw.connect(new InetSocketAddress("127.0.0.1",port),2000);
        try(SSLSocket socket=(SSLSocket)factory.createSocket(raw,host,port,true)){
            socket.setSoTimeout(2000);
            try{
                socket.getOutputStream().write("GET /body HTTP/1.0\r\nHost: localhost\r\n\r\n".getBytes("US-ASCII"));
                if(rejected)throw new AssertionError("Rejected TLS policy sent application bytes");
                BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),"US-ASCII"));
                NetworkChecks.require(in.readLine().contains("200"),"application trusted HTTP status");String line;while(!(line=in.readLine()).isEmpty()){}
                NetworkChecks.require(in.read()==42&&in.read()==-1,"application trusted HTTP body");
            }catch(SSLException expected){if(!rejected)throw expected;NetworkChecks.require(!socket.getSession().isValid(),"rejected session invalid");}
        }finally{raw.close();}
    }
    static void test(String config,String host,boolean rejected)throws Exception{policy(config);request(context().getSocketFactory(),host,rejected);System.out.println("APP_TRUST_RESULT "+config+" "+host+" "+(rejected?"REJECT":"ACCEPT"));passed++;}
    static X509Certificate cert(File file)throws Exception{try(InputStream in=new FileInputStream(file)){return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(in);}}
    static SSLContext server(File assets)throws Exception{
        X509Certificate cert=cert(new File(assets,"a/loopback-cert.pem"));byte[] key=java.nio.file.Files.readAllBytes(new File(assets,"a/loopback-key.der").toPath());
        KeyStore store=KeyStore.getInstance("BKS");store.load(null,null);store.setKeyEntry("fixture",KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key)),new char[0],new java.security.cert.Certificate[]{cert});
        KeyManagerFactory factory=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());factory.init(store,new char[0]);
        SSLContext server=SSLContext.getInstance("TLS");server.init(factory.getKeyManagers(),null,null);return server;
    }
    static boolean clientCertificateChosen;
    static SSLContext clientWithCertificate(File assets)throws Exception{
        final X509Certificate certificate=cert(new File(assets,"a/loopback-cert.pem"));
        final PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(java.nio.file.Files.readAllBytes(new File(assets,"a/loopback-key.der").toPath())));
        X509KeyManager manager=new X509KeyManager(){
            public String[] getClientAliases(String type,java.security.Principal[] issuers){return new String[]{"fixture"};}
            public String chooseClientAlias(String[] types,java.security.Principal[] issuers,Socket socket){clientCertificateChosen=true;return "fixture";}
            public String[] getServerAliases(String type,java.security.Principal[] issuers){return null;}
            public String chooseServerAlias(String type,java.security.Principal[] issuers,Socket socket){return null;}
            public X509Certificate[] getCertificateChain(String alias){return new X509Certificate[]{certificate};}
            public PrivateKey getPrivateKey(String alias){return key;}
        };
        SSLContext context=SSLContext.getInstance("TLS");context.init(new KeyManager[]{manager},null,null);return context;
    }
    static void clientAuth(SSLContext clientContext,SSLContext serverContext,boolean required,boolean supplied)throws Exception{
        SSLEngine client=clientContext.createSSLEngine("localhost",443),server=serverContext.createSSLEngine();client.setUseClientMode(true);server.setUseClientMode(false);
        client.setEnabledProtocols(new String[]{"TLSv1.2"});server.setEnabledProtocols(new String[]{"TLSv1.2"});
        if(required)server.setNeedClientAuth(true);else server.setWantClientAuth(true);
        java.nio.ByteBuffer toServer=java.nio.ByteBuffer.allocate(65536),toClient=java.nio.ByteBuffer.allocate(65536),clientPlain=java.nio.ByteBuffer.allocate(32768),serverPlain=java.nio.ByteBuffer.allocate(32768);
        boolean rejected=false,complete=false;
        try{
            client.beginHandshake();server.beginHandshake();long deadline=System.nanoTime()+4000000000L;
            for(int i=0;i<512&&System.nanoTime()<deadline;i++){
                try{TlsEngineChecks.tasks(client);TlsEngineChecks.wrap(client,toServer);TlsEngineChecks.unwrap(client,toClient,clientPlain);TlsEngineChecks.tasks(server);TlsEngineChecks.unwrap(server,toServer,serverPlain);TlsEngineChecks.tasks(server);TlsEngineChecks.wrap(server,toClient);}
                catch(SSLException expected){rejected=true;break;}
                if(client.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING&&server.getHandshakeStatus()==SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING){complete=true;break;}
            }
            NetworkChecks.require(rejected==(required||supplied)&&((required||supplied)||complete),"server client certificate policy required="+required+" rejected="+rejected+" supplied="+supplied+" complete="+complete);
            NetworkChecks.require(serverPlain.position()==0&&clientPlain.position()==0,"client-auth handshake exposes no application data");
        }finally{client.closeOutbound();server.closeOutbound();}
    }
    public static void main(String[] args)throws Exception{
        apk=args[0];port=Integer.parseInt(args[1]);File assets=new File(args[2]);HostTlsProvider.install();
        // Cache factories before installing application policy, as boot normally does.
        SSLSocketFactory cached=SSLContext.getDefault().getSocketFactory();
        test("custom","localhost",false);test("leaf","localhost",false);test("system","localhost",true);
        test("domain","localhost",false);test("domain","127.0.0.1",true);
        test("domain","sub.localhost",false);test("domain","badlocalhost",true);
        test("mismatch","localhost",true);test("sha512","localhost",false);test("backup","localhost",false);
        test("expired","localhost",false);test("empty_pins","localhost",false);
        test("inherited_pins","localhost",true);test("override_pins","localhost",false);
        test("multi_pem","localhost",false);test("system_custom","localhost",false);
        policy("custom");request(cached,"localhost",false);policy("system");request(cached,"localhost",true);passed++;
        HostNetworkPolicy.install(facts("debug",false));request(cached,"localhost",true);
        HostNetworkPolicy.install(facts("debug",true));request(cached,"localhost",false);
        HostNetworkPolicy.install(facts("debug_no_override",true));request(cached,"localhost",true);passed++;
        HostNetworkPolicy.install(facts("debug_user",false));request(cached,"localhost",false);
        try{HostNetworkPolicy.install(facts("debug_user",true));throw new AssertionError("active unsupported user-CA store accepted");}catch(UnsupportedOperationException expected){}passed++;
        // Android chain cleaner returns only the validated chain, never arbitrary
        // unrelated certificates supplied by the peer or a caller.
        policy("mismatch");TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init((KeyStore)null);
        X509TrustManager manager=(X509TrustManager)factory.getTrustManagers()[0];X509TrustManagerExtensions extensions=new X509TrustManagerExtensions(manager);
        X509Certificate a=cert(new File(assets,"a/loopback-cert.pem")),b=cert(new File(assets,"b/loopback-cert.pem"));
        try{extensions.checkServerTrusted(new X509Certificate[]{a,b},"RSA","localhost");throw new AssertionError("unrelated certificate bypassed pins");}catch(CertificateException expected){}passed++;
        policy("domain");
        NetworkChecks.require(extensions.isSameTrustConfiguration("localhost","sub.localhost"),"same domain trust policy");
        NetworkChecks.require(!extensions.isSameTrustConfiguration("localhost","127.0.0.1"),"distinct domain trust policy");
        NetworkChecks.require(!extensions.isUserAddedCertificate(a),"APK CA is not user-store CA");
        try{manager.checkServerTrusted(new X509Certificate[]{a},"RSA");throw new AssertionError("domain trust without peer name accepted");}catch(CertificateException expected){}
        NetworkChecks.require(!extensions.checkServerTrusted(new X509Certificate[]{a},"RSA","localhost").isEmpty(),"Android trusted chain");
        // Scope restores after nested and failed validation; a worker thread must
        // never inherit the previous connection's hostname.
        try(ApplicationTrustManager.Peer outer=ApplicationTrustManager.peer("localhost")){
            manager.checkServerTrusted(new X509Certificate[]{a},"RSA");
            try(ApplicationTrustManager.Peer inner=ApplicationTrustManager.peer("127.0.0.1")){try{manager.checkServerTrusted(new X509Certificate[]{a},"RSA");throw new AssertionError("nested domain policy bypass");}catch(CertificateException expected){}}
            manager.checkServerTrusted(new X509Certificate[]{a},"RSA");
        }
        try{manager.checkServerTrusted(new X509Certificate[]{a},"RSA");throw new AssertionError("peer scope leaked");}catch(CertificateException expected){}passed++;
        java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            List<java.util.concurrent.Future<?>> futures=new ArrayList<java.util.concurrent.Future<?>>();
            for(final String peer:new String[]{"localhost","127.0.0.1"})futures.add(workers.submit(new java.util.concurrent.Callable<Void>(){public Void call()throws Exception{
                for(int i=0;i<10;i++)try(ApplicationTrustManager.Peer scope=ApplicationTrustManager.peer(peer)){
                    boolean accepted=true;try{manager.checkServerTrusted(new X509Certificate[]{a},"RSA");}catch(CertificateException expected){accepted=false;}
                    NetworkChecks.require(accepted==peer.equals("localhost"),"concurrent peer policy isolation");
                }return null;
            }}));
            for(java.util.concurrent.Future<?> future:futures)future.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }finally{workers.shutdownNow();}passed++;
        policy("custom");
        try{extensions.checkServerTrusted(new X509Certificate[]{cert(new File(assets,"wrong-purpose.pem"))},"RSA","localhost");throw new AssertionError("TLS client-only certificate accepted as server");}catch(CertificateException expected){}passed++;
        byte[] damaged=cert(new File(assets,"leaf.pem")).getEncoded();damaged[damaged.length-1]^=1;
        X509Certificate invalidSignature=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(damaged));
        try{extensions.checkServerTrusted(new X509Certificate[]{invalidSignature},"RSA","localhost");throw new AssertionError("invalid leaf signature accepted");}catch(CertificateException expected){}passed++;
        for(String invalid:new String[]{"invalid_cert","invalid_date","invalid_pin","invalid_digest","invalid_base_pin"}){
            try{policy(invalid);throw new AssertionError("invalid policy accepted: "+invalid);}catch(IllegalArgumentException|UnsupportedOperationException expected){}
            request(cached,"localhost",false);
        }passed++;
        policy("custom");HttpsURLConnection connection=(HttpsURLConnection)new URL("https://localhost:"+port+"/body").openConnection();connection.setConnectTimeout(2000);connection.setReadTimeout(2000);
        try{NetworkChecks.require(connection.getResponseCode()==200,"default HTTPS honors APK trust");try(InputStream in=connection.getInputStream()){NetworkChecks.require(in.read()==42&&in.read()==-1,"default HTTPS body");}}finally{connection.disconnect();}
        policy("mismatch");connection=(HttpsURLConnection)new URL("https://localhost:"+port+"/body").openConnection();connection.setConnectTimeout(2000);connection.setReadTimeout(2000);
        try{try{connection.getResponseCode();throw new AssertionError("default HTTPS ignored application pins");}catch(SSLException expected){}}finally{connection.disconnect();}passed++;
        SSLContext server=server(assets);
        policy("domain");TlsEngineChecks.run(context(),server,"localhost",false);passed++;
        policy("mismatch");TlsEngineChecks.run(context(),server,"localhost",true);passed++;
        policy("system");TlsEngineChecks.run(context(),server,"localhost",true);passed++;
        policy("custom");clientAuth(context(),server,false,false);passed++;clientAuth(context(),server,true,false);passed++;clientAuth(clientWithCertificate(assets),server,false,true);NetworkChecks.require(clientCertificateChosen,"untrusted optional client certificate was offered");passed++;
        System.out.println("APPLICATION_TRUST_VERIFIED "+passed);
    }
}
