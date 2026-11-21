// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GetByDocumentIdRequest implements Parcelable {
    public static final String PROJECTION_SCHEMA_TYPE_WILDCARD = "*";

    private final String mNamespace;
    private final Set<String> mIds;
    private final Map<String, List<String>> mProjections;

    private GetByDocumentIdRequest(
            String namespace, Set<String> ids, Map<String, List<String>> projections) {
        mNamespace = Objects.requireNonNull(namespace);
        mIds = Collections.unmodifiableSet(new LinkedHashSet<String>(ids));
        Map<String, List<String>> copy = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> e : projections.entrySet()) {
            copy.put(e.getKey(), Collections.unmodifiableList(new ArrayList<String>(e.getValue())));
        }
        mProjections = Collections.unmodifiableMap(copy);
    }

    GetByDocumentIdRequest(Parcel in) {
        mNamespace = in.readString();
        int idCount = in.readInt();
        Set<String> ids = new LinkedHashSet<String>(Math.max(0, idCount));
        for (int i = 0; i < idCount; i++) ids.add(in.readString());
        mIds = Collections.unmodifiableSet(ids);
        int projCount = in.readInt();
        Map<String, List<String>> projs = new LinkedHashMap<String, List<String>>();
        for (int i = 0; i < projCount; i++) {
            String key = in.readString();
            int listLen = in.readInt();
            List<String> list = new ArrayList<String>(Math.max(0, listLen));
            for (int j = 0; j < listLen; j++) list.add(in.readString());
            projs.put(key, Collections.unmodifiableList(list));
        }
        mProjections = Collections.unmodifiableMap(projs);
    }

    public String getNamespace() {
        return mNamespace;
    }

    public Set<String> getIds() {
        return mIds;
    }

    public Map<String, List<String>> getProjections() {
        return mProjections;
    }

    public Map<String, List<PropertyPath>> getProjectionPaths() {
        Map<String, List<PropertyPath>> out = new LinkedHashMap<String, List<PropertyPath>>();
        for (Map.Entry<String, List<String>> e : mProjections.entrySet()) {
            List<PropertyPath> paths = new ArrayList<PropertyPath>(e.getValue().size());
            for (String p : e.getValue()) {
                paths.add(new PropertyPath(p));
            }
            out.put(e.getKey(), Collections.unmodifiableList(paths));
        }
        return Collections.unmodifiableMap(out);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNamespace);
        dest.writeInt(mIds.size());
        for (String id : mIds) dest.writeString(id);
        dest.writeInt(mProjections.size());
        for (Map.Entry<String, List<String>> e : mProjections.entrySet()) {
            dest.writeString(e.getKey());
            dest.writeInt(e.getValue().size());
            for (String p : e.getValue()) dest.writeString(p);
        }
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<GetByDocumentIdRequest> CREATOR =
            new Parcelable.Creator<GetByDocumentIdRequest>() {
                @Override
                public GetByDocumentIdRequest createFromParcel(Parcel in) {
                    return new GetByDocumentIdRequest(in);
                }

                @Override
                public GetByDocumentIdRequest[] newArray(int size) {
                    return new GetByDocumentIdRequest[size];
                }
            };

    public static final class Builder {
        private final String mNamespace;
        private final Set<String> mIds = new LinkedHashSet<String>();
        private final Map<String, List<String>> mProjections =
                new LinkedHashMap<String, List<String>>();

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

        public Builder addProjection(String schemaType, Collection<String> propertyPaths) {
            Objects.requireNonNull(schemaType);
            Objects.requireNonNull(propertyPaths);
            List<String> list = new ArrayList<String>(propertyPaths.size());
            for (String p : propertyPaths) {
                Objects.requireNonNull(p);
                list.add(p);
            }
            mProjections.put(schemaType, list);
            return this;
        }

        public Builder addProjectionPaths(
                String schemaType, Collection<PropertyPath> propertyPaths) {
            Objects.requireNonNull(schemaType);
            Objects.requireNonNull(propertyPaths);
            List<String> list = new ArrayList<String>(propertyPaths.size());
            for (PropertyPath pp : propertyPaths) {
                Objects.requireNonNull(pp);
                list.add(pp.toString());
            }
            mProjections.put(schemaType, list);
            return this;
        }

        public GetByDocumentIdRequest build() {
            return new GetByDocumentIdRequest(mNamespace, mIds, mProjections);
        }
    }
}
