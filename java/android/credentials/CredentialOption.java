// SPDX-License-Identifier: GPL-3.0-only
package android.credentials;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Specific credential type option requested inside a {@link GetCredentialRequest}. */
public final class CredentialOption implements Parcelable {
    public static final String SUPPORTED_ELEMENT_KEYS =
            "android.credentials.GetCredentialOption.SUPPORTED_ELEMENT_KEYS";

    private final String mType;
    private final Bundle mCredentialRetrievalData;
    private final Bundle mCandidateQueryData;
    private final boolean mIsSystemProviderRequired;
    private final Set<ComponentName> mAllowedProviders;

    private CredentialOption(
            String type,
            Bundle credentialRetrievalData,
            Bundle candidateQueryData,
            boolean isSystemProviderRequired,
            Set<ComponentName> allowedProviders) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be empty");
        }
        this.mType = type;
        this.mCredentialRetrievalData =
                new Bundle(
                        Objects.requireNonNull(
                                credentialRetrievalData,
                                "credentialRetrievalData must not be null"));
        this.mCandidateQueryData =
                new Bundle(
                        Objects.requireNonNull(
                                candidateQueryData, "candidateQueryData must not be null"));
        this.mIsSystemProviderRequired = isSystemProviderRequired;
        this.mAllowedProviders =
                Collections.unmodifiableSet(new LinkedHashSet<ComponentName>(allowedProviders));
    }

    private CredentialOption(Parcel in) {
        this.mType = Objects.requireNonNull(in.readString(), "type");
        Bundle retrieval = in.readBundle(CredentialOption.class.getClassLoader());
        this.mCredentialRetrievalData = retrieval != null ? retrieval : new Bundle();
        Bundle candidate = in.readBundle(CredentialOption.class.getClassLoader());
        this.mCandidateQueryData = candidate != null ? candidate : new Bundle();
        this.mIsSystemProviderRequired = in.readBoolean();
        int count = in.readInt();
        LinkedHashSet<ComponentName> providers = new LinkedHashSet<ComponentName>();
        for (int i = 0; i < count; i++) {
            String flat = in.readString();
            if (flat != null) {
                ComponentName cn = ComponentName.unflattenFromString(flat);
                if (cn != null) {
                    providers.add(cn);
                }
            }
        }
        this.mAllowedProviders = Collections.unmodifiableSet(providers);
    }

    public String getType() {
        return mType;
    }

    public Bundle getCredentialRetrievalData() {
        return new Bundle(mCredentialRetrievalData);
    }

    public Bundle getCandidateQueryData() {
        return new Bundle(mCandidateQueryData);
    }

    public boolean isSystemProviderRequired() {
        return mIsSystemProviderRequired;
    }

    public Set<ComponentName> getAllowedProviders() {
        return mAllowedProviders;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writeBundle(mCredentialRetrievalData);
        dest.writeBundle(mCandidateQueryData);
        dest.writeBoolean(mIsSystemProviderRequired);
        dest.writeInt(mAllowedProviders.size());
        for (ComponentName provider : mAllowedProviders) {
            dest.writeString(provider.flattenToString());
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "CredentialOption {type="
                + mType
                + ", credentialRetrievalData="
                + mCredentialRetrievalData
                + ", candidateQueryData="
                + mCandidateQueryData
                + ", isSystemProviderRequired="
                + mIsSystemProviderRequired
                + ", allowedProviders="
                + mAllowedProviders
                + "}";
    }

    public static final class Builder {
        private final String mType;
        private final Bundle mCredentialRetrievalData;
        private final Bundle mCandidateQueryData;
        private boolean mIsSystemProviderRequired = false;
        private Set<ComponentName> mAllowedProviders = new LinkedHashSet<ComponentName>();

        public Builder(
                String type, Bundle credentialRetrievalData, Bundle candidateQueryData) {
            if (type == null || type.isEmpty()) {
                throw new IllegalArgumentException("type must not be empty");
            }
            this.mType = type;
            this.mCredentialRetrievalData =
                    new Bundle(
                            Objects.requireNonNull(
                                    credentialRetrievalData,
                                    "credentialRetrievalData must not be null"));
            this.mCandidateQueryData =
                    new Bundle(
                            Objects.requireNonNull(
                                    candidateQueryData,
                                    "candidateQueryData must not be null"));
        }

        public Builder setIsSystemProviderRequired(boolean isSystemProviderRequired) {
            this.mIsSystemProviderRequired = isSystemProviderRequired;
            return this;
        }

        public Builder addAllowedProvider(ComponentName allowedProvider) {
            this.mAllowedProviders.add(
                    Objects.requireNonNull(
                            allowedProvider, "allowedProvider must not be null"));
            return this;
        }

        public Builder setAllowedProviders(Set<ComponentName> allowedProviders) {
            Objects.requireNonNull(allowedProviders, "allowedProviders must not be null");
            LinkedHashSet<ComponentName> copy = new LinkedHashSet<ComponentName>();
            for (ComponentName cn : allowedProviders) {
                copy.add(Objects.requireNonNull(cn, "allowedProvider element must not be null"));
            }
            this.mAllowedProviders = copy;
            return this;
        }

        public CredentialOption build() {
            return new CredentialOption(
                    mType,
                    mCredentialRetrievalData,
                    mCandidateQueryData,
                    mIsSystemProviderRequired,
                    mAllowedProviders);
        }
    }

    public static final Parcelable.Creator<CredentialOption> CREATOR =
            new Parcelable.Creator<CredentialOption>() {
                @Override
                public CredentialOption createFromParcel(Parcel in) {
                    return new CredentialOption(in);
                }

                @Override
                public CredentialOption[] newArray(int size) {
                    return new CredentialOption[size];
                }
            };
}
