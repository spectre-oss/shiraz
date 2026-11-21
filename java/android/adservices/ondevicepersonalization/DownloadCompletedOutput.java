// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Output returned from {@link IsolatedWorker#onDownloadCompleted}. */
public final class DownloadCompletedOutput {
    private final List<String> mRetainedKeys;

    private DownloadCompletedOutput(List<String> retainedKeys) {
        Objects.requireNonNull(retainedKeys, "retainedKeys");
        List<String> copy = new ArrayList<String>(retainedKeys.size());
        for (String k : retainedKeys) {
            copy.add(Objects.requireNonNull(k, "retainedKey"));
        }
        this.mRetainedKeys = Collections.unmodifiableList(copy);
    }

    public List<String> getRetainedKeys() {
        return mRetainedKeys;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DownloadCompletedOutput)) {
            return false;
        }
        DownloadCompletedOutput that = (DownloadCompletedOutput) o;
        return Objects.equals(mRetainedKeys, that.mRetainedKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRetainedKeys);
    }

    public static final class Builder {
        private List<String> mRetainedKeys = new ArrayList<String>();

        public Builder() {
        }

        public Builder setRetainedKeys(List<String> retainedKeys) {
            Objects.requireNonNull(retainedKeys, "retainedKeys");
            this.mRetainedKeys = new ArrayList<String>(retainedKeys);
            return this;
        }

        public Builder addRetainedKey(String value) {
            this.mRetainedKeys.add(Objects.requireNonNull(value, "value"));
            return this;
        }

        public DownloadCompletedOutput build() {
            return new DownloadCompletedOutput(mRetainedKeys);
        }
    }
}
