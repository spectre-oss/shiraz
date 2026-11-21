// SPDX-License-Identifier: GPL-3.0-only
package android.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Request for storing a newly created user credential in a credential provider. */
public final class CreateCredentialRequest implements Parcelable {
    private final String mType;
    private final Bundle mCredentialData;
    private final Bundle mCandidateQueryData;
    private final boolean mIsSystemProviderRequired;
    private final boolean mAlwaysSendAppInfoToProvider;
    private final String mOrigin;

    private CreateCredentialRequest(
            String type,
            Bundle credentialData,
            Bundle candidateQueryData,
            boolean isSystemProviderRequired,
            boolean alwaysSendAppInfoToProvider,
            String origin) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be empty");
        }
        this.mType = type;
        this.mCredentialData =
                new Bundle(Objects.requireNonNull(credentialData, "credentialData must not be null"));
        this.mCandidateQueryData =
                new Bundle(
                        Objects.requireNonNull(
                                candidateQueryData, "candidateQueryData must not be null"));
        this.mIsSystemProviderRequired = isSystemProviderRequired;
        this.mAlwaysSendAppInfoToProvider = alwaysSendAppInfoToProvider;
        this.mOrigin = origin;
    }

    private CreateCredentialRequest(Parcel in) {
        this.mType = Objects.requireNonNull(in.readString(), "type");
        Bundle cred = in.readBundle(CreateCredentialRequest.class.getClassLoader());
        this.mCredentialData = cred != null ? cred : new Bundle();
        Bundle cand = in.readBundle(CreateCredentialRequest.class.getClassLoader());
        this.mCandidateQueryData = cand != null ? cand : new Bundle();
        this.mIsSystemProviderRequired = in.readBoolean();
        this.mAlwaysSendAppInfoToProvider = in.readBoolean();
        this.mOrigin = in.readString();
    }

    public String getType() {
        return mType;
    }

    public Bundle getCredentialData() {
        return new Bundle(mCredentialData);
    }

    public Bundle getCandidateQueryData() {
        return new Bundle(mCandidateQueryData);
    }

    public boolean isSystemProviderRequired() {
        return mIsSystemProviderRequired;
    }

    public boolean alwaysSendAppInfoToProvider() {
        return mAlwaysSendAppInfoToProvider;
    }

    public String getOrigin() {
        return mOrigin;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writeBundle(mCredentialData);
        dest.writeBundle(mCandidateQueryData);
        dest.writeBoolean(mIsSystemProviderRequired);
        dest.writeBoolean(mAlwaysSendAppInfoToProvider);
        dest.writeString(mOrigin);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "CreateCredentialRequest {type="
                + mType
                + ", credentialData="
                + mCredentialData
                + ", candidateQueryData="
                + mCandidateQueryData
                + ", isSystemProviderRequired="
                + mIsSystemProviderRequired
                + ", alwaysSendAppInfoToProvider="
                + mAlwaysSendAppInfoToProvider
                + ", origin="
                + mOrigin
                + "}";
    }

    public static final class Builder {
        private final String mType;
        private final Bundle mCredentialData;
        private final Bundle mCandidateQueryData;
        private boolean mAlwaysSendAppInfoToProvider = true;
        private boolean mIsSystemProviderRequired = false;
        private String mOrigin;

        public Builder(String type, Bundle credentialData, Bundle candidateQueryData) {
            if (type == null || type.isEmpty()) {
                throw new IllegalArgumentException("type must not be empty");
            }
            this.mType = type;
            this.mCredentialData =
                    new Bundle(
                            Objects.requireNonNull(
                                    credentialData, "credentialData must not be null"));
            this.mCandidateQueryData =
                    new Bundle(
                            Objects.requireNonNull(
                                    candidateQueryData, "candidateQueryData must not be null"));
        }

        public Builder setAlwaysSendAppInfoToProvider(boolean value) {
            mAlwaysSendAppInfoToProvider = value;
            return this;
        }

        public Builder setIsSystemProviderRequired(boolean value) {
            mIsSystemProviderRequired = value;
            return this;
        }

        public Builder setOrigin(String origin) {
            mOrigin = origin;
            return this;
        }

        public CreateCredentialRequest build() {
            return new CreateCredentialRequest(
                    mType,
                    mCredentialData,
                    mCandidateQueryData,
                    mIsSystemProviderRequired,
                    mAlwaysSendAppInfoToProvider,
                    mOrigin);
        }
    }

    public static final Parcelable.Creator<CreateCredentialRequest> CREATOR =
            new Parcelable.Creator<CreateCredentialRequest>() {
                @Override
                public CreateCredentialRequest createFromParcel(Parcel in) {
                    return new CreateCredentialRequest(in);
                }

                @Override
                public CreateCredentialRequest[] newArray(int size) {
                    return new CreateCredentialRequest[size];
                }
            };
}
