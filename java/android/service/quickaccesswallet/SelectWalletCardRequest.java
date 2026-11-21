// SPDX-License-Identifier: GPL-3.0-only
package android.service.quickaccesswallet;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class SelectWalletCardRequest implements Parcelable {
    public static final Parcelable.Creator<SelectWalletCardRequest> CREATOR =
            new Parcelable.Creator<SelectWalletCardRequest>() {
                @Override
                public SelectWalletCardRequest createFromParcel(Parcel source) {
                    String id = source.readString();
                    return new SelectWalletCardRequest(id != null ? id : "");
                }

                @Override
                public SelectWalletCardRequest[] newArray(int size) {
                    return new SelectWalletCardRequest[size];
                }
            };

    private final String mCardId;

    public SelectWalletCardRequest(String cardId) {
        mCardId = Objects.requireNonNull(cardId, "cardId must not be null");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mCardId);
    }

    public String getCardId() {
        return mCardId;
    }
}
