// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.app.slice.Slice;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Selectable credential candidate entry returned by a credential provider. */
public final class CredentialEntry implements Parcelable {
    private final String mBeginGetCredentialOptionId;
    private final String mType;
    private final Slice mSlice;

    public CredentialEntry(String beginGetCredentialOptionId, String type, Slice slice) {
        if (beginGetCredentialOptionId == null || beginGetCredentialOptionId.isEmpty()) {
            throw new IllegalArgumentException(
                    "beginGetCredentialOptionId must not be null, or empty");
        }
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be null, or empty");
        }
        this.mBeginGetCredentialOptionId = beginGetCredentialOptionId;
        this.mType = type;
        this.mSlice = Objects.requireNonNull(slice, "slice must not be null");
    }

    public CredentialEntry(BeginGetCredentialOption beginGetCredentialOption, Slice slice) {
        Objects.requireNonNull(
                beginGetCredentialOption, "beginGetCredentialOption must not be null");
        this.mBeginGetCredentialOptionId = beginGetCredentialOption.getId();
        this.mType = beginGetCredentialOption.getType();
        this.mSlice = Objects.requireNonNull(slice, "slice must not be null");
    }

    public CredentialEntry(String type, Slice slice) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be null, or empty");
        }
        this.mBeginGetCredentialOptionId = "";
        this.mType = type;
        this.mSlice = Objects.requireNonNull(slice, "slice must not be null");
    }

    private CredentialEntry(Parcel in) {
        String id = in.readString();
        this.mBeginGetCredentialOptionId = id != null ? id : "";
        this.mType = Objects.requireNonNull(in.readString(), "type");
        this.mSlice =
                Objects.requireNonNull(
                        in.readTypedObject(Slice.CREATOR), "slice must not be null");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mBeginGetCredentialOptionId);
        dest.writeString(mType);
        dest.writeTypedObject(mSlice, flags);
    }

    public String getBeginGetCredentialOptionId() {
        return mBeginGetCredentialOptionId;
    }

    public String getType() {
        return mType;
    }

    public Slice getSlice() {
        return mSlice;
    }

    public static final Parcelable.Creator<CredentialEntry> CREATOR =
            new Parcelable.Creator<CredentialEntry>() {
                @Override
                public CredentialEntry createFromParcel(Parcel in) {
                    return new CredentialEntry(in);
                }

                @Override
                public CredentialEntry[] newArray(int size) {
                    return new CredentialEntry[size];
                }
            };
}
