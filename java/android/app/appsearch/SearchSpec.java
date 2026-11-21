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

public final class SearchSpec implements Parcelable {
    public static final String SCHEMA_TYPE_WILDCARD = "*";
    public static final String PROJECTION_SCHEMA_TYPE_WILDCARD = "*";

    public static final int TERM_MATCH_EXACT_ONLY = 1;
    public static final int TERM_MATCH_PREFIX = 2;

    public static final int RANKING_STRATEGY_NONE = 0;
    public static final int RANKING_STRATEGY_DOCUMENT_SCORE = 1;
    public static final int RANKING_STRATEGY_CREATION_TIMESTAMP = 2;
    public static final int RANKING_STRATEGY_RELEVANCE_SCORE = 3;
    public static final int RANKING_STRATEGY_USAGE_COUNT = 4;
    public static final int RANKING_STRATEGY_USAGE_LAST_USED_TIMESTAMP = 5;
    public static final int RANKING_STRATEGY_SYSTEM_USAGE_COUNT = 6;
    public static final int RANKING_STRATEGY_SYSTEM_USAGE_LAST_USED_TIMESTAMP = 7;
    public static final int RANKING_STRATEGY_JOIN_AGGREGATE_SCORE = 8;
    public static final int RANKING_STRATEGY_ADVANCED_RANKING_EXPRESSION = 9;

    public static final int ORDER_DESCENDING = 0;
    public static final int ORDER_ASCENDING = 1;

    public static final int GROUPING_TYPE_PER_PACKAGE = 1;
    public static final int GROUPING_TYPE_PER_NAMESPACE = 2;
    public static final int GROUPING_TYPE_PER_SCHEMA = 4;

    private final int mTermMatchType;
    private final List<String> mSchemas;
    private final Map<String, List<String>> mTypePropertyFilters;
    private final List<String> mNamespaces;
    private final List<String> mPackageNames;
    private final int mResultCountPerPage;
    private final int mRankingStrategy;
    private final int mOrder;
    private final int mSnippetCount;
    private final int mSnippetCountPerProperty;
    private final int mMaxSnippetSize;
    private final Map<String, List<String>> mProjections;
    private final Map<String, Map<String, Double>> mPropertyWeights;
    private final int mGroupingTypeFlags;
    private final int mGroupingLimit;
    private final JoinSpec mJoinSpec;
    private final String mAdvancedRankingExpression;
    private final String mSearchSourceLogTag;
    private final boolean mNumericSearchEnabled;
    private final boolean mVerbatimSearchEnabled;
    private final boolean mListFilterQueryLanguageEnabled;
    private final boolean mListFilterHasPropertyFunctionEnabled;

    SearchSpec(Builder b) {
        mTermMatchType = b.mTermMatchType;
        mSchemas = Collections.unmodifiableList(new ArrayList<String>(b.mSchemas));
        mTypePropertyFilters = copyStringListMap(b.mTypePropertyFilters);
        mNamespaces = Collections.unmodifiableList(new ArrayList<String>(b.mNamespaces));
        mPackageNames = Collections.unmodifiableList(new ArrayList<String>(b.mPackageNames));
        mResultCountPerPage = b.mResultCountPerPage;
        mRankingStrategy = b.mRankingStrategy;
        mOrder = b.mOrder;
        mSnippetCount = b.mSnippetCount;
        mSnippetCountPerProperty = b.mSnippetCountPerProperty;
        mMaxSnippetSize = b.mMaxSnippetSize;
        mProjections = copyStringListMap(b.mProjections);
        Map<String, Map<String, Double>> weightsCopy =
                new LinkedHashMap<String, Map<String, Double>>();
        for (Map.Entry<String, Map<String, Double>> e : b.mPropertyWeights.entrySet()) {
            weightsCopy.put(
                    e.getKey(),
                    Collections.unmodifiableMap(new LinkedHashMap<String, Double>(e.getValue())));
        }
        mPropertyWeights = Collections.unmodifiableMap(weightsCopy);
        mGroupingTypeFlags = b.mGroupingTypeFlags;
        mGroupingLimit = b.mGroupingLimit;
        mJoinSpec = b.mJoinSpec;
        mAdvancedRankingExpression = b.mAdvancedRankingExpression;
        mSearchSourceLogTag = b.mSearchSourceLogTag;
        mNumericSearchEnabled = b.mNumericSearchEnabled;
        mVerbatimSearchEnabled = b.mVerbatimSearchEnabled;
        mListFilterQueryLanguageEnabled = b.mListFilterQueryLanguageEnabled;
        mListFilterHasPropertyFunctionEnabled = b.mListFilterHasPropertyFunctionEnabled;
    }

