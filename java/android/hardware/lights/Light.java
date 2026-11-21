// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.lights;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class Light implements Parcelable {
    public static final int LIGHT_TYPE_MICROPHONE = 8;
    public static final int LIGHT_TYPE_INPUT = 10001;
    public static final int LIGHT_TYPE_PLAYER_ID = 10002;
    public static final int LIGHT_TYPE_KEYBOARD_BACKLIGHT = 10003;

    @Deprecated
    public static final int LIGHT_CAPABILITY_RGB = 0;
    public static final int LIGHT_CAPABILITY_BRIGHTNESS = 1;
    public static final int LIGHT_CAPABILITY_COLOR_RGB = 2;

    private final int mId;
    private final String mName;
    private final int mOrdinal;
    private final int mType;
    private final int mCapabilities;

    public Light(int id, int ordinal, int type) {
        this(id, "Light", ordinal, type, 0);
    }

    public Light(int id, String name, int ordinal, int type, int capabilities) {
        mId = id;
        mName = name == null ? "" : name;
        mOrdinal = ordinal;
        mType = type;
        mCapabilities = capabilities;
    }

    public int getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public int getOrdinal() {
        return mOrdinal;
    }

    public int getType() {
        return mType;
    }

    public boolean hasBrightnessControl() {
        return (mCapabilities & LIGHT_CAPABILITY_BRIGHTNESS) == LIGHT_CAPABILITY_BRIGHTNESS;
    }

    public boolean hasRgbControl() {
        return (mCapabilities & LIGHT_CAPABILITY_COLOR_RGB) == LIGHT_CAPABILITY_COLOR_RGB;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeString(mName);
        dest.writeInt(mOrdinal);
        dest.writeInt(mType);
        dest.writeInt(mCapabilities);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Light)) return false;
        Light light = (Light) obj;
        return mId == light.mId
                && Objects.equals(mName, light.mName)
                && mOrdinal == light.mOrdinal
                && mType == light.mType
                && mCapabilities == light.mCapabilities;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mId),
                mName,
                Integer.valueOf(mOrdinal),
                Integer.valueOf(mType),
                Integer.valueOf(mCapabilities));
    }

    @Override
    public String toString() {
        return "[Name=" + mName + " Id=" + mId + " Type=" + mType
                + " Capabilities=" + mCapabilities + " Ordinal=" + mOrdinal + "]";
    }

    public static final Parcelable.Creator<Light> CREATOR =
            new Parcelable.Creator<Light>() {
                @Override
                public Light createFromParcel(Parcel in) {
                    return new Light(
                            in.readInt(),
                            in.readString(),
                            in.readInt(),
                            in.readInt(),
                            in.readInt());
                }

                @Override
                public Light[] newArray(int size) {
                    return new Light[size];
                }
            };
}
