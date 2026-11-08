// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;
import java.lang.reflect.*;
import java.security.cert.*;
import java.util.List;
import javax.net.ssl.X509TrustManager;
/** Delegates Android chain validation to the supplied trust manager's real host-aware API. */
public final class X509TrustManagerExtensions {
    private final X509TrustManager manager;
    private final Method check;
    public X509TrustManagerExtensions(X509TrustManager manager){
        if(manager==null)throw new IllegalArgumentException("trust manager");this.manager=manager;
        try{check=manager.getClass().getMethod("checkServerTrusted",X509Certificate[].class,String.class,String.class);}
        catch(NoSuchMethodException failure){throw new IllegalArgumentException("Trust manager has no Android chain validation API",failure);}
    }
    /** No Android user-CA store is configured in Shiraz. APK anchors are not user-added CAs. */
    public boolean isUserAddedCertificate(X509Certificate certificate){if(certificate==null)throw new NullPointerException("certificate");return false;}
    public boolean isSameTrustConfiguration(String hostname1,String hostname2){
        if(hostname1==null||hostname2==null)throw new NullPointerException("hostname");
        try{return (Boolean)manager.getClass().getMethod("isSameTrustConfiguration",String.class,String.class).invoke(manager,hostname1,hostname2);}
        catch(NoSuchMethodException absent){return true;} // A uniform non-Android trust manager.
        catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new IllegalStateException("Trust configuration lookup failed",cause);}
        catch(IllegalAccessException error){throw new IllegalStateException("Trust configuration API inaccessible",error);}
    }
    @SuppressWarnings("unchecked")
    public List<X509Certificate> checkServerTrusted(X509Certificate[] chain,String authType,String host)throws CertificateException{
        try{return (List<X509Certificate>)check.invoke(manager,chain,authType,host);}
        catch(InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof CertificateException)throw (CertificateException)cause;if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new CertificateException("Trust manager validation failed",cause);}
        catch(IllegalAccessException failure){throw new CertificateException("Trust manager validation inaccessible",failure);}
    }
}
