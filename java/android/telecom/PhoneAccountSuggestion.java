// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Suggested {@link PhoneAccountHandle} for placing an outgoing call. */
public final class PhoneAccountSuggestion implements Parcelable {
    public static final int REASON_NONE = 0;
    public static final int REASON_INTRA_CARRIER = 1;
    public static final int REASON_FREQUENT = 2;
    public static final int REASON_USER_SET = 3;
    public static final int REASON_OTHER = 4;

    private final PhoneAccountHandle mHandle;
    private final int mReason;
    private final boolean mShouldAutoSelect;

    public PhoneAccountSuggestion(
            PhoneAccountHandle handle, int reason, boolean shouldAutoSelect) {
        this.mHandle = Objects.requireNonNull(handle, "handle");
        this.mReason = reason;
        this.mShouldAutoSelect = shouldAutoSelect;
    }

    private PhoneAccountSuggestion(Parcel in) {
        this.mHandle = in.readTypedObject(PhoneAccountHandle.CREATOR);
        this.mReason = in.readInt();
        this.mShouldAutoSelect = in.readBoolean();
    }

    public PhoneAccountHandle getPhoneAccountHandle() {
        return mHandle;
    }

    public int getReason() {
        return mReason;
    }

    public boolean shouldAutoSelect() {
        return mShouldAutoSelect;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mHandle, flags);
        dest.writeInt(mReason);
        dest.writeBoolean(mShouldAutoSelect);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PhoneAccountSuggestion)) {
            return false;
        }
        PhoneAccountSuggestion that = (PhoneAccountSuggestion) o;
        return mReason == that.mReason
                && mShouldAutoSelect == that.mShouldAutoSelect
                && Objects.equals(mHandle, that.mHandle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mHandle, Integer.valueOf(mReason), Boolean.valueOf(mShouldAutoSelect));
    }

    public static final Parcelable.Creator<PhoneAccountSuggestion> CREATOR =
            new Parcelable.Creator<PhoneAccountSuggestion>() {
                @Override
                public PhoneAccountSuggestion createFromParcel(Parcel in) {
                    return new PhoneAccountSuggestion(in);
                }

                @Override
                public PhoneAccountSuggestion[] newArray(int size) {
                    return new PhoneAccountSuggestion[size];
                }
            };
}
