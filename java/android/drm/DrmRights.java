// SPDX-License-Identifier: GPL-3.0-only
package android.drm;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/** License rights bytes and account/subscription metadata for protected DRM content. */
@Deprecated
public class DrmRights {
    private byte[] mData;
    private String mMimeType;
    private String mAccountId;
    private String mSubscriptionId;

    public DrmRights(String rightsFilePath, String mimeType) {
        File file = new File(rightsFilePath);
        instantiate(file, mimeType);
    }

    public DrmRights(String rightsFilePath, String mimeType, String accountId) {
        this(rightsFilePath, mimeType);
        this.mAccountId = accountId;
    }

    public DrmRights(
            String rightsFilePath, String mimeType, String accountId, String subscriptionId) {
        this(rightsFilePath, mimeType);
        this.mAccountId = accountId;
        this.mSubscriptionId = subscriptionId;
    }

    public DrmRights(File rightsFile, String mimeType) {
        instantiate(rightsFile, mimeType);
    }

    private void instantiate(File rightsFile, String mimeType) {
        if (rightsFile == null) {
            throw new IllegalArgumentException("rightsFile is null");
        }
        try {
            mData = DrmUtils.readBytes(rightsFile);
        } catch (IOException e) {
            mData = null;
        }
        mMimeType = mimeType;
        if (!isValid()) {
            throw new IllegalArgumentException(
                    "mimeType: " + mMimeType + ", data: " + Arrays.toString(mData));
        }
    }

    public DrmRights(ProcessedData data, String mimeType) {
        if (data == null) {
            throw new IllegalArgumentException("data is null");
        }
        mData = data.getData();
        mAccountId = data.getAccountId();
        mSubscriptionId = data.getSubscriptionId();
        mMimeType = mimeType;
        if (!isValid()) {
            throw new IllegalArgumentException(
                    "mimeType: " + mMimeType + ", data: " + Arrays.toString(mData));
        }
    }

    public byte[] getData() {
        return mData;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public String getAccountId() {
        return mAccountId;
    }

    public String getSubscriptionId() {
        return mSubscriptionId;
    }

    boolean isValid() {
        return mMimeType != null && !mMimeType.isEmpty() && mData != null && mData.length > 0;
    }
}
