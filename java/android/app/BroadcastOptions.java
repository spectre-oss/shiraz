// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Bundle;
import java.util.Objects;

public class BroadcastOptions {
    public static final int DEFERRAL_POLICY_DEFAULT = 0;
    public static final int DEFERRAL_POLICY_NONE = 1;
    public static final int DEFERRAL_POLICY_UNTIL_ACTIVE = 2;

    public static final int DELIVERY_GROUP_POLICY_ALL = 0;
    public static final int DELIVERY_GROUP_POLICY_MOST_RECENT = 1;

    private static final String KEY_SHARE_IDENTITY = "android:broadcast.shareIdentity";
    private static final String KEY_DEFERRAL_POLICY = "android:broadcast.deferralPolicy";
    private static final String KEY_DELIVERY_GROUP_POLICY = "android:broadcast.deliveryGroupPolicy";
    private static final String KEY_DELIVERY_GROUP_NAMESPACE = "android:broadcast.deliveryGroupMatchingNamespace";
    private static final String KEY_DELIVERY_GROUP_KEY = "android:broadcast.deliveryGroupMatchingKey";

    private boolean mShareIdentity;
    private int mDeferralPolicy = DEFERRAL_POLICY_DEFAULT;
    private int mDeliveryGroupPolicy = DELIVERY_GROUP_POLICY_ALL;
    private String mDeliveryGroupMatchingNamespaceFragment;
    private String mDeliveryGroupMatchingKeyFragment;

    private BroadcastOptions() {
    }

    public static BroadcastOptions makeBasic() {
        return new BroadcastOptions();
    }

    public BroadcastOptions setShareIdentityEnabled(boolean shareIdentityEnabled) {
        mShareIdentity = shareIdentityEnabled;
        return this;
    }

    public boolean isShareIdentityEnabled() {
        return mShareIdentity;
    }

    public BroadcastOptions setDeferralPolicy(int deferralPolicy) {
        mDeferralPolicy = deferralPolicy;
        return this;
    }

    public int getDeferralPolicy() {
        return mDeferralPolicy;
    }

    public void clearDeferralPolicy() {
        mDeferralPolicy = DEFERRAL_POLICY_DEFAULT;
    }

    public BroadcastOptions setDeliveryGroupPolicy(int policy) {
        mDeliveryGroupPolicy = policy;
        return this;
    }

    public int getDeliveryGroupPolicy() {
        return mDeliveryGroupPolicy;
    }

    public void clearDeliveryGroupPolicy() {
        mDeliveryGroupPolicy = DELIVERY_GROUP_POLICY_ALL;
    }

    public BroadcastOptions setDeliveryGroupMatchingKey(String namespace, String key) {
        mDeliveryGroupMatchingNamespaceFragment = Objects.requireNonNull(namespace, "namespace");
        mDeliveryGroupMatchingKeyFragment = Objects.requireNonNull(key, "key");
        return this;
    }

    public String getDeliveryGroupMatchingKey() {
        if (mDeliveryGroupMatchingNamespaceFragment == null || mDeliveryGroupMatchingKeyFragment == null) {
            return null;
        }
        return mDeliveryGroupMatchingNamespaceFragment + ":" + mDeliveryGroupMatchingKeyFragment;
    }

    public void clearDeliveryGroupMatchingKey() {
        mDeliveryGroupMatchingNamespaceFragment = null;
        mDeliveryGroupMatchingKeyFragment = null;
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        if (mShareIdentity) {
            b.putBoolean(KEY_SHARE_IDENTITY, true);
        }
        if (mDeferralPolicy != DEFERRAL_POLICY_DEFAULT) {
            b.putInt(KEY_DEFERRAL_POLICY, mDeferralPolicy);
        }
        if (mDeliveryGroupPolicy != DELIVERY_GROUP_POLICY_ALL) {
            b.putInt(KEY_DELIVERY_GROUP_POLICY, mDeliveryGroupPolicy);
        }
        if (mDeliveryGroupMatchingNamespaceFragment != null && mDeliveryGroupMatchingKeyFragment != null) {
            b.putString(KEY_DELIVERY_GROUP_NAMESPACE, mDeliveryGroupMatchingNamespaceFragment);
            b.putString(KEY_DELIVERY_GROUP_KEY, mDeliveryGroupMatchingKeyFragment);
        }
        return b;
    }

    public static BroadcastOptions fromBundle(Bundle options) {
        BroadcastOptions bo = new BroadcastOptions();
        if (options != null) {
            bo.mShareIdentity = options.getBoolean(KEY_SHARE_IDENTITY, false);
            bo.mDeferralPolicy = options.getInt(KEY_DEFERRAL_POLICY, DEFERRAL_POLICY_DEFAULT);
            bo.mDeliveryGroupPolicy = options.getInt(KEY_DELIVERY_GROUP_POLICY, DELIVERY_GROUP_POLICY_ALL);
            bo.mDeliveryGroupMatchingNamespaceFragment = options.getString(KEY_DELIVERY_GROUP_NAMESPACE);
            bo.mDeliveryGroupMatchingKeyFragment = options.getString(KEY_DELIVERY_GROUP_KEY);
        }
        return bo;
    }
}
