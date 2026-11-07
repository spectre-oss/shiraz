// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/** Default macOS trust anchors. Caller-supplied stores retain wolfJSSE behavior. */
public final class HostTrustProvider extends Provider {
    private HostTrustProvider() {
        super("ShirazTrust", 1.0, "macOS system certificate anchors with wolfJSSE validation");
        put("TrustManagerFactory.PKIX", Factory.class.getName());
        put("Alg.Alias.TrustManagerFactory.X509", "PKIX");
        put("Alg.Alias.TrustManagerFactory.SunX509", "PKIX");
    }
    public static synchronized void install() {
        if (Security.getProvider("ShirazTrust") != null) return;
        System.loadLibrary("shiraz-trust");
        Security.insertProviderAt(new HostTrustProvider(), 1);
    }
    private static native byte[][] anchors() throws CertificateException;
    public static final class Factory extends TrustManagerFactorySpi {
        private TrustManagerFactory delegate;
        protected void engineInit(KeyStore store) throws KeyStoreException {
            try {
                // Preserve explicit javax.net.ssl.trustStore settings, including
                // the backend's error for an unreadable or invalid store.
                if (store == null && System.getProperty("javax.net.ssl.trustStore") == null) {
                    byte[][] certificates = anchors();
                    if (certificates == null || certificates.length == 0)
                        throw new KeyStoreException("macOS returned no system trust anchors");
                    store = KeyStore.getInstance("BKS");
                    store.load(null, null);
                    CertificateFactory factory = CertificateFactory.getInstance("X.509");
                    for (int i = 0; i < certificates.length; i++)
                        store.setCertificateEntry("system-" + i,
                            factory.generateCertificate(new ByteArrayInputStream(certificates[i])));
                }
                TrustManagerFactory next = TrustManagerFactory.getInstance("PKIX", "wolfJSSE");
                next.init(store);
                delegate = next;
            } catch (Exception error) {
                throw new KeyStoreException("Cannot initialize macOS certificate trust", error);
            }
        }
        protected void engineInit(ManagerFactoryParameters parameters)
                throws InvalidAlgorithmParameterException {
            throw new InvalidAlgorithmParameterException("Use a KeyStore for certificate trust");
        }
        protected TrustManager[] engineGetTrustManagers() {
            if (delegate == null) throw new IllegalStateException("Trust manager is not initialized");
            return delegate.getTrustManagers();
        }
    }
}
