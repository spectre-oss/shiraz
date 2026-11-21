// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.Objects;

/** Output returned from {@link IsolatedWorker#onEvent}. */
public final class EventOutput {
    private final EventLogRecord mEventLogRecord;

    private EventOutput(EventLogRecord eventLogRecord) {
        this.mEventLogRecord = eventLogRecord;
    }

    public EventLogRecord getEventLogRecord() {
        return mEventLogRecord;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EventOutput)) {
            return false;
        }
        EventOutput that = (EventOutput) o;
        return Objects.equals(mEventLogRecord, that.mEventLogRecord);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEventLogRecord);
    }

    public static final class Builder {
        private EventLogRecord mEventLogRecord;

        public Builder() {
        }

        public Builder setEventLogRecord(EventLogRecord eventLogRecord) {
            this.mEventLogRecord = eventLogRecord;
            return this;
        }

        public EventOutput build() {
            return new EventOutput(mEventLogRecord);
        }
    }
}
