// SPDX-License-Identifier: GPL-3.0-only
package com.android.org.conscrypt;
import java.io.*;
import java.net.*;
import java.nio.channels.SocketChannel;
import java.lang.reflect.*;
import java.util.*;
import javax.net.ssl.*;

/** Android reflective socket contract implemented over a real wolfSSL socket. */
public final class OpenSSLSocketImpl extends SSLSocket {
    private final SSLSocket delegate;
    private String peerHost,endpointAlgorithm;
    private boolean verified;
    private IOException handshakeFailure;
    private final SSLSession failedSession=new FailedSession();
    private final List<HandshakeCompletedListener> listeners=new ArrayList<HandshakeCompletedListener>();
    OpenSSLSocketImpl(SSLSocket delegate,String peerHost){this.delegate=delegate;this.peerHost=peerHost;}
    private Object extension(String name,Class<?> type,Object value){
        try{return type==null?delegate.getClass().getMethod(name).invoke(delegate):delegate.getClass().getMethod(name,type).invoke(delegate,value);}
        catch(InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new IllegalStateException("wolfSSL extension failed: "+name,cause);}
        catch(ReflectiveOperationException failure){throw new UnsupportedOperationException("wolfSSL extension unavailable: "+name,failure);}
    }
    private void configuring(){if(verified||handshakeFailure!=null||delegate.isClosed())throw new IllegalStateException("TLS socket configuration is closed");}
    public void setUseSessionTickets(boolean value){configuring();extension("setUseSessionTickets",Boolean.TYPE,value);}
    public void setHostname(String host){configuring();
        // wolfJSSE seeds SNI from the factory/connect peer host. It cannot safely change it later.
        if(host==null||!host.equalsIgnoreCase(peerHost))throw new UnsupportedOperationException("TLS SNI must match the socket peer host");
    }
    public void setAlpnProtocols(byte[] protocols){configuring();
        if(protocols==null)throw new NullPointerException("protocols");if(protocols.length>65535)throw new IllegalArgumentException("ALPN vector length");
        for(int i=0;i<protocols.length;){int count=protocols[i++]&255;if(count==0||count>protocols.length-i)throw new IllegalArgumentException("Malformed ALPN vector");i+=count;}
        extension("setAlpnProtocols",byte[].class,protocols.clone());
    }
    public byte[] getAlpnSelectedProtocol(){byte[] value=(byte[])extension("getAlpnSelectedProtocol",null,null);return value==null?null:value.clone();}
    public synchronized void startHandshake()throws IOException{
        if(handshakeFailure!=null)throw handshakeFailure;
        if(delegate.isClosed())throw new SocketException("Socket is closed");
        if(verified)return;
        try{
            delegate.startHandshake();
            if(endpointAlgorithm!=null&&!endpointAlgorithm.isEmpty()){
                HostnameVerifier verifier;
                try{verifier=(HostnameVerifier)Class.forName("com.android.okhttp.internal.tls.OkHostnameVerifier").getField("INSTANCE").get(null);}
                catch(ReflectiveOperationException failure){throw new SSLException("Pinned hostname verifier unavailable",failure);}
                if(peerHost==null||!verifier.verify(peerHost,delegate.getSession()))throw new SSLHandshakeException("TLS endpoint hostname mismatch");
            }
            verified=true;
        }catch(IOException failure){handshakeFailure=failure;try{delegate.close();}catch(IOException close){failure.addSuppressed(close);}throw failure;}
        HandshakeCompletedEvent event=new HandshakeCompletedEvent(this,wrapSession(delegate.getSession()));
        for(HandshakeCompletedListener listener:new ArrayList<HandshakeCompletedListener>(listeners))listener.handshakeCompleted(event);
    }
    public SSLSession getSession(){try{startHandshake();return wrapSession(delegate.getSession());}catch(IOException failure){return failedSession;}}
    private SSLSession rawSession,publicSession;
    private synchronized SSLSession wrapSession(SSLSession session){
        if(session==null)return null;
        if(rawSession!=session){rawSession=session;publicSession=new PublicSession(session);}
        return publicSession;
    }
    private static final class PublicSession implements SSLSession {
        private final SSLSession delegate;
        private final Map<String,Object> values=new HashMap<String,Object>();
        PublicSession(SSLSession delegate){this.delegate=delegate;}
        public byte[] getId(){return delegate.getId();}
        public SSLSessionContext getSessionContext(){return shiraz.runtime.HostTlsProvider.shirazGuardSessionContext(delegate.getSessionContext());}
        public long getCreationTime(){return delegate.getCreationTime();}public long getLastAccessedTime(){return delegate.getLastAccessedTime();}
        public void invalidate(){delegate.invalidate();}public boolean isValid(){return delegate.isValid();}
        public synchronized void putValue(String name,Object value){if(name==null||value==null)throw new IllegalArgumentException();Object old=values.put(name,value);if(old instanceof SSLSessionBindingListener)((SSLSessionBindingListener)old).valueUnbound(new SSLSessionBindingEvent(this,name));if(value instanceof SSLSessionBindingListener)((SSLSessionBindingListener)value).valueBound(new SSLSessionBindingEvent(this,name));}
        public synchronized Object getValue(String name){if(name==null)throw new IllegalArgumentException();return values.get(name);}
        public synchronized void removeValue(String name){if(name==null)throw new IllegalArgumentException();Object old=values.remove(name);if(old instanceof SSLSessionBindingListener)((SSLSessionBindingListener)old).valueUnbound(new SSLSessionBindingEvent(this,name));}
        public synchronized String[] getValueNames(){return values.keySet().toArray(new String[values.size()]);}
        public java.security.cert.Certificate[] getPeerCertificates()throws SSLPeerUnverifiedException{return delegate.getPeerCertificates();}
        public java.security.cert.Certificate[] getLocalCertificates(){return delegate.getLocalCertificates();}
        public javax.security.cert.X509Certificate[] getPeerCertificateChain()throws SSLPeerUnverifiedException{return delegate.getPeerCertificateChain();}
        public java.security.Principal getPeerPrincipal()throws SSLPeerUnverifiedException{return delegate.getPeerPrincipal();}
        public java.security.Principal getLocalPrincipal(){return delegate.getLocalPrincipal();}
        public String getCipherSuite(){return delegate.getCipherSuite();}public String getProtocol(){return delegate.getProtocol();}
        public String getPeerHost(){return delegate.getPeerHost();}public int getPeerPort(){return delegate.getPeerPort();}
        public int getPacketBufferSize(){return delegate.getPacketBufferSize();}public int getApplicationBufferSize(){return delegate.getApplicationBufferSize();}
    }
    private static final class FailedSession implements SSLSession {
        private final Map<String,Object> values=new HashMap<String,Object>();
        public byte[] getId(){return new byte[0];}public SSLSessionContext getSessionContext(){return null;}
        public long getCreationTime(){return 0;}public long getLastAccessedTime(){return 0;}
        public void invalidate(){}public boolean isValid(){return false;}
        public void putValue(String name,Object value){if(name==null||value==null)throw new IllegalArgumentException();Object old=values.put(name,value);if(old instanceof SSLSessionBindingListener)((SSLSessionBindingListener)old).valueUnbound(new SSLSessionBindingEvent(this,name));if(value instanceof SSLSessionBindingListener)((SSLSessionBindingListener)value).valueBound(new SSLSessionBindingEvent(this,name));}
        public Object getValue(String name){if(name==null)throw new IllegalArgumentException();return values.get(name);}
        public void removeValue(String name){if(name==null)throw new IllegalArgumentException();Object old=values.remove(name);if(old instanceof SSLSessionBindingListener)((SSLSessionBindingListener)old).valueUnbound(new SSLSessionBindingEvent(this,name));}
        public String[] getValueNames(){return values.keySet().toArray(new String[values.size()]);}
        public java.security.cert.Certificate[] getPeerCertificates()throws SSLPeerUnverifiedException{throw new SSLPeerUnverifiedException("No verified peer");}
        public java.security.cert.Certificate[] getLocalCertificates(){return null;}
        public javax.security.cert.X509Certificate[] getPeerCertificateChain()throws SSLPeerUnverifiedException{throw new SSLPeerUnverifiedException("No verified peer");}
        public java.security.Principal getPeerPrincipal()throws SSLPeerUnverifiedException{throw new SSLPeerUnverifiedException("No verified peer");}
        public java.security.Principal getLocalPrincipal(){return null;}
        public String getCipherSuite(){return "SSL_NULL_WITH_NULL_NULL";}public String getProtocol(){return "NONE";}
        public String getPeerHost(){return null;}public int getPeerPort(){return -1;}
        public int getPacketBufferSize(){return 18437;}public int getApplicationBufferSize(){return 16384;}
    }
    public SSLSession getHandshakeSession(){return wrapSession(delegate.getHandshakeSession());}
    public void addHandshakeCompletedListener(HandshakeCompletedListener value){if(value==null)throw new IllegalArgumentException("listener");if(listeners.size()>=1024)throw new IllegalStateException("Handshake listener limit");listeners.add(value);}
    public void removeHandshakeCompletedListener(HandshakeCompletedListener value){if(value==null||!listeners.remove(value))throw new IllegalArgumentException("listener not registered");}
    public InputStream getInputStream()throws IOException{
        final InputStream input=delegate.getInputStream();return new FilterInputStream(input){
            public int read()throws IOException{startHandshake();return in.read();}
            public int read(byte[] data,int offset,int count)throws IOException{startHandshake();return in.read(data,offset,count);}
            public long skip(long count)throws IOException{startHandshake();return in.skip(count);}
            public int available()throws IOException{startHandshake();return in.available();}
        };
    }
    public OutputStream getOutputStream()throws IOException{
        final OutputStream output=delegate.getOutputStream();return new FilterOutputStream(output){
            public void write(int value)throws IOException{startHandshake();out.write(value);}
            public void write(byte[] data,int offset,int count)throws IOException{startHandshake();out.write(data,offset,count);}
            public void flush()throws IOException{startHandshake();out.flush();}
        };
    }
    public SSLParameters getSSLParameters(){SSLParameters parameters=delegate.getSSLParameters();parameters.setEndpointIdentificationAlgorithm(endpointAlgorithm);return parameters;}
    public void setSSLParameters(SSLParameters parameters){configuring();
        String algorithm=parameters.getEndpointIdentificationAlgorithm();
        if(algorithm!=null&&!algorithm.isEmpty()&&!algorithm.equalsIgnoreCase("HTTPS"))throw new UnsupportedOperationException("Only HTTPS endpoint identification is implemented");
        if(verified)throw new IllegalStateException("Cannot change TLS policy after handshake");
        if(parameters.getServerNames()!=null||parameters.getSNIMatchers()!=null||parameters.getAlgorithmConstraints()!=null)throw new UnsupportedOperationException("Custom SNI/algorithm constraints are not implemented");
        delegate.setSSLParameters(parameters);endpointAlgorithm=algorithm;
    }
    public String[] getSupportedCipherSuites(){return delegate.getSupportedCipherSuites();}public String[] getEnabledCipherSuites(){return delegate.getEnabledCipherSuites();}public void setEnabledCipherSuites(String[] value){configuring();delegate.setEnabledCipherSuites(value);}
    public String[] getSupportedProtocols(){return delegate.getSupportedProtocols();}public String[] getEnabledProtocols(){return delegate.getEnabledProtocols();}public void setEnabledProtocols(String[] value){configuring();delegate.setEnabledProtocols(value);}
    public void setUseClientMode(boolean value){configuring();delegate.setUseClientMode(value);}public boolean getUseClientMode(){return delegate.getUseClientMode();}
    public void setNeedClientAuth(boolean value){configuring();delegate.setNeedClientAuth(value);}public boolean getNeedClientAuth(){return delegate.getNeedClientAuth();}
    public void setWantClientAuth(boolean value){configuring();delegate.setWantClientAuth(value);}public boolean getWantClientAuth(){return delegate.getWantClientAuth();}
    public void setEnableSessionCreation(boolean value){configuring();delegate.setEnableSessionCreation(value);}public boolean getEnableSessionCreation(){return delegate.getEnableSessionCreation();}
    public void connect(SocketAddress address)throws IOException{connect(address,0);}
    public void connect(SocketAddress address,int timeout)throws IOException{delegate.connect(address,timeout);if(address instanceof InetSocketAddress)peerHost=((InetSocketAddress)address).getHostString();}
    public void bind(SocketAddress address)throws IOException{delegate.bind(address);}
    public InetAddress getInetAddress(){return delegate.getInetAddress();}public InetAddress getLocalAddress(){return delegate.getLocalAddress();}public int getPort(){return delegate.getPort();}public int getLocalPort(){return delegate.getLocalPort();}
    public SocketAddress getRemoteSocketAddress(){return delegate.getRemoteSocketAddress();}public SocketAddress getLocalSocketAddress(){return delegate.getLocalSocketAddress();}public SocketChannel getChannel(){return delegate.getChannel();}
    public void setTcpNoDelay(boolean value)throws SocketException{delegate.setTcpNoDelay(value);}public boolean getTcpNoDelay()throws SocketException{return delegate.getTcpNoDelay();}
    public void setSoLinger(boolean on,int value)throws SocketException{delegate.setSoLinger(on,value);}public int getSoLinger()throws SocketException{return delegate.getSoLinger();}
    public void sendUrgentData(int value)throws IOException{throw new UnsupportedOperationException("TLS urgent data is unsupported");}
    public void setOOBInline(boolean value)throws SocketException{delegate.setOOBInline(value);}public boolean getOOBInline()throws SocketException{return delegate.getOOBInline();}
    public void setSoTimeout(int value)throws SocketException{delegate.setSoTimeout(value);}public int getSoTimeout()throws SocketException{return delegate.getSoTimeout();}
    public void setSendBufferSize(int value)throws SocketException{delegate.setSendBufferSize(value);}public int getSendBufferSize()throws SocketException{return delegate.getSendBufferSize();}
    public void setReceiveBufferSize(int value)throws SocketException{delegate.setReceiveBufferSize(value);}public int getReceiveBufferSize()throws SocketException{return delegate.getReceiveBufferSize();}
    public void setKeepAlive(boolean value)throws SocketException{delegate.setKeepAlive(value);}public boolean getKeepAlive()throws SocketException{return delegate.getKeepAlive();}
    public void setTrafficClass(int value)throws SocketException{delegate.setTrafficClass(value);}public int getTrafficClass()throws SocketException{return delegate.getTrafficClass();}
    public void setReuseAddress(boolean value)throws SocketException{delegate.setReuseAddress(value);}public boolean getReuseAddress()throws SocketException{return delegate.getReuseAddress();}
    public void close()throws IOException{delegate.close();}public void shutdownInput()throws IOException{delegate.shutdownInput();}public void shutdownOutput()throws IOException{delegate.shutdownOutput();}
    public boolean isConnected(){return delegate.isConnected();}public boolean isBound(){return delegate.isBound();}public boolean isClosed(){return delegate.isClosed();}public boolean isInputShutdown(){return delegate.isInputShutdown();}public boolean isOutputShutdown(){return delegate.isOutputShutdown();}
    public void setPerformancePreferences(int connection,int latency,int bandwidth){delegate.setPerformancePreferences(connection,latency,bandwidth);}
}
