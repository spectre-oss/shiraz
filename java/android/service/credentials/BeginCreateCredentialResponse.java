// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Create destinations returned from {@link CredentialProviderService#onBeginCreateCredential}. */
public final class BeginCreateCredentialResponse implements Parcelable {
    private final List<CreateEntry> mCreateEntries;
    private final RemoteEntry mRemoteCreateEntry;

    public BeginCreateCredentialResponse() {
        this(Collections.<CreateEntry>emptyList(), null);
    }

    private BeginCreateCredentialResponse(
            List<CreateEntry> createEntries, RemoteEntry remoteCreateEntry) {
        this.mCreateEntries =
                Collections.unmodifiableList(new ArrayList<CreateEntry>(createEntries));
        this.mRemoteCreateEntry = remoteCreateEntry;
    }

    private BeginCreateCredentialResponse(Parcel in) {
        ArrayList<CreateEntry> entries = in.createTypedArrayList(CreateEntry.CREATOR);
        this.mCreateEntries =
                Collections.unmodifiableList(
                        entries != null ? entries : new ArrayList<CreateEntry>());
        this.mRemoteCreateEntry = in.readTypedObject(RemoteEntry.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mCreateEntries);
        dest.writeTypedObject(mRemoteCreateEntry, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public List<CreateEntry> getCreateEntries() {
        return mCreateEntries;
    }

    public RemoteEntry getRemoteCreateEntry() {
        return mRemoteCreateEntry;
    }

    public static final class Builder {
        private List<CreateEntry> mCreateEntries = new ArrayList<CreateEntry>();
        private RemoteEntry mRemoteCreateEntry;

        public Builder() {
        }

        public Builder setCreateEntries(List<CreateEntry> createEntries) {
            Objects.requireNonNull(createEntries, "createEntries must not be null");
            ArrayList<CreateEntry> copy = new ArrayList<CreateEntry>(createEntries.size());
            for (CreateEntry entry : createEntries) {
                copy.add(Objects.requireNonNull(entry, "createEntry must not be null"));
            }
            this.mCreateEntries = copy;
            return this;
        }

        public Builder addCreateEntry(CreateEntry createEntry) {
            this.mCreateEntries.add(
                    Objects.requireNonNull(createEntry, "createEntry must not be null"));
            return this;
        }

        public Builder setRemoteCreateEntry(RemoteEntry remoteCreateEntry) {
            this.mRemoteCreateEntry = remoteCreateEntry;
            return this;
        }

        public BeginCreateCredentialResponse build() {
            return new BeginCreateCredentialResponse(mCreateEntries, mRemoteCreateEntry);
        }
    }

    public static final Parcelable.Creator<BeginCreateCredentialResponse> CREATOR =
            new Parcelable.Creator<BeginCreateCredentialResponse>() {
                @Override
                public BeginCreateCredentialResponse createFromParcel(Parcel in) {
                    return new BeginCreateCredentialResponse(in);
                }

                @Override
                public BeginCreateCredentialResponse[] newArray(int size) {
                    return new BeginCreateCredentialResponse[size];
                }
            };
}
