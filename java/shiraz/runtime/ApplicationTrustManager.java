// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.security.GeneralSecurityException;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.security.cert.*;
import java.util.*;
import javax.net.ssl.*;

/** Default application trust with peer-scoped validation through the pinned synchronous callback. */
public final class ApplicationTrustManager implements X509TrustManager {
    private final X509TrustManager system;
    private static final ThreadLocal<Peer> CURRENT=new ThreadLocal<Peer>();
    public ApplicationTrustManager(X509TrustManager system){if(system==null)throw new NullPointerException("system trust");this.system=system;}
    /** Scope is restored even when the native handshake/callback throws. */
    public static final class Peer implements AutoCloseable {
        private final Peer previous=CURRENT.get();private final Thread owner=Thread.currentThread();
        private final String hostname;private CertificateException rejection;private boolean closed;
        private Peer(String host){hostname=host;CURRENT.set(this);}
        public <T extends IOException> T explain(T error){if(rejection!=null)error.addSuppressed(rejection);return error;}
        public void close(){if(Thread.currentThread()!=owner||CURRENT.get()!=this&&!closed)throw new IllegalStateException("TLS peer scope ownership");if(closed)return;closed=true;if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
    public static Peer peer(String hostname){return new Peer(hostname);}
    public void checkClientTrusted(X509Certificate[] chain,String authType)throws CertificateException{system.checkClientTrusted(chain,authType);}
    public void checkServerTrusted(X509Certificate[] chain,String authType)throws CertificateException{
        Peer scope=CURRENT.get();if(scope!=null)scope.rejection=null;
        try{checkServerTrusted(chain,authType,scope==null?null:scope.hostname);}
        catch(CertificateException rejected){if(scope!=null)scope.rejection=rejected;throw rejected;}
    }
    public List<X509Certificate> checkServerTrusted(X509Certificate[] chain,String authType,String hostname)throws CertificateException{
        NetworkSecurityConfig config=HostNetworkPolicy.configuration();
        return config==null?validatedChain(system,chain,authType,hostname):config.checkServerTrusted(chain,authType,hostname,system);
    }
    public boolean isSameTrustConfiguration(String first,String second){NetworkSecurityConfig config=HostNetworkPolicy.configuration();return config==null||config.sameTrustConfiguration(first,second);}
    public X509Certificate[] getAcceptedIssuers(){NetworkSecurityConfig config=HostNetworkPolicy.configuration();return config==null?system.getAcceptedIssuers():config.acceptedIssuers(system);}
    static List<X509Certificate> validatedChain(X509TrustManager manager,X509Certificate[] chain,String authType,String hostname)throws CertificateException{
        return validatedChain(manager.getAcceptedIssuers(),chain,authType);
    }
    static List<X509Certificate> validatedChain(X509Certificate[] roots,X509Certificate[] chain,String authType)throws CertificateException{
        if(chain==null||chain.length==0||chain.length>64||authType==null||authType.isEmpty())throw new CertificateException("Invalid peer certificate chain");
        for(X509Certificate certificate:chain)if(certificate==null)throw new CertificateException("Null peer certificate");
        try{
            // The JNI certificate facade omits some X509 extension accessors.
            // Reparse DER with the same bundled provider used for PKIX so EKU,
            // key usage and all critical extensions have complete semantics.
            CertificateFactory certificates=CertificateFactory.getInstance("X.509","BC");
            chain=chain.clone();int encodedBytes=0;
            for(int i=0;i<chain.length;i++){byte[] encoded=chain[i].getEncoded();if(encoded==null||(encodedBytes+=encoded.length)>4*1024*1024)throw new CertificateException("Peer certificate size limit");chain[i]=(X509Certificate)certificates.generateCertificate(new ByteArrayInputStream(encoded));}
            chain[0].checkValidity();
            List<String> usage=chain[0].getExtendedKeyUsage();
            if(usage!=null&&!usage.contains("1.3.6.1.5.5.7.3.1")&&!usage.contains("2.5.29.37.0"))throw new CertificateException("Certificate does not permit TLS server authentication");
            boolean[] keys=chain[0].getKeyUsage();
            if(keys!=null&&!(keys.length>0&&keys[0])&&!(keys.length>2&&keys[2])&&!(keys.length>4&&keys[4]))throw new CertificateException("Certificate key usage does not permit TLS");
            // Android permits an app to trust the exact endpoint certificate,
            // including a non-CA certificate. PKIX builders otherwise search
            // for its issuer instead of treating that endpoint as an anchor.
            for(X509Certificate root:roots)if(root.equals(chain[0]))return Collections.singletonList(chain[0]);
            Set<TrustAnchor> anchors=new HashSet<TrustAnchor>();for(X509Certificate root:roots)anchors.add(new TrustAnchor(root,null));
            if(anchors.isEmpty())throw new CertificateException("No certificate trust anchors");
            X509CertSelector target=new X509CertSelector();target.setCertificate(chain[0]);
            PKIXBuilderParameters parameters=new PKIXBuilderParameters(anchors,target);
            // Android's app policy does not enable network CRL/OCSP lookups.
            parameters.setRevocationEnabled(false);parameters.setMaxPathLength(-1);
            parameters.addCertStore(CertStore.getInstance("Collection",new CollectionCertStoreParameters(Arrays.asList(chain))));
            PKIXCertPathBuilderResult result=(PKIXCertPathBuilderResult)CertPathBuilder.getInstance("PKIX","BC").build(parameters);
            List<X509Certificate> validated=new ArrayList<X509Certificate>();
            for(java.security.cert.Certificate certificate:result.getCertPath().getCertificates())validated.add((X509Certificate)certificate);
            X509Certificate root=result.getTrustAnchor().getTrustedCert();
            if(root==null)throw new CertificateException("PKIX path has no certificate anchor");
            validated.add(root);
            return Collections.unmodifiableList(validated);
        }catch(GeneralSecurityException error){throw new CertificateException("Application certificate path validation failed",error);}
    }
}
