// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.view.InputEvent;
import java.util.Objects;

/** Request payload for {@link AdSelectionManager#reportEvent}. */
public class ReportEventRequest {
    public static final int FLAG_REPORTING_DESTINATION_SELLER = 1 << 0;
    public static final int FLAG_REPORTING_DESTINATION_BUYER = 1 << 1;

    private final long mAdSelectionId;
    private final String mEventKey;
    private final String mEventData;
    private final int mReportingDestinations;
    private final InputEvent mInputEvent;

    private ReportEventRequest(
            long adSelectionId,
            String eventKey,
            String eventData,
            int reportingDestinations,
            InputEvent inputEvent) {
        if (adSelectionId <= 0L) {
            throw new IllegalArgumentException("adSelectionId must be positive");
        }
        this.mAdSelectionId = adSelectionId;
        this.mEventKey = Objects.requireNonNull(eventKey, "eventKey");
        this.mEventData = Objects.requireNonNull(eventData, "eventData");
        if (reportingDestinations <= 0
                || (reportingDestinations
                                & ~(FLAG_REPORTING_DESTINATION_SELLER
                                        | FLAG_REPORTING_DESTINATION_BUYER))
                        != 0) {
            throw new IllegalArgumentException("Invalid reportingDestinations");
        }
        this.mReportingDestinations = reportingDestinations;
        this.mInputEvent = inputEvent;
    }

    public long getAdSelectionId() {
        return mAdSelectionId;
    }

    public String getKey() {
        return mEventKey;
    }

    public InputEvent getInputEvent() {
        return mInputEvent;
    }

    public String getData() {
        return mEventData;
    }

    public int getReportingDestinations() {
        return mReportingDestinations;
    }

    public static final class Builder {
        private long mAdSelectionId;
        private String mEventKey;
        private String mEventData;
        private int mReportingDestinations;
        private InputEvent mInputEvent;

        public Builder(
                long adSelectionId,
                String eventKey,
                String eventData,
                int reportingDestinations) {
            this.mAdSelectionId = adSelectionId;
            this.mEventKey = Objects.requireNonNull(eventKey, "eventKey");
            this.mEventData = Objects.requireNonNull(eventData, "eventData");
            this.mReportingDestinations = reportingDestinations;
        }

        public Builder setAdSelectionId(long adSelectionId) {
            this.mAdSelectionId = adSelectionId;
            return this;
        }

        public Builder setKey(String eventKey) {
            this.mEventKey = Objects.requireNonNull(eventKey, "eventKey");
            return this;
        }

        public Builder setInputEvent(InputEvent inputEvent) {
            this.mInputEvent = inputEvent;
            return this;
        }

        public Builder setData(String eventData) {
            this.mEventData = Objects.requireNonNull(eventData, "eventData");
            return this;
        }

        public Builder setReportingDestinations(int reportingDestinations) {
            this.mReportingDestinations = reportingDestinations;
            return this;
        }

        public ReportEventRequest build() {
            return new ReportEventRequest(
                    mAdSelectionId,
                    mEventKey,
                    mEventData,
                    mReportingDestinations,
                    mInputEvent);
        }
    }
}
