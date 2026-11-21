// SPDX-License-Identifier: GPL-3.0-only
package android.service.quickaccesswallet;

import android.os.Parcel;
import android.os.Parcelable;

public final class WalletServiceEvent implements Parcelable {
    public static final int TYPE_NFC_PAYMENT_STARTED = 1;

    public static final Parcelable.Creator<WalletServiceEvent> CREATOR =
            new Parcelable.Creator<WalletServiceEvent>() {
                @Override
                public WalletServiceEvent createFromParcel(Parcel source) {
                    return new WalletServiceEvent(source.readInt());
                }

                @Override
                public WalletServiceEvent[] newArray(int size) {
                    return new WalletServiceEvent[size];
                }
            };

    private final int mEventType;

    public WalletServiceEvent(int eventType) {
        mEventType = eventType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mEventType);
    }

    public int getEventType() {
        return mEventType;
    }
}
