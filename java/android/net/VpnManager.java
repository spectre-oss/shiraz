// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.content.Intent;
import java.util.Objects;
import java.util.UUID;

/** System service manager for platform-managed VPN profiles. */
public class VpnManager {
    public static final String ACTION_VPN_MANAGER_EVENT = "android.net.action.VPN_MANAGER_EVENT";
    public static final String CATEGORY_EVENT_IKE_ERROR = "android.net.category.EVENT_IKE_ERROR";
    public static final String CATEGORY_EVENT_NETWORK_ERROR =
            "android.net.category.EVENT_NETWORK_ERROR";
    public static final String CATEGORY_EVENT_DEACTIVATED_BY_USER =
            "android.net.category.EVENT_DEACTIVATED_BY_USER";
    public static final String CATEGORY_EVENT_ALWAYS_ON_STATE_CHANGED =
            "android.net.category.EVENT_ALWAYS_ON_STATE_CHANGED";

    public static final String EXTRA_VPN_PROFILE_STATE = "android.net.extra.VPN_PROFILE_STATE";
    public static final String EXTRA_SESSION_KEY = "android.net.extra.SESSION_KEY";
    public static final String EXTRA_UNDERLYING_NETWORK = "android.net.extra.UNDERLYING_NETWORK";
    public static final String EXTRA_UNDERLYING_NETWORK_CAPABILITIES =
            "android.net.extra.UNDERLYING_NETWORK_CAPABILITIES";
    public static final String EXTRA_UNDERLYING_LINK_PROPERTIES =
            "android.net.extra.UNDERLYING_LINK_PROPERTIES";
    public static final String EXTRA_TIMESTAMP_MILLIS = "android.net.extra.TIMESTAMP_MILLIS";
    public static final String EXTRA_ERROR_CLASS = "android.net.extra.ERROR_CLASS";
    public static final String EXTRA_ERROR_CODE = "android.net.extra.ERROR_CODE";

    public static final int ERROR_CLASS_NOT_RECOVERABLE = 1;
    public static final int ERROR_CLASS_RECOVERABLE = 2;

    public static final int ERROR_CODE_NETWORK_UNKNOWN_HOST = 0;
    public static final int ERROR_CODE_NETWORK_PROTOCOL_TIMEOUT = 1;
    public static final int ERROR_CODE_NETWORK_LOST = 2;
    public static final int ERROR_CODE_NETWORK_IO = 3;

    private PlatformVpnProfile mProvisionedProfile;
    private VpnProfileState mState;

    VpnManager() {
    }

    public synchronized Intent provisionVpnProfile(PlatformVpnProfile profile) {
        Objects.requireNonNull(profile, "profile");
        this.mProvisionedProfile = profile;
        this.mState = new VpnProfileState(VpnProfileState.STATE_DISCONNECTED, null, false, false);
        return null;
    }

    public synchronized void deleteProvisionedVpnProfile() {
        mProvisionedProfile = null;
        mState = null;
    }

    public synchronized String startProvisionedVpnProfileSession() {
        if (mProvisionedProfile == null) {
            throw new SecurityException("No VPN profile provisioned");
        }
        String key = UUID.randomUUID().toString();
        mState = new VpnProfileState(VpnProfileState.STATE_DISCONNECTED, key, false, false);
        return key;
    }

    @Deprecated
    public void startProvisionedVpnProfile() {
        startProvisionedVpnProfileSession();
    }

    public synchronized void stopProvisionedVpnProfile() {
        if (mProvisionedProfile != null) {
            mState = new VpnProfileState(VpnProfileState.STATE_DISCONNECTED, null, false, false);
        }
    }

    public synchronized VpnProfileState getProvisionedVpnProfileState() {
        return mState;
    }
}
