// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class PlaybackMetrics implements Parcelable {
    public static final int STREAM_SOURCE_UNKNOWN = 0;
    public static final int STREAM_SOURCE_NETWORK = 1;
    public static final int STREAM_SOURCE_DEVICE = 2;
    public static final int STREAM_SOURCE_MIXED = 3;

    public static final int STREAM_TYPE_UNKNOWN = 0;
    public static final int STREAM_TYPE_OTHER = 1;
    public static final int STREAM_TYPE_PROGRESSIVE = 2;
    public static final int STREAM_TYPE_DASH = 3;
    public static final int STREAM_TYPE_HLS = 4;
    public static final int STREAM_TYPE_SS = 5;

    public static final int PLAYBACK_TYPE_UNKNOWN = 0;
    public static final int PLAYBACK_TYPE_VOD = 1;
    public static final int PLAYBACK_TYPE_LIVE = 2;
    public static final int PLAYBACK_TYPE_OTHER = 3;

    public static final int DRM_TYPE_NONE = 0;
    public static final int DRM_TYPE_OTHER = 1;
    public static final int DRM_TYPE_PLAY_READY = 2;
    public static final int DRM_TYPE_WIDEVINE_L1 = 3;
    public static final int DRM_TYPE_WIDEVINE_L3 = 4;
    public static final int DRM_TYPE_WV_L3_FALLBACK = 5;
    public static final int DRM_TYPE_CLEARKEY = 6;

    public static final int CONTENT_TYPE_UNKNOWN = 0;
    public static final int CONTENT_TYPE_MAIN = 1;
    public static final int CONTENT_TYPE_AD = 2;
    public static final int CONTENT_TYPE_OTHER = 3;

    public static final class Builder {
        private long mMediaDurationMillis = -1L;
        private int mStreamSource = STREAM_SOURCE_UNKNOWN;
        private int mStreamType = STREAM_TYPE_UNKNOWN;
        private int mPlaybackType = PLAYBACK_TYPE_UNKNOWN;
        private int mDrmType = DRM_TYPE_NONE;
        private int mContentType = CONTENT_TYPE_UNKNOWN;
        private String mPlayerName;
        private String mPlayerVersion;
        private final List<Long> mExperimentIds = new ArrayList<Long>();
        private int mVideoFramesPlayed = -1;
        private int mVideoFramesDropped = -1;
        private int mAudioUnderrunCount = -1;
        private long mNetworkBytesRead = -1L;
        private long mLocalBytesRead = -1L;
        private long mNetworkTransferDurationMillis = -1L;
        private byte[] mDrmSessionId = new byte[0];
        private Bundle mMetricsBundle = new Bundle();

        public Builder() {
        }

        public Builder setMediaDurationMillis(long value) {
            mMediaDurationMillis = value;
            return this;
        }

        public Builder setStreamSource(int value) {
            mStreamSource = value;
            return this;
        }

        public Builder setStreamType(int value) {
            mStreamType = value;
            return this;
        }

        public Builder setPlaybackType(int value) {
            mPlaybackType = value;
            return this;
        }

        public Builder setDrmType(int value) {
            mDrmType = value;
            return this;
        }

        public Builder setContentType(int value) {
            mContentType = value;
            return this;
        }

        public Builder setPlayerName(String value) {
            mPlayerName = value;
            return this;
        }

        public Builder setPlayerVersion(String value) {
            mPlayerVersion = value;
            return this;
        }

        public Builder addExperimentId(long value) {
            mExperimentIds.add(value);
            return this;
        }

        public Builder setVideoFramesPlayed(int value) {
            mVideoFramesPlayed = value;
            return this;
        }

        public Builder setVideoFramesDropped(int value) {
            mVideoFramesDropped = value;
            return this;
        }

        public Builder setAudioUnderrunCount(int value) {
            mAudioUnderrunCount = value;
            return this;
        }

        public Builder setNetworkBytesRead(long value) {
            mNetworkBytesRead = value;
            return this;
        }

        public Builder setLocalBytesRead(long value) {
            mLocalBytesRead = value;
            return this;
        }

        public Builder setNetworkTransferDurationMillis(long value) {
            mNetworkTransferDurationMillis = value;
            return this;
        }

        public Builder setDrmSessionId(byte[] drmSessionId) {
            mDrmSessionId = drmSessionId != null
                    ? Arrays.copyOf(drmSessionId, drmSessionId.length)
                    : new byte[0];
            return this;
        }

        public Builder setMetricsBundle(Bundle metricsBundle) {
            mMetricsBundle = metricsBundle != null ? new Bundle(metricsBundle) : new Bundle();
            return this;
        }

        public PlaybackMetrics build() {
            long[] expIds = new long[mExperimentIds.size()];
            for (int i = 0; i < mExperimentIds.size(); i++) {
                expIds[i] = mExperimentIds.get(i);
            }
            return new PlaybackMetrics(
                    mMediaDurationMillis,
                    mStreamSource,
                    mStreamType,
                    mPlaybackType,
                    mDrmType,
                    mContentType,
                    mPlayerName,
                    mPlayerVersion,
                    expIds,
                    mVideoFramesPlayed,
                    mVideoFramesDropped,
                    mAudioUnderrunCount,
                    mNetworkBytesRead,
                    mLocalBytesRead,
                    mNetworkTransferDurationMillis,
                    mDrmSessionId,
                    mMetricsBundle);
        }
    }

    private final long mMediaDurationMillis;
    private final int mStreamSource;
    private final int mStreamType;
    private final int mPlaybackType;
    private final int mDrmType;
    private final int mContentType;
    private final String mPlayerName;
    private final String mPlayerVersion;
    private final long[] mExperimentIds;
    private final int mVideoFramesPlayed;
    private final int mVideoFramesDropped;
    private final int mAudioUnderrunCount;
    private final long mNetworkBytesRead;
    private final long mLocalBytesRead;
    private final long mNetworkTransferDurationMillis;
    private final byte[] mDrmSessionId;
    private final Bundle mMetricsBundle;

    PlaybackMetrics(
            long mediaDurationMillis,
            int streamSource,
            int streamType,
            int playbackType,
            int drmType,
            int contentType,
            String playerName,
            String playerVersion,
            long[] experimentIds,
            int videoFramesPlayed,
            int videoFramesDropped,
            int audioUnderrunCount,
            long networkBytesRead,
            long localBytesRead,
            long networkTransferDurationMillis,
            byte[] drmSessionId,
            Bundle extras) {
        mMediaDurationMillis = mediaDurationMillis;
        mStreamSource = streamSource;
        mStreamType = streamType;
        mPlaybackType = playbackType;
        mDrmType = drmType;
        mContentType = contentType;
        mPlayerName = playerName;
        mPlayerVersion = playerVersion;
        mExperimentIds = experimentIds != null
                ? Arrays.copyOf(experimentIds, experimentIds.length)
                : new long[0];
        mVideoFramesPlayed = videoFramesPlayed;
        mVideoFramesDropped = videoFramesDropped;
        mAudioUnderrunCount = audioUnderrunCount;
        mNetworkBytesRead = networkBytesRead;
        mLocalBytesRead = localBytesRead;
        mNetworkTransferDurationMillis = networkTransferDurationMillis;
        mDrmSessionId = drmSessionId != null
                ? Arrays.copyOf(drmSessionId, drmSessionId.length)
                : new byte[0];
        mMetricsBundle = extras != null ? new Bundle(extras) : new Bundle();
    }

    public long getMediaDurationMillis() {
        return mMediaDurationMillis;
    }

    public int getStreamSource() {
        return mStreamSource;
    }

    public int getStreamType() {
        return mStreamType;
    }

    public int getPlaybackType() {
        return mPlaybackType;
    }

    public int getDrmType() {
        return mDrmType;
    }

    public int getContentType() {
        return mContentType;
    }

    public String getPlayerName() {
        return mPlayerName;
    }

    public String getPlayerVersion() {
        return mPlayerVersion;
    }

    public long[] getExperimentIds() {
        return Arrays.copyOf(mExperimentIds, mExperimentIds.length);
    }

    public int getVideoFramesPlayed() {
        return mVideoFramesPlayed;
    }

    public int getVideoFramesDropped() {
        return mVideoFramesDropped;
    }

    public int getAudioUnderrunCount() {
        return mAudioUnderrunCount;
    }

    public long getNetworkBytesRead() {
        return mNetworkBytesRead;
    }

    public long getLocalBytesRead() {
        return mLocalBytesRead;
    }

    public long getNetworkTransferDurationMillis() {
        return mNetworkTransferDurationMillis;
    }

    public byte[] getDrmSessionId() {
        return Arrays.copyOf(mDrmSessionId, mDrmSessionId.length);
    }

    public Bundle getMetricsBundle() {
        return new Bundle(mMetricsBundle);
    }

    @Override
    public String toString() {
        return "PlaybackMetrics { mediaDurationMillis = " + mMediaDurationMillis
                + ", streamSource = " + mStreamSource
                + ", streamType = " + mStreamType
                + ", playbackType = " + mPlaybackType
                + ", drmType = " + mDrmType
                + ", contentType = " + mContentType
                + ", playerName = " + mPlayerName
                + ", playerVersion = " + mPlayerVersion
                + ", experimentIds = " + Arrays.toString(mExperimentIds)
                + ", videoFramesPlayed = " + mVideoFramesPlayed
                + ", videoFramesDropped = " + mVideoFramesDropped
                + ", audioUnderrunCount = " + mAudioUnderrunCount
                + ", networkBytesRead = " + mNetworkBytesRead
                + ", localBytesRead = " + mLocalBytesRead
                + ", networkTransferDurationMillis = " + mNetworkTransferDurationMillis
                + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlaybackMetrics)) return false;
        PlaybackMetrics that = (PlaybackMetrics) o;
        return mMediaDurationMillis == that.mMediaDurationMillis
                && mStreamSource == that.mStreamSource
                && mStreamType == that.mStreamType
                && mPlaybackType == that.mPlaybackType
                && mDrmType == that.mDrmType
                && mContentType == that.mContentType
                && Objects.equals(mPlayerName, that.mPlayerName)
                && Objects.equals(mPlayerVersion, that.mPlayerVersion)
                && Arrays.equals(mExperimentIds, that.mExperimentIds)
                && mVideoFramesPlayed == that.mVideoFramesPlayed
                && mVideoFramesDropped == that.mVideoFramesDropped
                && mAudioUnderrunCount == that.mAudioUnderrunCount
                && mNetworkBytesRead == that.mNetworkBytesRead
                && mLocalBytesRead == that.mLocalBytesRead
                && mNetworkTransferDurationMillis == that.mNetworkTransferDurationMillis
                && Arrays.equals(mDrmSessionId, that.mDrmSessionId);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                mMediaDurationMillis, mStreamSource, mStreamType, mPlaybackType,
                mDrmType, mContentType, mPlayerName, mPlayerVersion,
                mVideoFramesPlayed, mVideoFramesDropped, mAudioUnderrunCount,
                mNetworkBytesRead, mLocalBytesRead, mNetworkTransferDurationMillis);
        result = 31 * result + Arrays.hashCode(mExperimentIds);
        result = 31 * result + Arrays.hashCode(mDrmSessionId);
        return result;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mMediaDurationMillis);
        dest.writeInt(mStreamSource);
        dest.writeInt(mStreamType);
        dest.writeInt(mPlaybackType);
        dest.writeInt(mDrmType);
        dest.writeInt(mContentType);
        dest.writeString(mPlayerName);
        dest.writeString(mPlayerVersion);
        dest.writeInt(mExperimentIds.length);
        for (int i = 0; i < mExperimentIds.length; i++) {
            dest.writeLong(mExperimentIds[i]);
        }
        dest.writeInt(mVideoFramesPlayed);
        dest.writeInt(mVideoFramesDropped);
        dest.writeInt(mAudioUnderrunCount);
        dest.writeLong(mNetworkBytesRead);
        dest.writeLong(mLocalBytesRead);
        dest.writeLong(mNetworkTransferDurationMillis);
        dest.writeByteArray(mDrmSessionId);
        dest.writeBundle(mMetricsBundle);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<PlaybackMetrics> CREATOR =
            new Parcelable.Creator<PlaybackMetrics>() {
                @Override
                public PlaybackMetrics createFromParcel(Parcel in) {
                    long mediaDurationMillis = in.readLong();
                    int streamSource = in.readInt();
                    int streamType = in.readInt();
                    int playbackType = in.readInt();
                    int drmType = in.readInt();
                    int contentType = in.readInt();
                    String playerName = in.readString();
                    String playerVersion = in.readString();
                    int expLen = Math.max(0, in.readInt());
                    long[] expIds = new long[expLen];
                    for (int i = 0; i < expLen; i++) {
                        expIds[i] = in.readLong();
                    }
                    int videoFramesPlayed = in.readInt();
                    int videoFramesDropped = in.readInt();
                    int audioUnderrunCount = in.readInt();
                    long networkBytesRead = in.readLong();
                    long localBytesRead = in.readLong();
                    long networkTransferDurationMillis = in.readLong();
                    byte[] drmSessionId = in.createByteArray();
                    Bundle extras = in.readBundle();
                    return new PlaybackMetrics(
                            mediaDurationMillis, streamSource, streamType, playbackType,
                            drmType, contentType, playerName, playerVersion, expIds,
                            videoFramesPlayed, videoFramesDropped, audioUnderrunCount,
                            networkBytesRead, localBytesRead, networkTransferDurationMillis,
                            drmSessionId, extras);
                }

                @Override
                public PlaybackMetrics[] newArray(int size) {
                    return new PlaybackMetrics[size];
                }
            };
}
