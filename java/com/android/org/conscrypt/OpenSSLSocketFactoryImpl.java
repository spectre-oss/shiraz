// SPDX-License-Identifier: GPL-3.0-only
package com.android.org.conscrypt;
import java.io.IOException;
import java.net.*;
import javax.net.ssl.*;
/** Functional Android socket-factory API bridge; cryptography remains in wolfSSL. */
public final class OpenSSLSocketFactoryImpl extends SSLSocketFactory {
    private final SSLSocketFactory delegate;
    final SSLParametersImpl sslParameters;
    public OpenSSLSocketFactoryImpl(SSLSocketFactory delegate,X509TrustManager trust){this.delegate=delegate;sslParameters=new SSLParametersImpl(trust);}
    private Socket wrap(Socket socket,String host){return new OpenSSLSocketImpl((SSLSocket)socket,host);}
    public String[] getDefaultCipherSuites(){return delegate.getDefaultCipherSuites();}
    public String[] getSupportedCipherSuites(){return delegate.getSupportedCipherSuites();}
    public Socket createSocket()throws IOException{return wrap(delegate.createSocket(),null);}
    public Socket createSocket(Socket socket,String host,int port,boolean close)throws IOException{return wrap(delegate.createSocket(socket,host,port,close),host);}
    public Socket createSocket(String host,int port)throws IOException{return wrap(delegate.createSocket(host,port),host);}
    public Socket createSocket(String host,int port,InetAddress local,int localPort)throws IOException{return wrap(delegate.createSocket(host,port,local,localPort),host);}
    public Socket createSocket(InetAddress host,int port)throws IOException{return wrap(delegate.createSocket(host,port),host.getHostAddress());}
    public Socket createSocket(InetAddress host,int port,InetAddress local,int localPort)throws IOException{return wrap(delegate.createSocket(host,port,local,localPort),host.getHostAddress());}
}
