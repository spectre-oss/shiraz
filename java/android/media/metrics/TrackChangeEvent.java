// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class TrackChangeEvent extends Event implements Parcelable {
    public static final int TRACK_STATE_OFF = 0;
    public static final int TRACK_STATE_ON = 1;

    public static final int TRACK_CHANGE_REASON_UNKNOWN = 0;
    public static final int TRACK_CHANGE_REASON_OTHER = 1;
    public static final int TRACK_CHANGE_REASON_INITIAL = 2;
    public static final int TRACK_CHANGE_REASON_MANUAL = 3;
    public static final int TRACK_CHANGE_REASON_ADAPTIVE = 4;

    public static final int TRACK_TYPE_AUDIO = 0;
    public static final int TRACK_TYPE_VIDEO = 1;
    public static final int TRACK_TYPE_TEXT = 2;

    public static final class Builder {
        private final int mType;
        private int mState = TRACK_STATE_OFF;
        private int mReason = TRACK_CHANGE_REASON_UNKNOWN;
        private String mContainerMimeType;
        private String mSampleMimeType;
        private String mCodecName;
        private int mBitrate = -1;
        private long mTimeSinceCreatedMillis = -1L;
        private String mLanguage;
        private String mLanguageRegion;
        private int mChannelCount = -1;
        private int mSampleRate = -1;
        private int mWidth = -1;
        private int mHeight = -1;
        private float mVideoFrameRate = -1.0f;
        private Bundle mMetricsBundle = new Bundle();

        public Builder(int type) {
            if (type != TRACK_TYPE_AUDIO && type != TRACK_TYPE_VIDEO && type != TRACK_TYPE_TEXT) {
                throw new IllegalArgumentException(
                        "track type must be one of TRACK_TYPE_AUDIO, TRACK_TYPE_VIDEO, "
                                + "TRACK_TYPE_TEXT.");
            }
            mType = type;
        }

        public Builder setTrackState(int value) {
            mState = value;
            return this;
        }

        public Builder setTrackChangeReason(int value) {
            mReason = value;
            return this;
        }

        public Builder setContainerMimeType(String value) {
            mContainerMimeType = value;
            return this;
        }

        public Builder setSampleMimeType(String value) {
            mSampleMimeType = value;
            return this;
        }

        public Builder setCodecName(String value) {
            mCodecName = value;
            return this;
        }

        public Builder setBitrate(int value) {
            mBitrate = value;
            return this;
        }

        public Builder setTimeSinceCreatedMillis(long value) {
            mTimeSinceCreatedMillis = value;
            return this;
        }

        public Builder setLanguage(String value) {
            mLanguage = value;
            return this;
        }

        public Builder setLanguageRegion(String value) {
            mLanguageRegion = value;
            return this;
        }

        public Builder setChannelCount(int value) {
            mChannelCount = value;
            return this;
        }

        public Builder setAudioSampleRate(int value) {
            mSampleRate = value;
            return this;
        }

        public Builder setWidth(int value) {
            mWidth = value;
            return this;
        }

        public Builder setHeight(int value) {
            mHeight = value;
            return this;
        }

        public Builder setVideoFrameRate(float value) {
            mVideoFrameRate = value;
            return this;
        }

        public Builder setMetricsBundle(Bundle metricsBundle) {
            mMetricsBundle = metricsBundle != null ? new Bundle(metricsBundle) : new Bundle();
            return this;
        }

        public TrackChangeEvent build() {
            return new TrackChangeEvent(
                    mState, mReason, mContainerMimeType, mSampleMimeType,
                    mCodecName, mBitrate, mTimeSinceCreatedMillis, mType,
                    mLanguage, mLanguageRegion, mChannelCount, mSampleRate,
                    mWidth, mHeight, mVideoFrameRate, mMetricsBundle);
        }
    }

    private final int mState;
    private final int mReason;
    private final String mContainerMimeType;
    private final String mSampleMimeType;
    private final String mCodecName;
    private final int mBitrate;
    private final int mType;
    private final String mLanguage;
    private final String mLanguageRegion;
    private final int mChannelCount;
    private final int mAudioSampleRate;
    private final int mWidth;
    private final int mHeight;
    private final float mVideoFrameRate;

    TrackChangeEvent(
            int state,
            int reason,
            String containerMimeType,
            String sampleMimeType,
            String codecName,
            int bitrate,
            long timeSinceCreatedMillis,
            int type,
            String language,
            String languageRegion,
            int channelCount,
            int sampleRate,
            int width,
            int height,
            float videoFrameRate,
            Bundle extras) {
        super(timeSinceCreatedMillis, extras);
        mState = state;
        mReason = reason;
        mContainerMimeType = containerMimeType;
        mSampleMimeType = sampleMimeType;
        mCodecName = codecName;
        mBitrate = bitrate;
        mType = type;
        mLanguage = language;
        mLanguageRegion = languageRegion;
        mChannelCount = channelCount;
        mAudioSampleRate = sampleRate;
        mWidth = width;
        mHeight = height;
        mVideoFrameRate = videoFrameRate;
    }

    public int getTrackState() {
        return mState;
    }

    public int getTrackChangeReason() {
        return mReason;
    }

    public String getContainerMimeType() {
        return mContainerMimeType;
    }

    public String getSampleMimeType() {
        return mSampleMimeType;
    }

    public String getCodecName() {
        return mCodecName;
    }

    public int getBitrate() {
        return mBitrate;
    }

    @Override
    public long getTimeSinceCreatedMillis() {
        return mTimeSinceCreatedMillis;
    }

    public int getTrackType() {
        return mType;
    }

    public String getLanguage() {
        return mLanguage;
    }

    public String getLanguageRegion() {
        return mLanguageRegion;
    }

    public int getChannelCount() {
        return mChannelCount;
    }

    public int getAudioSampleRate() {
        return mAudioSampleRate;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public float getVideoFrameRate() {
        return mVideoFrameRate;
    }

    @Override
    public Bundle getMetricsBundle() {
        return super.getMetricsBundle();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mState);
        dest.writeInt(mReason);
        dest.writeString(mContainerMimeType);
        dest.writeString(mSampleMimeType);
        dest.writeString(mCodecName);
        dest.writeInt(mBitrate);
        dest.writeLong(mTimeSinceCreatedMillis);
        dest.writeInt(mType);
        dest.writeString(mLanguage);
        dest.writeString(mLanguageRegion);
        dest.writeInt(mChannelCount);
        dest.writeInt(mAudioSampleRate);
        dest.writeInt(mWidth);
        dest.writeInt(mHeight);
        dest.writeFloat(mVideoFrameRate);
        dest.writeBundle(mMetricsBundle);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "TrackChangeEvent { state = " + mState
                + ", reason = " + mReason
                + ", containerMimeType = " + mContainerMimeType
                + ", sampleMimeType = " + mSampleMimeType
                + ", codecName = " + mCodecName
                + ", bitrate = " + mBitrate
                + ", timeSinceCreatedMillis = " + mTimeSinceCreatedMillis
                + ", type = " + mType
                + ", language = " + mLanguage
                + ", languageRegion = " + mLanguageRegion
                + ", channelCount = " + mChannelCount
                + ", sampleRate = " + mAudioSampleRate
                + ", width = " + mWidth
                + ", height = " + mHeight
                + ", videoFrameRate = " + mVideoFrameRate
                + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackChangeEvent)) return false;
        TrackChangeEvent that = (TrackChangeEvent) o;
        return mState == that.mState
                && mReason == that.mReason
                && Objects.equals(mContainerMimeType, that.mContainerMimeType)
                && Objects.equals(mSampleMimeType, that.mSampleMimeType)
                && Objects.equals(mCodecName, that.mCodecName)
                && mBitrate == that.mBitrate
                && mTimeSinceCreatedMillis == that.mTimeSinceCreatedMillis
                && mType == that.mType
                && Objects.equals(mLanguage, that.mLanguage)
                && Objects.equals(mLanguageRegion, that.mLanguageRegion)
                && mChannelCount == that.mChannelCount
                && mAudioSampleRate == that.mAudioSampleRate
                && mWidth == that.mWidth
                && mHeight == that.mHeight
                && Float.compare(that.mVideoFrameRate, mVideoFrameRate) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mState, mReason, mContainerMimeType, mSampleMimeType,
                mCodecName, mBitrate, mTimeSinceCreatedMillis, mType,
                mLanguage, mLanguageRegion, mChannelCount, mAudioSampleRate,
                mWidth, mHeight, mVideoFrameRate);
    }

    public static final Parcelable.Creator<TrackChangeEvent> CREATOR =
            new Parcelable.Creator<TrackChangeEvent>() {
                @Override
                public TrackChangeEvent createFromParcel(Parcel in) {
                    int state = in.readInt();
                    int reason = in.readInt();
                    String containerMime = in.readString();
                    String sampleMime = in.readString();
                    String codecName = in.readString();
                    int bitrate = in.readInt();
                    long time = in.readLong();
                    int type = in.readInt();
                    String language = in.readString();
                    String languageRegion = in.readString();
                    int channelCount = in.readInt();
                    int sampleRate = in.readInt();
                    int width = in.readInt();
                    int height = in.readInt();
                    float videoFrameRate = in.readFloat();
                    Bundle extras = in.readBundle();
                    return new TrackChangeEvent(
                            state, reason, containerMime, sampleMime, codecName, bitrate,
                            time, type, language, languageRegion, channelCount, sampleRate,
                            width, height, videoFrameRate, extras);
                }

                @Override
                public TrackChangeEvent[] newArray(int size) {
                    return new TrackChangeEvent[size];
                }
            };
}
