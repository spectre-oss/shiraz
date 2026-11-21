// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import android.media.AudioAttributes;
import java.util.Objects;

/** Attributes describing the semantic usage and flags of a vibration request. */
public final class VibrationAttributes implements Parcelable {
    public static final int USAGE_CLASS_MASK = 0x0f;
    public static final int USAGE_CLASS_UNKNOWN = 0x00;
    public static final int USAGE_CLASS_ALARM = 0x01;
    public static final int USAGE_CLASS_FEEDBACK = 0x02;
    public static final int USAGE_CLASS_MEDIA = 0x03;

    public static final int USAGE_UNKNOWN = 0x00;
    public static final int USAGE_ALARM = 0x11;
    public static final int USAGE_TOUCH = 0x12;
    public static final int USAGE_MEDIA = 0x13;
    public static final int USAGE_RINGTONE = 0x21;
    public static final int USAGE_PHYSICAL_EMULATION = 0x22;
    public static final int USAGE_NOTIFICATION = 0x31;
    public static final int USAGE_HARDWARE_FEEDBACK = 0x32;
    public static final int USAGE_COMMUNICATION_REQUEST = 0x41;
    public static final int USAGE_ACCESSIBILITY = 0x42;

    public static final int FLAG_BYPASS_INTERRUPTION_POLICY = 0x01;

    public static final Parcelable.Creator<VibrationAttributes> CREATOR =
            new Parcelable.Creator<VibrationAttributes>() {
                @Override
                public VibrationAttributes createFromParcel(Parcel in) {
                    int usage = in.readInt();
                    int flags = in.readInt();
                    return new VibrationAttributes(usage, flags);
                }

                @Override
                public VibrationAttributes[] newArray(int size) {
                    return new VibrationAttributes[size];
                }
            };

    private final int usage;
    private final int flags;

    VibrationAttributes(int usage, int flags) {
        this.usage = usage;
        this.flags = flags;
    }

    public static VibrationAttributes createForUsage(int usage) {
        return new Builder().setUsage(usage).build();
    }

    public int getUsageClass() {
        return usage & USAGE_CLASS_MASK;
    }

    public int getUsage() {
        return usage;
    }

    public int getFlags() {
        return flags;
    }

    public boolean isFlagSet(int flag) {
        return (flags & flag) > 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(usage);
        dest.writeInt(this.flags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VibrationAttributes)) {
            return false;
        }
        VibrationAttributes other = (VibrationAttributes) o;
        return usage == other.usage && flags == other.flags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(usage), Integer.valueOf(flags));
    }

    @Override
    public String toString() {
        return "VibrationAttributes{usage=" + usage + ", flags=" + flags + "}";
    }

    public static final class Builder {
        private int usage = USAGE_UNKNOWN;
        private int flags = 0;

        public Builder() {
        }

        public Builder(VibrationAttributes vib) {
            if (vib != null) {
                usage = vib.usage;
                flags = vib.flags;
            }
        }

        public Builder(AudioAttributes audio) {
            if (audio != null) {
                switch (audio.getUsage()) {
                    case AudioAttributes.USAGE_ALARM:
                        usage = USAGE_ALARM;
                        break;
                    case AudioAttributes.USAGE_NOTIFICATION:
                    case AudioAttributes.USAGE_NOTIFICATION_EVENT:
                    case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT:
                    case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED:
                        usage = USAGE_NOTIFICATION;
                        break;
                    case AudioAttributes.USAGE_NOTIFICATION_RINGTONE:
                        usage = USAGE_RINGTONE;
                        break;
                    case AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST:
                    case AudioAttributes.USAGE_VOICE_COMMUNICATION:
                    case AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING:
                        usage = USAGE_COMMUNICATION_REQUEST;
                        break;
                    case AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY:
                        usage = USAGE_ACCESSIBILITY;
                        break;
                    case AudioAttributes.USAGE_ASSISTANCE_SONIFICATION:
                        usage = USAGE_TOUCH;
                        break;
                    case AudioAttributes.USAGE_MEDIA:
                    case AudioAttributes.USAGE_GAME:
                    case AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
                    case AudioAttributes.USAGE_ASSISTANT:
                        usage = USAGE_MEDIA;
                        break;
                    default:
                        usage = USAGE_UNKNOWN;
                        break;
                }
            }
        }

        public VibrationAttributes build() {
            return new VibrationAttributes(usage, flags);
        }

        public Builder setUsage(int usage) {
            this.usage = usage;
            return this;
        }

        public Builder setFlags(int flags, int mask) {
            this.flags = (this.flags & ~mask) | (flags & mask);
            return this;
        }
    }
}
