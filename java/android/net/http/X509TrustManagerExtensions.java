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
    @SuppressWarnings("unchecked")
    public List<X509Certificate> checkServerTrusted(X509Certificate[] chain,String authType,String host)throws CertificateException{
        try{return (List<X509Certificate>)check.invoke(manager,chain,authType,host);}
        catch(InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof CertificateException)throw (CertificateException)cause;if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new CertificateException("Trust manager validation failed",cause);}
        catch(IllegalAccessException failure){throw new CertificateException("Trust manager validation inaccessible",failure);}
    }
}
