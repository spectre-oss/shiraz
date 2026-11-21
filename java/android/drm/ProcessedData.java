// SPDX-License-Identifier: GPL-3.0-only
package android.drm;

/** Processed DRM transaction payload with optional account and subscription identifiers. */
@Deprecated
public class ProcessedData {
    private final byte[] mData;
    private String mAccountId = "_NO_USER";
    private String mSubscriptionId = "";

    ProcessedData(byte[] data, String accountId) {
        this.mData = data;
        if (accountId != null) {
            this.mAccountId = accountId;
        }
    }

    ProcessedData(byte[] data, String accountId, String subscriptionId) {
        this.mData = data;
        if (accountId != null) {
            this.mAccountId = accountId;
        }
        if (subscriptionId != null) {
            this.mSubscriptionId = subscriptionId;
        }
    }

    public byte[] getData() {
        return mData;
    }

    public String getAccountId() {
        return mAccountId;
    }

    public String getSubscriptionId() {
        return mSubscriptionId;
    }
}