    SearchSpec(Parcel in) {
        mTermMatchType = in.readInt();
        mSchemas = readStringList(in);
        mTypePropertyFilters = readStringListMap(in);
        mNamespaces = readStringList(in);
        mPackageNames = readStringList(in);
        mResultCountPerPage = in.readInt();
        mRankingStrategy = in.readInt();
        mOrder = in.readInt();
        mSnippetCount = in.readInt();
        mSnippetCountPerProperty = in.readInt();
        mMaxSnippetSize = in.readInt();
        mProjections = readStringListMap(in);
        mPropertyWeights = Collections.emptyMap();
        mGroupingTypeFlags = in.readInt();
        mGroupingLimit = in.readInt();
        mJoinSpec = in.readInt() != 0 ? JoinSpec.CREATOR.createFromParcel(in) : null;
        mAdvancedRankingExpression = in.readString();
        mSearchSourceLogTag = in.readString();
        mNumericSearchEnabled = in.readInt() != 0;
        mVerbatimSearchEnabled = in.readInt() != 0;
        mListFilterQueryLanguageEnabled = in.readInt() != 0;
        mListFilterHasPropertyFunctionEnabled = in.readInt() != 0;
    }

    private static Map<String, List<String>> copyStringListMap(Map<String, List<String>> src) {
        Map<String, List<String>> copy = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> e : src.entrySet()) {
            copy.put(
                    e.getKey(),
                    Collections.unmodifiableList(new ArrayList<String>(e.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static List<String> readStringList(Parcel in) {
        int count = in.readInt();
        List<String> list = new ArrayList<String>(Math.max(0, count));
        for (int i = 0; i < count; i++) list.add(in.readString());
        return Collections.unmodifiableList(list);
    }

    private static void writeStringList(Parcel dest, List<String> list) {
        dest.writeInt(list.size());
        for (String s : list) dest.writeString(s);
    }

    private static Map<String, List<String>> readStringListMap(Parcel in) {
        int count = in.readInt();
        Map<String, List<String>> map = new LinkedHashMap<String, List<String>>();
        for (int i = 0; i < count; i++) {
            String key = in.readString();
            map.put(key, readStringList(in));
        }
        return Collections.unmodifiableMap(map);
    }

    private static void writeStringListMap(Parcel dest, Map<String, List<String>> map) {
        dest.writeInt(map.size());
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            dest.writeString(e.getKey());
            writeStringList(dest, e.getValue());
        }
    }

    public int getTermMatch() {
        return mTermMatchType;
    }

    public List<String> getFilterSchemas() {
        return mSchemas;
    }

    public Map<String, List<String>> getFilterProperties() {
        return mTypePropertyFilters;
    }

    public List<String> getFilterNamespaces() {
        return mNamespaces;
    }

    public List<String> getFilterPackageNames() {
        return mPackageNames;
    }

    public int getResultCountPerPage() {
        return mResultCountPerPage;
    }

    public int getRankingStrategy() {
        return mRankingStrategy;
    }

    public int getOrder() {
        return mOrder;
    }

    public int getSnippetCount() {
        return mSnippetCount;
    }

    public int getSnippetCountPerProperty() {
        return mSnippetCountPerProperty;
    }

    public int getMaxSnippetSize() {
        return mMaxSnippetSize;
    }

    public Map<String, List<String>> getProjections() {
        return mProjections;
    }

    public Map<String, List<PropertyPath>> getProjectionPaths() {
        Map<String, List<PropertyPath>> out = new LinkedHashMap<String, List<PropertyPath>>();
        for (Map.Entry<String, List<String>> e : mProjections.entrySet()) {
            List<PropertyPath> paths = new ArrayList<PropertyPath>(e.getValue().size());
            for (String s : e.getValue()) paths.add(new PropertyPath(s));
            out.put(e.getKey(), Collections.unmodifiableList(paths));
        }
        return Collections.unmodifiableMap(out);
    }

    public Map<String, Map<String, Double>> getPropertyWeights() {
        return mPropertyWeights;
    }

    public Map<String, Map<PropertyPath, Double>> getPropertyWeightPaths() {
        Map<String, Map<PropertyPath, Double>> out =
                new LinkedHashMap<String, Map<PropertyPath, Double>>();
        for (Map.Entry<String, Map<String, Double>> e : mPropertyWeights.entrySet()) {
            Map<PropertyPath, Double> inner = new LinkedHashMap<PropertyPath, Double>();
            for (Map.Entry<String, Double> ie : e.getValue().entrySet()) {
                inner.put(new PropertyPath(ie.getKey()), ie.getValue());
            }
            out.put(e.getKey(), Collections.unmodifiableMap(inner));
        }
        return Collections.unmodifiableMap(out);
    }

    public int getResultGroupingTypeFlags() {
        return mGroupingTypeFlags;
    }

    public int getResultGroupingLimit() {
        return mGroupingLimit;
    }

    public JoinSpec getJoinSpec() {
        return mJoinSpec;
    }

    public String getAdvancedRankingExpression() {
        return mAdvancedRankingExpression;
    }

    public String getSearchSourceLogTag() {
        return mSearchSourceLogTag;
    }

    public boolean isNumericSearchEnabled() {
        return mNumericSearchEnabled;
    }

    public boolean isVerbatimSearchEnabled() {
        return mVerbatimSearchEnabled;
    }

    public boolean isListFilterQueryLanguageEnabled() {
        return mListFilterQueryLanguageEnabled;
    }

    public boolean isListFilterHasPropertyFunctionEnabled() {
        return mListFilterHasPropertyFunctionEnabled;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mTermMatchType);
        writeStringList(dest, mSchemas);
        writeStringListMap(dest, mTypePropertyFilters);
        writeStringList(dest, mNamespaces);
        writeStringList(dest, mPackageNames);
        dest.writeInt(mResultCountPerPage);
        dest.writeInt(mRankingStrategy);
        dest.writeInt(mOrder);
        dest.writeInt(mSnippetCount);
        dest.writeInt(mSnippetCountPerProperty);
        dest.writeInt(mMaxSnippetSize);
        writeStringListMap(dest, mProjections);
        dest.writeInt(mGroupingTypeFlags);
        dest.writeInt(mGroupingLimit);
        if (mJoinSpec != null) {
            dest.writeInt(1);
            mJoinSpec.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeString(mAdvancedRankingExpression);
        dest.writeString(mSearchSourceLogTag);
        dest.writeInt(mNumericSearchEnabled ? 1 : 0);
        dest.writeInt(mVerbatimSearchEnabled ? 1 : 0);
        dest.writeInt(mListFilterQueryLanguageEnabled ? 1 : 0);
        dest.writeInt(mListFilterHasPropertyFunctionEnabled ? 1 : 0);
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<SearchSpec> CREATOR =
            new Parcelable.Creator<SearchSpec>() {
                @Override
                public SearchSpec createFromParcel(Parcel in) {
                    return new SearchSpec(in);
                }

                @Override
                public SearchSpec[] newArray(int size) {
                    return new SearchSpec[size];
                }
            };

    public static final class Builder {
        int mTermMatchType = TERM_MATCH_EXACT_ONLY;
        final List<String> mSchemas = new ArrayList<String>();
        final Map<String, List<String>> mTypePropertyFilters =
                new LinkedHashMap<String, List<String>>();
        final List<String> mNamespaces = new ArrayList<String>();
        final List<String> mPackageNames = new ArrayList<String>();
        int mResultCountPerPage = 10;
        int mRankingStrategy = RANKING_STRATEGY_NONE;
        int mOrder = ORDER_DESCENDING;
        int mSnippetCount = 0;
        int mSnippetCountPerProperty = 10000;
        int mMaxSnippetSize = 0;
        final Map<String, List<String>> mProjections =
                new LinkedHashMap<String, List<String>>();
        final Map<String, Map<String, Double>> mPropertyWeights =
                new LinkedHashMap<String, Map<String, Double>>();
        int mGroupingTypeFlags = 0;
        int mGroupingLimit = 0;
        JoinSpec mJoinSpec;
        String mAdvancedRankingExpression = "";
        String mSearchSourceLogTag;
        boolean mNumericSearchEnabled;
        boolean mVerbatimSearchEnabled;
        boolean mListFilterQueryLanguageEnabled;
        boolean mListFilterHasPropertyFunctionEnabled;

        public Builder() {
        }

        public Builder setTermMatch(int termMatchType) {
            if (termMatchType != TERM_MATCH_EXACT_ONLY && termMatchType != TERM_MATCH_PREFIX) {
                throw new IllegalArgumentException("Invalid termMatchType: " + termMatchType);
            }
            mTermMatchType = termMatchType;
            return this;
        }

        public Builder addFilterSchemas(String... schemas) {
            Objects.requireNonNull(schemas);
            return addFilterSchemas(Arrays.asList(schemas));
        }

        public Builder addFilterSchemas(Collection<String> schemas) {
            Objects.requireNonNull(schemas);
            for (String s : schemas) {
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

        public Builder addFilterPackageNames(String... packageNames) {
            Objects.requireNonNull(packageNames);
            return addFilterPackageNames(Arrays.asList(packageNames));
        }

        public Builder addFilterPackageNames(Collection<String> packageNames) {
            Objects.requireNonNull(packageNames);
            for (String pkg : packageNames) {
                Objects.requireNonNull(pkg);
                mPackageNames.add(pkg);
            }
            return this;
        }

        public Builder setResultCountPerPage(int resultCountPerPage) {
            if (resultCountPerPage < 0 || resultCountPerPage > 10000) {
                throw new IllegalArgumentException(
                        "Invalid resultCountPerPage: " + resultCountPerPage);
            }
            mResultCountPerPage = resultCountPerPage;
            return this;
        }

        public Builder setRankingStrategy(int rankingStrategy) {
            if (rankingStrategy < RANKING_STRATEGY_NONE
                    || rankingStrategy >= RANKING_STRATEGY_ADVANCED_RANKING_EXPRESSION) {
                throw new IllegalArgumentException("Invalid rankingStrategy: " + rankingStrategy);
            }
            mRankingStrategy = rankingStrategy;
            mAdvancedRankingExpression = "";
            return this;
        }

        public Builder setRankingStrategy(String advancedRankingExpression) {
            Objects.requireNonNull(advancedRankingExpression);
            if (advancedRankingExpression.isEmpty()) {
                throw new IllegalArgumentException("advancedRankingExpression cannot be empty");
            }
            mRankingStrategy = RANKING_STRATEGY_ADVANCED_RANKING_EXPRESSION;
            mAdvancedRankingExpression = advancedRankingExpression;
            return this;
        }

        public Builder setSearchSourceLogTag(String searchSourceLogTag) {
            mSearchSourceLogTag = Objects.requireNonNull(searchSourceLogTag);
            return this;
        }

        public Builder setOrder(int order) {
            if (order != ORDER_DESCENDING && order != ORDER_ASCENDING) {
                throw new IllegalArgumentException("Invalid order: " + order);
            }
            mOrder = order;
            return this;
        }

        public Builder setSnippetCount(int snippetCount) {
            if (snippetCount < 0) throw new IllegalArgumentException("snippetCount < 0");
            mSnippetCount = snippetCount;
            return this;
        }

        public Builder setSnippetCountPerProperty(int snippetCountPerProperty) {
            if (snippetCountPerProperty < 0) {
                throw new IllegalArgumentException("snippetCountPerProperty < 0");
            }
            mSnippetCountPerProperty = snippetCountPerProperty;
            return this;
        }

        public Builder setMaxSnippetSize(int maxSnippetSize) {
            if (maxSnippetSize < 0) throw new IllegalArgumentException("maxSnippetSize < 0");
            mMaxSnippetSize = maxSnippetSize;
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

        public Builder setResultGrouping(int groupingTypeFlags, int limit) {
            if (groupingTypeFlags <= 0 || limit <= 0) {
                throw new IllegalArgumentException("Invalid result grouping parameters");
            }
            mGroupingTypeFlags = groupingTypeFlags;
            mGroupingLimit = limit;
            return this;
        }

        public Builder setPropertyWeights(
                String schemaType, Map<String, Double> propertyPathWeights) {
            Objects.requireNonNull(schemaType);
            Objects.requireNonNull(propertyPathWeights);
            Map<String, Double> copy = new LinkedHashMap<String, Double>();
            for (Map.Entry<String, Double> e : propertyPathWeights.entrySet()) {
                Objects.requireNonNull(e.getKey());
                Objects.requireNonNull(e.getValue());
                if (e.getValue().doubleValue() <= 0.0) {
                    throw new IllegalArgumentException("Property weight must be positive");
                }
                copy.put(e.getKey(), e.getValue());
            }
            mPropertyWeights.put(schemaType, copy);
            return this;
        }

        public Builder setJoinSpec(JoinSpec joinSpec) {
            mJoinSpec = Objects.requireNonNull(joinSpec);
            return this;
        }

        public Builder setPropertyWeightPaths(
                String schemaType, Map<PropertyPath, Double> propertyPathWeights) {
            Objects.requireNonNull(schemaType);
            Objects.requireNonNull(propertyPathWeights);
            Map<String, Double> strs = new LinkedHashMap<String, Double>();
            for (Map.Entry<PropertyPath, Double> e : propertyPathWeights.entrySet()) {
                Objects.requireNonNull(e.getKey());
                strs.put(e.getKey().toString(), e.getValue());
            }
            return setPropertyWeights(schemaType, strs);
        }

        public Builder setNumericSearchEnabled(boolean enabled) {
            mNumericSearchEnabled = enabled;
            return this;
        }

        public Builder setVerbatimSearchEnabled(boolean enabled) {
            mVerbatimSearchEnabled = enabled;
            return this;
        }

        public Builder setListFilterQueryLanguageEnabled(boolean enabled) {
            mListFilterQueryLanguageEnabled = enabled;
            return this;
        }

        public Builder setListFilterHasPropertyFunctionEnabled(boolean enabled) {
            mListFilterHasPropertyFunctionEnabled = enabled;
            return this;
        }

        public SearchSpec build() {
            return new SearchSpec(this);
        }
    }
}
