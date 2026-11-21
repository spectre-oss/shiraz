// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;

import android.os.Parcel;
import android.os.Parcelable;

public class WpsInfo implements Parcelable {
    public static final int PBC = 0;
    public static final int DISPLAY = 1;
    public static final int KEYPAD = 2;
    public static final int LABEL = 3;
    public static final int INVALID = 4;

    public static final Creator<WpsInfo> CREATOR =
            new Creator<WpsInfo>() {
                @Override
                public WpsInfo createFromParcel(Parcel in) {
                    WpsInfo config = new WpsInfo();
                    config.setup = in.readInt();
                    config.BSSID = in.readString();
                    config.pin = in.readString();
                    return config;
                }

                @Override
                public WpsInfo[] newArray(int size) {
                    return new WpsInfo[size];
                }
            };

    public int setup;
    public String BSSID;
    public String pin;

    public WpsInfo() {
        this.setup = INVALID;
        this.BSSID = null;
        this.pin = null;
    }

    public WpsInfo(WpsInfo source) {
        if (source != null) {
            this.setup = source.setup;
            this.BSSID = source.BSSID;
            this.pin = source.pin;
        }
    }

    @Override
    public String toString() {
        return " setup: " + setup + "\n BSSID: " + BSSID + "\n pin: " + pin + "\n";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(setup);
        dest.writeString(BSSID);
        dest.writeString(pin);
    }
}
