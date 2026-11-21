// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.net.ipsec.ike.IkeTunnelConnectionParams;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Configuration profile for an IKEv2/IPsec platform VPN. */
public final class Ikev2VpnProfile extends PlatformVpnProfile {
    private static final List<String> DEFAULT_ALGORITHMS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            IpSecAlgorithm.CRYPT_AES_CBC,
                            IpSecAlgorithm.CRYPT_AES_CTR,
                            IpSecAlgorithm.AUTH_HMAC_SHA256,
                            IpSecAlgorithm.AUTH_HMAC_SHA384,
                            IpSecAlgorithm.AUTH_HMAC_SHA512,
                            IpSecAlgorithm.AUTH_AES_XCBC,
                            IpSecAlgorithm.AUTH_AES_CMAC,
                            IpSecAlgorithm.AUTH_CRYPT_AES_GCM,
                            IpSecAlgorithm.AUTH_CRYPT_CHACHA20_POLY1305));

    private final String mServerAddr;
    private final String mUserIdentity;
    private final byte[] mPresharedKey;
    private final X509Certificate mServerRootCaCert;
    private final String mUsername;
    private final String mPassword;
    private final PrivateKey mRsaPrivateKey;
    private final X509Certificate mUserCert;
    private final ProxyInfo mProxyInfo;
    private final List<String> mAllowedAlgorithms;
    private final boolean mIsBypassable;
    private final boolean mIsMetered;
    private final int mMaxMtu;
    private final IkeTunnelConnectionParams mIkeTunnelConnectionParams;
    private final boolean mAutomaticNattKeepaliveTimerEnabled;
    private final boolean mAutomaticIpVersionSelectionEnabled;

    Ikev2VpnProfile(
            int type,
            String serverAddr,
            String userIdentity,
            byte[] presharedKey,
            X509Certificate serverRootCaCert,
            String username,
            String password,
            PrivateKey rsaPrivateKey,
            X509Certificate userCert,
            ProxyInfo proxyInfo,
            List<String> allowedAlgorithms,
            boolean isBypassable,
            boolean isMetered,
            int maxMtu,
            boolean restrictToTestNetworks,
            boolean excludeLocalRoutes,
            boolean requiresInternetValidation,
            IkeTunnelConnectionParams ikeTunConnParams,
            boolean automaticNattKeepaliveTimerEnabled,
            boolean automaticIpVersionSelectionEnabled) {
        super(type, excludeLocalRoutes, requiresInternetValidation);
        this.mServerAddr = serverAddr;
        this.mUserIdentity = userIdentity;
        this.mPresharedKey = presharedKey == null ? null : presharedKey.clone();
        this.mServerRootCaCert = serverRootCaCert;
        this.mUsername = username;
        this.mPassword = password;
        this.mRsaPrivateKey = rsaPrivateKey;
        this.mUserCert = userCert;
        this.mProxyInfo = proxyInfo == null ? null : new ProxyInfo(proxyInfo);
        this.mAllowedAlgorithms =
                Collections.unmodifiableList(new ArrayList<String>(allowedAlgorithms));
        this.mIsBypassable = isBypassable;
        this.mIsMetered = isMetered;
        this.mMaxMtu = maxMtu;
        this.mIkeTunnelConnectionParams = ikeTunConnParams;
        this.mAutomaticNattKeepaliveTimerEnabled = automaticNattKeepaliveTimerEnabled;
        this.mAutomaticIpVersionSelectionEnabled = automaticIpVersionSelectionEnabled;
    }

    public String getServerAddr() {
        return mServerAddr;
    }

    public String getUserIdentity() {
        return mUserIdentity;
    }

    public byte[] getPresharedKey() {
        return mPresharedKey == null ? null : mPresharedKey.clone();
    }

    public X509Certificate getServerRootCaCert() {
        return mServerRootCaCert;
    }

    public String getUsername() {
        return mUsername;
    }

    public String getPassword() {
        return mPassword;
    }

    public PrivateKey getRsaPrivateKey() {
        return mRsaPrivateKey;
    }

    public X509Certificate getUserCert() {
        return mUserCert;
    }

    public ProxyInfo getProxyInfo() {
        return mProxyInfo == null ? null : new ProxyInfo(mProxyInfo);
    }

    public List<String> getAllowedAlgorithms() {
        return mAllowedAlgorithms;
    }

    public boolean isBypassable() {
        return mIsBypassable;
    }

    public boolean isMetered() {
        return mIsMetered;
    }

    public int getMaxMtu() {
        return mMaxMtu;
    }

    public IkeTunnelConnectionParams getIkeTunnelConnectionParams() {
        return mIkeTunnelConnectionParams;
    }

    public boolean isAutomaticNattKeepaliveTimerEnabled() {
        return mAutomaticNattKeepaliveTimerEnabled;
    }

    public boolean isAutomaticIpVersionSelectionEnabled() {
        return mAutomaticIpVersionSelectionEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mType),
                mServerAddr,
                mUserIdentity,
                Integer.valueOf(Arrays.hashCode(mPresharedKey)),
                mServerRootCaCert,
                mUsername,
                mPassword,
                mRsaPrivateKey,
                mUserCert,
                mProxyInfo,
                mAllowedAlgorithms,
                Boolean.valueOf(mIsBypassable),
                Boolean.valueOf(mIsMetered),
                Integer.valueOf(mMaxMtu),
                Boolean.valueOf(mExcludeLocalRoutes),
                Boolean.valueOf(mRequiresInternetValidation),
                mIkeTunnelConnectionParams,
                Boolean.valueOf(mAutomaticNattKeepaliveTimerEnabled),
                Boolean.valueOf(mAutomaticIpVersionSelectionEnabled));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Ikev2VpnProfile)) {
            return false;
        }
        Ikev2VpnProfile other = (Ikev2VpnProfile) obj;
        return mType == other.mType
                && Objects.equals(mServerAddr, other.mServerAddr)
                && Objects.equals(mUserIdentity, other.mUserIdentity)
                && Arrays.equals(mPresharedKey, other.mPresharedKey)
                && Objects.equals(mServerRootCaCert, other.mServerRootCaCert)
                && Objects.equals(mUsername, other.mUsername)
                && Objects.equals(mPassword, other.mPassword)
                && Objects.equals(mRsaPrivateKey, other.mRsaPrivateKey)
                && Objects.equals(mUserCert, other.mUserCert)
                && Objects.equals(mProxyInfo, other.mProxyInfo)
                && Objects.equals(mAllowedAlgorithms, other.mAllowedAlgorithms)
                && mIsBypassable == other.mIsBypassable
                && mIsMetered == other.mIsMetered
                && mMaxMtu == other.mMaxMtu
                && mExcludeLocalRoutes == other.mExcludeLocalRoutes
                && mRequiresInternetValidation == other.mRequiresInternetValidation
                && Objects.equals(mIkeTunnelConnectionParams, other.mIkeTunnelConnectionParams)
                && mAutomaticNattKeepaliveTimerEnabled == other.mAutomaticNattKeepaliveTimerEnabled
                && mAutomaticIpVersionSelectionEnabled == other.mAutomaticIpVersionSelectionEnabled;
    }

    @Override
    public String toString() {
        return "Ikev2VpnProfile[" + getTypeString() + ", server=" + mServerAddr + "]";
    }

    public static final class Builder {
        private int mType = -1;
        private final String mServerAddr;
        private final String mUserIdentity;
        private final IkeTunnelConnectionParams mIkeTunConnParams;
        private byte[] mPresharedKey;
        private X509Certificate mServerRootCaCert;
        private String mUsername;
        private String mPassword;
        private PrivateKey mRsaPrivateKey;
        private X509Certificate mUserCert;
        private ProxyInfo mProxyInfo;
        private List<String> mAllowedAlgorithms = DEFAULT_ALGORITHMS;
        private boolean mIsBypassable;
        private boolean mIsMetered = true;
        private int mMaxMtu = 1360;
        private boolean mRequiresInternetValidation;
        private boolean mExcludeLocalRoutes;
        private boolean mAutomaticNattKeepaliveTimerEnabled;
        private boolean mAutomaticIpVersionSelectionEnabled;

        public Builder(String serverAddr, String identity) {
            this.mServerAddr = Objects.requireNonNull(serverAddr, "serverAddr");
            this.mUserIdentity = Objects.requireNonNull(identity, "identity");
            if (serverAddr.isEmpty() || identity.isEmpty()) {
                throw new IllegalArgumentException("serverAddr and identity must be non-empty");
            }
            this.mIkeTunConnParams = null;
        }

        public Builder(IkeTunnelConnectionParams ikeTunConnParams) {
            this.mIkeTunConnParams =
                    Objects.requireNonNull(ikeTunConnParams, "ikeTunConnParams");
            this.mServerAddr = null;
            this.mUserIdentity = null;
            this.mType = TYPE_IKEV2_IPSEC_PSK;
        }

        private void checkNotIkeParams() {
            if (mIkeTunConnParams != null) {
                throw new IllegalArgumentException(
                        "Auth method cannot be set when built with IkeTunnelConnectionParams");
            }
        }

        private void resetAuth() {
            mPresharedKey = null;
            mServerRootCaCert = null;
            mUsername = null;
            mPassword = null;
            mRsaPrivateKey = null;
            mUserCert = null;
        }

        public Builder setAuthUsernamePassword(
                String user, String pass, X509Certificate serverRootCa) {
            checkNotIkeParams();
            Objects.requireNonNull(user, "user");
            Objects.requireNonNull(pass, "pass");
            resetAuth();
            this.mType = TYPE_IKEV2_IPSEC_USER_PASS;
            this.mUsername = user;
            this.mPassword = pass;
            this.mServerRootCaCert = serverRootCa;
            return this;
        }

        public Builder setAuthDigitalSignature(
                X509Certificate userCert, PrivateKey key, X509Certificate serverRootCa) {
            checkNotIkeParams();
            Objects.requireNonNull(userCert, "userCert");
            Objects.requireNonNull(key, "key");
            resetAuth();
            this.mType = TYPE_IKEV2_IPSEC_RSA;
            this.mUserCert = userCert;
            this.mRsaPrivateKey = key;
            this.mServerRootCaCert = serverRootCa;
            return this;
        }

        public Builder setAuthPsk(byte[] psk) {
            checkNotIkeParams();
            Objects.requireNonNull(psk, "psk");
            if (psk.length == 0) {
                throw new IllegalArgumentException("psk must not be empty");
            }
            resetAuth();
            this.mType = TYPE_IKEV2_IPSEC_PSK;
            this.mPresharedKey = psk.clone();
            return this;
        }

        public Builder setBypassable(boolean isBypassable) {
            this.mIsBypassable = isBypassable;
            return this;
        }

        public Builder setProxy(ProxyInfo proxy) {
            this.mProxyInfo = proxy;
            return this;
        }

        public Builder setMaxMtu(int mtu) {
            if (mtu < 1280) {
                throw new IllegalArgumentException("Max MTU must be at least 1280");
            }
            this.mMaxMtu = mtu;
            return this;
        }

        public Builder setRequiresInternetValidation(boolean requiresInternetValidation) {
            this.mRequiresInternetValidation = requiresInternetValidation;
            return this;
        }

        public Builder setMetered(boolean isMetered) {
            this.mIsMetered = isMetered;
            return this;
        }

        public Builder setAllowedAlgorithms(List<String> algorithmNames) {
            Objects.requireNonNull(algorithmNames, "algorithmNames");
            if (algorithmNames.isEmpty()) {
                throw new IllegalArgumentException("algorithmNames must not be empty");
            }
            this.mAllowedAlgorithms = new ArrayList<String>(algorithmNames);
            return this;
        }

        public Builder setAutomaticNattKeepaliveTimerEnabled(boolean isEnabled) {
            this.mAutomaticNattKeepaliveTimerEnabled = isEnabled;
            return this;
        }

        public Builder setAutomaticIpVersionSelectionEnabled(boolean isEnabled) {
            this.mAutomaticIpVersionSelectionEnabled = isEnabled;
            return this;
        }

        public Builder setLocalRoutesExcluded(boolean excludeLocalRoutes) {
            this.mExcludeLocalRoutes = excludeLocalRoutes;
            return this;
        }

        public Ikev2VpnProfile build() {
            if (mType == -1) {
                throw new IllegalArgumentException("Authentication method was not configured");
            }
            return new Ikev2VpnProfile(
                    mType,
                    mServerAddr,
                    mUserIdentity,
                    mPresharedKey,
                    mServerRootCaCert,
                    mUsername,
                    mPassword,
                    mRsaPrivateKey,
                    mUserCert,
                    mProxyInfo,
                    mAllowedAlgorithms,
                    mIsBypassable,
                    mIsMetered,
                    mMaxMtu,
                    false,
                    mExcludeLocalRoutes,
                    mRequiresInternetValidation,
                    mIkeTunConnParams,
                    mAutomaticNattKeepaliveTimerEnabled,
                    mAutomaticIpVersionSelectionEnabled);
        }
    }
}
