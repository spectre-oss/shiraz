// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.net.Uri;
import java.util.Arrays;
import java.util.Objects;

/** Input passed to {@link IsolatedWorker#onWebTrigger}. */
public final class WebTriggerInput {
    private final Uri mDestinationUrl;
    private final String mAppPackageName;
    private final byte[] mData;

    WebTriggerInput(Uri destinationUrl, String appPackageName, byte[] data) {
        this.mDestinationUrl =
                Objects.requireNonNull(destinationUrl, "destinationUrl");
        this.mAppPackageName =
                Objects.requireNonNull(appPackageName, "appPackageName");
        Objects.requireNonNull(data, "data");
        this.mData = data.clone();
    }

    public Uri getDestinationUrl() {
        return mDestinationUrl;
    }

    public String getAppPackageName() {
        return mAppPackageName;
    }

    public byte[] getData() {
        return mData.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebTriggerInput)) {
            return false;
        }
        WebTriggerInput that = (WebTriggerInput) o;
        return Objects.equals(mDestinationUrl, that.mDestinationUrl)
                && Objects.equals(mAppPackageName, that.mAppPackageName)
                && Arrays.equals(mData, that.mData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mDestinationUrl,
                mAppPackageName,
                Integer.valueOf(Arrays.hashCode(mData)));
    }
}
