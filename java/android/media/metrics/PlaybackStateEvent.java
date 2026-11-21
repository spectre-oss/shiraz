// SPDX-License-Identifier: GPL-3.0-only
package android.media.metrics;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class PlaybackStateEvent extends Event implements Parcelable {
    public static final int STATE_NOT_STARTED = 0;
    public static final int STATE_JOINING_BACKGROUND = 1;
    public static final int STATE_JOINING_FOREGROUND = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_PAUSED = 4;
    public static final int STATE_SEEKING = 5;
    public static final int STATE_BUFFERING = 6;
    public static final int STATE_PAUSED_BUFFERING = 7;
    public static final int STATE_SUPPRESSED = 9;
    public static final int STATE_SUPPRESSED_BUFFERING = 10;
    public static final int STATE_ENDED = 11;
    public static final int STATE_STOPPED = 12;
    public static final int STATE_FAILED = 13;
    public static final int STATE_INTERRUPTED_BY_AD = 14;
    public static final int STATE_ABANDONED = 15;

    public static final class Builder {
        private int mState = STATE_NOT_STARTED;
        private long mTimeSinceCreatedMillis = -1L;
        private Bundle mMetricsBundle = new Bundle();

        public Builder() {
        }

        public Builder setState(int value) {
            mState = value;
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

        public PlaybackStateEvent build() {
            return new PlaybackStateEvent(mState, mTimeSinceCreatedMillis, mMetricsBundle);
        }
    }

    private final int mState;

    PlaybackStateEvent(int state, long timeSinceCreatedMillis, Bundle extras) {
        super(timeSinceCreatedMillis, extras);
        mState = state;
    }

    public int getState() {
        return mState;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlaybackStateEvent)) return false;
        PlaybackStateEvent that = (PlaybackStateEvent) o;
        return mState == that.mState
                && mTimeSinceCreatedMillis == that.mTimeSinceCreatedMillis;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mState, mTimeSinceCreatedMillis);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mState);
        dest.writeLong(mTimeSinceCreatedMillis);
        dest.writeBundle(mMetricsBundle);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<PlaybackStateEvent> CREATOR =
            new Parcelable.Creator<PlaybackStateEvent>() {
                @Override
                public PlaybackStateEvent createFromParcel(Parcel in) {
                    int state = in.readInt();
                    long time = in.readLong();
                    Bundle extras = in.readBundle();
                    return new PlaybackStateEvent(state, time, extras);
                }

                @Override
                public PlaybackStateEvent[] newArray(int size) {
                    return new PlaybackStateEvent[size];
                }
            };
}
