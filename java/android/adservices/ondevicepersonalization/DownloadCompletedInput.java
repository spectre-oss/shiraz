// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.Objects;

/** Input passed to {@link IsolatedWorker#onDownloadCompleted}. */
public final class DownloadCompletedInput {
    private final KeyValueStore mDownloadedContents;

    DownloadCompletedInput(KeyValueStore downloadedContents) {
        this.mDownloadedContents =
                Objects.requireNonNull(downloadedContents, "downloadedContents");
    }

    public KeyValueStore getDownloadedContents() {
        return mDownloadedContents;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DownloadCompletedInput)) {
            return false;
        }
        DownloadCompletedInput that = (DownloadCompletedInput) o;
        return Objects.equals(mDownloadedContents, that.mDownloadedContents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDownloadedContents);
    }
}
