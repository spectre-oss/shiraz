// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;

import android.os.Parcel;
import android.os.Parcelable;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

public class WifiEnterpriseConfig implements Parcelable {
    public static final String EXTRA_WAPI_AS_CERTIFICATE_NAME =
            "android.net.wifi.extra.WAPI_AS_CERTIFICATE_NAME";
    public static final String EXTRA_WAPI_AS_CERTIFICATE_DATA =
            "android.net.wifi.extra.WAPI_AS_CERTIFICATE_DATA";
    public static final String EXTRA_WAPI_USER_CERTIFICATE_NAME =
            "android.net.wifi.extra.WAPI_USER_CERTIFICATE_NAME";
    public static final String EXTRA_WAPI_USER_CERTIFICATE_DATA =
            "android.net.wifi.extra.WAPI_USER_CERTIFICATE_DATA";
    public static final String WAPI_AS_CERTIFICATE = "WAPIAS_";
    public static final String WAPI_USER_CERTIFICATE = "WAPIUSR_";

    public static final int TLS_V1_0 = 0;
    public static final int TLS_V1_1 = 1;
    public static final int TLS_V1_2 = 2;
    public static final int TLS_V1_3 = 3;

    public static final Creator<WifiEnterpriseConfig> CREATOR =
            new Creator<WifiEnterpriseConfig>() {
                @Override
                public WifiEnterpriseConfig createFromParcel(Parcel in) {
                    WifiEnterpriseConfig ec = new WifiEnterpriseConfig();
                    int count = in.readInt();
                    for (int i = 0; i < count; i++) {
                        String k = in.readString();
                        String v = in.readString();
                        ec.mFields.put(k, v);
                    }
                    ec.mEapMethod = in.readInt();
                    ec.mPhase2Method = in.readInt();
                    ec.mClientKeyPairAlias = in.readString();
                    ec.mIsTrustOnFirstUseEnabled = in.readInt() != 0;
                    ec.mMinimumTlsVersion = in.readInt();
                    return ec;
                }

                @Override
                public WifiEnterpriseConfig[] newArray(int size) {
                    return new WifiEnterpriseConfig[size];
                }
            };

    private final Map<String, String> mFields = new LinkedHashMap<String, String>();
    private int mEapMethod = Eap.NONE;
    private int mPhase2Method = Phase2.NONE;
    private X509Certificate[] mCaCerts;
    private PrivateKey mClientPrivateKey;
    private X509Certificate[] mClientCertificateChain;
    private String mClientKeyPairAlias;
    private boolean mIsTrustOnFirstUseEnabled;
    private int mMinimumTlsVersion = TLS_V1_0;

    public WifiEnterpriseConfig() {}

    public WifiEnterpriseConfig(WifiEnterpriseConfig source) {
        if (source != null) {
            this.mFields.putAll(source.mFields);
            this.mEapMethod = source.mEapMethod;
            this.mPhase2Method = source.mPhase2Method;
            this.mCaCerts = source.mCaCerts == null ? null : source.mCaCerts.clone();
            this.mClientPrivateKey = source.mClientPrivateKey;
            this.mClientCertificateChain =
                    source.mClientCertificateChain == null
                            ? null
                            : source.mClientCertificateChain.clone();
            this.mClientKeyPairAlias = source.mClientKeyPairAlias;
            this.mIsTrustOnFirstUseEnabled = source.mIsTrustOnFirstUseEnabled;
            this.mMinimumTlsVersion = source.mMinimumTlsVersion;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mFields.size());
        for (Map.Entry<String, String> e : mFields.entrySet()) {
            dest.writeString(e.getKey());
            dest.writeString(e.getValue());
        }
        dest.writeInt(mEapMethod);
        dest.writeInt(mPhase2Method);
        dest.writeString(mClientKeyPairAlias);
        dest.writeInt(mIsTrustOnFirstUseEnabled ? 1 : 0);
        dest.writeInt(mMinimumTlsVersion);
    }

    public void setEapMethod(int eapMethod) {
        if (eapMethod < Eap.PEAP || eapMethod > Eap.WAPI_CERT) {
            throw new IllegalArgumentException("Unknown EAP method: " + eapMethod);
        }
        this.mEapMethod = eapMethod;
    }

    public int getEapMethod() {
        return mEapMethod;
    }

    public void setPhase2Method(int phase2Method) {
        if (phase2Method < Phase2.NONE || phase2Method > Phase2.AKA_PRIME) {
            throw new IllegalArgumentException("Unknown Phase 2 method: " + phase2Method);
        }
        this.mPhase2Method = phase2Method;
    }

    public int getPhase2Method() {
        return mPhase2Method;
    }

    public void setIdentity(String identity) {
        putField("identity", identity);
    }

    public String getIdentity() {
        return getField("identity");
    }

    public void setAnonymousIdentity(String anonymousIdentity) {
        putField("anonymous_identity", anonymousIdentity);
    }

    public String getAnonymousIdentity() {
        return getField("anonymous_identity");
    }

    public void setPassword(String password) {
        putField("password", password);
    }

    public String getPassword() {
        return getField("password");
    }

    public boolean hasCaCertificate() {
        return mCaCerts != null && mCaCerts.length > 0;
    }

    public void setCaCertificate(X509Certificate cert) {
        this.mCaCerts = cert == null ? null : new X509Certificate[] {cert};
    }

    public X509Certificate getCaCertificate() {
        if (mCaCerts != null) {
            if (mCaCerts.length > 1) {
                throw new IllegalStateException("More than one CA certificate configured");
            }
            return mCaCerts.length == 1 ? mCaCerts[0] : null;
        }
        return null;
    }

