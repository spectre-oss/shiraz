// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.ims;

import android.content.Context;
import android.telephony.SubscriptionManager;

public class ImsManager {
    public static final String ACTION_WFC_IMS_REGISTRATION_ERROR =
            "android.telephony.ims.action.WFC_IMS_REGISTRATION_ERROR";
    public static final String EXTRA_WFC_REGISTRATION_FAILURE_TITLE =
            "android.telephony.ims.extra.WFC_REGISTRATION_FAILURE_TITLE";
    public static final String EXTRA_WFC_REGISTRATION_FAILURE_MESSAGE =
            "android.telephony.ims.extra.WFC_REGISTRATION_FAILURE_MESSAGE";

    private final Context mContext;

    ImsManager(Context context) {
        mContext = context;
    }

    public ImsRcsManager getImsRcsManager(int subscriptionId) {
        if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
            throw new IllegalArgumentException("Invalid subscription ID: " + subscriptionId);
        }
        return new ImsRcsManager(mContext, subscriptionId);
    }

    public ImsMmTelManager getImsMmTelManager(int subscriptionId) {
        if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
            throw new IllegalArgumentException("Invalid subscription ID: " + subscriptionId);
        }
        return new ImsMmTelManager(subscriptionId);
    }

    public ProvisioningManager getProvisioningManager(int subscriptionId) {
        if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
            throw new IllegalArgumentException("Invalid subscription ID: " + subscriptionId);
        }
        return new ProvisioningManager(subscriptionId);
    }
}
