// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.hotspot2.pps;

import android.net.wifi.WifiEnterpriseConfig;
import android.os.Parcel;
import android.os.Parcelable;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Objects;

public final class Credential implements Parcelable {
    public static final Creator<Credential> CREATOR =
            new Creator<Credential>() {
                @Override
                public Credential createFromParcel(Parcel in) {
                    Credential c = new Credential();
                    c.mRealm = in.readString();
                    c.mUserCredential = in.readTypedObject(UserCredential.CREATOR);
                    c.mCertCredential = in.readTypedObject(CertificateCredential.CREATOR);
                    c.mSimCredential = in.readTypedObject(SimCredential.CREATOR);
                    c.mMinimumTlsVersion = in.readInt();
                    return c;
                }

                @Override
                public Credential[] newArray(int size) {
                    return new Credential[size];
                }
            };

    private String mRealm;
    private UserCredential mUserCredential;
    private CertificateCredential mCertCredential;
    private SimCredential mSimCredential;
    private X509Certificate mCaCertificate;
    private X509Certificate[] mClientCertificateChain;
    private PrivateKey mClientPrivateKey;
    private int mMinimumTlsVersion = WifiEnterpriseConfig.TLS_V1_0;

    public Credential() {}

    public Credential(Credential source) {
        if (source != null) {
            this.mRealm = source.mRealm;
            this.mUserCredential =
                    source.mUserCredential == null
                            ? null
                            : new UserCredential(source.mUserCredential);
            this.mCertCredential =
                    source.mCertCredential == null
                            ? null
                            : new CertificateCredential(source.mCertCredential);
            this.mSimCredential =
                    source.mSimCredential == null
                            ? null
                            : new SimCredential(source.mSimCredential);
            this.mCaCertificate = source.mCaCertificate;
            this.mClientCertificateChain =
                    source.mClientCertificateChain == null
                            ? null
                            : source.mClientCertificateChain.clone();
            this.mClientPrivateKey = source.mClientPrivateKey;
            this.mMinimumTlsVersion = source.mMinimumTlsVersion;
        }
    }

    public void setRealm(String realm) {
        this.mRealm = realm;
    }

    public String getRealm() {
        return mRealm;
    }

    public void setUserCredential(UserCredential userCredential) {
        this.mUserCredential = userCredential;
    }

    public UserCredential getUserCredential() {
        return mUserCredential;
    }

    public void setCertCredential(CertificateCredential certCredential) {
        this.mCertCredential = certCredential;
    }

    public CertificateCredential getCertCredential() {
        return mCertCredential;
    }

    public void setSimCredential(SimCredential simCredential) {
        this.mSimCredential = simCredential;
    }

    public SimCredential getSimCredential() {
        return mSimCredential;
    }

    public void setCaCertificate(X509Certificate caCertificate) {
        this.mCaCertificate = caCertificate;
    }

    public X509Certificate getCaCertificate() {
        return mCaCertificate;
    }

    public void setClientCertificateChain(X509Certificate[] certificateChain) {
        this.mClientCertificateChain =
                certificateChain == null ? null : certificateChain.clone();
    }

    public X509Certificate[] getClientCertificateChain() {
        return mClientCertificateChain == null ? null : mClientCertificateChain.clone();
    }

    public void setClientPrivateKey(PrivateKey privateKey) {
        this.mClientPrivateKey = privateKey;
    }

    public PrivateKey getClientPrivateKey() {
        return mClientPrivateKey;
    }

    public void setMinimumTlsVersion(int tlsVersion) throws IllegalArgumentException {
        if (tlsVersion < WifiEnterpriseConfig.TLS_V1_0
                || tlsVersion > WifiEnterpriseConfig.TLS_V1_3) {
            throw new IllegalArgumentException("Invalid TLS version: " + tlsVersion);
        }
        this.mMinimumTlsVersion = tlsVersion;
    }

