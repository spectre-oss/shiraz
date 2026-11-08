// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.security.*;
import javax.net.ssl.*;
import com.android.org.conscrypt.OpenSSLSocketFactoryImpl;

/** Android socket API compatibility backed by the pinned wolfJSSE provider, not Conscrypt crypto. */
public final class HostTlsProvider extends Provider {
    private HostTlsProvider(){super("ShirazTLS",1.0,"Shiraz Android socket API bridge backed by wolfSSL; session resumption disabled");
        put("SSLContext.TLS",TLS.class.getName());put("SSLContext.TLSv1.2",TLS12.class.getName());
        if(Security.getProvider("wolfJSSE").getService("SSLContext","TLSv1.3")!=null)put("SSLContext.TLSv1.3",TLS13.class.getName());
        put("SSLContext.Default",Default.class.getName());
    }
    private static boolean unavailableReported;
    public static synchronized void install()throws GeneralSecurityException{
        if(Security.getProvider("ShirazTLS")!=null)return;
        if(unavailable())return;
        if(Security.getProvider("wolfJSSE")==null)throw new NoSuchProviderException("Pinned wolfJSSE provider is unavailable");
        if(HostOs.darwin())HostTrustProvider.install();
        SSLContext oldContext=SSLContext.getDefault();
        SSLSocketFactory oldSocket=(SSLSocketFactory)SSLSocketFactory.getDefault();
        SSLSocketFactory oldHttps=HttpsURLConnection.getDefaultSSLSocketFactory();
        String oldFactory=Security.getProperty("ssl.SocketFactory.provider");
        HostTlsProvider provider=new HostTlsProvider();
        SSLContext replacement=SSLContext.getInstance("Default",provider);
        Security.insertProviderAt(provider,1);
        try{
            if(oldContext.getProvider().getName().equals("wolfJSSE")&&oldContext.getProtocol().equalsIgnoreCase("Default"))SSLContext.setDefault(replacement);
            if("com.wolfssl.provider.jsse.WolfSSLSocketFactory".equals(oldFactory))Security.setProperty("ssl.SocketFactory.provider","");
            if(oldHttps==oldSocket&&oldHttps.getClass().getName().equals("com.wolfssl.provider.jsse.WolfSSLSocketFactory"))HttpsURLConnection.setDefaultSSLSocketFactory(SSLContext.getDefault().getSocketFactory());
        }catch(RuntimeException failure){
            SSLContext.setDefault(oldContext);HttpsURLConnection.setDefaultSSLSocketFactory(oldHttps);
            Security.setProperty("ssl.SocketFactory.provider",oldFactory==null?"":oldFactory);
            Security.removeProvider(provider.getName());throw failure;
        }
    }
    // An explicitly selected host without the pinned wolfJSSE stack boots without
    // TLS. Normal launchers require the backend. Nothing is installed in this mode:
    // SSLContext lookups throw NoSuchAlgorithmException and the default SSL socket factories
    // throw SocketException. There is no trust-all manager and no plaintext fallback.
    // Linux without wolfJSSE still fails in install(), as before.
    private static boolean unavailable(){
        String mode=System.getProperty("shiraz.tls");
        if(mode!=null&&!mode.equals("unavailable"))throw new IllegalArgumentException("Unsupported shiraz.tls mode: "+mode);
        boolean pinned=Security.getProvider("wolfJSSE")!=null;
        if(mode==null&&(pinned||!HostOs.darwin()))return false;
        if(pinned)throw new IllegalStateException("shiraz.tls=unavailable conflicts with the registered wolfJSSE provider");
        for(String service:new String[]{"SSLContext.Default","SSLContext.TLS"})
            if(Security.getProviders(service)!=null)throw new IllegalStateException("TLS is unavailable but a provider offers "+service);
        if("com.wolfssl.provider.jsse.WolfSSLSocketFactory".equals(Security.getProperty("ssl.SocketFactory.provider")))Security.setProperty("ssl.SocketFactory.provider","");
        if(!unavailableReported){unavailableReported=true;System.err.println("SHIRAZ_UNSUPPORTED tls: pinned wolfJSSE provider absent on this host; SSLContext and HTTPS use will throw");}
        return true;
    }
    public static class TLS extends Context {public TLS(){super("TLS");}}
    public static final class TLS12 extends Context {public TLS12(){super("TLSv1.2");}}
    public static final class TLS13 extends Context {public TLS13(){super("TLSv1.3");}}
    public static final class Default extends Context {public Default(){super("TLS");try{engineInit(null,null,null);}catch(KeyManagementException failure){throw new IllegalStateException("Default TLS initialization failed",failure);}}}
    public static SSLSessionContext shirazGuardSessionContext(SSLSessionContext context){return context==null?null:new NoResumptionContext(context);}
    private static final class NoResumptionContext implements SSLSessionContext {
        private final SSLSessionContext delegate;
        NoResumptionContext(SSLSessionContext delegate){this.delegate=delegate;}
        public SSLSession getSession(byte[] id){return delegate.getSession(id);}
        public java.util.Enumeration<byte[]> getIds(){return delegate.getIds();}
        public void setSessionTimeout(int seconds){if(seconds<0)throw new IllegalArgumentException("timeout");delegate.setSessionTimeout(seconds);}
        public int getSessionTimeout(){return delegate.getSessionTimeout();}
        public void setSessionCacheSize(int size){if(size<0)throw new IllegalArgumentException("cache size");throw new UnsupportedOperationException("TLS session resumption is disabled for this backend");}
        public int getSessionCacheSize(){throw new UnsupportedOperationException("TLS session resumption is disabled; no standard cache-size setting is exposed");}
    }
    public abstract static class Context extends SSLContextSpi {
        private final SSLContext delegate;
        private X509TrustManager trust;
        Context(String protocol){try{delegate=SSLContext.getInstance(protocol,"wolfJSSE");}catch(GeneralSecurityException failure){throw new IllegalStateException("Pinned TLS backend unavailable",failure);}}
        protected void engineInit(KeyManager[] keys,TrustManager[] managers,SecureRandom random)throws KeyManagementException{
            boolean defaults=managers==null;
            if(managers==null){try{TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init((KeyStore)null);managers=factory.getTrustManagers();}catch(GeneralSecurityException failure){throw new KeyManagementException("Default trust initialization failed",failure);}}
            if(defaults){managers=managers.clone();for(int i=0;i<managers.length;i++)if(managers[i] instanceof X509TrustManager&&!(managers[i] instanceof ApplicationTrustManager))managers[i]=new ApplicationTrustManager((X509TrustManager)managers[i]);}
            X509TrustManager next=null;for(TrustManager manager:managers)if(manager instanceof X509TrustManager){next=(X509TrustManager)manager;break;}
            delegate.init(keys,managers,random);
            // Pinned wolfJSSE SessionStore(0) evicts every entry. Its host+port-only
            // cache produces invalid TLS 1.3 binders after changing ALPN offers.
            // Until the backend supports safe resumption, every connection verifies afresh.
            delegate.getClientSessionContext().setSessionCacheSize(0);
            delegate.getServerSessionContext().setSessionCacheSize(0);
            trust=next;
        }
        protected SSLSocketFactory engineGetSocketFactory(){return new OpenSSLSocketFactoryImpl(delegate.getSocketFactory(),trust);}
        protected SSLServerSocketFactory engineGetServerSocketFactory(){return delegate.getServerSocketFactory();}
        protected SSLEngine engineCreateSSLEngine(){return new HostTlsEngine(delegate.createSSLEngine(),null,-1);}
        protected SSLEngine engineCreateSSLEngine(String host,int port){return new HostTlsEngine(delegate.createSSLEngine(host,port),host,port);}
        protected SSLSessionContext engineGetClientSessionContext(){return new NoResumptionContext(delegate.getClientSessionContext());}
        protected SSLSessionContext engineGetServerSessionContext(){return new NoResumptionContext(delegate.getServerSessionContext());}
        protected SSLParameters engineGetDefaultSSLParameters(){return delegate.getDefaultSSLParameters();}
        protected SSLParameters engineGetSupportedSSLParameters(){return delegate.getSupportedSSLParameters();}
    }
}
