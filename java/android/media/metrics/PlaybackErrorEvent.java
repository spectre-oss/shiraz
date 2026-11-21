// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class PlaybackErrorEvent extends Event implements Parcelable {
    public static final int ERROR_UNKNOWN = 0;
    public static final int ERROR_OTHER = 1;
    public static final int ERROR_RUNTIME = 2;
    public static final int ERROR_IO_NETWORK_UNAVAILABLE = 3;
    public static final int ERROR_IO_NETWORK_CONNECTION_FAILED = 4;
    public static final int ERROR_IO_BAD_HTTP_STATUS = 5;
    public static final int ERROR_IO_DNS_FAILED = 6;
    public static final int ERROR_IO_CONNECTION_TIMEOUT = 7;
    public static final int ERROR_IO_CONNECTION_CLOSED = 8;
    public static final int ERROR_IO_OTHER = 9;
    public static final int ERROR_PARSING_MANIFEST_MALFORMED = 10;
    public static final int ERROR_PARSING_CONTAINER_MALFORMED = 11;
    public static final int ERROR_PARSING_OTHER = 12;
    public static final int ERROR_DECODER_INIT_FAILED = 13;
    public static final int ERROR_DECODING_FAILED = 14;
    public static final int ERROR_DECODING_FORMAT_EXCEEDS_CAPABILITIES = 15;
    public static final int ERROR_DECODING_OTHER = 16;
    public static final int ERROR_AUDIO_TRACK_INIT_FAILED = 17;
    public static final int ERROR_AUDIO_TRACK_WRITE_FAILED = 18;
    public static final int ERROR_AUDIO_TRACK_OTHER = 19;
    public static final int ERROR_PLAYER_REMOTE = 20;
    public static final int ERROR_PLAYER_BEHIND_LIVE_WINDOW = 21;
    public static final int ERROR_PLAYER_OTHER = 22;
    public static final int ERROR_DRM_SCHEME_UNSUPPORTED = 23;
    public static final int ERROR_DRM_PROVISIONING_FAILED = 24;
    public static final int ERROR_DRM_LICENSE_ACQUISITION_FAILED = 25;
    public static final int ERROR_DRM_DISALLOWED_OPERATION = 26;
    public static final int ERROR_DRM_SYSTEM_ERROR = 27;
    public static final int ERROR_DRM_CONTENT_ERROR = 28;
    public static final int ERROR_DRM_DEVICE_REVOKED = 29;
    public static final int ERROR_DRM_OTHER = 30;
    public static final int ERROR_IO_FILE_NOT_FOUND = 31;
    public static final int ERROR_IO_NO_PERMISSION = 32;
    public static final int ERROR_PARSING_MANIFEST_UNSUPPORTED = 33;
    public static final int ERROR_PARSING_CONTAINER_UNSUPPORTED = 34;
    public static final int ERROR_DECODING_FORMAT_UNSUPPORTED = 35;

    public static final class Builder {
        private Exception mException;
        private int mErrorCode = ERROR_UNKNOWN;
        private int mSubErrorCode = 0;
        private long mTimeSinceCreatedMillis = -1L;
        private Bundle mMetricsBundle = new Bundle();

        public Builder() {
        }

        public Builder setException(Exception value) {
            mException = value;
            return this;
        }

        public Builder setErrorCode(int value) {
            mErrorCode = value;
            return this;
        }

        public Builder setSubErrorCode(int value) {
            mSubErrorCode = value;
            return this;
        }

        public Builder setTimeSinceCreatedMillis(long value) {
            mTimeSinceCreatedMillis = value;
            return this;
        }

        public Builder setMetricsBundle(Bundle metricsBundle) {
            mMetricsBundle = metricsBundle != null ? new Bundle(metricsBundle) : new Bundle();
            return this;
        }

        public PlaybackErrorEvent build() {
            String stack = mException != null ? mException.toString() : null;
            return new PlaybackErrorEvent(
                    stack, mErrorCode, mSubErrorCode, mTimeSinceCreatedMillis, mMetricsBundle);
        }
    }

    private final String mExceptionStack;
    private final int mErrorCode;
    private final int mSubErrorCode;

    PlaybackErrorEvent(
            String exceptionStack,
            int errorCode,
            int subErrorCode,
            long timeSinceCreatedMillis,
            Bundle extras) {
        super(timeSinceCreatedMillis, extras);
        mExceptionStack = exceptionStack;
        mErrorCode = errorCode;
        mSubErrorCode = subErrorCode;
    }

    public int getErrorCode() {
        return mErrorCode;
    }

    public int getSubErrorCode() {
        return mSubErrorCode;
    }

    @Override
    public long getTimeSinceCreatedMillis() {
        return mTimeSinceCreatedMillis;
    }

    @Override
    public Bundle getMetricsBundle() {
        return super.getMetricsBundle();
    }

    @Override
    public String toString() {
        return "PlaybackErrorEvent { exceptionStack = " + mExceptionStack
                + ", errorCode = " + mErrorCode
                + ", subErrorCode = " + mSubErrorCode
                + ", timeSinceCreatedMillis = " + mTimeSinceCreatedMillis + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlaybackErrorEvent)) return false;
        PlaybackErrorEvent that = (PlaybackErrorEvent) o;
        return mErrorCode == that.mErrorCode
                && mSubErrorCode == that.mSubErrorCode
                && mTimeSinceCreatedMillis == that.mTimeSinceCreatedMillis
                && Objects.equals(mExceptionStack, that.mExceptionStack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mExceptionStack, mErrorCode, mSubErrorCode, mTimeSinceCreatedMillis);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mExceptionStack);
        dest.writeInt(mErrorCode);
        dest.writeInt(mSubErrorCode);
        dest.writeLong(mTimeSinceCreatedMillis);
        dest.writeBundle(mMetricsBundle);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<PlaybackErrorEvent> CREATOR =
            new Parcelable.Creator<PlaybackErrorEvent>() {
                @Override
                public PlaybackErrorEvent createFromParcel(Parcel in) {
                    String stack = in.readString();
                    int errorCode = in.readInt();
                    int subErrorCode = in.readInt();
                    long time = in.readLong();
                    Bundle extras = in.readBundle();
                    return new PlaybackErrorEvent(stack, errorCode, subErrorCode, time, extras);
                }

                @Override
                public PlaybackErrorEvent[] newArray(int size) {
                    return new PlaybackErrorEvent[size];
                }
            };
}
