// SPDX-License-Identifier: GPL-3.0-only
package android.service.quickaccesswallet;

import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

public final class GetWalletCardsError implements Parcelable {
    public static final Parcelable.Creator<GetWalletCardsError> CREATOR =
            new Parcelable.Creator<GetWalletCardsError>() {
                @Override
                public GetWalletCardsError createFromParcel(Parcel source) {
                    Icon icon = source.readTypedObject(Icon.CREATOR);
                    CharSequence msg = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    return new GetWalletCardsError(icon, msg);
                }

                @Override
                public GetWalletCardsError[] newArray(int size) {
                    return new GetWalletCardsError[size];
                }
            };

    private final Icon mIcon;
    private final CharSequence mMessage;

    public GetWalletCardsError(Icon icon, CharSequence message) {
        mIcon = icon;
        mMessage = message;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mIcon, flags);
        TextUtils.writeToParcel(mMessage, dest, flags);
    }

    public Icon getIcon() {
        return mIcon;
    }

    public CharSequence getMessage() {
        return mMessage;
    }
}
