// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.util.Objects;

/** Descriptor for an energy measurement or power consumer monitor. */
public final class PowerMonitor implements Parcelable {
    public static final int POWER_MONITOR_TYPE_CONSUMER = 0;
    public static final int POWER_MONITOR_TYPE_MEASUREMENT = 1;

    public static final Parcelable.Creator<PowerMonitor> CREATOR =
            new Parcelable.Creator<PowerMonitor>() {
                @Override
                public PowerMonitor createFromParcel(Parcel in) {
                    int index = in.readInt();
                    int type = in.readInt();
                    String name = in.readString();
                    return new PowerMonitor(index, type, name);
                }

                @Override
                public PowerMonitor[] newArray(int size) {
                    return new PowerMonitor[size];
                }
            };

    public final int index;
    private final int type;
    private final String name;

    public PowerMonitor(int index, int type, String name) {
        this.index = index;
        this.type = type;
        this.name = Objects.requireNonNull(name, "name");
    }

    public int getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(index);
        dest.writeInt(type);
        dest.writeString(name);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
