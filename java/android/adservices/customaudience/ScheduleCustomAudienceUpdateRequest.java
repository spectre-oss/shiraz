// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.customaudience;

import android.net.Uri;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Request payload for {@link CustomAudienceManager#scheduleCustomAudienceUpdate}. */
public final class ScheduleCustomAudienceUpdateRequest {
    private final Uri mUpdateUri;
    private final Duration mMinDelay;
    private final List<PartialCustomAudience> mPartialCustomAudienceList;

    private ScheduleCustomAudienceUpdateRequest(
            Uri updateUri,
            Duration minDelay,
            List<PartialCustomAudience> partialCustomAudienceList) {
        this.mUpdateUri = Objects.requireNonNull(updateUri, "updateUri");
        this.mMinDelay = Objects.requireNonNull(minDelay, "minDelay");
        if (minDelay.isNegative()) {
            throw new IllegalArgumentException("minDelay must not be negative");
        }
        Objects.requireNonNull(partialCustomAudienceList, "partialCustomAudienceList");
        this.mPartialCustomAudienceList =
                Collections.unmodifiableList(
                        new ArrayList<PartialCustomAudience>(partialCustomAudienceList));
    }

    public Uri getUpdateUri() {
        return mUpdateUri;
    }

    public Duration getMinDelay() {
        return mMinDelay;
    }

    public List<PartialCustomAudience> getPartialCustomAudienceList() {
        return mPartialCustomAudienceList;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUpdateUri, mMinDelay, mPartialCustomAudienceList);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScheduleCustomAudienceUpdateRequest)) {
            return false;
        }
        ScheduleCustomAudienceUpdateRequest that = (ScheduleCustomAudienceUpdateRequest) o;
        return Objects.equals(mUpdateUri, that.mUpdateUri)
                && Objects.equals(mMinDelay, that.mMinDelay)
                && Objects.equals(mPartialCustomAudienceList, that.mPartialCustomAudienceList);
    }

    @Override
    public String toString() {
        return "ScheduleCustomAudienceUpdateRequest{mUpdateUri="
                + mUpdateUri
                + ", mMinDelay="
                + mMinDelay
                + ", mPartialCustomAudienceList="
                + mPartialCustomAudienceList
                + "}";
    }

    public static final class Builder {
        private Uri mUpdateUri;
        private Duration mMinDelay;
        private List<PartialCustomAudience> mPartialCustomAudienceList;

        public Builder(
                Uri updateUri,
                Duration minDelay,
                List<PartialCustomAudience> partialCustomAudienceList) {
            this.mUpdateUri = Objects.requireNonNull(updateUri, "updateUri");
            this.mMinDelay = Objects.requireNonNull(minDelay, "minDelay");
            Objects.requireNonNull(partialCustomAudienceList, "partialCustomAudienceList");
            this.mPartialCustomAudienceList =
                    new ArrayList<PartialCustomAudience>(partialCustomAudienceList);
        }

        public Builder setUpdateUri(Uri updateUri) {
            this.mUpdateUri = Objects.requireNonNull(updateUri, "updateUri");
            return this;
        }

        public Builder setMinDelay(Duration minDelay) {
            this.mMinDelay = Objects.requireNonNull(minDelay, "minDelay");
            return this;
        }

        public Builder setPartialCustomAudienceList(
                List<PartialCustomAudience> partialCustomAudienceList) {
            Objects.requireNonNull(partialCustomAudienceList, "partialCustomAudienceList");
            this.mPartialCustomAudienceList =
                    new ArrayList<PartialCustomAudience>(partialCustomAudienceList);
            return this;
        }

        public ScheduleCustomAudienceUpdateRequest build() {
            return new ScheduleCustomAudienceUpdateRequest(
                    mUpdateUri, mMinDelay, mPartialCustomAudienceList);
        }
    }
}
