// SPDX-License-Identifier: GPL-3.0-only
package android.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Request encapsulating a list of {@link CredentialOption}s for credential retrieval. */
public final class GetCredentialRequest implements Parcelable {
    private final List<CredentialOption> mCredentialOptions;
    private final Bundle mData;
    private final boolean mAlwaysSendAppInfoToProvider;
    private final String mOrigin;

    private GetCredentialRequest(
            List<CredentialOption> credentialOptions,
            Bundle data,
            boolean alwaysSendAppInfoToProvider,
            String origin) {
        Objects.requireNonNull(credentialOptions, "credentialOptions must not be null");
        if (credentialOptions.isEmpty()) {
            throw new IllegalArgumentException("credentialOptions must not be empty");
        }
        ArrayList<CredentialOption> copy = new ArrayList<CredentialOption>(credentialOptions.size());
        for (CredentialOption option : credentialOptions) {
            copy.add(Objects.requireNonNull(option, "credentialOption must not be null"));
        }
        this.mCredentialOptions = Collections.unmodifiableList(copy);
        this.mData = new Bundle(Objects.requireNonNull(data, "data must not be null"));
        this.mAlwaysSendAppInfoToProvider = alwaysSendAppInfoToProvider;
        this.mOrigin = origin;
    }

    private GetCredentialRequest(Parcel in) {
        ArrayList<CredentialOption> options = in.createTypedArrayList(CredentialOption.CREATOR);
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("credentialOptions must not be empty");
        }
        this.mCredentialOptions = Collections.unmodifiableList(options);
        Bundle b = in.readBundle(GetCredentialRequest.class.getClassLoader());
        this.mData = b != null ? b : new Bundle();
        this.mAlwaysSendAppInfoToProvider = in.readBoolean();
        this.mOrigin = in.readString();
    }

    public List<CredentialOption> getCredentialOptions() {
        return mCredentialOptions;
    }

    public Bundle getData() {
        return new Bundle(mData);
    }

    public String getOrigin() {
        return mOrigin;
    }

    public boolean alwaysSendAppInfoToProvider() {
        return mAlwaysSendAppInfoToProvider;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mCredentialOptions);
        dest.writeBundle(mData);
        dest.writeBoolean(mAlwaysSendAppInfoToProvider);
        dest.writeString(mOrigin);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "GetCredentialRequest {credentialOptions="
                + mCredentialOptions
                + ", data="
                + mData
                + ", alwaysSendAppInfoToProvider="
                + mAlwaysSendAppInfoToProvider
                + ", origin="
                + mOrigin
                + "}";
    }

    public static final class Builder {
        private List<CredentialOption> mCredentialOptions = new ArrayList<CredentialOption>();
        private final Bundle mData;
        private String mOrigin;
        private boolean mAlwaysSendAppInfoToProvider = true;

        public Builder(Bundle data) {
            this.mData = new Bundle(Objects.requireNonNull(data, "data must not be null"));
        }

        public Builder addCredentialOption(CredentialOption credentialOption) {
            mCredentialOptions.add(
                    Objects.requireNonNull(
                            credentialOption, "credentialOption must not be null"));
            return this;
        }

        public Builder setAlwaysSendAppInfoToProvider(boolean value) {
            mAlwaysSendAppInfoToProvider = value;
            return this;
        }

        public Builder setCredentialOptions(List<CredentialOption> credentialOptions) {
            Objects.requireNonNull(credentialOptions, "credentialOptions must not be null");
            ArrayList<CredentialOption> copy =
                    new ArrayList<CredentialOption>(credentialOptions.size());
            for (CredentialOption option : credentialOptions) {
                copy.add(Objects.requireNonNull(option, "credentialOption must not be null"));
            }
            mCredentialOptions = copy;
            return this;
        }

        public Builder setOrigin(String origin) {
            mOrigin = origin;
            return this;
        }

        public GetCredentialRequest build() {
            return new GetCredentialRequest(
                    mCredentialOptions, mData, mAlwaysSendAppInfoToProvider, mOrigin);
        }
    }

    public static final Parcelable.Creator<GetCredentialRequest> CREATOR =
            new Parcelable.Creator<GetCredentialRequest>() {
                @Override
                public GetCredentialRequest createFromParcel(Parcel in) {
                    return new GetCredentialRequest(in);
                }

                @Override
                public GetCredentialRequest[] newArray(int size) {
                    return new GetCredentialRequest[size];
                }
            };
}
