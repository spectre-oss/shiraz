// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MediaItemInfo implements Parcelable {
    public static final int SOURCE_TYPE_UNSPECIFIED = 0;
    public static final int SOURCE_TYPE_GALLERY = 1;
    public static final int SOURCE_TYPE_CAMERA = 2;
    public static final int SOURCE_TYPE_EDITING_SESSION = 3;
    public static final int SOURCE_TYPE_LOCAL_FILE = 4;
    public static final int SOURCE_TYPE_REMOTE_FILE = 5;
    public static final int SOURCE_TYPE_REMOTE_LIVE_STREAM = 6;
    public static final int SOURCE_TYPE_GENERATED = 7;

    public static final long DATA_TYPE_IMAGE = 1L;
    public static final long DATA_TYPE_VIDEO = 2L;
    public static final long DATA_TYPE_AUDIO = 4L;
    public static final long DATA_TYPE_METADATA = 8L;
    public static final long DATA_TYPE_DEPTH = 16L;
    public static final long DATA_TYPE_GAIN_MAP = 32L;
    public static final long DATA_TYPE_HIGH_FRAME_RATE = 64L;
    public static final long DATA_TYPE_SPEED_SETTING_CUE_POINTS = 128L;
    public static final long DATA_TYPE_GAPLESS = 256L;
    public static final long DATA_TYPE_SPATIAL_AUDIO = 512L;
    public static final long DATA_TYPE_HIGH_DYNAMIC_RANGE_VIDEO = 1024L;

    public static final int VALUE_UNSPECIFIED = -1;

    public static final class Builder {
        private int mSourceType = SOURCE_TYPE_UNSPECIFIED;
        private long mDataTypes = 0L;
        private long mDurationMillis = VALUE_UNSPECIFIED;
        private long mClipDurationMillis = VALUE_UNSPECIFIED;
        private String mContainerMimeType;
        private final List<String> mSampleMimeTypes = new ArrayList<String>();
        private final List<String> mCodecNames = new ArrayList<String>();
        private int mAudioSampleRateHz = VALUE_UNSPECIFIED;
        private int mAudioChannelCount = VALUE_UNSPECIFIED;
        private long mAudioSampleCount = VALUE_UNSPECIFIED;
        private Size mVideoSize = new Size(VALUE_UNSPECIFIED, VALUE_UNSPECIFIED);
        private int mVideoDataSpace = 0;
        private float mVideoFrameRate = VALUE_UNSPECIFIED;
        private long mVideoSampleCount = VALUE_UNSPECIFIED;

        public Builder() {
        }

        public Builder setSourceType(int sourceType) {
            mSourceType = sourceType;
            return this;
        }

        public Builder addDataType(long dataType) {
            mDataTypes |= dataType;
            return this;
        }

        public Builder setDurationMillis(long durationMillis) {
            mDurationMillis = durationMillis;
            return this;
        }

        public Builder setClipDurationMillis(long clipDurationMillis) {
            mClipDurationMillis = clipDurationMillis;
            return this;
        }

        public Builder setContainerMimeType(String containerMimeType) {
            mContainerMimeType = Objects.requireNonNull(containerMimeType);
            return this;
        }

        public Builder addSampleMimeType(String mimeType) {
            mSampleMimeTypes.add(Objects.requireNonNull(mimeType));
            return this;
        }

        public Builder addCodecName(String codecName) {
            mCodecNames.add(Objects.requireNonNull(codecName));
            return this;
        }

        public Builder setAudioSampleRateHz(int audioSampleRateHz) {
            mAudioSampleRateHz = audioSampleRateHz;
            return this;
        }

        public Builder setAudioChannelCount(int audioChannelCount) {
            mAudioChannelCount = audioChannelCount;
            return this;
        }

        public Builder setAudioSampleCount(long audioSampleCount) {
            mAudioSampleCount = audioSampleCount;
            return this;
        }

        public Builder setVideoSize(Size videoSize) {
            mVideoSize = Objects.requireNonNull(videoSize);
            return this;
        }

        public Builder setVideoDataSpace(int videoDataSpace) {
            mVideoDataSpace = videoDataSpace;
            return this;
        }

        public Builder setVideoFrameRate(float videoFrameRate) {
            mVideoFrameRate = videoFrameRate;
            return this;
        }

        public Builder setVideoSampleCount(long videoSampleCount) {
            mVideoSampleCount = videoSampleCount;
            return this;
        }

        public MediaItemInfo build() {
            return new MediaItemInfo(
                    mSourceType,
                    mDataTypes,
                    mDurationMillis,
                    mClipDurationMillis,
                    mContainerMimeType,
                    mSampleMimeTypes,
                    mCodecNames,
                    mAudioSampleRateHz,
                    mAudioChannelCount,
                    mAudioSampleCount,
                    mVideoSize,
                    mVideoDataSpace,
                    mVideoFrameRate,
                    mVideoSampleCount);
        }
    }

    private final int mSourceType;
    private final long mDataTypes;
    private final long mDurationMillis;
    private final long mClipDurationMillis;
    private final String mContainerMimeType;
    private final List<String> mSampleMimeTypes;
    private final List<String> mCodecNames;
    private final int mAudioSampleRateHz;
    private final int mAudioChannelCount;
    private final long mAudioSampleCount;
    private final Size mVideoSize;
    private final int mVideoDataSpace;
    private final float mVideoFrameRate;
    private final long mVideoSampleCount;

    MediaItemInfo(
            int sourceType,
            long dataTypes,
            long durationMillis,
            long clipDurationMillis,
            String containerMimeType,
            List<String> sampleMimeTypes,
            List<String> codecNames,
            int audioSampleRateHz,
            int audioChannelCount,
            long audioSampleCount,
            Size videoSize,
            int videoDataSpace,
            float videoFrameRate,
            long videoSampleCount) {
        mSourceType = sourceType;
        mDataTypes = dataTypes;
        mDurationMillis = durationMillis;
        mClipDurationMillis = clipDurationMillis;
        mContainerMimeType = containerMimeType;
        mSampleMimeTypes = Collections.unmodifiableList(
                new ArrayList<String>(sampleMimeTypes != null
                        ? sampleMimeTypes : Collections.<String>emptyList()));
        mCodecNames = Collections.unmodifiableList(
                new ArrayList<String>(codecNames != null
                        ? codecNames : Collections.<String>emptyList()));
        mAudioSampleRateHz = audioSampleRateHz;
        mAudioChannelCount = audioChannelCount;
        mAudioSampleCount = audioSampleCount;
        mVideoSize = videoSize != null ? videoSize : new Size(VALUE_UNSPECIFIED, VALUE_UNSPECIFIED);
        mVideoDataSpace = videoDataSpace;
        mVideoFrameRate = videoFrameRate;
        mVideoSampleCount = videoSampleCount;
    }

    public int getSourceType() {
        return mSourceType;
    }

    public long getDataTypes() {
        return mDataTypes;
    }

    public long getDurationMillis() {
        return mDurationMillis;
    }

    public long getClipDurationMillis() {
        return mClipDurationMillis;
    }

    public String getContainerMimeType() {
        return mContainerMimeType;
    }

    public List<String> getSampleMimeTypes() {
        return new ArrayList<String>(mSampleMimeTypes);
    }

    public List<String> getCodecNames() {
        return new ArrayList<String>(mCodecNames);
    }

    public int getAudioSampleRateHz() {
        return mAudioSampleRateHz;
    }

    public int getAudioChannelCount() {
        return mAudioChannelCount;
    }

    public long getAudioSampleCount() {
        return mAudioSampleCount;
    }

    public Size getVideoSize() {
        return mVideoSize;
    }

    public int getVideoDataSpace() {
        return mVideoDataSpace;
    }

    public float getVideoFrameRate() {
        return mVideoFrameRate;
    }

    public long getVideoSampleCount() {
        return mVideoSampleCount;
    }

    @Override
    public String toString() {
        return "MediaItemInfo { sourceType = " + mSourceType
                + ", dataTypes = " + mDataTypes
                + ", durationMillis = " + mDurationMillis
                + ", clipDurationMillis = " + mClipDurationMillis
                + ", containerMimeType = " + mContainerMimeType
                + ", sampleMimeTypes = " + mSampleMimeTypes
                + ", codecNames = " + mCodecNames
                + ", audioSampleRateHz = " + mAudioSampleRateHz
                + ", audioChannelCount = " + mAudioChannelCount
                + ", audioSampleCount = " + mAudioSampleCount
                + ", videoSize = " + mVideoSize
                + ", videoDataSpace = " + mVideoDataSpace
                + ", videoFrameRate = " + mVideoFrameRate
                + ", videoSampleCount = " + mVideoSampleCount
                + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaItemInfo)) return false;
        MediaItemInfo that = (MediaItemInfo) o;
        return mSourceType == that.mSourceType
                && mDataTypes == that.mDataTypes
                && mDurationMillis == that.mDurationMillis
                && mClipDurationMillis == that.mClipDurationMillis
                && Objects.equals(mContainerMimeType, that.mContainerMimeType)
                && Objects.equals(mSampleMimeTypes, that.mSampleMimeTypes)
                && Objects.equals(mCodecNames, that.mCodecNames)
                && mAudioSampleRateHz == that.mAudioSampleRateHz
                && mAudioChannelCount == that.mAudioChannelCount
                && mAudioSampleCount == that.mAudioSampleCount
                && Objects.equals(mVideoSize, that.mVideoSize)
                && mVideoDataSpace == that.mVideoDataSpace
                && Float.compare(that.mVideoFrameRate, mVideoFrameRate) == 0
                && mVideoSampleCount == that.mVideoSampleCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mSourceType, mDataTypes, mDurationMillis, mClipDurationMillis,
                mContainerMimeType, mSampleMimeTypes, mCodecNames,
                mAudioSampleRateHz, mAudioChannelCount, mAudioSampleCount,
                mVideoSize, mVideoDataSpace, mVideoFrameRate, mVideoSampleCount);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSourceType);
        dest.writeLong(mDataTypes);
        dest.writeLong(mDurationMillis);
        dest.writeLong(mClipDurationMillis);
        dest.writeString(mContainerMimeType);
        dest.writeInt(mSampleMimeTypes.size());
        for (int i = 0; i < mSampleMimeTypes.size(); i++) {
            dest.writeString(mSampleMimeTypes.get(i));
        }
        dest.writeInt(mCodecNames.size());
        for (int i = 0; i < mCodecNames.size(); i++) {
            dest.writeString(mCodecNames.get(i));
        }
        dest.writeInt(mAudioSampleRateHz);
        dest.writeInt(mAudioChannelCount);
        dest.writeLong(mAudioSampleCount);
        dest.writeInt(mVideoSize.getWidth());
        dest.writeInt(mVideoSize.getHeight());
        dest.writeInt(mVideoDataSpace);
        dest.writeFloat(mVideoFrameRate);
        dest.writeLong(mVideoSampleCount);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<MediaItemInfo> CREATOR =
            new Parcelable.Creator<MediaItemInfo>() {
                @Override
                public MediaItemInfo createFromParcel(Parcel in) {
                    int sourceType = in.readInt();
                    long dataTypes = in.readLong();
                    long durationMillis = in.readLong();
                    long clipDurationMillis = in.readLong();
                    String containerMimeType = in.readString();
                    int mimeCount = Math.max(0, in.readInt());
                    List<String> sampleMimeTypes = new ArrayList<String>(mimeCount);
                    for (int i = 0; i < mimeCount; i++) {
                        sampleMimeTypes.add(in.readString());
                    }
                    int codecCount = Math.max(0, in.readInt());
                    List<String> codecNames = new ArrayList<String>(codecCount);
                    for (int i = 0; i < codecCount; i++) {
                        codecNames.add(in.readString());
                    }
                    int audioSampleRateHz = in.readInt();
                    int audioChannelCount = in.readInt();
                    long audioSampleCount = in.readLong();
                    Size videoSize = new Size(in.readInt(), in.readInt());
                    int videoDataSpace = in.readInt();
                    float videoFrameRate = in.readFloat();
                    long videoSampleCount = in.readLong();
                    return new MediaItemInfo(
                            sourceType, dataTypes, durationMillis, clipDurationMillis,
                            containerMimeType, sampleMimeTypes, codecNames,
                            audioSampleRateHz, audioChannelCount, audioSampleCount,
                            videoSize, videoDataSpace, videoFrameRate, videoSampleCount);
                }

                @Override
                public MediaItemInfo[] newArray(int size) {
                    return new MediaItemInfo[size];
                }
            };
}
