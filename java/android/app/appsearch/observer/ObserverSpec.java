// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch.observer;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ObserverSpec implements Parcelable {
    private final Set<String> mFilterSchemas;

    private ObserverSpec(Collection<String> filterSchemas) {
        mFilterSchemas = Collections.unmodifiableSet(new LinkedHashSet<String>(filterSchemas));
    }

    ObserverSpec(Parcel in) {
        int count = in.readInt();
        Set<String> set = new LinkedHashSet<String>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            set.add(in.readString());
        }
        mFilterSchemas = Collections.unmodifiableSet(set);
    }

    public Set<String> getFilterSchemas() {
        return mFilterSchemas;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mFilterSchemas.size());
        for (String schema : mFilterSchemas) {
            dest.writeString(schema);
        }
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<ObserverSpec> CREATOR =
            new Parcelable.Creator<ObserverSpec>() {
                @Override
                public ObserverSpec createFromParcel(Parcel in) {
                    return new ObserverSpec(in);
                }

                @Override
                public ObserverSpec[] newArray(int size) {
                    return new ObserverSpec[size];
                }
            };

    public static final class Builder {
        private final Set<String> mFilterSchemas = new LinkedHashSet<String>();

        public Builder() {
        }

        public Builder addFilterSchemas(String... schemas) {
            Objects.requireNonNull(schemas);
            return addFilterSchemas(Arrays.asList(schemas));
        }

        public Builder addFilterSchemas(Collection<String> schemas) {
            Objects.requireNonNull(schemas);
            for (String s : schemas) {
                Objects.requireNonNull(s);
                mFilterSchemas.add(s);
            }
            return this;
        }

        public ObserverSpec build() {
            return new ObserverSpec(mFilterSchemas);
        }
    }
}
