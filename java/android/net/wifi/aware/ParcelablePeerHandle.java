// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import android.os.Parcel;
import android.os.Parcelable;

public final class ParcelablePeerHandle extends PeerHandle implements Parcelable {
    public static final Creator<ParcelablePeerHandle> CREATOR =
            new Creator<ParcelablePeerHandle>() {
                @Override
                public ParcelablePeerHandle createFromParcel(Parcel in) {
                    return new ParcelablePeerHandle(new PeerHandle(in.readInt()));
                }

                @Override
                public ParcelablePeerHandle[] newArray(int size) {
                    return new ParcelablePeerHandle[size];
                }
            };

    public ParcelablePeerHandle(PeerHandle peerHandle) {
        super(peerHandle.peerId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(peerId);
    }
}
