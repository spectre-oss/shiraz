// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.changelog;

import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Record;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ChangeLogTokenRequest implements Parcelable {
    public static final Parcelable.Creator<ChangeLogTokenRequest> CREATOR =
            new Parcelable.Creator<ChangeLogTokenRequest>() {
                @Override
                @SuppressWarnings("unchecked")
                public ChangeLogTokenRequest createFromParcel(Parcel in) {
                    int originCount = in.readInt();
                    Set<DataOrigin> origins = new LinkedHashSet<DataOrigin>();
                    for (int i = 0; i < originCount; i++) {
                        String pkg = in.readString();
                        origins.add(new DataOrigin.Builder().setPackageName(pkg != null ? pkg : "").build());
                    }
                    int typeCount = in.readInt();
                    Set<Class<? extends Record>> types = new LinkedHashSet<Class<? extends Record>>();
                    for (int i = 0; i < typeCount; i++) {
                        String className = in.readString();
                        if (className != null) {
                            try {
                                Class<?> cls = Class.forName(className);
                                if (Record.class.isAssignableFrom(cls)) {
                                    types.add((Class<? extends Record>) cls);
                                }
                            } catch (ClassNotFoundException ignored) {
                            }
                        }
                    }
                    return new ChangeLogTokenRequest(origins, types);
                }

                @Override
                public ChangeLogTokenRequest[] newArray(int size) {
                    return new ChangeLogTokenRequest[size];
                }
            };

    private final Set<DataOrigin> mDataOriginFilters;
    private final Set<Class<? extends Record>> mRecordTypes;

    ChangeLogTokenRequest(Set<DataOrigin> dataOriginFilters,
            Set<Class<? extends Record>> recordTypes) {
        mDataOriginFilters = Collections.unmodifiableSet(
                new LinkedHashSet<DataOrigin>(dataOriginFilters));
        mRecordTypes = Collections.unmodifiableSet(
                new LinkedHashSet<Class<? extends Record>>(recordTypes));
    }

    public Set<DataOrigin> getDataOriginFilters() {
        return mDataOriginFilters;
    }

    public Set<Class<? extends Record>> getRecordTypes() {
        return mRecordTypes;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDataOriginFilters.size());
        for (DataOrigin origin : mDataOriginFilters) {
            dest.writeString(origin.getPackageName());
        }
        dest.writeInt(mRecordTypes.size());
        for (Class<? extends Record> type : mRecordTypes) {
            dest.writeString(type.getName());
        }
    }

    public static final class Builder {
        private final Set<DataOrigin> mDataOriginFilters = new LinkedHashSet<DataOrigin>();
        private final Set<Class<? extends Record>> mRecordTypes =
                new LinkedHashSet<Class<? extends Record>>();

        public Builder() {
        }

        public Builder addRecordType(Class<? extends Record> recordType) {
            Objects.requireNonNull(recordType, "recordType must not be null");
            mRecordTypes.add(recordType);
            return this;
        }

        public Builder addDataOriginFilter(DataOrigin dataOriginFilter) {
            Objects.requireNonNull(dataOriginFilter, "dataOriginFilter must not be null");
            mDataOriginFilters.add(dataOriginFilter);
            return this;
        }

        public ChangeLogTokenRequest build() {
            return new ChangeLogTokenRequest(mDataOriginFilters, mRecordTypes);
        }
    }
}
