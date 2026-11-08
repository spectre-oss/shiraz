// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import android.net.ConnectivityManager;
import android.net.Network;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Real IPv6 loopback data paths, independent of the host's external IPv6 route. */
final class Ipv6Checks {
    static InetAddress loopback()throws Exception{
        InetAddress address=InetAddress.getByName("::1");
        NetworkChecks.require(address instanceof Inet6Address&&address.isLoopbackAddress(),"numeric IPv6 loopback");return address;
    }
    static String run(String id,ConnectivityManager manager,TlsNetworkChecks.Assets assets)throws Exception{
        InetAddress address=loopback();Network network=manager.getActiveNetwork();NetworkChecks.require(network!=null,"default network");
        if(id.equals("network.ipv6_udp")){
            try(DatagramSocket server=new DatagramSocket(new InetSocketAddress(address,0));DatagramSocket client=new DatagramSocket(new InetSocketAddress(address,0))){
                server.setSoTimeout(1500);client.setSoTimeout(1500);byte[] sent={0,42,(byte)255};
                client.send(new DatagramPacket(sent,sent.length,address,server.getLocalPort()));
                byte[] received=new byte[8];java.util.Arrays.fill(received,(byte)99);DatagramPacket packet=new DatagramPacket(received,2,3);server.receive(packet);
                NetworkChecks.require(packet.getAddress() instanceof Inet6Address&&packet.getPort()==client.getLocalPort()&&packet.getLength()==3,"IPv6 datagram peer and length");
                NetworkChecks.require(received[0]==99&&received[1]==99&&received[2]==0&&received[3]==42&&(received[4]&255)==255&&received[5]==99,"IPv6 datagram offsets/payload");
                server.send(new DatagramPacket(sent,sent.length,packet.getAddress(),packet.getPort()));DatagramPacket reply=new DatagramPacket(received,received.length);client.receive(reply);
                NetworkChecks.require(reply.getAddress().equals(address)&&reply.getPort()==server.getLocalPort()&&reply.getLength()==3&&received[0]==0&&received[1]==42&&(received[2]&255)==255,"IPv6 datagram reply payload/peer");
            }
            return "IPv6 UDP preserves binary payload, offsets and peer identity";
        }
        if(id.equals("network.ipv6_tls"))return tls(assets);
        if(!id.equals("network.ipv6_tcp")&&!id.equals("network.ipv6_http"))throw new IllegalArgumentException("IPv6 case");
        final boolean http=id.equals("network.ipv6_http");
        try(ServerSocket server=new ServerSocket(0,1,address)){
            server.setSoTimeout(2000);FutureTask<Void> peer=new FutureTask<Void>(()->{
                try(Socket socket=server.accept()){
                    socket.setSoTimeout(1500);NetworkChecks.require(socket.getInetAddress() instanceof Inet6Address,"IPv6 accepted peer");
                    if(http){NetworkChecks.require(NetworkChecks.headers(socket).startsWith("GET /v6 HTTP/1."),"IPv6 HTTP request");NetworkChecks.response(socket,"HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nv6");}
                    else{InputStream in=socket.getInputStream();NetworkChecks.require(in.read()==123&&in.read()==-1,"IPv6 request and half-close");socket.getOutputStream().write(42);}
                }return null;
            });Thread worker=new Thread(peer,"probe-ipv6-peer");worker.setDaemon(true);worker.start();
            try{
                if(http){HttpURLConnection connection=(HttpURLConnection)network.openConnection(new URL("http://[::1]:"+server.getLocalPort()+"/v6"),Proxy.NO_PROXY);connection.setConnectTimeout(1500);connection.setReadTimeout(1500);try{NetworkChecks.require(connection.getResponseCode()==200&&"v6".equals(NetworkChecks.body(connection.getInputStream())),"IPv6 HTTP status/body");}finally{connection.disconnect();}}
                else try(Socket socket=network.getSocketFactory().createSocket()){
                    socket.connect(new InetSocketAddress(address,server.getLocalPort()),1500);socket.setSoTimeout(1500);socket.getOutputStream().write(123);socket.shutdownOutput();
                    NetworkChecks.require(socket.getInputStream().read()==42&&socket.getInputStream().read()==-1,"IPv6 TCP response/EOF");
                }
                peer.get(3000,TimeUnit.MILLISECONDS);
            }finally{server.close();worker.join(3000);NetworkChecks.require(!worker.isAlive(),"IPv6 peer deadline");}
        }
        return http?"Network URL API exchanges an HTTP response over IPv6":"Network SocketFactory exchanges IPv6 data, half-close and EOF";
    }
    static String tls(TlsNetworkChecks.Assets assets)throws Exception{
        X509Certificate cert;try(InputStream in=assets.open("loopback-cert.pem")){cert=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(in);}cert.checkValidity();
        PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(TlsNetworkChecks.bytes(assets.open("loopback-key.der"))));
        KeyStore keys=KeyStore.getInstance(KeyStore.getDefaultType());keys.load(null,null);keys.setKeyEntry("fixture",key,new char[0],new java.security.cert.Certificate[]{cert});
        KeyManagerFactory km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(keys,new char[0]);
        KeyStore trust=KeyStore.getInstance(KeyStore.getDefaultType());trust.load(null,null);trust.setCertificateEntry("fixture",cert);TrustManagerFactory tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(trust);
        SSLContext server=TlsNetworkChecks.context(km.getKeyManagers(),null),trusted=TlsNetworkChecks.context(null,tm.getTrustManagers()),untrusted=TlsNetworkChecks.context(null,null);
        int tested=0;
        for(String protocol:trusted.getSupportedSSLParameters().getProtocols())if(protocol.equals("TLSv1.2")||protocol.equals("TLSv1.3")){
            tested++;tlsPeer(server,trusted,"::1",protocol,false);tlsPeer(server,trusted,"0:0:0:0:0:0:0:1",protocol,false);
            tlsPeer(server,untrusted,"::1",protocol,true);tlsPeer(server,trusted,"::2",protocol,true);
        }
        NetworkChecks.require(tested>0,"at least one supported TLS protocol tested");
        tlsHttp(server,trusted,"::1");tlsHttp(server,trusted,"0:0:0:0:0:0:0:1");
        return "IPv6 TLS accepts both loopback spellings and rejects wrong IP/untrusted peers before application bytes";
    }
    static void tlsHttp(SSLContext serverContext,SSLContext client,String hostname)throws Exception{
        try(SSLServerSocket server=(SSLServerSocket)serverContext.getServerSocketFactory().createServerSocket(0,1,loopback())){
            server.setEnabledProtocols(new String[]{"TLSv1.2"});server.setSoTimeout(2000);
            FutureTask<Void> peer=new FutureTask<Void>(()->{
                try(SSLSocket socket=(SSLSocket)server.accept()){
                    socket.setSoTimeout(1500);NetworkChecks.require(NetworkChecks.headers(socket).startsWith("GET /v6 HTTP/1."),"IPv6 HTTPS request");
                    NetworkChecks.response(socket,"HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nv6");
                }return null;
            });Thread worker=new Thread(peer,"probe-ipv6-https");worker.setDaemon(true);worker.start();
            HttpsURLConnection connection=(HttpsURLConnection)new URL("https://["+hostname+"]:"+server.getLocalPort()+"/v6").openConnection(Proxy.NO_PROXY);
            connection.setSSLSocketFactory(client.getSocketFactory());connection.setConnectTimeout(1500);connection.setReadTimeout(1500);
            try{NetworkChecks.require(connection.getResponseCode()==200&&"v6".equals(NetworkChecks.body(connection.getInputStream())),"IPv6 HTTPS verified status/body");peer.get(3000,TimeUnit.MILLISECONDS);}
            finally{connection.disconnect();server.close();worker.join(3000);NetworkChecks.require(!worker.isAlive(),"IPv6 HTTPS peer deadline");}
        }
    }
    static void tlsPeer(SSLContext serverContext,SSLContext client,String hostname,String protocol,boolean reject)throws Exception{
        try(SSLServerSocket server=(SSLServerSocket)serverContext.getServerSocketFactory().createServerSocket(0,1,loopback())){
            server.setEnabledProtocols(new String[]{protocol});server.setSoTimeout(2000);java.util.concurrent.atomic.AtomicBoolean received=new java.util.concurrent.atomic.AtomicBoolean();
            FutureTask<Void> peer=new FutureTask<Void>(()->{
                try(SSLSocket socket=(SSLSocket)server.accept()){
                    socket.setSoTimeout(1500);
                    try{socket.startHandshake();int value=socket.getInputStream().read();if(value>=0){received.set(true);NetworkChecks.require(value==123,"IPv6 TLS request");socket.getOutputStream().write(42);}}
                    catch(IOException expected){if(!reject)throw expected;}
                }return null;
            });Thread worker=new Thread(peer,"probe-ipv6-tls");worker.setDaemon(true);worker.start();Socket plain=new Socket();
            try{
                plain.connect(new InetSocketAddress(loopback(),server.getLocalPort()),1500);
                try(SSLSocket socket=(SSLSocket)client.getSocketFactory().createSocket(plain,hostname,server.getLocalPort(),true)){
                    socket.setEnabledProtocols(new String[]{protocol});socket.setSoTimeout(1500);SSLParameters params=socket.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");socket.setSSLParameters(params);
                    boolean failed=false;try{socket.getOutputStream().write(123);NetworkChecks.require(socket.getInputStream().read()==42,"IPv6 TLS response");}catch(SSLException expected){if(!reject)throw expected;failed=true;}
                    NetworkChecks.require(failed==reject,"IPv6 TLS rejection result "+hostname+" "+protocol);
                }
                peer.get(3000,TimeUnit.MILLISECONDS);NetworkChecks.require(received.get()!=reject,"IPv6 TLS exposes no rejected application bytes");
            }finally{plain.close();server.close();worker.join(3000);NetworkChecks.require(!worker.isAlive(),"IPv6 TLS peer deadline");}
        }
    }
}
