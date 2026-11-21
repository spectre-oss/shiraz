// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.euicc;

import android.os.Parcel;
import android.os.Parcelable;

public final class EuiccInfo implements Parcelable {
    private final String osVersion;

    public EuiccInfo(String osVersion) {
        this.osVersion = osVersion;
    }

    private EuiccInfo(Parcel in) {
        osVersion = in.readString();
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(osVersion);
    }

    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<EuiccInfo> CREATOR =
            new Parcelable.Creator<EuiccInfo>() {
                public EuiccInfo createFromParcel(Parcel in) {
                    return new EuiccInfo(in);
                }

                public EuiccInfo[] newArray(int size) {
                    return new EuiccInfo[size];
                }
            };
}
