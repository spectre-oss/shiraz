// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Candidate query parameters for a single credential option during the begin-get phase. */
public final class BeginGetCredentialOption implements Parcelable {
    private final String mId;
    private final String mType;
    private final Bundle mCandidateQueryData;

    public BeginGetCredentialOption(String id, String type, Bundle candidateQueryData) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be empty");
        }
        this.mId = id;
        this.mType = type;
        this.mCandidateQueryData =
                candidateQueryData == null ? new Bundle() : new Bundle(candidateQueryData);
    }

    private BeginGetCredentialOption(Parcel in) {
        this.mId = Objects.requireNonNull(in.readString(), "id");
        this.mType = Objects.requireNonNull(in.readString(), "type");
        Bundle b = in.readBundle(BeginGetCredentialOption.class.getClassLoader());
        this.mCandidateQueryData = b != null ? b : new Bundle();
    }

    public String getId() {
        return mId;
    }

    public String getType() {
        return mType;
    }

    public Bundle getCandidateQueryData() {
        return new Bundle(mCandidateQueryData);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeString(mType);
        dest.writeBundle(mCandidateQueryData);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "BeginGetCredentialOption {id="
                + mId
                + ", type="
                + mType
                + ", candidateQueryData="
                + mCandidateQueryData
                + "}";
    }

    public static final Parcelable.Creator<BeginGetCredentialOption> CREATOR =
            new Parcelable.Creator<BeginGetCredentialOption>() {
                @Override
                public BeginGetCredentialOption createFromParcel(Parcel in) {
                    return new BeginGetCredentialOption(in);
                }

                @Override
                public BeginGetCredentialOption[] newArray(int size) {
                    return new BeginGetCredentialOption[size];
                }
            };
}
