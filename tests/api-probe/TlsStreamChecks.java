// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Normal TLS records followed by clean/abrupt transport closure, with bounded peers. */
final class TlsStreamChecks {
    static String run(String id,TlsNetworkChecks.Assets assets)throws Exception{
        X509Certificate certificate;
        try(InputStream input=assets.open("loopback-cert.pem")){certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(input);}
        certificate.checkValidity();
        PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(TlsNetworkChecks.bytes(assets.open("loopback-key.der"))));
        KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);store.setKeyEntry("fixture",key,new char[0],new java.security.cert.Certificate[]{certificate});
        KeyManagerFactory keys=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());keys.init(store,new char[0]);
        KeyStore trusted=KeyStore.getInstance(KeyStore.getDefaultType());trusted.load(null,null);trusted.setCertificateEntry("fixture",certificate);
        TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(trusted);
        SSLContext server=TlsNetworkChecks.context(keys.getKeyManagers(),null),client=TlsNetworkChecks.context(null,trust.getTrustManagers());
        List<SSLContext> clients=new ArrayList<SSLContext>();clients.add(client);
        if(Security.getProvider("wolfJSSE")!=null){SSLContext backend=SSLContext.getInstance("TLS","wolfJSSE");backend.init(null,trust.getTrustManagers(),null);clients.add(backend);}
        List<String> protocols=new ArrayList<String>();protocols.add("TLSv1.2");
        if(Arrays.asList(client.getSupportedSSLParameters().getProtocols()).contains("TLSv1.3"))protocols.add("TLSv1.3");
        for(SSLContext tested:clients){
            if(id.equals("network.tls_stream_clean"))argumentsBeforeHandshake(tested);
            for(String protocol:protocols){for(boolean bulk:new boolean[]{false,true})exchange(id,tested,server,protocol,bulk);if(id.equals("network.tls_stream_abrupt")&&protocol.equals("TLSv1.2"))truncatedHttp(tested,server,protocol);}
        }
        return "TLS single/bulk reads preserve bytes, bounds and "+id.substring(id.lastIndexOf('_')+1)+" closure across "+protocols+"; "+clients.size()+" providers"+(id.equals("network.tls_stream_abrupt")?"; truncated fixed-length HTTPS rejected":"");
    }
    static void truncatedHttp(SSLContext client,SSLContext server,String protocol)throws Exception{
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            listener.setSoTimeout(2000);
            FutureTask<Void> peer=new FutureTask<Void>(()->{
                try(Socket raw=listener.accept()){
                    SSLSocket secure=(SSLSocket)server.getSocketFactory().createSocket(raw,"localhost",listener.getLocalPort(),false);
                    try{
                        secure.setUseClientMode(false);secure.setEnabledProtocols(new String[]{protocol});secure.setSoTimeout(2000);secure.startHandshake();
                        NetworkChecks.require(NetworkChecks.headers(secure).startsWith("GET /truncated HTTP/1."),"HTTPS truncation request");
                        NetworkChecks.response(secure,"HTTP/1.1 200 OK\r\nContent-Length: 8\r\nConnection: close\r\n\r\nabc");raw.close();
                    }finally{try{secure.close();}catch(IOException expected){}}
                }
                return null;
            });Thread worker=new Thread(peer,"probe-https-truncated");worker.setDaemon(true);worker.start();
            HttpsURLConnection connection=(HttpsURLConnection)new URL("https://localhost:"+listener.getLocalPort()+"/truncated").openConnection(Proxy.NO_PROXY);
            connection.setSSLSocketFactory(client.getSocketFactory());connection.setConnectTimeout(1500);connection.setReadTimeout(1500);
            try{
                NetworkChecks.require(connection.getResponseCode()==200,"truncated HTTPS status before body failure");
                int total=0;boolean rejected=false;
                try(InputStream input=connection.getInputStream()){
                    byte[] buffer=new byte[16];
                    for(int attempt=0;attempt<16;attempt++){
                        int count=input.read(buffer);NetworkChecks.require(count>0,"truncated HTTPS silently returned EOF/zero");total+=count;
                        NetworkChecks.require(total<=3,"truncated HTTPS invented response bytes");
                    }
                }catch(SocketTimeoutException unexpected){throw unexpected;}catch(IOException expected){rejected=true;}
                NetworkChecks.require(rejected,"truncated HTTPS response did not fail within read bound");peer.get(3000,TimeUnit.MILLISECONDS);
            }finally{connection.disconnect();listener.close();worker.join(3000);NetworkChecks.require(!worker.isAlive(),"truncated HTTPS peer did not terminate");}
        }
    }
    static void argumentsBeforeHandshake(SSLContext client)throws Exception{
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));Socket raw=new Socket()){
            listener.setSoTimeout(1500);raw.connect(new InetSocketAddress("127.0.0.1",listener.getLocalPort()),1500);
            try(Socket accepted=listener.accept();SSLSocket secure=(SSLSocket)client.getSocketFactory().createSocket(raw,"localhost",listener.getLocalPort(),false)){
                accepted.setSoTimeout(100);secure.setSoTimeout(100);
                InputStream input=secure.getInputStream();
                NetworkChecks.require(input.read(new byte[0])==0&&input.skip(0)==0&&input.skip(-1)==0,"TLS no-op reads before handshake");
                try{input.read(null,0,0);throw new AssertionError("TLS null buffer accepted");}catch(NullPointerException expected){}
                for(int[] range:new int[][]{{-1,0},{2,0},{0,-1},{1,1},{Integer.MAX_VALUE,1}})
                    try{input.read(new byte[1],range[0],range[1]);throw new AssertionError("TLS invalid range accepted");}catch(IndexOutOfBoundsException expected){}
                try{accepted.getInputStream().read();throw new AssertionError("TLS no-op/invalid reads sent handshake bytes");}catch(SocketTimeoutException expected){}
            }
        }
    }
    static void exchange(String id,SSLContext client,SSLContext server,String protocol,boolean bulk)throws Exception{
        final boolean abrupt=id.equals("network.tls_stream_abrupt"),timeout=id.equals("network.tls_stream_timeout");
        if(!abrupt&&!timeout&&!id.equals("network.tls_stream_clean"))throw new IllegalArgumentException("TLS stream case");
        final CountDownLatch release=new CountDownLatch(1);
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            listener.setSoTimeout(2000);
            FutureTask<Void> peer=new FutureTask<Void>(()->{
                try(Socket raw=listener.accept()){
                    SSLSocket secure=(SSLSocket)server.getSocketFactory().createSocket(raw,"localhost",listener.getLocalPort(),false);
                    try{
                        secure.setUseClientMode(false);secure.setEnabledProtocols(new String[]{protocol});secure.setSoTimeout(2000);secure.startHandshake();
                        secure.getOutputStream().write(new byte[]{0,42,(byte)255});secure.getOutputStream().flush();
                        NetworkChecks.require(release.await(2500,TimeUnit.MILLISECONDS),"TLS close signal deadline");
                        if(abrupt)raw.close();else secure.close();
                    }finally{try{secure.close();}catch(IOException expected){if(!abrupt)throw expected;}}
                }
                return null;
            });
            Thread worker=new Thread(peer,"probe-tls-stream");worker.setDaemon(true);worker.start();
            Socket raw=new Socket();raw.connect(new InetSocketAddress("127.0.0.1",listener.getLocalPort()),1500);
            try(SSLSocket socket=(SSLSocket)client.getSocketFactory().createSocket(raw,"localhost",listener.getLocalPort(),true)){
                socket.setEnabledProtocols(new String[]{protocol});socket.setSoTimeout(1500);
                SSLParameters params=socket.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");socket.setSSLParameters(params);
                InputStream input=socket.getInputStream();
                if(bulk){
                    byte[] bytes=new byte[7];Arrays.fill(bytes,(byte)99);int total=0;
                    while(total<3){int n=input.read(bytes,2+total,3-total);NetworkChecks.require(n>0&&n<=3-total,"TLS positive bulk read");total+=n;}
                    NetworkChecks.require(bytes[0]==99&&bytes[1]==99&&bytes[2]==0&&bytes[3]==42&&(bytes[4]&255)==255&&bytes[5]==99&&bytes[6]==99,"TLS bulk offsets preserve payload and sentinels");
                }else NetworkChecks.require(input.read()==0&&input.read()==42&&input.read()==255,"TLS unsigned single-byte payload");
                NetworkChecks.require(input.read(new byte[1],0,0)==0,"TLS zero-length read");
                try{input.read(new byte[1],-1,0);throw new AssertionError("TLS negative offset accepted");}catch(IndexOutOfBoundsException expected){}
                if(timeout){socket.setSoTimeout(100);try{input.read();throw new AssertionError("TLS read timeout missing");}catch(SocketTimeoutException expected){}socket.setSoTimeout(1500);}
                release.countDown();
                try{
                    int end=bulk?input.read(new byte[4],1,2):input.read();
                    NetworkChecks.require(end==-1,"TLS close invented bytes or returned zero: "+end+" "+protocol+" bulk="+bulk+" abrupt="+abrupt);
                    if(!abrupt)NetworkChecks.require(input.read()==-1,"TLS clean EOF remains stable");
                }catch(SocketTimeoutException unexpected){throw unexpected;}catch(IOException expected){if(!abrupt)throw expected;}
                peer.get(3000,TimeUnit.MILLISECONDS);
            }finally{release.countDown();raw.close();listener.close();worker.join(3000);NetworkChecks.require(!worker.isAlive(),"TLS peer did not finish");}
        }
    }
}
