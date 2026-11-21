// SPDX-License-Identifier: GPL-3.0-only
package android.credentials;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Request to unregister one or more {@link CredentialDescription} instances. */
public final class UnregisterCredentialDescriptionRequest implements Parcelable {
    private final Set<CredentialDescription> mCredentialDescriptions;

    public UnregisterCredentialDescriptionRequest(CredentialDescription credentialDescription) {
        Objects.requireNonNull(credentialDescription, "credentialDescription must not be null");
        LinkedHashSet<CredentialDescription> set = new LinkedHashSet<CredentialDescription>();
        set.add(credentialDescription);
        this.mCredentialDescriptions = Collections.unmodifiableSet(set);
    }

    public UnregisterCredentialDescriptionRequest(
            Set<CredentialDescription> credentialDescriptions) {
        Objects.requireNonNull(
                credentialDescriptions, "credentialDescriptions must not be null");
        LinkedHashSet<CredentialDescription> set = new LinkedHashSet<CredentialDescription>();
        for (CredentialDescription desc : credentialDescriptions) {
            set.add(Objects.requireNonNull(desc, "credentialDescription must not be null"));
        }
        this.mCredentialDescriptions = Collections.unmodifiableSet(set);
    }

    private UnregisterCredentialDescriptionRequest(Parcel in) {
        ArrayList<CredentialDescription> list =
                in.createTypedArrayList(CredentialDescription.CREATOR);
        LinkedHashSet<CredentialDescription> set = new LinkedHashSet<CredentialDescription>();
        if (list != null) {
            set.addAll(list);
        }
        this.mCredentialDescriptions = Collections.unmodifiableSet(set);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(new ArrayList<CredentialDescription>(mCredentialDescriptions));
    }

    public Set<CredentialDescription> getCredentialDescriptions() {
        return mCredentialDescriptions;
    }

    public static final Parcelable.Creator<UnregisterCredentialDescriptionRequest> CREATOR =
            new Parcelable.Creator<UnregisterCredentialDescriptionRequest>() {
                @Override
                public UnregisterCredentialDescriptionRequest createFromParcel(Parcel in) {
                    return new UnregisterCredentialDescriptionRequest(in);
                }

                @Override
                public UnregisterCredentialDescriptionRequest[] newArray(int size) {
                    return new UnregisterCredentialDescriptionRequest[size];
                }
            };
}
