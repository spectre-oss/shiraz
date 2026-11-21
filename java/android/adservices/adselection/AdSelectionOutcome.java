// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.net.Uri;
import java.util.Objects;

/** Result of an ad selection auction containing the winning render URI and selection ID. */
public class AdSelectionOutcome {
    public static final AdSelectionOutcome NO_OUTCOME =
            new AdSelectionOutcome(0L, Uri.EMPTY);

    private final long mAdSelectionId;
    private final Uri mRenderUri;

    private AdSelectionOutcome(long adSelectionId, Uri renderUri) {
        this.mAdSelectionId = adSelectionId;
        this.mRenderUri = Objects.requireNonNull(renderUri, "renderUri");
    }

    public Uri getRenderUri() {
        return mRenderUri;
    }

    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    public boolean hasOutcome() {
        return !this.equals(NO_OUTCOME);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AdSelectionOutcome)) {
            return false;
        }
        AdSelectionOutcome that = (AdSelectionOutcome) o;
        return mAdSelectionId == that.mAdSelectionId
                && Objects.equals(mRenderUri, that.mRenderUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Long.valueOf(mAdSelectionId), mRenderUri);
    }

    public static final class Builder {
        private long mAdSelectionId;
        private Uri mRenderUri = Uri.EMPTY;

        public Builder() {
        }

        public Builder setAdSelectionId(long adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        public Builder setRenderUri(Uri renderUri) {
            this.mRenderUri = Objects.requireNonNull(renderUri, "renderUri");
            return this;
        }

        public AdSelectionOutcome build() {
            if (mAdSelectionId <= 0L) {
                throw new IllegalArgumentException("adSelectionId must be positive");
            }
            if (Uri.EMPTY.equals(mRenderUri)) {
                throw new IllegalArgumentException("renderUri must not be empty");
            }
            return new AdSelectionOutcome(mAdSelectionId, mRenderUri);
        }
    }
}
