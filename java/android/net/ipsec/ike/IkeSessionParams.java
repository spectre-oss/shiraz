// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import android.net.Network;
import android.net.eap.EapSessionConfig;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class IkeSessionParams {
    public static final int IKE_DPD_DELAY_SEC_DISABLED = Integer.MAX_VALUE;
    public static final int IKE_OPTION_ACCEPT_ANY_REMOTE_ID = 0;
    public static final int IKE_OPTION_EAP_ONLY_AUTH = 1;
    public static final int IKE_OPTION_MOBIKE = 2;
    public static final int IKE_OPTION_FORCE_PORT_4500 = 3;
    public static final int IKE_OPTION_INITIAL_CONTACT = 4;

    private static final int DEFAULT_HARD_LIFETIME_SEC = 14400;
    private static final int DEFAULT_SOFT_LIFETIME_SEC = 7200;
    private static final int DEFAULT_DPD_DELAY_SEC = 120;
    private static final int DEFAULT_NATT_KEEPALIVE_SEC = 10;
    private static final int[] DEFAULT_RETRANS_TIMEOUT_MS = {500, 1000, 2000, 4000, 8000};

    private final String mServerHostname;
    private final Network mNetwork;
    private final List<IkeSaProposal> mSaProposals;
    private final IkeIdentification mLocalIdentification;
    private final IkeIdentification mRemoteIdentification;
    private final IkeAuthConfig mLocalAuthConfig;
    private final IkeAuthConfig mRemoteAuthConfig;
    private final int mHardLifetimeSec;
    private final int mSoftLifetimeSec;
    private final int mDpdDelaySec;
    private final int mNattKeepaliveDelaySec;
    private final int[] mRetransTimeoutMsList;
    private final Set<Integer> mIkeOptions;

    IkeSessionParams(Builder builder) {
        this.mServerHostname = builder.mServerHostname;
        this.mNetwork = builder.mNetwork;
        this.mSaProposals =
                Collections.unmodifiableList(new ArrayList<IkeSaProposal>(builder.mSaProposals));
        this.mLocalIdentification = builder.mLocalIdentification;
        this.mRemoteIdentification = builder.mRemoteIdentification;
        this.mLocalAuthConfig = builder.mLocalAuthConfig;
        this.mRemoteAuthConfig = builder.mRemoteAuthConfig;
        this.mHardLifetimeSec = builder.mHardLifetimeSec;
        this.mSoftLifetimeSec = builder.mSoftLifetimeSec;
        this.mDpdDelaySec = builder.mDpdDelaySec;
        this.mNattKeepaliveDelaySec = builder.mNattKeepaliveDelaySec;
        this.mRetransTimeoutMsList = builder.mRetransTimeoutMsList.clone();
        this.mIkeOptions =
                Collections.unmodifiableSet(new LinkedHashSet<Integer>(builder.mIkeOptions));
    }

    public String getServerHostname() {
        return mServerHostname;
    }

    public Network getNetwork() {
        return mNetwork;
    }

    public List<IkeSaProposal> getIkeSaProposals() {
        return mSaProposals;
    }

    public IkeIdentification getLocalIdentification() {
        return mLocalIdentification;
    }

    public IkeIdentification getRemoteIdentification() {
        return mRemoteIdentification;
    }

    public IkeAuthConfig getLocalAuthConfig() {
        return mLocalAuthConfig;
    }

    public IkeAuthConfig getRemoteAuthConfig() {
        return mRemoteAuthConfig;
    }

    public int getHardLifetimeSeconds() {
        return mHardLifetimeSec;
    }

    public int getSoftLifetimeSeconds() {
        return mSoftLifetimeSec;
    }

    public int getDpdDelaySeconds() {
        return mDpdDelaySec;
    }

    public int getNattKeepAliveDelaySeconds() {
        return mNattKeepaliveDelaySec;
    }

    public int[] getRetransmissionTimeoutsMillis() {
        return mRetransTimeoutMsList.clone();
    }

    public boolean hasIkeOption(int ikeOption) {
        return mIkeOptions.contains(Integer.valueOf(ikeOption));
    }

    public Set<Integer> getIkeOptions() {
        return mIkeOptions;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mServerHostname,
                mNetwork,
                mSaProposals,
                mLocalIdentification,
                mRemoteIdentification,
                mLocalAuthConfig,
                mRemoteAuthConfig,
                Integer.valueOf(mHardLifetimeSec),
                Integer.valueOf(mSoftLifetimeSec),
                Integer.valueOf(mDpdDelaySec),
                Integer.valueOf(mNattKeepaliveDelaySec),
                Integer.valueOf(Arrays.hashCode(mRetransTimeoutMsList)),
                mIkeOptions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IkeSessionParams)) {
            return false;
        }
        IkeSessionParams other = (IkeSessionParams) o;
        return mHardLifetimeSec == other.mHardLifetimeSec
                && mSoftLifetimeSec == other.mSoftLifetimeSec
                && mDpdDelaySec == other.mDpdDelaySec
                && mNattKeepaliveDelaySec == other.mNattKeepaliveDelaySec
                && Objects.equals(mServerHostname, other.mServerHostname)
                && Objects.equals(mNetwork, other.mNetwork)
                && Objects.equals(mSaProposals, other.mSaProposals)
                && Objects.equals(mLocalIdentification, other.mLocalIdentification)
                && Objects.equals(mRemoteIdentification, other.mRemoteIdentification)
                && Objects.equals(mLocalAuthConfig, other.mLocalAuthConfig)
                && Objects.equals(mRemoteAuthConfig, other.mRemoteAuthConfig)
                && Arrays.equals(mRetransTimeoutMsList, other.mRetransTimeoutMsList)
                && Objects.equals(mIkeOptions, other.mIkeOptions);
    }

    public abstract static class IkeAuthConfig {
        private final int mAuthMethod;

        IkeAuthConfig(int authMethod) {
            this.mAuthMethod = authMethod;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Integer.valueOf(mAuthMethod));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IkeAuthConfig)) {
                return false;
            }
            return mAuthMethod == ((IkeAuthConfig) o).mAuthMethod;
        }
    }

    public static class IkeAuthPskConfig extends IkeAuthConfig {
        private final byte[] mPsk;

        IkeAuthPskConfig(byte[] psk) {
            super(1);
            this.mPsk = psk.clone();
        }

        public byte[] getPsk() {
            return mPsk.clone();
        }

        @Override
        public int hashCode() {
            return Objects.hash(Integer.valueOf(super.hashCode()), Integer.valueOf(Arrays.hashCode(mPsk)));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IkeAuthPskConfig) || !super.equals(o)) {
                return false;
            }
            return Arrays.equals(mPsk, ((IkeAuthPskConfig) o).mPsk);
        }
    }

    public static class IkeAuthEapConfig extends IkeAuthConfig {
        private final EapSessionConfig mEapConfig;

        IkeAuthEapConfig(EapSessionConfig eapConfig) {
            super(3);
            this.mEapConfig = eapConfig;
        }

        public EapSessionConfig getEapConfig() {
            return mEapConfig;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Integer.valueOf(super.hashCode()), mEapConfig);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IkeAuthEapConfig) || !super.equals(o)) {
                return false;
            }
            return Objects.equals(mEapConfig, ((IkeAuthEapConfig) o).mEapConfig);
        }
    }

    public static class IkeAuthDigitalSignLocalConfig extends IkeAuthConfig {
        private final X509Certificate mEndCert;
        private final List<X509Certificate> mIntermediateCerts;
        private final PrivateKey mPrivateKey;

        IkeAuthDigitalSignLocalConfig(
                X509Certificate clientEndCert,
                List<X509Certificate> clientIntermediateCerts,
                PrivateKey privateKey) {
            super(2);
            this.mEndCert = clientEndCert;
            this.mIntermediateCerts =
                    Collections.unmodifiableList(
                            new ArrayList<X509Certificate>(clientIntermediateCerts));
            this.mPrivateKey = privateKey;
        }

        public X509Certificate getClientEndCertificate() {
            return mEndCert;
        }

        public List<X509Certificate> getIntermediateCertificates() {
            return mIntermediateCerts;
        }

        public PrivateKey getPrivateKey() {
            return mPrivateKey;
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Integer.valueOf(super.hashCode()),
                    mEndCert,
                    mIntermediateCerts,
                    mPrivateKey);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IkeAuthDigitalSignLocalConfig) || !super.equals(o)) {
                return false;
            }
            IkeAuthDigitalSignLocalConfig other = (IkeAuthDigitalSignLocalConfig) o;
            return Objects.equals(mEndCert, other.mEndCert)
                    && Objects.equals(mIntermediateCerts, other.mIntermediateCerts)
                    && Objects.equals(mPrivateKey, other.mPrivateKey);
        }
    }

    public static class IkeAuthDigitalSignRemoteConfig extends IkeAuthConfig {
        private final X509Certificate mTrustAnchor;

        IkeAuthDigitalSignRemoteConfig(X509Certificate caCert) {
            super(2);
            this.mTrustAnchor = caCert;
        }

        public X509Certificate getRemoteCaCert() {
            return mTrustAnchor;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Integer.valueOf(super.hashCode()), mTrustAnchor);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IkeAuthDigitalSignRemoteConfig) || !super.equals(o)) {
                return false;
            }
            return Objects.equals(
                    mTrustAnchor, ((IkeAuthDigitalSignRemoteConfig) o).mTrustAnchor);
        }
    }

    public static final class Builder {
        String mServerHostname;
        Network mNetwork;
        final List<IkeSaProposal> mSaProposals = new ArrayList<IkeSaProposal>();
        IkeIdentification mLocalIdentification;
        IkeIdentification mRemoteIdentification;
        IkeAuthConfig mLocalAuthConfig;
        IkeAuthConfig mRemoteAuthConfig;
        int mHardLifetimeSec = DEFAULT_HARD_LIFETIME_SEC;
        int mSoftLifetimeSec = DEFAULT_SOFT_LIFETIME_SEC;
        int mDpdDelaySec = DEFAULT_DPD_DELAY_SEC;
        int mNattKeepaliveDelaySec = DEFAULT_NATT_KEEPALIVE_SEC;
        int[] mRetransTimeoutMsList = DEFAULT_RETRANS_TIMEOUT_MS.clone();
        final Set<Integer> mIkeOptions = new LinkedHashSet<Integer>();

        public Builder() {}

        public Builder(IkeSessionParams ikeSessionParams) {
            Objects.requireNonNull(ikeSessionParams, "ikeSessionParams was null");
            this.mServerHostname = ikeSessionParams.mServerHostname;
            this.mNetwork = ikeSessionParams.mNetwork;
            this.mSaProposals.addAll(ikeSessionParams.mSaProposals);
            this.mLocalIdentification = ikeSessionParams.mLocalIdentification;
            this.mRemoteIdentification = ikeSessionParams.mRemoteIdentification;
            this.mLocalAuthConfig = ikeSessionParams.mLocalAuthConfig;
            this.mRemoteAuthConfig = ikeSessionParams.mRemoteAuthConfig;
            this.mHardLifetimeSec = ikeSessionParams.mHardLifetimeSec;
            this.mSoftLifetimeSec = ikeSessionParams.mSoftLifetimeSec;
            this.mDpdDelaySec = ikeSessionParams.mDpdDelaySec;
            this.mNattKeepaliveDelaySec = ikeSessionParams.mNattKeepaliveDelaySec;
            this.mRetransTimeoutMsList = ikeSessionParams.mRetransTimeoutMsList.clone();
            this.mIkeOptions.addAll(ikeSessionParams.mIkeOptions);
        }

        public Builder setServerHostname(String serverHostname) {
            this.mServerHostname =
                    Objects.requireNonNull(serverHostname, "serverHostname was null");
            return this;
        }

        public Builder setNetwork(Network network) {
            this.mNetwork = network;
            return this;
        }

        public Builder setLocalIdentification(IkeIdentification identification) {
            this.mLocalIdentification =
                    Objects.requireNonNull(identification, "identification was null");
            return this;
        }

        public Builder setRemoteIdentification(IkeIdentification identification) {
            this.mRemoteIdentification =
                    Objects.requireNonNull(identification, "identification was null");
            return this;
        }

        public Builder addIkeSaProposal(IkeSaProposal proposal) {
            mSaProposals.add(Objects.requireNonNull(proposal, "proposal was null"));
            return this;
        }

        public Builder setAuthPsk(byte[] sharedKey) {
            Objects.requireNonNull(sharedKey, "sharedKey was null");
            this.mLocalAuthConfig = new IkeAuthPskConfig(sharedKey);
            this.mRemoteAuthConfig = new IkeAuthPskConfig(sharedKey);
            return this;
        }

        public Builder setAuthEap(X509Certificate serverCaCert, EapSessionConfig eapConfig) {
            Objects.requireNonNull(eapConfig, "eapConfig was null");
            this.mLocalAuthConfig = new IkeAuthEapConfig(eapConfig);
            this.mRemoteAuthConfig = new IkeAuthDigitalSignRemoteConfig(serverCaCert);
            return this;
        }

        public Builder setAuthDigitalSignature(
                X509Certificate serverCaCert,
                X509Certificate clientEndCert,
                PrivateKey clientPrivateKey) {
            return setAuthDigitalSignature(
                    serverCaCert,
                    clientEndCert,
                    Collections.<X509Certificate>emptyList(),
                    clientPrivateKey);
        }

        public Builder setAuthDigitalSignature(
                X509Certificate serverCaCert,
                X509Certificate clientEndCert,
                List<X509Certificate> clientIntermediateCerts,
                PrivateKey clientPrivateKey) {
            Objects.requireNonNull(clientEndCert, "clientEndCert was null");
            Objects.requireNonNull(clientIntermediateCerts, "clientIntermediateCerts was null");
            Objects.requireNonNull(clientPrivateKey, "clientPrivateKey was null");
            this.mLocalAuthConfig =
                    new IkeAuthDigitalSignLocalConfig(
                            clientEndCert, clientIntermediateCerts, clientPrivateKey);
            this.mRemoteAuthConfig = new IkeAuthDigitalSignRemoteConfig(serverCaCert);
            return this;
        }

        public Builder setLifetimeSeconds(int hardLifetimeSeconds, int softLifetimeSeconds) {
            if (hardLifetimeSeconds <= softLifetimeSeconds || softLifetimeSeconds <= 0) {
                throw new IllegalArgumentException("Invalid lifetime values");
            }
            this.mHardLifetimeSec = hardLifetimeSeconds;
            this.mSoftLifetimeSec = softLifetimeSeconds;
            return this;
        }

        public Builder setDpdDelaySeconds(int dpdDelaySeconds) {
            if (dpdDelaySeconds <= 0) {
                throw new IllegalArgumentException("Invalid DPD delay");
            }
            this.mDpdDelaySec = dpdDelaySeconds;
            return this;
        }

        public Builder setNattKeepAliveDelaySeconds(int nattKeepaliveDelaySeconds) {
            if (nattKeepaliveDelaySeconds <= 0) {
                throw new IllegalArgumentException("Invalid NAT-T keepalive delay");
            }
            this.mNattKeepaliveDelaySec = nattKeepaliveDelaySeconds;
            return this;
        }

        public Builder setRetransmissionTimeoutsMillis(int[] retransTimeoutMillisList) {
            Objects.requireNonNull(retransTimeoutMillisList, "retransTimeoutMillisList was null");
            if (retransTimeoutMillisList.length == 0) {
                throw new IllegalArgumentException("Empty retransmission timeout list");
            }
            this.mRetransTimeoutMsList = retransTimeoutMillisList.clone();
            return this;
        }

        public Builder addIkeOption(int ikeOption) {
            validateIkeOption(ikeOption);
            mIkeOptions.add(Integer.valueOf(ikeOption));
            return this;
        }

        public Builder removeIkeOption(int ikeOption) {
            validateIkeOption(ikeOption);
            mIkeOptions.remove(Integer.valueOf(ikeOption));
            return this;
        }

        private static void validateIkeOption(int ikeOption) {
            if (ikeOption < IKE_OPTION_ACCEPT_ANY_REMOTE_ID
                    || ikeOption > IKE_OPTION_INITIAL_CONTACT) {
                throw new IllegalArgumentException("Invalid IKE Option: " + ikeOption);
            }
        }

        public IkeSessionParams build() {
            if (mSaProposals.isEmpty()) {
                throw new IllegalArgumentException("IKE Session requires at least one SA proposal");
            }
            if (mServerHostname == null
                    || mLocalIdentification == null
                    || mRemoteIdentification == null
                    || mLocalAuthConfig == null
                    || mRemoteAuthConfig == null) {
                throw new IllegalArgumentException("Necessary parameter missing");
            }
            return new IkeSessionParams(this);
        }
    }
}