    public void setCaCertificates(X509Certificate[] certs) {
        if (certs != null) {
            for (int i = 0; i < certs.length; i++) {
                if (certs[i] == null) {
                    throw new IllegalArgumentException("Null certificate in array");
                }
            }
            this.mCaCerts = certs.clone();
        } else {
            this.mCaCerts = null;
        }
    }

    public X509Certificate[] getCaCertificates() {
        return mCaCerts == null ? null : mCaCerts.clone();
    }

    public void setClientKeyEntry(PrivateKey privateKey, X509Certificate clientCertificate) {
        setClientKeyEntryWithCertificateChain(
                privateKey,
                clientCertificate == null ? null : new X509Certificate[] {clientCertificate});
    }

    public void setClientKeyEntryWithCertificateChain(
            PrivateKey privateKey, X509Certificate[] clientCertificateChain) {
        this.mClientPrivateKey = privateKey;
        this.mClientCertificateChain =
                clientCertificateChain == null ? null : clientCertificateChain.clone();
    }

    public void setClientKeyPairAlias(String alias) {
        this.mClientKeyPairAlias = alias;
    }

    public String getClientKeyPairAlias() {
        return mClientKeyPairAlias;
    }

    public X509Certificate getClientCertificate() {
        return (mClientCertificateChain != null && mClientCertificateChain.length > 0)
                ? mClientCertificateChain[0]
                : null;
    }

    public X509Certificate[] getClientCertificateChain() {
        return mClientCertificateChain == null ? null : mClientCertificateChain.clone();
    }

    public PrivateKey getClientPrivateKey() {
        return mClientPrivateKey;
    }

    @Deprecated
    public void setSubjectMatch(String subjectMatch) {
        putField("subject_match", subjectMatch);
    }

    @Deprecated
    public String getSubjectMatch() {
        return getField("subject_match");
    }

    public void setAltSubjectMatch(String altSubjectMatch) {
        putField("altsubject_match", altSubjectMatch);
    }

    public String getAltSubjectMatch() {
        return getField("altsubject_match");
    }

    public void setDomainSuffixMatch(String domain) {
        putField("domain_suffix_match", domain);
    }

    public String getDomainSuffixMatch() {
        return getField("domain_suffix_match");
    }

    public void setRealm(String realm) {
        putField("realm", realm);
    }

    public String getRealm() {
        return getField("realm");
    }

    public void setPlmn(String plmn) {
        putField("plmn", plmn);
    }

    public String getPlmn() {
        return getField("plmn");
    }

    @Override
    public String toString() {
        return "WifiEnterpriseConfig{eap="
                + mEapMethod
                + ", phase2="
                + mPhase2Method
                + ", identity="
                + getIdentity()
                + "}";
    }

    public boolean isAuthenticationSimBased() {
        if (mEapMethod == Eap.SIM || mEapMethod == Eap.AKA || mEapMethod == Eap.AKA_PRIME) {
            return true;
        }
        if (mEapMethod == Eap.PEAP) {
            return mPhase2Method == Phase2.SIM
                    || mPhase2Method == Phase2.AKA
                    || mPhase2Method == Phase2.AKA_PRIME;
        }
        return false;
    }

    public boolean isEapMethodServerCertUsed() {
        return mEapMethod == Eap.PEAP
                || mEapMethod == Eap.TLS
                || mEapMethod == Eap.TTLS
                || mEapMethod == Eap.UNAUTH_TLS;
    }

    public boolean isServerCertValidationEnabled() {
        if (!isEapMethodServerCertUsed()) {
            throw new IllegalStateException("Configuration doesn't use server certificates");
        }
        return hasCaCertificate()
                || !getDomainSuffixMatch().isEmpty()
                || !getAltSubjectMatch().isEmpty()
                || !getSubjectMatch().isEmpty();
    }

    public void setDecoratedIdentityPrefix(String decoratedIdentityPrefix) {
        putField("decorated_username_prefix", decoratedIdentityPrefix);
    }

    public String getDecoratedIdentityPrefix() {
        String v = mFields.get("decorated_username_prefix");
        return v;
    }

    public void enableTrustOnFirstUse(boolean enable) {
        this.mIsTrustOnFirstUseEnabled = enable;
    }

    public boolean isTrustOnFirstUseEnabled() {
        return mIsTrustOnFirstUseEnabled;
    }

    public void setMinimumTlsVersion(int tlsVersion) throws IllegalArgumentException {
        if (tlsVersion < TLS_V1_0 || tlsVersion > TLS_V1_3) {
            throw new IllegalArgumentException("Invalid TLS version: " + tlsVersion);
        }
        this.mMinimumTlsVersion = tlsVersion;
    }

    public int getMinimumTlsVersion() {
        return mMinimumTlsVersion;
    }

    private void putField(String key, String value) {
        if (value == null) {
            mFields.remove(key);
        } else {
            mFields.put(key, value);
        }
    }

    private String getField(String key) {
        String v = mFields.get(key);
        return v == null ? "" : v;
    }

    public static final class Eap {
        public static final int NONE = -1;
        public static final int PEAP = 0;
        public static final int TLS = 1;
        public static final int TTLS = 2;
        public static final int PWD = 3;
        public static final int SIM = 4;
        public static final int AKA = 5;
        public static final int AKA_PRIME = 6;
        public static final int UNAUTH_TLS = 7;
        public static final int WAPI_CERT = 8;

        private Eap() {}
    }

    public static final class Phase2 {
        public static final int NONE = 0;
        public static final int PAP = 1;
        public static final int MSCHAP = 2;
        public static final int MSCHAPV2 = 3;
        public static final int GTC = 4;
        public static final int SIM = 5;
        public static final int AKA = 6;
        public static final int AKA_PRIME = 7;

        private Phase2() {}
    }
}