    public int getMinimumTlsVersion() {
        return mMinimumTlsVersion;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mRealm);
        dest.writeTypedObject(mUserCredential, flags);
        dest.writeTypedObject(mCertCredential, flags);
        dest.writeTypedObject(mSimCredential, flags);
        dest.writeInt(mMinimumTlsVersion);
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject) {
            return true;
        }
        if (!(thatObject instanceof Credential)) {
            return false;
        }
        Credential that = (Credential) thatObject;
        return mMinimumTlsVersion == that.mMinimumTlsVersion
                && Objects.equals(mRealm, that.mRealm)
                && Objects.equals(mUserCredential, that.mUserCredential)
                && Objects.equals(mCertCredential, that.mCertCredential)
                && Objects.equals(mSimCredential, that.mSimCredential)
                && Objects.equals(mCaCertificate, that.mCaCertificate)
                && Arrays.equals(mClientCertificateChain, that.mClientCertificateChain)
                && Objects.equals(mClientPrivateKey, that.mClientPrivateKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mRealm,
                mUserCredential,
                mCertCredential,
                mSimCredential,
                mCaCertificate,
                Integer.valueOf(Arrays.hashCode(mClientCertificateChain)),
                mClientPrivateKey,
                Integer.valueOf(mMinimumTlsVersion));
    }

    @Override
    public String toString() {
        return "Credential{realm=" + mRealm + "}";
    }

    public static final class UserCredential implements Parcelable {
        public static final Creator<UserCredential> CREATOR =
                new Creator<UserCredential>() {
                    @Override
                    public UserCredential createFromParcel(Parcel in) {
                        UserCredential u = new UserCredential();
                        u.mUsername = in.readString();
                        u.mPassword = in.readString();
                        u.mEapType = in.readInt();
                        u.mNonEapInnerMethod = in.readString();
                        return u;
                    }

                    @Override
                    public UserCredential[] newArray(int size) {
                        return new UserCredential[size];
                    }
                };

        private String mUsername;
        private String mPassword;
        private int mEapType;
        private String mNonEapInnerMethod;

        public UserCredential() {}

        public UserCredential(UserCredential source) {
            if (source != null) {
                this.mUsername = source.mUsername;
                this.mPassword = source.mPassword;
                this.mEapType = source.mEapType;
                this.mNonEapInnerMethod = source.mNonEapInnerMethod;
            }
        }

        public void setUsername(String username) {
            this.mUsername = username;
        }

        public String getUsername() {
            return mUsername;
        }

        public void setPassword(String password) {
            this.mPassword = password;
        }

        public String getPassword() {
            return mPassword;
        }

        public void setEapType(int eapType) {
            this.mEapType = eapType;
        }

        public int getEapType() {
            return mEapType;
        }

        public void setNonEapInnerMethod(String nonEapInnerMethod) {
            this.mNonEapInnerMethod = nonEapInnerMethod;
        }

        public String getNonEapInnerMethod() {
            return mNonEapInnerMethod;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mUsername);
            dest.writeString(mPassword);
            dest.writeInt(mEapType);
            dest.writeString(mNonEapInnerMethod);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof UserCredential)) {
                return false;
            }
            UserCredential that = (UserCredential) o;
            return mEapType == that.mEapType
                    && Objects.equals(mUsername, that.mUsername)
                    && Objects.equals(mPassword, that.mPassword)
                    && Objects.equals(mNonEapInnerMethod, that.mNonEapInnerMethod);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mUsername, mPassword, Integer.valueOf(mEapType), mNonEapInnerMethod);
        }

        @Override
        public String toString() {
            return "UserCredential{username=" + mUsername + ", eapType=" + mEapType + "}";
        }
    }

    public static final class CertificateCredential implements Parcelable {
        public static final Creator<CertificateCredential> CREATOR =
                new Creator<CertificateCredential>() {
                    @Override
                    public CertificateCredential createFromParcel(Parcel in) {
                        CertificateCredential c = new CertificateCredential();
                        c.mCertType = in.readString();
                        c.mCertSha256Fingerprint = in.createByteArray();
                        return c;
                    }

                    @Override
                    public CertificateCredential[] newArray(int size) {
                        return new CertificateCredential[size];
                    }
                };

        private String mCertType;
        private byte[] mCertSha256Fingerprint;

        public CertificateCredential() {}

        public CertificateCredential(CertificateCredential source) {
            if (source != null) {
                this.mCertType = source.mCertType;
                this.mCertSha256Fingerprint =
                        source.mCertSha256Fingerprint == null
                                ? null
                                : source.mCertSha256Fingerprint.clone();
            }
        }

        public void setCertType(String certType) {
            this.mCertType = certType;
        }

        public String getCertType() {
            return mCertType;
        }

        public void setCertSha256Fingerprint(byte[] certSha256Fingerprint) {
            this.mCertSha256Fingerprint =
                    certSha256Fingerprint == null ? null : certSha256Fingerprint.clone();
        }

        public byte[] getCertSha256Fingerprint() {
            return mCertSha256Fingerprint == null ? null : mCertSha256Fingerprint.clone();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mCertType);
            dest.writeByteArray(mCertSha256Fingerprint);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CertificateCredential)) {
                return false;
            }
            CertificateCredential that = (CertificateCredential) o;
            return Objects.equals(mCertType, that.mCertType)
                    && Arrays.equals(mCertSha256Fingerprint, that.mCertSha256Fingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mCertType, Integer.valueOf(Arrays.hashCode(mCertSha256Fingerprint)));
        }

        @Override
        public String toString() {
            return "CertificateCredential{certType=" + mCertType + "}";
        }
    }

    public static final class SimCredential implements Parcelable {
        public static final Creator<SimCredential> CREATOR =
                new Creator<SimCredential>() {
                    @Override
                    public SimCredential createFromParcel(Parcel in) {
                        SimCredential s = new SimCredential();
                        s.mImsi = in.readString();
                        s.mEapType = in.readInt();
                        return s;
                    }

                    @Override
                    public SimCredential[] newArray(int size) {
                        return new SimCredential[size];
                    }
                };

        private String mImsi;
        private int mEapType;

        public SimCredential() {}

        public SimCredential(SimCredential source) {
            if (source != null) {
                this.mImsi = source.mImsi;
                this.mEapType = source.mEapType;
            }
        }

        public void setImsi(String imsi) {
            this.mImsi = imsi;
        }

        public String getImsi() {
            return mImsi;
        }

        public void setEapType(int eapType) {
            this.mEapType = eapType;
        }

        public int getEapType() {
            return mEapType;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mImsi);
            dest.writeInt(mEapType);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof SimCredential)) {
                return false;
            }
            SimCredential that = (SimCredential) o;
            return mEapType == that.mEapType && Objects.equals(mImsi, that.mImsi);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mImsi, Integer.valueOf(mEapType));
        }

        @Override
        public String toString() {
            return "SimCredential{imsi=" + mImsi + ", eapType=" + mEapType + "}";
        }
    }
}
