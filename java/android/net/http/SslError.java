// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

import java.security.cert.X509Certificate;
import java.util.Objects;

public class SslError {
    public static final int SSL_NOTYETVALID = 0;
    public static final int SSL_EXPIRED = 1;
    public static final int SSL_IDMISMATCH = 2;
    public static final int SSL_UNTRUSTED = 3;
    public static final int SSL_DATE_INVALID = 4;
    public static final int SSL_INVALID = 5;
    @Deprecated public static final int SSL_MAX_ERROR = 6;

    private int mErrors;
    private final SslCertificate mCertificate;
    private final String mUrl;

    @Deprecated
    public SslError(int error, SslCertificate certificate) {
        this(error, certificate, "");
    }

    @Deprecated
    public SslError(int error, X509Certificate certificate) {
        this(error, certificate, "");
    }

    public SslError(int error, SslCertificate certificate, String url) {
        this.mCertificate = Objects.requireNonNull(certificate, "certificate");
        this.mUrl = Objects.requireNonNull(url, "url");
        addError(error);
    }

    public SslError(int error, X509Certificate certificate, String url) {
        this(error, new SslCertificate(Objects.requireNonNull(certificate, "certificate")), url);
    }

    public SslCertificate getCertificate() {
        return mCertificate;
    }

    public String getUrl() {
        return mUrl;
    }

    public boolean addError(int error) {
        boolean valid = (0 <= error && error < SSL_MAX_ERROR);
        if (valid) {
            mErrors |= (1 << error);
        }
        return valid;
    }

    public boolean hasError(int error) {
        if (0 <= error && error < SSL_MAX_ERROR) {
            return (mErrors & (1 << error)) != 0;
        }
        return false;
    }

    public int getPrimaryError() {
        if (mErrors != 0) {
            for (int error = SSL_MAX_ERROR - 1; error >= 0; --error) {
                if ((mErrors & (1 << error)) != 0) {
                    return error;
                }
            }
        }
        return -1;
    }

    @Override
    public String toString() {
        return "primary error: "
                + getPrimaryError()
                + " certificate: "
                + getCertificate()
                + " on URL: "
                + getUrl();
    }
}
