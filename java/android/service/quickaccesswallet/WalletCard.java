// SPDX-License-Identifier: GPL-3.0-only
package android.service.quickaccesswallet;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class WalletCard implements Parcelable {
    public static final int CARD_TYPE_UNKNOWN = 0;
    public static final int CARD_TYPE_PAYMENT = 1;
    public static final int CARD_TYPE_NON_PAYMENT = 2;

    public static final Parcelable.Creator<WalletCard> CREATOR =
            new Parcelable.Creator<WalletCard>() {
                @Override
                public WalletCard createFromParcel(Parcel source) {
                    String cardId = source.readString();
                    int cardType = source.readInt();
                    Icon cardImage = source.readTypedObject(Icon.CREATOR);
                    CharSequence contentDesc = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    PendingIntent pi = source.readTypedObject(PendingIntent.CREATOR);
                    Icon cardIcon = source.readTypedObject(Icon.CREATOR);
                    CharSequence cardLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
                    Icon secondary = source.readTypedObject(Icon.CREATOR);
                    List<Location> locations = source.createTypedArrayList(Location.CREATOR);
                    Builder b = new Builder(
                            cardId != null ? cardId : "",
                            cardType,
                            cardImage,
                            contentDesc != null ? contentDesc : "",
                            pi);
                    b.setCardIcon(cardIcon);
                    b.setCardLabel(cardLabel);
                    b.setNonPaymentCardSecondaryImage(secondary);
                    if (locations != null) {
                        b.setCardLocations(locations);
                    }
                    return b.build();
                }

                @Override
                public WalletCard[] newArray(int size) {
                    return new WalletCard[size];
                }
            };

    private final String mCardId;
    private final int mCardType;
    private final Icon mCardImage;
    private final CharSequence mContentDescription;
    private final PendingIntent mPendingIntent;
    private final Icon mCardIcon;
    private final CharSequence mCardLabel;
    private final Icon mNonPaymentCardSecondaryImage;
    private final List<Location> mCardLocations;

    WalletCard(Builder b) {
        mCardId = b.mCardId;
        mCardType = b.mCardType;
        mCardImage = b.mCardImage;
        mContentDescription = b.mContentDescription;
        mPendingIntent = b.mPendingIntent;
        mCardIcon = b.mCardIcon;
        mCardLabel = b.mCardLabel;
        mNonPaymentCardSecondaryImage = b.mNonPaymentCardSecondaryImage;
        mCardLocations = Collections.unmodifiableList(new ArrayList<Location>(b.mCardLocations));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mCardId);
        dest.writeInt(mCardType);
        dest.writeTypedObject(mCardImage, flags);
        TextUtils.writeToParcel(mContentDescription, dest, flags);
        dest.writeTypedObject(mPendingIntent, flags);
        dest.writeTypedObject(mCardIcon, flags);
        TextUtils.writeToParcel(mCardLabel, dest, flags);
        dest.writeTypedObject(mNonPaymentCardSecondaryImage, flags);
        dest.writeTypedList(mCardLocations);
    }

    public String getCardId() { return mCardId; }
    public int getCardType() { return mCardType; }
    public Icon getCardImage() { return mCardImage; }
    public CharSequence getContentDescription() { return mContentDescription; }
    public PendingIntent getPendingIntent() { return mPendingIntent; }
    public Icon getCardIcon() { return mCardIcon; }
    public CharSequence getCardLabel() { return mCardLabel; }
    public Icon getNonPaymentCardSecondaryImage() { return mNonPaymentCardSecondaryImage; }
    public List<Location> getCardLocations() { return mCardLocations; }

    public static final class Builder {
        final String mCardId;
        final int mCardType;
        final Icon mCardImage;
        final CharSequence mContentDescription;
        final PendingIntent mPendingIntent;
        Icon mCardIcon;
        CharSequence mCardLabel;
        Icon mNonPaymentCardSecondaryImage;
        List<Location> mCardLocations = new ArrayList<Location>();

        public Builder(String cardId, int cardType, Icon cardImage,
                CharSequence contentDescription, PendingIntent pendingIntent) {
            mCardId = Objects.requireNonNull(cardId, "cardId must not be null");
            mCardType = cardType;
            mCardImage = Objects.requireNonNull(cardImage, "cardImage must not be null");
            mContentDescription = Objects.requireNonNull(
                    contentDescription, "contentDescription must not be null");
            mPendingIntent = Objects.requireNonNull(pendingIntent, "pendingIntent must not be null");
        }

        public Builder(String cardId, Icon cardImage, CharSequence contentDescription,
                PendingIntent pendingIntent) {
            this(cardId, CARD_TYPE_UNKNOWN, cardImage, contentDescription, pendingIntent);
        }

        public Builder setCardIcon(Icon cardIcon) {
            mCardIcon = cardIcon;
            return this;
        }

        public Builder setCardLabel(CharSequence cardLabel) {
            mCardLabel = cardLabel;
            return this;
        }

        public Builder setNonPaymentCardSecondaryImage(Icon nonPaymentCardSecondaryImage) {
            mNonPaymentCardSecondaryImage = nonPaymentCardSecondaryImage;
            return this;
        }

        public Builder setCardLocations(List<Location> cardLocations) {
            Objects.requireNonNull(cardLocations, "cardLocations must not be null");
            mCardLocations = new ArrayList<Location>(cardLocations);
            return this;
        }

        public WalletCard build() {
            return new WalletCard(this);
        }
    }
}
