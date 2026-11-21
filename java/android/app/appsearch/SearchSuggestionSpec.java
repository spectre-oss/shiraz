// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SearchSuggestionSpec implements Parcelable {
    public static final int SUGGESTION_RANKING_STRATEGY_DOCUMENT_COUNT = 0;
    public static final int SUGGESTION_RANKING_STRATEGY_TERM_FREQUENCY = 1;
    public static final int SUGGESTION_RANKING_STRATEGY_NONE = 2;

    private final int mMaximumResultCount;
    private final int mRankingStrategy;
    private final List<String> mFilterNamespaces;
    private final List<String> mFilterSchemas;
    private final Map<String, List<String>> mFilterProperties;
    private final Map<String, List<String>> mFilterDocumentIds;

    SearchSuggestionSpec(Builder b) {
        mMaximumResultCount = b.mTotalResultCount;
        mRankingStrategy = b.mRankingStrategy;
        mFilterNamespaces = Collections.unmodifiableList(
                new ArrayList<String>(b.mNamespaces));
        mFilterSchemas = Collections.unmodifiableList(
                new ArrayList<String>(b.mSchemas));
        mFilterProperties = copyMap(b.mTypePropertyFilters);
        mFilterDocumentIds = copyMap(b.mDocumentIds);
    }

    SearchSuggestionSpec(Parcel in) {
        mMaximumResultCount = in.readInt();
        mRankingStrategy = in.readInt();
        mFilterNamespaces = readList(in);
        mFilterSchemas = readList(in);
        mFilterProperties = readMap(in);
        mFilterDocumentIds = readMap(in);
    }

    private static Map<String, List<String>> copyMap(Map<String, List<String>> src) {
        Map<String, List<String>> out = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> e : src.entrySet()) {
            out.put(e.getKey(), Collections.unmodifiableList(new ArrayList<String>(e.getValue())));
        }
        return Collections.unmodifiableMap(out);
    }

    private static List<String> readList(Parcel in) {
        int n = in.readInt();
        List<String> list = new ArrayList<String>(Math.max(0, n));
        for (int i = 0; i < n; i++) list.add(in.readString());
        return Collections.unmodifiableList(list);
    }

    private static void writeList(Parcel dest, List<String> list) {
        dest.writeInt(list.size());
        for (String s : list) dest.writeString(s);
    }

    private static Map<String, List<String>> readMap(Parcel in) {
        int n = in.readInt();
        Map<String, List<String>> map = new LinkedHashMap<String, List<String>>();
        for (int i = 0; i < n; i++) {
            String k = in.readString();
            map.put(k, readList(in));
        }
        return Collections.unmodifiableMap(map);
    }

    private static void writeMap(Parcel dest, Map<String, List<String>> map) {
        dest.writeInt(map.size());
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            dest.writeString(e.getKey());
            writeList(dest, e.getValue());
        }
    }

    public int getMaximumResultCount() {
        return mMaximumResultCount;
    }

    public List<String> getFilterNamespaces() {
        return mFilterNamespaces;
    }

    public int getRankingStrategy() {
        return mRankingStrategy;
    }

    public List<String> getFilterSchemas() {
        return mFilterSchemas;
    }

    public Map<String, List<String>> getFilterProperties() {
        return mFilterProperties;
    }

    public Map<String, List<String>> getFilterDocumentIds() {
        return mFilterDocumentIds;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mMaximumResultCount);
        dest.writeInt(mRankingStrategy);
        writeList(dest, mFilterNamespaces);
        writeList(dest, mFilterSchemas);
        writeMap(dest, mFilterProperties);
        writeMap(dest, mFilterDocumentIds);
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<SearchSuggestionSpec> CREATOR =
            new Parcelable.Creator<SearchSuggestionSpec>() {
                @Override
                public SearchSuggestionSpec createFromParcel(Parcel in) {
                    return new SearchSuggestionSpec(in);
                }

                @Override
                public SearchSuggestionSpec[] newArray(int size) {
                    return new SearchSuggestionSpec[size];
                }
            };

    public static final class Builder {
        final int mTotalResultCount;
        int mRankingStrategy = SUGGESTION_RANKING_STRATEGY_DOCUMENT_COUNT;
        final List<String> mNamespaces = new ArrayList<String>();
        final List<String> mSchemas = new ArrayList<String>();
        final Map<String, List<String>> mTypePropertyFilters =
                new LinkedHashMap<String, List<String>>();
        final Map<String, List<String>> mDocumentIds =
                new LinkedHashMap<String, List<String>>();

        public Builder(int maximumResultCount) {
            if (maximumResultCount < 1) {
                throw new IllegalArgumentException("maximumResultCount must be positive");
            }
            mTotalResultCount = maximumResultCount;
        }

        public Builder addFilterNamespaces(String... namespaces) {
            Objects.requireNonNull(namespaces);
            return addFilterNamespaces(Arrays.asList(namespaces));
        }

        public Builder addFilterNamespaces(Collection<String> namespaces) {
            Objects.requireNonNull(namespaces);
            for (String ns : namespaces) {
                Objects.requireNonNull(ns);
                mNamespaces.add(ns);
            }
            return this;
        }

        public Builder setRankingStrategy(int rankingStrategy) {
            if (rankingStrategy < SUGGESTION_RANKING_STRATEGY_DOCUMENT_COUNT
                    || rankingStrategy > SUGGESTION_RANKING_STRATEGY_NONE) {
                throw new IllegalArgumentException("Invalid rankingStrategy: " + rankingStrategy);
            }
            mRankingStrategy = rankingStrategy;
            return this;
        }

        public Builder addFilterSchemas(String... schemaTypes) {
            Objects.requireNonNull(schemaTypes);
            return addFilterSchemas(Arrays.asList(schemaTypes));
        }

        public Builder addFilterSchemas(Collection<String> schemaTypes) {
            Objects.requireNonNull(schemaTypes);
            for (String s : schemaTypes) {
                Objects.requireNonNull(s);
                mSchemas.add(s);
            }
            return this;
        }

        public Builder addFilterProperties(String schema, Collection<String> propertyPaths) {
            Objects.requireNonNull(schema);
            Objects.requireNonNull(propertyPaths);
            List<String> list = mTypePropertyFilters.get(schema);
            if (list == null) {
                list = new ArrayList<String>();
                mTypePropertyFilters.put(schema, list);
            }
            for (String p : propertyPaths) {
                Objects.requireNonNull(p);
                list.add(p);
            }
            return this;
        }

        public Builder addFilterPropertyPaths(
                String schema, Collection<PropertyPath> propertyPaths) {
            Objects.requireNonNull(schema);
            Objects.requireNonNull(propertyPaths);
            List<String> strs = new ArrayList<String>(propertyPaths.size());
            for (PropertyPath pp : propertyPaths) {
                Objects.requireNonNull(pp);
                strs.add(pp.toString());
            }
            return addFilterProperties(schema, strs);
        }

        public Builder addFilterDocumentIds(String namespace, String... documentIds) {
            Objects.requireNonNull(namespace);
            Objects.requireNonNull(documentIds);
            return addFilterDocumentIds(namespace, Arrays.asList(documentIds));
        }

        public Builder addFilterDocumentIds(String namespace, Collection<String> documentIds) {
            Objects.requireNonNull(namespace);
            Objects.requireNonNull(documentIds);
            List<String> list = mDocumentIds.get(namespace);
            if (list == null) {
                list = new ArrayList<String>();
                mDocumentIds.put(namespace, list);
            }
            for (String id : documentIds) {
                Objects.requireNonNull(id);
                list.add(id);
            }
            return this;
        }

        public SearchSuggestionSpec build() {
            return new SearchSuggestionSpec(this);
        }
    }
}
