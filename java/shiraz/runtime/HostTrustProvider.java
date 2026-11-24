// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.security.*;
import java.security.cert.*;
import java.util.Enumeration;
import javax.net.ssl.*;

/**
 * Default host OS trust anchors (macOS Security.framework, Linux system CA bundles).
 * Caller-supplied stores retain wolfJSSE behavior.
 */
public final class HostTrustProvider extends Provider {
    private HostTrustProvider() {
        super("ShirazTrust", 1.0, "Host OS public anchors with application PKIX trust");
        put("TrustManagerFactory.PKIX", Factory.class.getName());
        put("Alg.Alias.TrustManagerFactory.X509", "PKIX");
        put("Alg.Alias.TrustManagerFactory.SunX509", "PKIX");
    }
    public static synchronized void install() {
        if (Security.getProvider("ShirazTrust") != null) return;
        System.loadLibrary("shiraz-trust");
        Security.insertProviderAt(new HostTrustProvider(), 1);
    }
    public static synchronized boolean installIfAvailable() {
        if (Security.getProvider("ShirazTrust") != null) return true;
        if ("1".equals(System.getenv("SHIRAZ_DISABLE_HOST_TRUST"))) return false;
        try {
            System.loadLibrary("shiraz-trust");
            Security.insertProviderAt(new HostTrustProvider(), 1);
            return true;
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        }
    }
    private static native byte[][] anchors() throws CertificateException;
    private static void mergeFallbackStore(KeyStore target, String path) {
        if (path == null || path.isEmpty()) return;
        String type = System.getProperty("javax.net.ssl.trustStoreType", KeyStore.getDefaultType());
        String password = System.getProperty("javax.net.ssl.trustStorePassword");
        try (FileInputStream stream = new FileInputStream(path)) {
            KeyStore fallback = KeyStore.getInstance(type);
            fallback.load(stream, password != null ? password.toCharArray() : null);
            Enumeration<String> aliases = fallback.aliases();
            int index = 0;
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (fallback.isCertificateEntry(alias)) {
                    java.security.cert.Certificate cert = fallback.getCertificate(alias);
                    if (cert != null) {
                        target.setCertificateEntry("bundled-" + (index++), cert);
                    }
                }
            }
        } catch (Exception ignored) {
            // Host anchors remain usable even if the optional bundled fallback is absent.
        }
    }
    public static final class Factory extends TrustManagerFactorySpi {
        private TrustManagerFactory delegate;
        private boolean applicationDefault;
        protected void engineInit(KeyStore store) throws KeyStoreException {
            try {
                // Preserve explicit javax.net.ssl.trustStore settings, including
                // the backend's error for an unreadable or invalid store. Packaged
                // bundles mark their default cacerts.jks with shiraz.hostTrustStoreFallback=true
                // so live host system CAs are combined with the bundled Mozilla roots.
                String trustStoreProp = System.getProperty("javax.net.ssl.trustStore");
                boolean bundledFallback = "true".equals(System.getProperty("shiraz.hostTrustStoreFallback"));
                boolean useApplicationDefault = store == null && (trustStoreProp == null || bundledFallback);
                if (useApplicationDefault) {
                    byte[][] certificates = anchors();
                    if (certificates != null && certificates.length > 0) {
                        store = KeyStore.getInstance("BKS");
                        store.load(null, null);
                        CertificateFactory factory = CertificateFactory.getInstance("X.509");
                        for (int i = 0; i < certificates.length; i++)
                            store.setCertificateEntry("system-" + i,
                                factory.generateCertificate(new ByteArrayInputStream(certificates[i])));
                        if (bundledFallback && trustStoreProp != null) {
                            mergeFallbackStore(store, trustStoreProp);
                        }
                    } else if (HostOs.darwin()) {
                        throw new KeyStoreException("macOS returned no system trust anchors");
                    }
                    // On Linux without a host /etc/ssl CA bundle (e.g. minimal sandbox),
                    // leave store=null so wolfJSSE falls back to javax.net.ssl.trustStore.
                }
                TrustManagerFactory next = TrustManagerFactory.getInstance("PKIX", "wolfJSSE");
                next.init(store);
                delegate = next;
                applicationDefault=useApplicationDefault;
            } catch (Exception error) {
                throw new KeyStoreException("Cannot initialize host certificate trust", error);
            }
        }
        protected void engineInit(ManagerFactoryParameters parameters)
                throws InvalidAlgorithmParameterException {
            throw new InvalidAlgorithmParameterException("Use a KeyStore for certificate trust");
        }
        protected TrustManager[] engineGetTrustManagers() {
            if (delegate == null) throw new IllegalStateException("Trust manager is not initialized");
            TrustManager[] managers=delegate.getTrustManagers();
            if(applicationDefault)for(int i=0;i<managers.length;i++)if(managers[i] instanceof X509TrustManager)managers[i]=new ApplicationTrustManager((X509TrustManager)managers[i]);
            return managers;
        }
    }
}
