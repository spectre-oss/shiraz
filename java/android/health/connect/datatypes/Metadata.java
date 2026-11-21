// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import java.time.Instant;
import java.util.Objects;

public final class Metadata {
    public static final int RECORDING_METHOD_UNKNOWN = 0;
    public static final int RECORDING_METHOD_ACTIVELY_RECORDED = 1;
    public static final int RECORDING_METHOD_AUTOMATICALLY_RECORDED = 2;
    public static final int RECORDING_METHOD_MANUAL_ENTRY = 3;

    private final String mId;
    private final DataOrigin mDataOrigin;
    private final Instant mLastModifiedTime;
    private final String mClientRecordId;
    private final long mClientRecordVersion;
    private final int mRecordingMethod;
    private final Device mDevice;

    Metadata(String id, DataOrigin dataOrigin, Instant lastModifiedTime,
            String clientRecordId, long clientRecordVersion, int recordingMethod, Device device) {
        mId = id != null ? id : "";
        mDataOrigin = dataOrigin != null ? dataOrigin : new DataOrigin.Builder().build();
        mLastModifiedTime = lastModifiedTime != null ? lastModifiedTime : Instant.EPOCH;
        mClientRecordId = clientRecordId;
        mClientRecordVersion = clientRecordVersion;
        mRecordingMethod = recordingMethod;
        mDevice = device != null ? device : new Device.Builder().build();
    }

    public String getClientRecordId() {
        return mClientRecordId;
    }

    public long getClientRecordVersion() {
        return mClientRecordVersion;
    }

    public DataOrigin getDataOrigin() {
        return mDataOrigin;
    }

    public String getId() {
        return mId;
    }

    public int getRecordingMethod() {
        return mRecordingMethod;
    }

    public Instant getLastModifiedTime() {
        return mLastModifiedTime;
    }

    public Device getDevice() {
        return mDevice;
    }

    public Metadata shirazWithAssignedId(String id, Instant modifiedTime) {
        return new Metadata(id, mDataOrigin, modifiedTime,
                mClientRecordId, mClientRecordVersion, mRecordingMethod, mDevice);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Metadata)) return false;
        Metadata that = (Metadata) object;
        return mClientRecordVersion == that.mClientRecordVersion
                && mRecordingMethod == that.mRecordingMethod
                && Objects.equals(mId, that.mId)
                && Objects.equals(mDataOrigin, that.mDataOrigin)
                && Objects.equals(mLastModifiedTime, that.mLastModifiedTime)
                && Objects.equals(mClientRecordId, that.mClientRecordId)
                && Objects.equals(mDevice, that.mDevice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mDataOrigin, mLastModifiedTime,
                mClientRecordId, mClientRecordVersion, mRecordingMethod, mDevice);
    }

    public static final class Builder {
        private String mId = "";
        private DataOrigin mDataOrigin = new DataOrigin.Builder().build();
        private Instant mLastModifiedTime = Instant.EPOCH;
        private String mClientRecordId;
        private long mClientRecordVersion;
        private int mRecordingMethod = RECORDING_METHOD_UNKNOWN;
        private Device mDevice = new Device.Builder().build();

        public Builder() {
        }

        public Builder setDevice(Device device) {
            Objects.requireNonNull(device, "device must not be null");
            mDevice = device;
            return this;
        }

        public Builder setDataOrigin(DataOrigin dataOrigin) {
            Objects.requireNonNull(dataOrigin, "dataOrigin must not be null");
            mDataOrigin = dataOrigin;
            return this;
        }

        public Builder setId(String id) {
            Objects.requireNonNull(id, "id must not be null");
            mId = id;
            return this;
        }

        public Builder setLastModifiedTime(Instant lastModifiedTime) {
            Objects.requireNonNull(lastModifiedTime, "lastModifiedTime must not be null");
            mLastModifiedTime = lastModifiedTime;
            return this;
        }

        public Builder setClientRecordId(String clientRecordId) {
            mClientRecordId = clientRecordId;
            return this;
        }

        public Builder setClientRecordVersion(long clientRecordVersion) {
            mClientRecordVersion = clientRecordVersion;
            return this;
        }

        public Builder setRecordingMethod(int recordingMethod) {
            mRecordingMethod = recordingMethod;
            return this;
        }

        public Metadata build() {
            return new Metadata(mId, mDataOrigin, mLastModifiedTime,
                    mClientRecordId, mClientRecordVersion, mRecordingMethod, mDevice);
        }
    }
}
