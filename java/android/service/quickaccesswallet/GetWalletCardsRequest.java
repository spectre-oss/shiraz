// SPDX-License-Identifier: GPL-3.0-only
package android.service.quickaccesswallet;

import android.os.Parcel;
import android.os.Parcelable;

public final class GetWalletCardsRequest implements Parcelable {
    public static final Parcelable.Creator<GetWalletCardsRequest> CREATOR =
            new Parcelable.Creator<GetWalletCardsRequest>() {
                @Override
                public GetWalletCardsRequest createFromParcel(Parcel source) {
                    return new GetWalletCardsRequest(
                            source.readInt(),
                            source.readInt(),
                            source.readInt(),
                            source.readInt());
                }

                @Override
                public GetWalletCardsRequest[] newArray(int size) {
                    return new GetWalletCardsRequest[size];
                }
            };

    private final int mCardWidthPx;
    private final int mCardHeightPx;
    private final int mIconSizePx;
    private final int mMaxCards;

    public GetWalletCardsRequest(int cardWidthPx, int cardHeightPx, int iconSizePx, int maxCards) {
        mCardWidthPx = cardWidthPx;
        mCardHeightPx = cardHeightPx;
        mIconSizePx = iconSizePx;
        mMaxCards = maxCards;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCardWidthPx);
        dest.writeInt(mCardHeightPx);
        dest.writeInt(mIconSizePx);
        dest.writeInt(mMaxCards);
    }

    public int getCardWidthPx() { return mCardWidthPx; }
    public int getCardHeightPx() { return mCardHeightPx; }
    public int getIconSizePx() { return mIconSizePx; }
    public int getMaxCards() { return mMaxCards; }
}
