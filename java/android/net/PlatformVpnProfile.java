// SPDX-License-Identifier: GPL-3.0-only
package android.net;

/** Base class for platform-managed VPN profiles (IKEv2/IPsec). */
public abstract class PlatformVpnProfile {
    public static final int TYPE_IKEV2_IPSEC_USER_PASS = 6;
    public static final int TYPE_IKEV2_IPSEC_PSK = 7;
    public static final int TYPE_IKEV2_IPSEC_RSA = 8;

    final int mType;
    final boolean mExcludeLocalRoutes;
    final boolean mRequiresInternetValidation;

    PlatformVpnProfile(
            int type, boolean excludeLocalRoutes, boolean requiresInternetValidation) {
        this.mType = type;
        this.mExcludeLocalRoutes = excludeLocalRoutes;
        this.mRequiresInternetValidation = requiresInternetValidation;
    }

    public final int getType() {
        return mType;
    }

    public final boolean areLocalRoutesExcluded() {
        return mExcludeLocalRoutes;
    }

    public final boolean isInternetValidationRequired() {
        return mRequiresInternetValidation;
    }

    public final String getTypeString() {
        switch (mType) {
            case TYPE_IKEV2_IPSEC_USER_PASS:
                return "IKEv2/IPsec Username/Password";
            case TYPE_IKEV2_IPSEC_PSK:
                return "IKEv2/IPsec Preshared key";
            case TYPE_IKEV2_IPSEC_RSA:
                return "IKEv2/IPsec RSA Digital Signature";
            default:
                return "Unknown VPN profile type";
        }
    }
}
