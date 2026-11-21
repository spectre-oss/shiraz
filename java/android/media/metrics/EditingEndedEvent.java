// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EditingEndedEvent extends Event implements Parcelable {
    public static final int FINAL_STATE_SUCCEEDED = 1;
    public static final int FINAL_STATE_CANCELED = 2;
    public static final int FINAL_STATE_ERROR = 3;

    public static final int PROGRESS_PERCENT_UNKNOWN = -1;
    public static final int TIME_SINCE_CREATED_UNKNOWN = -1;

    public static final int ERROR_CODE_NONE = 1;
    public static final int ERROR_CODE_FAILED_RUNTIME_CHECK = 2;
    public static final int ERROR_CODE_IO_UNSPECIFIED = 3;
    public static final int ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 4;
    public static final int ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 5;
    public static final int ERROR_CODE_IO_BAD_HTTP_STATUS = 6;
    public static final int ERROR_CODE_IO_FILE_NOT_FOUND = 7;
    public static final int ERROR_CODE_IO_NO_PERMISSION = 8;
    public static final int ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED = 9;
    public static final int ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE = 10;
    public static final int ERROR_CODE_DECODER_INIT_FAILED = 11;
    public static final int ERROR_CODE_DECODING_FAILED = 12;
    public static final int ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 13;
    public static final int ERROR_CODE_ENCODER_INIT_FAILED = 14;
    public static final int ERROR_CODE_ENCODING_FAILED = 15;
    public static final int ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED = 16;
    public static final int ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED = 17;
    public static final int ERROR_CODE_AUDIO_PROCESSING_FAILED = 18;
    public static final int ERROR_CODE_MUXING_FAILED = 19;

    public static final long OPERATION_TYPE_VIDEO_TRANSCODE = 1L;
    public static final long OPERATION_TYPE_AUDIO_TRANSCODE = 2L;
    public static final long OPERATION_TYPE_VIDEO_EDIT = 4L;
    public static final long OPERATION_TYPE_AUDIO_EDIT = 8L;
    public static final long OPERATION_TYPE_VIDEO_TRANSMUX = 16L;
    public static final long OPERATION_TYPE_AUDIO_TRANSMUX = 32L;
    public static final long OPERATION_TYPE_PAUSED = 64L;
    public static final long OPERATION_TYPE_RESUMED = 128L;

    public static final class Builder {
        private final int mFinalState;
        private float mFinalProgressPercent = PROGRESS_PERCENT_UNKNOWN;
        private int mErrorCode = ERROR_CODE_NONE;
        private long mTimeSinceCreatedMillis = TIME_SINCE_CREATED_UNKNOWN;
        private String mExporterName;
        private String mMuxerName;
        private final ArrayList<MediaItemInfo> mInputMediaItemInfos =
                new ArrayList<MediaItemInfo>();
        private MediaItemInfo mOutputMediaItemInfo;
        private long mOperationTypes;
        private Bundle mMetricsBundle = new Bundle();

        public Builder(int finalState) {
            mFinalState = finalState;
        }

        public Builder setFinalProgressPercent(float finalProgressPercent) {
            mFinalProgressPercent = finalProgressPercent;
            return this;
        }

        public Builder setTimeSinceCreatedMillis(long timeSinceCreatedMillis) {
            mTimeSinceCreatedMillis = timeSinceCreatedMillis;
            return this;
        }

        public Builder setExporterName(String exporterName) {
            mExporterName = Objects.requireNonNull(exporterName);
            return this;
        }

        public Builder setMuxerName(String muxerName) {
            mMuxerName = Objects.requireNonNull(muxerName);
            return this;
        }

        public Builder setErrorCode(int value) {
            mErrorCode = value;
            return this;
        }

        public Builder addInputMediaItemInfo(MediaItemInfo mediaItemInfo) {
            mInputMediaItemInfos.add(Objects.requireNonNull(mediaItemInfo));
            return this;
        }

        public Builder setOutputMediaItemInfo(MediaItemInfo mediaItemInfo) {
            mOutputMediaItemInfo = Objects.requireNonNull(mediaItemInfo);
            return this;
        }

        public Builder addOperationType(long operationType) {
            mOperationTypes |= operationType;
            return this;
        }

        public Builder setMetricsBundle(Bundle metricsBundle) {
            mMetricsBundle = Objects.requireNonNull(metricsBundle);
            return this;
        }

        public EditingEndedEvent build() {
            return new EditingEndedEvent(
                    mFinalState,
                    mFinalProgressPercent,
                    mErrorCode,
                    mTimeSinceCreatedMillis,
                    mExporterName,
                    mMuxerName,
                    mInputMediaItemInfos,
                    mOutputMediaItemInfo,
                    mOperationTypes,
                    mMetricsBundle);
        }
    }

    private final int mFinalState;
    private final float mFinalProgressPercent;
    private final int mErrorCode;
    private final String mExporterName;
    private final String mMuxerName;
    private final ArrayList<MediaItemInfo> mInputMediaItemInfos;
    private final MediaItemInfo mOutputMediaItemInfo;
    private final long mOperationTypes;

    EditingEndedEvent(
            int finalState,
            float finalProgressPercent,
            int errorCode,
            long timeSinceCreatedMillis,
            String exporterName,
            String muxerName,
            ArrayList<MediaItemInfo> inputMediaItemInfos,
            MediaItemInfo outputMediaItemInfo,
            long operationTypes,
            Bundle extras) {
        super(timeSinceCreatedMillis, extras);
        mFinalState = finalState;
        mFinalProgressPercent = finalProgressPercent;
        mErrorCode = errorCode;
        mExporterName = exporterName;
        mMuxerName = muxerName;
        mInputMediaItemInfos = new ArrayList<MediaItemInfo>(
                inputMediaItemInfos != null
                        ? inputMediaItemInfos : new ArrayList<MediaItemInfo>());
        mOutputMediaItemInfo = outputMediaItemInfo;
        mOperationTypes = operationTypes;
    }

    public int getFinalState() {
        return mFinalState;
    }

    public float getFinalProgressPercent() {
        return mFinalProgressPercent;
    }

    public int getErrorCode() {
        return mErrorCode;
    }

    @Override
    public long getTimeSinceCreatedMillis() {
        return mTimeSinceCreatedMillis;
    }

    public String getExporterName() {
        return mExporterName;
    }

    public String getMuxerName() {
        return mMuxerName;
    }

    public List<MediaItemInfo> getInputMediaItemInfos() {
        return new ArrayList<MediaItemInfo>(mInputMediaItemInfos);
    }

    public MediaItemInfo getOutputMediaItemInfo() {
        return mOutputMediaItemInfo;
    }

    public long getOperationTypes() {
        return mOperationTypes;
    }

    @Override
    public Bundle getMetricsBundle() {
        return super.getMetricsBundle();
    }

    @Override
    public String toString() {
        return "EditingEndedEvent { finalState = " + mFinalState
                + ", finalProgressPercent = " + mFinalProgressPercent
                + ", errorCode = " + mErrorCode
                + ", timeSinceCreatedMillis = " + mTimeSinceCreatedMillis
                + ", exporterName = " + mExporterName
                + ", muxerName = " + mMuxerName
                + ", inputMediaItemInfos = " + mInputMediaItemInfos
                + ", outputMediaItemInfo = " + mOutputMediaItemInfo
                + ", operationTypes = " + mOperationTypes
                + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EditingEndedEvent)) return false;
        EditingEndedEvent that = (EditingEndedEvent) o;
        return mFinalState == that.mFinalState
                && Float.compare(that.mFinalProgressPercent, mFinalProgressPercent) == 0
                && mErrorCode == that.mErrorCode
                && mTimeSinceCreatedMillis == that.mTimeSinceCreatedMillis
                && Objects.equals(mExporterName, that.mExporterName)
                && Objects.equals(mMuxerName, that.mMuxerName)
                && Objects.equals(mInputMediaItemInfos, that.mInputMediaItemInfos)
                && Objects.equals(mOutputMediaItemInfo, that.mOutputMediaItemInfo)
                && mOperationTypes == that.mOperationTypes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mFinalState, mFinalProgressPercent, mErrorCode, mTimeSinceCreatedMillis,
                mExporterName, mMuxerName, mInputMediaItemInfos, mOutputMediaItemInfo,
                mOperationTypes);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mFinalState);
        dest.writeFloat(mFinalProgressPercent);
        dest.writeInt(mErrorCode);
        dest.writeLong(mTimeSinceCreatedMillis);
        dest.writeString(mExporterName);
        dest.writeString(mMuxerName);
        dest.writeInt(mInputMediaItemInfos.size());
        for (int i = 0; i < mInputMediaItemInfos.size(); i++) {
            mInputMediaItemInfos.get(i).writeToParcel(dest, flags);
        }
        if (mOutputMediaItemInfo != null) {
            dest.writeInt(1);
            mOutputMediaItemInfo.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeLong(mOperationTypes);
        dest.writeBundle(mMetricsBundle);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<EditingEndedEvent> CREATOR =
            new Parcelable.Creator<EditingEndedEvent>() {
                @Override
                public EditingEndedEvent createFromParcel(Parcel in) {
                    int finalState = in.readInt();
                    float finalProgressPercent = in.readFloat();
                    int errorCode = in.readInt();
                    long timeSinceCreatedMillis = in.readLong();
                    String exporterName = in.readString();
                    String muxerName = in.readString();
                    int inputCount = Math.max(0, in.readInt());
                    ArrayList<MediaItemInfo> inputs = new ArrayList<MediaItemInfo>(inputCount);
                    for (int i = 0; i < inputCount; i++) {
                        inputs.add(MediaItemInfo.CREATOR.createFromParcel(in));
                    }
                    MediaItemInfo output = in.readInt() != 0
                            ? MediaItemInfo.CREATOR.createFromParcel(in)
                            : null;
                    long operationTypes = in.readLong();
                    Bundle extras = in.readBundle();
                    return new EditingEndedEvent(
                            finalState, finalProgressPercent, errorCode,
                            timeSinceCreatedMillis, exporterName, muxerName,
                            inputs, output, operationTypes, extras);
                }

                @Override
                public EditingEndedEvent[] newArray(int size) {
                    return new EditingEndedEvent[size];
                }
            };
}
