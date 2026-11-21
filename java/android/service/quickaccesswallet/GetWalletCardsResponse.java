// SPDX-License-Identifier: GPL-3.0-only
package android.service.quickaccesswallet;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class GetWalletCardsResponse implements Parcelable {
    public static final Parcelable.Creator<GetWalletCardsResponse> CREATOR =
            new Parcelable.Creator<GetWalletCardsResponse>() {
                @Override
                public GetWalletCardsResponse createFromParcel(Parcel source) {
                    List<WalletCard> cards = source.createTypedArrayList(WalletCard.CREATOR);
                    int selected = source.readInt();
                    return new GetWalletCardsResponse(
                            cards != null ? cards : Collections.<WalletCard>emptyList(),
                            selected);
                }

                @Override
                public GetWalletCardsResponse[] newArray(int size) {
                    return new GetWalletCardsResponse[size];
                }
            };

    private final List<WalletCard> mWalletCards;
    private final int mSelectedIndex;

    public GetWalletCardsResponse(List<WalletCard> walletCards, int selectedIndex) {
        mWalletCards = Collections.unmodifiableList(new ArrayList<WalletCard>(
                Objects.requireNonNull(walletCards, "walletCards must not be null")));
        mSelectedIndex = selectedIndex;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mWalletCards);
        dest.writeInt(mSelectedIndex);
    }

    public List<WalletCard> getWalletCards() {
        return mWalletCards;
    }

    public int getSelectedIndex() {
        return mSelectedIndex;
    }
}
