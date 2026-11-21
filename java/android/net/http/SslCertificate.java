// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

import android.os.Bundle;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SslCertificate {
    private static final String ISSUED_TO = "issued-to";
    private static final String ISSUED_BY = "issued-by";
    private static final String VALID_NOT_BEFORE = "valid-not-before";
    private static final String VALID_NOT_AFTER = "valid-not-after";
    private static final String X509_CERTIFICATE = "x509-certificate";
    private static final String ISO_8601_DATE_FORMAT = "yyyy-MM-dd HH:mm:ssZ";

    private final DName mIssuedTo;
    private final DName mIssuedBy;
    private final Date mValidNotBefore;
    private final Date mValidNotAfter;
    private final X509Certificate mX509Certificate;

    @Deprecated
    public SslCertificate(
            String issuedTo, String issuedBy, String validNotBefore, String validNotAfter) {
        this(issuedTo, issuedBy, parseDate(validNotBefore), parseDate(validNotAfter), null);
    }

    @Deprecated
    public SslCertificate(
            String issuedTo, String issuedBy, Date validNotBefore, Date validNotAfter) {
        this(issuedTo, issuedBy, validNotBefore, validNotAfter, null);
    }

    public SslCertificate(X509Certificate certificate) {
        this(
                certificate.getSubjectDN().getName(),
                certificate.getIssuerDN().getName(),
                certificate.getNotBefore(),
                certificate.getNotAfter(),
                certificate);
    }

    private SslCertificate(
            String issuedTo,
            String issuedBy,
            Date validNotBefore,
            Date validNotAfter,
            X509Certificate x509Certificate) {
        this.mIssuedTo = new DName(issuedTo);
        this.mIssuedBy = new DName(issuedBy);
        this.mValidNotBefore = cloneDate(validNotBefore);
        this.mValidNotAfter = cloneDate(validNotAfter);
        this.mX509Certificate = x509Certificate;
    }

    public static Bundle saveState(SslCertificate certificate) {
        if (certificate == null) {
            return null;
        }
        Bundle bundle = new Bundle();
        bundle.putString(ISSUED_TO, certificate.getIssuedTo().getDName());
        bundle.putString(ISSUED_BY, certificate.getIssuedBy().getDName());
        bundle.putString(VALID_NOT_BEFORE, certificate.getValidNotBefore());
        bundle.putString(VALID_NOT_AFTER, certificate.getValidNotAfter());
        X509Certificate x509Certificate = certificate.mX509Certificate;
        if (x509Certificate != null) {
            try {
                bundle.putByteArray(X509_CERTIFICATE, x509Certificate.getEncoded());
            } catch (CertificateEncodingException ignored) {
            }
        }
        return bundle;
    }

    public static SslCertificate restoreState(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        byte[] bytes = bundle.getByteArray(X509_CERTIFICATE);
        X509Certificate x509Certificate = null;
        if (bytes != null) {
            try {
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                x509Certificate =
                        (X509Certificate)
                                certFactory.generateCertificate(new ByteArrayInputStream(bytes));
            } catch (CertificateException ignored) {
            }
        }
        return new SslCertificate(
                bundle.getString(ISSUED_TO),
                bundle.getString(ISSUED_BY),
                parseDate(bundle.getString(VALID_NOT_BEFORE)),
                parseDate(bundle.getString(VALID_NOT_AFTER)),
                x509Certificate);
    }

    public Date getValidNotBeforeDate() {
        return cloneDate(mValidNotBefore);
    }

    @Deprecated
    public String getValidNotBefore() {
        return formatDate(mValidNotBefore);
    }

    public Date getValidNotAfterDate() {
        return cloneDate(mValidNotAfter);
    }

    @Deprecated
    public String getValidNotAfter() {
        return formatDate(mValidNotAfter);
    }

    public DName getIssuedTo() {
        return mIssuedTo;
    }

    public DName getIssuedBy() {
        return mIssuedBy;
    }

    public X509Certificate getX509Certificate() {
        return mX509Certificate;
    }

    @Override
    public String toString() {
        return "Issued to: "
                + mIssuedTo.getDName()
                + ";\nIssued by: "
                + mIssuedBy.getDName()
                + ";\n";
    }

    private static Date parseDate(String string) {
        if (string == null || string.isEmpty()) {
            return null;
        }
        try {
            return new SimpleDateFormat(ISO_8601_DATE_FORMAT, Locale.US).parse(string);
        } catch (ParseException ignored) {
        }
        String[] fallbacks = {
            "EEE MMM dd HH:mm:ss zzz yyyy",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd"
        };
        for (int i = 0; i < fallbacks.length; i++) {
            try {
                return new SimpleDateFormat(fallbacks[i], Locale.US).parse(string);
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    private static String formatDate(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat(ISO_8601_DATE_FORMAT, Locale.US).format(date);
    }

    private static Date cloneDate(Date date) {
        return date == null ? null : (Date) date.clone();
    }

    public class DName {
        private final String mDName;
        private String mCName;
        private String mOName;
        private String mUName;

        public DName(String dName) {
            this.mDName = dName;
            if (dName != null) {
                parseRfc2253(dName);
            }
        }

        public String getDName() {
            return mDName != null ? mDName : "";
        }

        public String getCName() {
            return mCName != null ? mCName : "";
        }

        public String getOName() {
            return mOName != null ? mOName : "";
        }

        public String getUName() {
            return mUName != null ? mUName : "";
        }

        private void parseRfc2253(String dn) {
            int n = dn.length();
            int i = 0;
            while (i < n) {
                while (i < n && (dn.charAt(i) == ' ' || dn.charAt(i) == ',' || dn.charAt(i) == ';' || dn.charAt(i) == '+')) {
                    i++;
                }
                if (i >= n) {
                    break;
                }
                int keyStart = i;
                while (i < n && dn.charAt(i) != '=' && dn.charAt(i) != ',' && dn.charAt(i) != ';') {
                    i++;
                }
                if (i >= n || dn.charAt(i) != '=') {
                    while (i < n && dn.charAt(i) != ',' && dn.charAt(i) != ';') {
                        i++;
                    }
                    continue;
                }
                String attr = dn.substring(keyStart, i).trim().toUpperCase(Locale.US);
                i++; // skip '='
                while (i < n && dn.charAt(i) == ' ') {
                    i++;
                }
                StringBuilder val = new StringBuilder();
                if (i < n && dn.charAt(i) == '"') {
                    i++;
                    while (i < n && dn.charAt(i) != '"') {
                        if (dn.charAt(i) == '\\' && i + 1 < n) {
                            i++;
                        }
                        val.append(dn.charAt(i));
                        i++;
                    }
                    if (i < n && dn.charAt(i) == '"') {
                        i++;
                    }
                } else {
                    while (i < n && dn.charAt(i) != ',' && dn.charAt(i) != ';' && dn.charAt(i) != '+') {
                        if (dn.charAt(i) == '\\' && i + 1 < n) {
                            i++;
                            val.append(dn.charAt(i));
                            i++;
                        } else {
                            val.append(dn.charAt(i));
                            i++;
                        }
                    }
                    // trim trailing unescaped spaces
                    int vLen = val.length();
                    while (vLen > 0 && val.charAt(vLen - 1) == ' ') {
                        vLen--;
                    }
                    val.setLength(vLen);
                }
                String parsedValue = val.toString();
                if (("CN".equals(attr) || "2.5.4.3".equals(attr)) && mCName == null) {
                    mCName = parsedValue;
                } else if (("O".equals(attr) || "2.5.4.10".equals(attr)) && mOName == null) {
                    mOName = parsedValue;
                } else if (("OU".equals(attr) || "2.5.4.11".equals(attr)) && mUName == null) {
                    mUName = parsedValue;
                }
            }
        }
    }
}
