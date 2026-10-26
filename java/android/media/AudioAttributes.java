// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import android.os.Parcelable;
import android.os.Parcel;
import java.util.Objects;

/** Immutable playback configuration; creating attributes does not open an audio device. */
public final class AudioAttributes implements Parcelable {
    public static final int CONTENT_TYPE_UNKNOWN = 0, CONTENT_TYPE_SPEECH = 1, CONTENT_TYPE_MUSIC = 2,
        CONTENT_TYPE_MOVIE = 3, CONTENT_TYPE_SONIFICATION = 4;
    public static final int USAGE_UNKNOWN = 0, USAGE_MEDIA = 1, USAGE_VOICE_COMMUNICATION = 2,
        USAGE_VOICE_COMMUNICATION_SIGNALLING = 3, USAGE_ALARM = 4, USAGE_NOTIFICATION = 5,
        USAGE_NOTIFICATION_RINGTONE = 6, USAGE_NOTIFICATION_COMMUNICATION_REQUEST = 7,
        USAGE_NOTIFICATION_COMMUNICATION_INSTANT = 8, USAGE_NOTIFICATION_COMMUNICATION_DELAYED = 9,
        USAGE_NOTIFICATION_EVENT = 10, USAGE_ASSISTANCE_ACCESSIBILITY = 11,
        USAGE_ASSISTANCE_NAVIGATION_GUIDANCE = 12, USAGE_ASSISTANCE_SONIFICATION = 13, USAGE_GAME = 14, USAGE_ASSISTANT = 16;
    public static final int FLAG_AUDIBILITY_ENFORCED = 1, FLAG_HW_AV_SYNC = 16, FLAG_LOW_LATENCY = 256;
    private static final int PUBLIC_FLAGS = 0x111, ALL_FLAGS = 0x3ff;
    private final int usage, content, flags;
    private AudioAttributes(Builder builder) { usage = builder.usage; content = builder.content; flags = builder.flags; }
    public int getUsage() { return usage; }
    public int getContentType() { return content; }
    public int getFlags() { return flags & PUBLIC_FLAGS; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof AudioAttributes)) return false;
        AudioAttributes a = (AudioAttributes)other; return usage == a.usage && content == a.content && flags == a.flags;
    }
    @Override public int hashCode() { return Objects.hash(usage, content, flags); }
    @Override public String toString() { return "AudioAttributes{usage=" + usage + ", content=" + content + ", flags=" + flags + "}"; }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel out, int options) { throw new UnsupportedOperationException("AudioAttributes parcel transport is not implemented"); }
    public static final Parcelable.Creator<AudioAttributes> CREATOR = new Parcelable.Creator<AudioAttributes>() {
        public AudioAttributes createFromParcel(Parcel source) { throw new UnsupportedOperationException("AudioAttributes parcel decoding is not implemented"); }
        public AudioAttributes[] newArray(int size) { return new AudioAttributes[size]; }
    };
    public static final class Builder {
        private int usage, content, flags;
        public Builder() { }
        public Builder(AudioAttributes source) { Objects.requireNonNull(source); usage = source.usage; content = source.content; flags = source.flags; }
        public Builder setUsage(int value) {
            if (value < 0 || value > 16) throw new IllegalArgumentException("Unsupported Android audio usage");
            usage = value; return this;
        }
        public Builder setContentType(int value) {
            if (value < 0 || value > 4) throw new IllegalArgumentException("Unsupported Android audio content type");
            content = value; return this;
        }
        public Builder setFlags(int value) { flags |= value & ALL_FLAGS; return this; }
        public Builder setLegacyStreamType(int stream) {
            if (stream < 0 || stream > 9) throw new IllegalArgumentException("Unsupported legacy playback stream");
            int[] usages = {2, 13, 6, 1, 4, 5, 2, 13, 3, 0};
            int[] contents = {1, 4, 4, 2, 4, 4, 1, 4, 4, 4};
            usage = usages[stream]; content = contents[stream];
            if (stream == 6) flags |= 4;
            if (stream == 7) flags |= FLAG_AUDIBILITY_ENFORCED;
            return this;
        }
        public AudioAttributes build() { return new AudioAttributes(this); }
    }
}
