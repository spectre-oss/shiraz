// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.signals;

import android.net.Uri;
import java.util.Objects;

/** Request payload for {@link ProtectedSignalsManager#updateSignals}. */
public final class UpdateSignalsRequest {
    private final Uri mUpdateUri;

    private UpdateSignalsRequest(Uri updateUri) {
        this.mUpdateUri = Objects.requireNonNull(updateUri, "updateUri");
    }

    public Uri getUpdateUri() {
        return mUpdateUri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UpdateSignalsRequest)) {
            return false;
        }
        UpdateSignalsRequest that = (UpdateSignalsRequest) o;
        return Objects.equals(mUpdateUri, that.mUpdateUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUpdateUri);
    }

    @Override
    public String toString() {
        return "UpdateSignalsRequest{mUpdateUri=" + mUpdateUri + "}";
    }

    public static final class Builder {
        private Uri mUpdateUri;

        public Builder(Uri updateUri) {
            this.mUpdateUri = Objects.requireNonNull(updateUri, "updateUri");
        }

        public Builder setUpdateUri(Uri updateUri) {
            this.mUpdateUri = Objects.requireNonNull(updateUri, "updateUri");
            return this;
        }

        public UpdateSignalsRequest build() {
            return new UpdateSignalsRequest(mUpdateUri);
        }
    }
}
