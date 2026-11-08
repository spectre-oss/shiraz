// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import android.net.http.X509TrustManagerExtensions;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.*;
import java.util.List;
import javax.net.ssl.*;

/** Android host-aware chain validation, including rejection with an empty trust store. */
final class TrustManagerChecks {
    static String run(TlsNetworkChecks.Assets assets)throws Exception{
        X509Certificate certificate;
        try(InputStream input=assets.open("loopback-cert.pem")){certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(input);}
        certificate.checkValidity();KeyStore store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);store.setCertificateEntry("fixture",certificate);
        TrustManagerFactory factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());factory.init(store);
        X509TrustManagerExtensions trusted=new X509TrustManagerExtensions((X509TrustManager)factory.getTrustManagers()[0]);
        List<X509Certificate> chain=trusted.checkServerTrusted(new X509Certificate[]{certificate},"RSA","localhost");
        NetworkChecks.require(!trusted.isUserAddedCertificate(certificate),"APK fixture is not a user-added CA");
        NetworkChecks.require(trusted.isSameTrustConfiguration("localhost","example.com"),"explicit store uniform trust");
        NetworkChecks.require(!chain.isEmpty()&&certificate.equals(chain.get(0)),"Android validated leaf chain");
        store.deleteEntry("fixture");factory.init(store);X509TrustManagerExtensions untrusted=new X509TrustManagerExtensions((X509TrustManager)factory.getTrustManagers()[0]);
        try{untrusted.checkServerTrusted(new X509Certificate[]{certificate},"RSA","localhost");throw new AssertionError("empty Android trust store accepted peer");}catch(CertificateException expected){}
        try{trusted.checkServerTrusted(new X509Certificate[0],"RSA","localhost");throw new AssertionError("empty peer chain accepted");}catch(CertificateException expected){}
        return "Android chain extension returns a verified leaf chain and rejects missing trust/peer chains";
    }
}
