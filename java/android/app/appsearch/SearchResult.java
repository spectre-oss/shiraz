// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SearchResult implements Parcelable {
    private final GenericDocument mDocument;
    private final List<MatchInfo> mMatchInfos;
    private final String mPackageName;
    private final String mDatabaseName;
    private final double mRankingSignal;
    private final List<SearchResult> mJoinedResults;

    private SearchResult(
            GenericDocument document,
            List<MatchInfo> matchInfos,
            String packageName,
            String databaseName,
            double rankingSignal,
            List<SearchResult> joinedResults) {
        mDocument = Objects.requireNonNull(document);
        mPackageName = Objects.requireNonNull(packageName);
        mDatabaseName = Objects.requireNonNull(databaseName);
        mRankingSignal = rankingSignal;
        List<MatchInfo> boundInfos = new ArrayList<MatchInfo>(matchInfos.size());
        for (MatchInfo mi : matchInfos) {
            mi.setDocument(document);
            boundInfos.add(mi);
        }
        mMatchInfos = Collections.unmodifiableList(boundInfos);
        mJoinedResults = Collections.unmodifiableList(new ArrayList<SearchResult>(joinedResults));
    }

    SearchResult(Parcel in) {
        mDocument = GenericDocument.createFromParcelInternal(in);
        int matchCount = in.readInt();
        List<MatchInfo> matches = new ArrayList<MatchInfo>(Math.max(0, matchCount));
        for (int i = 0; i < matchCount; i++) {
            MatchInfo mi = MatchInfo.CREATOR.createFromParcel(in);
            mi.setDocument(mDocument);
            matches.add(mi);
        }
        mMatchInfos = Collections.unmodifiableList(matches);
        mPackageName = in.readString();
        mDatabaseName = in.readString();
        mRankingSignal = in.readDouble();
        int joinedCount = in.readInt();
        List<SearchResult> joined = new ArrayList<SearchResult>(Math.max(0, joinedCount));
        for (int i = 0; i < joinedCount; i++) {
            joined.add(CREATOR.createFromParcel(in));
        }
        mJoinedResults = Collections.unmodifiableList(joined);
    }

    public GenericDocument getGenericDocument() {
        return mDocument;
    }

    public List<MatchInfo> getMatchInfos() {
        return mMatchInfos;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getDatabaseName() {
        return mDatabaseName;
    }

    public double getRankingSignal() {
        return mRankingSignal;
    }

    public List<SearchResult> getJoinedResults() {
        return mJoinedResults;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        mDocument.writeToParcelInternal(dest, flags);
        dest.writeInt(mMatchInfos.size());
        for (MatchInfo mi : mMatchInfos) {
            mi.writeToParcel(dest, flags);
        }
        dest.writeString(mPackageName);
        dest.writeString(mDatabaseName);
        dest.writeDouble(mRankingSignal);
        dest.writeInt(mJoinedResults.size());
        for (SearchResult jr : mJoinedResults) {
            jr.writeToParcel(dest, flags);
        }
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<SearchResult> CREATOR =
            new Parcelable.Creator<SearchResult>() {
                @Override
                public SearchResult createFromParcel(Parcel in) {
                    return new SearchResult(in);
                }

                @Override
                public SearchResult[] newArray(int size) {
                    return new SearchResult[size];
                }
            };

    public static final class Builder {
        private final String mPackageName;
        private final String mDatabaseName;
        private GenericDocument mGenericDocument;
        private final List<MatchInfo> mMatchInfos = new ArrayList<MatchInfo>();
        private double mRankingSignal;
        private final List<SearchResult> mJoinedResults = new ArrayList<SearchResult>();

        public Builder(String packageName, String databaseName) {
            mPackageName = Objects.requireNonNull(packageName);
            mDatabaseName = Objects.requireNonNull(databaseName);
        }

        public Builder setGenericDocument(GenericDocument document) {
            mGenericDocument = Objects.requireNonNull(document);
            return this;
        }

        public Builder addMatchInfo(MatchInfo matchInfo) {
            mMatchInfos.add(Objects.requireNonNull(matchInfo));
            return this;
        }

        public Builder setRankingSignal(double rankingSignal) {
            mRankingSignal = rankingSignal;
            return this;
        }

        public Builder addJoinedResult(SearchResult joinedResult) {
            mJoinedResults.add(Objects.requireNonNull(joinedResult));
            return this;
        }

        public SearchResult build() {
            Objects.requireNonNull(mGenericDocument, "GenericDocument must be set");
            return new SearchResult(
                    mGenericDocument,
                    mMatchInfos,
                    mPackageName,
                    mDatabaseName,
                    mRankingSignal,
                    mJoinedResults);
        }
    }

    public static final class MatchInfo implements Parcelable {
        private final String mPropertyPath;
        private final MatchRange mExactMatchRange;
        private final MatchRange mSubmatchRange;
        private final MatchRange mSnippetRange;
        private String mFullText;

        private MatchInfo(
                String propertyPath,
                MatchRange exactMatchRange,
                MatchRange submatchRange,
                MatchRange snippetRange) {
            mPropertyPath = Objects.requireNonNull(propertyPath);
            mExactMatchRange = Objects.requireNonNull(exactMatchRange);
            mSubmatchRange = submatchRange != null ? submatchRange : new MatchRange(0, 0);
            mSnippetRange = Objects.requireNonNull(snippetRange);
        }

        MatchInfo(Parcel in) {
            mPropertyPath = in.readString();
            mExactMatchRange = new MatchRange(in.readInt(), in.readInt());
            mSubmatchRange = new MatchRange(in.readInt(), in.readInt());
            mSnippetRange = new MatchRange(in.readInt(), in.readInt());
        }

        void setDocument(GenericDocument document) {
            if (document != null) {
                String text = document.getPropertyString(mPropertyPath);
                mFullText = text != null ? text : "";
            }
        }

        public String getPropertyPath() {
            return mPropertyPath;
        }

        public PropertyPath getPropertyPathObject() {
            return new PropertyPath(mPropertyPath);
        }

        public String getFullText() {
            if (mFullText == null) {
                throw new IllegalStateException("MatchInfo has not been attached to a SearchResult");
            }
            return mFullText;
        }

        public MatchRange getExactMatchRange() {
            return mExactMatchRange;
        }

        public CharSequence getExactMatch() {
            return slice(getFullText(), mExactMatchRange);
        }

        public MatchRange getSubmatchRange() {
            return mSubmatchRange;
        }

        public CharSequence getSubmatch() {
            return slice(getFullText(), mSubmatchRange);
        }

        public MatchRange getSnippetRange() {
            return mSnippetRange;
        }

        public CharSequence getSnippet() {
            return slice(getFullText(), mSnippetRange);
        }

        private static CharSequence slice(String text, MatchRange range) {
            int start = Math.max(0, Math.min(text.length(), range.getStart()));
            int end = Math.max(start, Math.min(text.length(), range.getEnd()));
            return text.substring(start, end);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mPropertyPath);
            dest.writeInt(mExactMatchRange.getStart());
            dest.writeInt(mExactMatchRange.getEnd());
            dest.writeInt(mSubmatchRange.getStart());
            dest.writeInt(mSubmatchRange.getEnd());
            dest.writeInt(mSnippetRange.getStart());
            dest.writeInt(mSnippetRange.getEnd());
        }

        @Override
        public final int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<MatchInfo> CREATOR =
                new Parcelable.Creator<MatchInfo>() {
                    @Override
                    public MatchInfo createFromParcel(Parcel in) {
                        return new MatchInfo(in);
                    }

                    @Override
                    public MatchInfo[] newArray(int size) {
                        return new MatchInfo[size];
                    }
                };

        public static final class Builder {
            private final String mPropertyPath;
            private MatchRange mExactMatchRange = new MatchRange(0, 0);
            private MatchRange mSubmatchRange;
            private MatchRange mSnippetRange = new MatchRange(0, 0);

            public Builder(String propertyPath) {
                mPropertyPath = Objects.requireNonNull(propertyPath);
            }

            public Builder setExactMatchRange(MatchRange matchRange) {
                mExactMatchRange = Objects.requireNonNull(matchRange);
                return this;
            }

            public Builder setSubmatchRange(MatchRange matchRange) {
                mSubmatchRange = Objects.requireNonNull(matchRange);
                return this;
            }

            public Builder setSnippetRange(MatchRange matchRange) {
                mSnippetRange = Objects.requireNonNull(matchRange);
                return this;
            }

            public MatchInfo build() {
                return new MatchInfo(
                        mPropertyPath, mExactMatchRange, mSubmatchRange, mSnippetRange);
            }
        }
    }

    public static final class MatchRange {
        private final int mStart;
        private final int mEnd;

        public MatchRange(int start, int end) {
            if (start > end) {
                throw new IllegalArgumentException(
                        "Start point must be less than or equal to end point");
            }
            mStart = start;
            mEnd = end;
        }

        public int getStart() {
            return mStart;
        }

        public int getEnd() {
            return mEnd;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof MatchRange)) return false;
            MatchRange that = (MatchRange) other;
            return mStart == that.mStart && mEnd == that.mEnd;
        }

        @Override
        public String toString() {
            return "MatchRange { start: " + mStart + " , end: " + mEnd + "}";
        }

        @Override
        public int hashCode() {
            return Objects.hash(Integer.valueOf(mStart), Integer.valueOf(mEnd));
        }
    }
}
