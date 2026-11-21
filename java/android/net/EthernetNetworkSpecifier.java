// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Network specifier selecting a specific Ethernet interface by name. */
public final class EthernetNetworkSpecifier extends NetworkSpecifier implements Parcelable {
    private final String mInterfaceName;

    public EthernetNetworkSpecifier(String interfaceName) {
        if (interfaceName == null || interfaceName.isEmpty()) {
            throw new IllegalArgumentException("interfaceName must not be null or empty");
        }
        this.mInterfaceName = interfaceName;
    }

    public String getInterfaceName() {
        return mInterfaceName;
    }

    public boolean canBeSatisfiedBy(NetworkSpecifier other) {
        return equals(other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EthernetNetworkSpecifier)) {
            return false;
        }
        return Objects.equals(mInterfaceName, ((EthernetNetworkSpecifier) o).mInterfaceName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mInterfaceName);
    }

    @Override
    public String toString() {
        return "EthernetNetworkSpecifier (" + mInterfaceName + ")";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mInterfaceName);
    }

    public static final Parcelable.Creator<EthernetNetworkSpecifier> CREATOR =
            new Parcelable.Creator<EthernetNetworkSpecifier>() {
                @Override
                public EthernetNetworkSpecifier createFromParcel(Parcel in) {
                    return new EthernetNetworkSpecifier(in.readString());
                }

                @Override
                public EthernetNetworkSpecifier[] newArray(int size) {
                    return new EthernetNetworkSpecifier[size];
                }
            };
}
