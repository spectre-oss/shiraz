// SPDX-License-Identifier: GPL-3.0-only
package android.credentials;

import android.os.Parcel;
import android.os.Parcelable;
import android.service.credentials.CredentialEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Description of credentials supported by an in-process or registered provider. */
public final class CredentialDescription implements Parcelable {
    private final String mType;
    private final Set<String> mSupportedElementKeys;
    private final List<CredentialEntry> mCredentialEntries;

    public CredentialDescription(
            String type,
            Set<String> supportedElementKeys,
            List<CredentialEntry> credentialEntries) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be empty");
        }
        Objects.requireNonNull(supportedElementKeys, "supportedElementKeys must not be null");
        Objects.requireNonNull(credentialEntries, "credentialEntries must not be null");
        LinkedHashSet<String> keysCopy = new LinkedHashSet<String>();
        for (String key : supportedElementKeys) {
            keysCopy.add(Objects.requireNonNull(key, "supportedElementKey must not be null"));
        }
        ArrayList<CredentialEntry> entriesCopy =
                new ArrayList<CredentialEntry>(credentialEntries.size());
        for (CredentialEntry entry : credentialEntries) {
            Objects.requireNonNull(entry, "credentialEntry must not be null");
            if (!type.equals(entry.getType())) {
                throw new IllegalArgumentException(
                        "CredentialEntry type mismatch: expected "
                                + type
                                + " but got "
                                + entry.getType());
            }
            entriesCopy.add(entry);
        }
        this.mType = type;
        this.mSupportedElementKeys = Collections.unmodifiableSet(keysCopy);
        this.mCredentialEntries = Collections.unmodifiableList(entriesCopy);
    }

    private CredentialDescription(Parcel in) {
        this.mType = Objects.requireNonNull(in.readString(), "type");
        int keyCount = in.readInt();
        LinkedHashSet<String> keys = new LinkedHashSet<String>();
        for (int i = 0; i < keyCount; i++) {
            String key = in.readString();
            if (key != null) {
                keys.add(key);
            }
        }
        this.mSupportedElementKeys = Collections.unmodifiableSet(keys);
        ArrayList<CredentialEntry> entries = in.createTypedArrayList(CredentialEntry.CREATOR);
        this.mCredentialEntries =
                Collections.unmodifiableList(
                        entries != null ? entries : new ArrayList<CredentialEntry>());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writeInt(mSupportedElementKeys.size());
        for (String key : mSupportedElementKeys) {
            dest.writeString(key);
        }
        dest.writeTypedList(mCredentialEntries);
    }

    public String getType() {
        return mType;
    }

    public Set<String> getSupportedElementKeys() {
        return mSupportedElementKeys;
    }

    public List<CredentialEntry> getCredentialEntries() {
        return mCredentialEntries;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mType, mSupportedElementKeys);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CredentialDescription)) {
            return false;
        }
        CredentialDescription other = (CredentialDescription) obj;
        return mType.equals(other.mType)
                && mSupportedElementKeys.equals(other.mSupportedElementKeys);
    }

    public static final Parcelable.Creator<CredentialDescription> CREATOR =
            new Parcelable.Creator<CredentialDescription>() {
                @Override
                public CredentialDescription createFromParcel(Parcel in) {
                    return new CredentialDescription(in);
                }

                @Override
                public CredentialDescription[] newArray(int size) {
                    return new CredentialDescription[size];
                }
            };
}
