// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public class MediaSyncEvent implements Parcelable {
    public static final int SYNC_EVENT_NONE = 0;
    public static final int SYNC_EVENT_PRESENTATION_COMPLETE = 1;

    private final int mType;
    private int mAudioSession;

    private MediaSyncEvent(int eventType) {
        mType = eventType;
    }

    private MediaSyncEvent(Parcel in) {
        mType = in.readInt();
        mAudioSession = in.readInt();
    }

    public static MediaSyncEvent createEvent(int eventType) throws IllegalArgumentException {
        if (eventType != SYNC_EVENT_NONE && eventType != SYNC_EVENT_PRESENTATION_COMPLETE) {
            throw new IllegalArgumentException(eventType + " is not a valid MediaSyncEvent type.");
        }
        return new MediaSyncEvent(eventType);
    }

    public MediaSyncEvent setAudioSessionId(int audioSessionId) throws IllegalArgumentException {
        if (audioSessionId <= 0) {
            throw new IllegalArgumentException(audioSessionId + " is not a valid session ID.");
        }
        mAudioSession = audioSessionId;
        return this;
    }

    public int getType() {
        return mType;
    }

    public int getAudioSessionId() {
        return mAudioSession;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeInt(mType);
        dest.writeInt(mAudioSession);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaSyncEvent)) return false;
        MediaSyncEvent that = (MediaSyncEvent) o;
        return mType == that.mType && mAudioSession == that.mAudioSession;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(mType), Integer.valueOf(mAudioSession));
    }

    @Override
    public String toString() {
        return "MediaSyncEvent: type=" + mType + " session=" + mAudioSession;
    }

    public static final Parcelable.Creator<MediaSyncEvent> CREATOR =
            new Parcelable.Creator<MediaSyncEvent>() {
                @Override
                public MediaSyncEvent createFromParcel(Parcel p) {
                    return new MediaSyncEvent(p);
                }

                @Override
                public MediaSyncEvent[] newArray(int size) {
                    return new MediaSyncEvent[size];
                }
            };
}
