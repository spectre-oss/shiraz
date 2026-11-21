// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class RemoveByDocumentIdRequest implements Parcelable {
    private final String mNamespace;
    private final Set<String> mIds;

    private RemoveByDocumentIdRequest(String namespace, Set<String> ids) {
        mNamespace = Objects.requireNonNull(namespace);
        mIds = Collections.unmodifiableSet(new LinkedHashSet<String>(ids));
    }

    RemoveByDocumentIdRequest(Parcel in) {
        mNamespace = in.readString();
        int count = in.readInt();
        Set<String> set = new LinkedHashSet<String>(Math.max(0, count));
        for (int i = 0; i < count; i++) set.add(in.readString());
        mIds = Collections.unmodifiableSet(set);
    }

    public String getNamespace() {
        return mNamespace;
    }

    public Set<String> getIds() {
        return mIds;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNamespace);
        dest.writeInt(mIds.size());
        for (String id : mIds) dest.writeString(id);
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<RemoveByDocumentIdRequest> CREATOR =
            new Parcelable.Creator<RemoveByDocumentIdRequest>() {
                @Override
                public RemoveByDocumentIdRequest createFromParcel(Parcel in) {
                    return new RemoveByDocumentIdRequest(in);
                }

                @Override
                public RemoveByDocumentIdRequest[] newArray(int size) {
                    return new RemoveByDocumentIdRequest[size];
                }
            };

    public static final class Builder {
        private final String mNamespace;
        private final Set<String> mIds = new LinkedHashSet<String>();

        public Builder(String namespace) {
            mNamespace = Objects.requireNonNull(namespace);
        }

        public Builder addIds(String... ids) {
            Objects.requireNonNull(ids);
            return addIds(Arrays.asList(ids));
        }

        public Builder addIds(Collection<String> ids) {
            Objects.requireNonNull(ids);
            for (String id : ids) {
                Objects.requireNonNull(id);
                mIds.add(id);
            }
            return this;
        }

        public RemoveByDocumentIdRequest build() {
            return new RemoveByDocumentIdRequest(mNamespace, mIds);
        }
    }
}
