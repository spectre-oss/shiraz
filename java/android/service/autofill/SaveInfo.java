// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.content.IntentSender;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.autofill.AutofillId;
import java.util.Arrays;

/** Configuration used to indicate that an AutofillService is interested in saving user input. */
public final class SaveInfo implements Parcelable {
    public static final int SAVE_DATA_TYPE_GENERIC = 0;
    public static final int SAVE_DATA_TYPE_PASSWORD = 1;
    public static final int SAVE_DATA_TYPE_ADDRESS = 2;
    public static final int SAVE_DATA_TYPE_CREDIT_CARD = 4;
    public static final int SAVE_DATA_TYPE_USERNAME = 8;
    public static final int SAVE_DATA_TYPE_EMAIL_ADDRESS = 16;
    public static final int SAVE_DATA_TYPE_DEBIT_CARD = 32;
    public static final int SAVE_DATA_TYPE_PAYMENT_CARD = 64;
    public static final int SAVE_DATA_TYPE_GENERIC_CARD = 128;

    public static final int NEGATIVE_BUTTON_STYLE_CANCEL = 0;
    public static final int NEGATIVE_BUTTON_STYLE_REJECT = 1;
    public static final int NEGATIVE_BUTTON_STYLE_NEVER = 2;

    public static final int POSITIVE_BUTTON_STYLE_SAVE = 0;
    public static final int POSITIVE_BUTTON_STYLE_CONTINUE = 1;

    public static final int FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE = 1;
    public static final int FLAG_DONT_SAVE_ON_FINISH = 2;
    public static final int FLAG_DELAY_SAVE = 4;

    private final int mType;
    private final AutofillId[] mRequiredIds;
    private final AutofillId[] mOptionalIds;
    private final int mFlags;
    private final CharSequence mDescription;
    private final CustomDescription mCustomDescription;
    private final int mNegativeButtonStyle;
    private final IntentSender mNegativeActionListener;
    private final int mPositiveButtonStyle;
    private final AutofillId mTriggerId;

    SaveInfo(Builder b) {
        mType = b.mType;
        mRequiredIds = b.mRequiredIds;
        mOptionalIds = b.mOptionalIds;
        mFlags = b.mFlags;
        mDescription = b.mDescription;
        mCustomDescription = b.mCustomDescription;
        mNegativeButtonStyle = b.mNegativeButtonStyle;
        mNegativeActionListener = b.mNegativeActionListener;
        mPositiveButtonStyle = b.mPositiveButtonStyle;
        mTriggerId = b.mTriggerId;
    }

    SaveInfo(Parcel in) {
        mType = in.readInt();
        mRequiredIds = in.createTypedArray(AutofillId.CREATOR);
        mOptionalIds = in.createTypedArray(AutofillId.CREATOR);
        mFlags = in.readInt();
        mDescription = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
        mCustomDescription = in.readTypedObject(CustomDescription.CREATOR);
        mNegativeButtonStyle = in.readInt();
        mNegativeActionListener = in.readTypedObject(IntentSender.CREATOR);
        mPositiveButtonStyle = in.readInt();
        mTriggerId = in.readTypedObject(AutofillId.CREATOR);
    }

    @Override
    public String toString() {
        return "SaveInfo: [type=" + mType + ", required=" + Arrays.toString(mRequiredIds) + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mType);
        parcel.writeTypedArray(mRequiredIds, flags);
        parcel.writeTypedArray(mOptionalIds, flags);
        parcel.writeInt(mFlags);
        TextUtils.writeToParcel(mDescription, parcel, flags);
        parcel.writeTypedObject(mCustomDescription, flags);
        parcel.writeInt(mNegativeButtonStyle);
        parcel.writeTypedObject(mNegativeActionListener, flags);
        parcel.writeInt(mPositiveButtonStyle);
        parcel.writeTypedObject(mTriggerId, flags);
    }

    public static final Parcelable.Creator<SaveInfo> CREATOR =
            new Parcelable.Creator<SaveInfo>() {
                @Override
                public SaveInfo createFromParcel(Parcel parcel) {
                    return new SaveInfo(parcel);
                }

                @Override
                public SaveInfo[] newArray(int size) {
                    return new SaveInfo[size];
                }
            };

    public static final class Builder {
        final int mType;
        final AutofillId[] mRequiredIds;
        AutofillId[] mOptionalIds;
        int mFlags;
        CharSequence mDescription;
        CustomDescription mCustomDescription;
        int mNegativeButtonStyle = NEGATIVE_BUTTON_STYLE_CANCEL;
        IntentSender mNegativeActionListener;
        int mPositiveButtonStyle = POSITIVE_BUTTON_STYLE_SAVE;
        Validator mValidator;
        AutofillId mTriggerId;
        private boolean mDestroyed;

        public Builder(int type, AutofillId[] requiredIds) {
            if (requiredIds == null || requiredIds.length == 0) {
                throw new IllegalArgumentException("requiredIds cannot be null or empty");
            }
            mType = type;
            mRequiredIds = requiredIds.clone();
        }

        public Builder(int type) {
            mType = type;
            mRequiredIds = null;
        }

        public Builder setFlags(int flags) {
            mFlags = flags;
            return this;
        }

        public Builder setOptionalIds(AutofillId[] ids) {
            if (ids == null || ids.length == 0) {
                throw new IllegalArgumentException("ids cannot be null or empty");
            }
            mOptionalIds = ids.clone();
            return this;
        }

        public Builder setDescription(CharSequence description) {
            mDescription = description;
            return this;
        }

        public Builder setCustomDescription(CustomDescription customDescription) {
            mCustomDescription = customDescription;
            return this;
        }

        public Builder setNegativeAction(int style, IntentSender listener) {
            mNegativeButtonStyle = style;
            mNegativeActionListener = listener;
            return this;
        }

        public Builder setPositiveAction(int style) {
            mPositiveButtonStyle = style;
            return this;
        }

        public Builder setValidator(Validator validator) {
            if (!(validator instanceof Parcelable)) {
                throw new IllegalArgumentException("not a Parcelable: " + validator);
            }
            mValidator = validator;
            return this;
        }

        public Builder addSanitizer(Sanitizer sanitizer, AutofillId... ids) {
            if (ids == null || ids.length == 0) {
                throw new IllegalArgumentException("ids cannot be empty");
            }
            if (!(sanitizer instanceof Parcelable)) {
                throw new IllegalArgumentException("not a Parcelable: " + sanitizer);
            }
            return this;
        }

        public Builder setTriggerId(AutofillId id) {
            if (id == null) throw new NullPointerException("id");
            mTriggerId = id;
            return this;
        }

        public SaveInfo build() {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (mRequiredIds == null && mOptionalIds == null && (mFlags & FLAG_DELAY_SAVE) == 0) {
                throw new IllegalStateException("must have at least one required or optional id");
            }
            mDestroyed = true;
            return new SaveInfo(this);
        }
    }
}
