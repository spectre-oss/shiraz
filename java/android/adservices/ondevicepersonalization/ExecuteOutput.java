// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Output returned from {@link IsolatedWorker#onExecute}. */
public final class ExecuteOutput {
    private final RequestLogRecord mRequestLogRecord;
    private final RenderingConfig mRenderingConfig;
    private final List<EventLogRecord> mEventLogRecords;
    private final byte[] mOutputData;

    private ExecuteOutput(
            RequestLogRecord requestLogRecord,
            RenderingConfig renderingConfig,
            List<EventLogRecord> eventLogRecords,
            byte[] outputData) {
        this.mRequestLogRecord = requestLogRecord;
        this.mRenderingConfig = renderingConfig;
        List<EventLogRecord> copy = new ArrayList<EventLogRecord>();
        if (eventLogRecords != null) {
            for (EventLogRecord r : eventLogRecords) {
                copy.add(Objects.requireNonNull(r, "eventLogRecord"));
            }
        }
        this.mEventLogRecords = Collections.unmodifiableList(copy);
        this.mOutputData = outputData == null ? null : outputData.clone();
    }

    public RequestLogRecord getRequestLogRecord() {
        return mRequestLogRecord;
    }

    public RenderingConfig getRenderingConfig() {
        return mRenderingConfig;
    }

    public List<EventLogRecord> getEventLogRecords() {
        return mEventLogRecords;
    }

    public byte[] getOutputData() {
        return mOutputData == null ? null : mOutputData.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExecuteOutput)) {
            return false;
        }
        ExecuteOutput that = (ExecuteOutput) o;
        return Objects.equals(mRequestLogRecord, that.mRequestLogRecord)
                && Objects.equals(mRenderingConfig, that.mRenderingConfig)
                && Objects.equals(mEventLogRecords, that.mEventLogRecords)
                && Arrays.equals(mOutputData, that.mOutputData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mRequestLogRecord,
                mRenderingConfig,
                mEventLogRecords,
                Integer.valueOf(Arrays.hashCode(mOutputData)));
    }

    public static final class Builder {
        private RequestLogRecord mRequestLogRecord;
        private RenderingConfig mRenderingConfig;
        private List<EventLogRecord> mEventLogRecords = new ArrayList<EventLogRecord>();
        private byte[] mOutputData;

        public Builder() {
        }

        public Builder setRequestLogRecord(RequestLogRecord requestLogRecord) {
            this.mRequestLogRecord = requestLogRecord;
            return this;
        }

        public Builder setRenderingConfig(RenderingConfig renderingConfig) {
            this.mRenderingConfig = renderingConfig;
            return this;
        }

        public Builder setEventLogRecords(List<EventLogRecord> eventLogRecords) {
            Objects.requireNonNull(eventLogRecords, "eventLogRecords");
            this.mEventLogRecords = new ArrayList<EventLogRecord>(eventLogRecords);
            return this;
        }

        public Builder addEventLogRecord(EventLogRecord eventLogRecord) {
            this.mEventLogRecords.add(Objects.requireNonNull(eventLogRecord, "eventLogRecord"));
            return this;
        }

        public Builder setOutputData(byte... outputData) {
            this.mOutputData = outputData == null ? null : outputData.clone();
            return this;
        }

        public ExecuteOutput build() {
            return new ExecuteOutput(
                    mRequestLogRecord, mRenderingConfig, mEventLogRecords, mOutputData);
        }
    }
}
