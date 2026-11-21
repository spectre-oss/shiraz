// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Output returned from {@link IsolatedWorker#onWebTrigger}. */
public final class WebTriggerOutput {
    private final RequestLogRecord mRequestLogRecord;
    private final List<EventLogRecord> mEventLogRecords;

    private WebTriggerOutput(
            RequestLogRecord requestLogRecord, List<EventLogRecord> eventLogRecords) {
        this.mRequestLogRecord = requestLogRecord;
        List<EventLogRecord> copy = new ArrayList<EventLogRecord>();
        if (eventLogRecords != null) {
            for (EventLogRecord r : eventLogRecords) {
                copy.add(Objects.requireNonNull(r, "eventLogRecord"));
            }
        }
        this.mEventLogRecords = Collections.unmodifiableList(copy);
    }

    public RequestLogRecord getRequestLogRecord() {
        return mRequestLogRecord;
    }

    public List<EventLogRecord> getEventLogRecords() {
        return mEventLogRecords;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebTriggerOutput)) {
            return false;
        }
        WebTriggerOutput that = (WebTriggerOutput) o;
        return Objects.equals(mRequestLogRecord, that.mRequestLogRecord)
                && Objects.equals(mEventLogRecords, that.mEventLogRecords);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRequestLogRecord, mEventLogRecords);
    }

    public static final class Builder {
        private RequestLogRecord mRequestLogRecord;
        private List<EventLogRecord> mEventLogRecords = new ArrayList<EventLogRecord>();

        public Builder() {
        }

        public Builder setRequestLogRecord(RequestLogRecord value) {
            this.mRequestLogRecord = value;
            return this;
        }

        public Builder setEventLogRecords(List<EventLogRecord> value) {
            Objects.requireNonNull(value, "eventLogRecords");
            this.mEventLogRecords = new ArrayList<EventLogRecord>(value);
            return this;
        }

        public Builder addEventLogRecord(EventLogRecord value) {
            this.mEventLogRecords.add(Objects.requireNonNull(value, "eventLogRecord"));
            return this;
        }

        public WebTriggerOutput build() {
            return new WebTriggerOutput(mRequestLogRecord, mEventLogRecords);
        }
    }
}
