// SPDX-License-Identifier: GPL-3.0-only
package android.security;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Objects;

public final class FileIntegrityManager {
    public FileIntegrityManager() {
    }

    public boolean isApkVeritySupported() {
        return false;
    }

    @Deprecated
    public boolean isAppSourceCertificateTrusted(X509Certificate certificate)
            throws CertificateEncodingException {
        Objects.requireNonNull(certificate, "Certificate must not be null");
        certificate.getEncoded();
        return false;
    }
}
