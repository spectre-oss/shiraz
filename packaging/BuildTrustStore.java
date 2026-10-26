// SPDX-License-Identifier: GPL-3.0-only
import java.io.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
/** Build-only conversion of the pinned public Mozilla CA bundle, never host trust stores. */
public final class BuildTrustStore {
    public static void main(String[] args) throws Exception {
        KeyStore store = KeyStore.getInstance("JKS");
        store.load(null, null);
        int count = 0;
        try (InputStream in = new FileInputStream(args[0])) {
            for (java.security.cert.Certificate cert : CertificateFactory.getInstance("X.509").generateCertificates(in))
                store.setCertificateEntry("mozilla-" + count++, cert);
        }
        if (count < 50) throw new IllegalArgumentException("Incomplete public CA bundle");
        try (OutputStream out = new FileOutputStream(args[1])) { store.store(out, "changeit".toCharArray()); }
        System.out.println("Public CA certificates: " + count);
    }
}
