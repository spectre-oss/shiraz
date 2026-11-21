// SPDX-License-Identifier: GPL-3.0-only
package android.net.eap;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EapSessionConfig {
    private static final byte[] DEFAULT_IDENTITY = new byte[0];

    private final byte[] mEapIdentity;
    private final Map<Integer, EapMethodConfig> mEapConfigs;

    EapSessionConfig(Map<Integer, EapMethodConfig> eapConfigs, byte[] eapIdentity) {
        this.mEapIdentity =
                eapIdentity == null ? DEFAULT_IDENTITY.clone() : eapIdentity.clone();
        this.mEapConfigs =
                Collections.unmodifiableMap(
                        new LinkedHashMap<Integer, EapMethodConfig>(eapConfigs));
    }

    public byte[] getEapIdentity() {
        return mEapIdentity.clone();
    }

    public EapSimConfig getEapSimConfig() {
        return (EapSimConfig) mEapConfigs.get(Integer.valueOf(EapMethodConfig.EAP_TYPE_SIM));
    }

    public EapAkaConfig getEapAkaConfig() {
        return (EapAkaConfig) mEapConfigs.get(Integer.valueOf(EapMethodConfig.EAP_TYPE_AKA));
    }

    public EapAkaPrimeConfig getEapAkaPrimeConfig() {
        return (EapAkaPrimeConfig)
                mEapConfigs.get(Integer.valueOf(EapMethodConfig.EAP_TYPE_AKA_PRIME));
    }

    public EapMsChapV2Config getEapMsChapV2Config() {
        return (EapMsChapV2Config)
                mEapConfigs.get(Integer.valueOf(EapMethodConfig.EAP_TYPE_MSCHAP_V2));
    }

    public EapTtlsConfig getEapTtlsConfig() {
        return (EapTtlsConfig) mEapConfigs.get(Integer.valueOf(EapMethodConfig.EAP_TYPE_TTLS));
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(Arrays.hashCode(mEapIdentity)), mEapConfigs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EapSessionConfig)) {
            return false;
        }
        EapSessionConfig other = (EapSessionConfig) o;
        return Arrays.equals(mEapIdentity, other.mEapIdentity)
                && Objects.equals(mEapConfigs, other.mEapConfigs);
    }

    public static final class Builder {
        private final Map<Integer, EapMethodConfig> mEapConfigs =
                new LinkedHashMap<Integer, EapMethodConfig>();
        private byte[] mEapIdentity;

        public Builder() {}

        public Builder setEapIdentity(byte[] eapIdentity) {
            Objects.requireNonNull(eapIdentity, "eapIdentity");
            this.mEapIdentity = eapIdentity.clone();
            return this;
        }

        public Builder setEapSimConfig(int subId, int apptType) {
            mEapConfigs.put(
                    Integer.valueOf(EapMethodConfig.EAP_TYPE_SIM),
                    new EapSimConfig(subId, apptType));
            return this;
        }

        public Builder setEapAkaConfig(int subId, int apptType) {
            return setEapAkaConfig(subId, apptType, null);
        }

        public Builder setEapAkaConfig(int subId, int apptType, EapAkaOption options) {
            mEapConfigs.put(
                    Integer.valueOf(EapMethodConfig.EAP_TYPE_AKA),
                    new EapAkaConfig(EapMethodConfig.EAP_TYPE_AKA, subId, apptType, options));
            return this;
        }

        public Builder setEapAkaPrimeConfig(
                int subId,
                int apptType,
                String networkName,
                boolean allowMismatchedNetworkNames) {
            mEapConfigs.put(
                    Integer.valueOf(EapMethodConfig.EAP_TYPE_AKA_PRIME),
                    new EapAkaPrimeConfig(
                            subId, apptType, networkName, allowMismatchedNetworkNames));
            return this;
        }

        public Builder setEapMsChapV2Config(String username, String password) {
            mEapConfigs.put(
                    Integer.valueOf(EapMethodConfig.EAP_TYPE_MSCHAP_V2),
                    new EapMsChapV2Config(username, password));
            return this;
        }

        public Builder setEapTtlsConfig(
                X509Certificate serverCaCert, EapSessionConfig innerEapSessionConfig) {
            mEapConfigs.put(
                    Integer.valueOf(EapMethodConfig.EAP_TYPE_TTLS),
                    new EapTtlsConfig(serverCaCert, innerEapSessionConfig));
            return this;
        }

        public EapSessionConfig build() {
            if (mEapConfigs.isEmpty()) {
                throw new IllegalStateException("Must have at least one EAP method configured");
            }
            return new EapSessionConfig(mEapConfigs, mEapIdentity);
        }
    }

    public abstract static class EapMethodConfig {
        public static final int EAP_TYPE_SIM = 18;
        public static final int EAP_TYPE_TTLS = 21;
        public static final int EAP_TYPE_AKA = 23;
        public static final int EAP_TYPE_MSCHAP_V2 = 26;
        public static final int EAP_TYPE_AKA_PRIME = 50;

        private final int mMethodType;

        EapMethodConfig(int methodType) {
            this.mMethodType = methodType;
        }

        public int getMethodType() {
            return mMethodType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Integer.valueOf(mMethodType));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EapMethodConfig)) {
                return false;
            }
            return mMethodType == ((EapMethodConfig) o).mMethodType;
        }
    }

    public static class EapSimConfig extends EapMethodConfig {
        private final int mSubId;
        private final int mApptType;

        EapSimConfig(int subId, int apptType) {
            super(EAP_TYPE_SIM);
            this.mSubId = subId;
            this.mApptType = apptType;
        }

        public int getSubId() {
            return mSubId;
        }

        public int getAppType() {
            return mApptType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Integer.valueOf(super.hashCode()),
                    Integer.valueOf(mSubId),
                    Integer.valueOf(mApptType));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EapSimConfig) || !super.equals(o)) {
                return false;
            }
            EapSimConfig other = (EapSimConfig) o;
            return mSubId == other.mSubId && mApptType == other.mApptType;
        }
    }

    public static final class EapAkaOption {
        private final byte[] mReauthId;

        EapAkaOption(byte[] reauthId) {
            this.mReauthId = reauthId == null ? null : reauthId.clone();
        }

        public byte[] getReauthId() {
            return mReauthId == null ? null : mReauthId.clone();
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(mReauthId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EapAkaOption)) {
                return false;
            }
            return Arrays.equals(mReauthId, ((EapAkaOption) o).mReauthId);
        }

        public static final class Builder {
            private byte[] mReauthId;

            public Builder() {}

            public Builder setReauthId(byte[] reauthId) {
                if (reauthId != null) {
                    String s = new String(reauthId, StandardCharsets.UTF_8);
                    this.mReauthId = s.getBytes(StandardCharsets.UTF_8);
                } else {
                    this.mReauthId = null;
                }
                return this;
            }

            public EapAkaOption build() {
                return new EapAkaOption(mReauthId);
            }
        }
    }

    public static class EapAkaConfig extends EapMethodConfig {
        private final int mSubId;
        private final int mApptType;
        private final EapAkaOption mEapAkaOption;

        EapAkaConfig(int methodType, int subId, int apptType, EapAkaOption options) {
            super(methodType);
            this.mSubId = subId;
            this.mApptType = apptType;
            this.mEapAkaOption = options == null ? new EapAkaOption.Builder().build() : options;
        }

        public EapAkaOption getEapAkaOption() {
            return mEapAkaOption;
        }

        public int getSubId() {
            return mSubId;
        }

        public int getAppType() {
            return mApptType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Integer.valueOf(super.hashCode()),
                    Integer.valueOf(mSubId),
                    Integer.valueOf(mApptType),
                    mEapAkaOption);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EapAkaConfig) || !super.equals(o)) {
                return false;
            }
            EapAkaConfig other = (EapAkaConfig) o;
            return mSubId == other.mSubId
                    && mApptType == other.mApptType
                    && Objects.equals(mEapAkaOption, other.mEapAkaOption);
        }
    }

    public static class EapAkaPrimeConfig extends EapAkaConfig {
        private final String mNetworkName;
        private final boolean mAllowMismatchedNetworkNames;

        EapAkaPrimeConfig(
                int subId,
                int apptType,
                String networkName,
                boolean allowMismatchedNetworkNames) {
            super(EAP_TYPE_AKA_PRIME, subId, apptType, null);
            this.mNetworkName = Objects.requireNonNull(networkName, "networkName");
            this.mAllowMismatchedNetworkNames = allowMismatchedNetworkNames;
        }

        public String getNetworkName() {
            return mNetworkName;
        }

        public boolean allowsMismatchedNetworkNames() {
            return mAllowMismatchedNetworkNames;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Integer.valueOf(super.hashCode()),
                    mNetworkName,
                    Boolean.valueOf(mAllowMismatchedNetworkNames));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EapAkaPrimeConfig) || !super.equals(o)) {
                return false;
            }
            EapAkaPrimeConfig other = (EapAkaPrimeConfig) o;
            return mAllowMismatchedNetworkNames == other.mAllowMismatchedNetworkNames
                    && Objects.equals(mNetworkName, other.mNetworkName);
        }
    }

    public static class EapMsChapV2Config extends EapMethodConfig {
        private final String mUsername;
        private final String mPassword;

        EapMsChapV2Config(String username, String password) {
            super(EAP_TYPE_MSCHAP_V2);
            this.mUsername = Objects.requireNonNull(username, "username");
            this.mPassword = Objects.requireNonNull(password, "password");
        }

        public String getUsername() {
            return mUsername;
        }

        public String getPassword() {
            return mPassword;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Integer.valueOf(super.hashCode()), mUsername, mPassword);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EapMsChapV2Config) || !super.equals(o)) {
                return false;
            }
            EapMsChapV2Config other = (EapMsChapV2Config) o;
            return Objects.equals(mUsername, other.mUsername)
                    && Objects.equals(mPassword, other.mPassword);
        }
    }

    public static class EapTtlsConfig extends EapMethodConfig {
        private final X509Certificate mServerCaCert;
        private final EapSessionConfig mInnerEapSessionConfig;

        EapTtlsConfig(X509Certificate serverCaCert, EapSessionConfig innerEapSessionConfig) {
            super(EAP_TYPE_TTLS);
            this.mServerCaCert = serverCaCert;
            this.mInnerEapSessionConfig =
                    Objects.requireNonNull(innerEapSessionConfig, "innerEapSessionConfig");
        }

        public X509Certificate getServerCaCert() {
            return mServerCaCert;
        }

        public EapSessionConfig getInnerEapSessionConfig() {
            return mInnerEapSessionConfig;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Integer.valueOf(super.hashCode()), mServerCaCert, mInnerEapSessionConfig);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EapTtlsConfig) || !super.equals(o)) {
                return false;
            }
            EapTtlsConfig other = (EapTtlsConfig) o;
            return Objects.equals(mServerCaCert, other.mServerCaCert)
                    && Objects.equals(mInnerEapSessionConfig, other.mInnerEapSessionConfig);
        }
    }
}
