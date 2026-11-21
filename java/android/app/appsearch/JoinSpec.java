// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class JoinSpec implements Parcelable {
    public static final int AGGREGATION_SCORING_OUTER_RESULT_RANKING_SIGNAL = 0;
    public static final int AGGREGATION_SCORING_RESULT_COUNT = 1;
    public static final int AGGREGATION_SCORING_MIN_RANKING_SIGNAL = 2;
    public static final int AGGREGATION_SCORING_AVG_RANKING_SIGNAL = 3;
    public static final int AGGREGATION_SCORING_MAX_RANKING_SIGNAL = 4;
    public static final int AGGREGATION_SCORING_SUM_RANKING_SIGNAL = 5;

    private final String mNestedQuery;
    private final SearchSpec mNestedSearchSpec;
    private final String mChildPropertyExpression;
    private final int mMaxJoinedResultCount;
    private final int mAggregationScoringStrategy;

    private JoinSpec(
            String nestedQuery,
            SearchSpec nestedSearchSpec,
            String childPropertyExpression,
            int maxJoinedResultCount,
            int aggregationScoringStrategy) {
        mNestedQuery = Objects.requireNonNull(nestedQuery);
        mNestedSearchSpec = Objects.requireNonNull(nestedSearchSpec);
        mChildPropertyExpression = Objects.requireNonNull(childPropertyExpression);
        mMaxJoinedResultCount = maxJoinedResultCount;
        mAggregationScoringStrategy = aggregationScoringStrategy;
    }

    JoinSpec(Parcel in) {
        mNestedQuery = in.readString();
        mNestedSearchSpec = SearchSpec.CREATOR.createFromParcel(in);
        mChildPropertyExpression = in.readString();
        mMaxJoinedResultCount = in.readInt();
        mAggregationScoringStrategy = in.readInt();
    }

    public String getNestedQuery() {
        return mNestedQuery;
    }

    public String getChildPropertyExpression() {
        return mChildPropertyExpression;
    }

    public int getMaxJoinedResultCount() {
        return mMaxJoinedResultCount;
    }

    public SearchSpec getNestedSearchSpec() {
        return mNestedSearchSpec;
    }

    public int getAggregationScoringStrategy() {
        return mAggregationScoringStrategy;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNestedQuery);
        mNestedSearchSpec.writeToParcel(dest, flags);
        dest.writeString(mChildPropertyExpression);
        dest.writeInt(mMaxJoinedResultCount);
        dest.writeInt(mAggregationScoringStrategy);
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<JoinSpec> CREATOR =
            new Parcelable.Creator<JoinSpec>() {
                @Override
                public JoinSpec createFromParcel(Parcel in) {
                    return new JoinSpec(in);
                }

                @Override
                public JoinSpec[] newArray(int size) {
                    return new JoinSpec[size];
                }
            };

    public static final class Builder {
        private String mNestedQuery = "";
        private SearchSpec mNestedSearchSpec = new SearchSpec.Builder().build();
        private final String mChildPropertyExpression;
        private int mMaxJoinedResultCount = 10;
        private int mAggregationScoringStrategy =
                AGGREGATION_SCORING_OUTER_RESULT_RANKING_SIGNAL;

        public Builder(String childPropertyExpression) {
            mChildPropertyExpression = Objects.requireNonNull(childPropertyExpression);
        }

        public Builder setNestedSearch(String nestedQuery, SearchSpec nestedSearchSpec) {
            mNestedQuery = Objects.requireNonNull(nestedQuery);
            mNestedSearchSpec = Objects.requireNonNull(nestedSearchSpec);
            return this;
        }

        public Builder setMaxJoinedResultCount(int maxJoinedResultCount) {
            mMaxJoinedResultCount = maxJoinedResultCount;
            return this;
        }

        public Builder setAggregationScoringStrategy(int aggregationScoringStrategy) {
            if (aggregationScoringStrategy < AGGREGATION_SCORING_OUTER_RESULT_RANKING_SIGNAL
                    || aggregationScoringStrategy > AGGREGATION_SCORING_SUM_RANKING_SIGNAL) {
                throw new IllegalArgumentException(
                        "Invalid aggregationScoringStrategy: " + aggregationScoringStrategy);
            }
            mAggregationScoringStrategy = aggregationScoringStrategy;
            return this;
        }

        public JoinSpec build() {
            return new JoinSpec(
                    mNestedQuery,
                    mNestedSearchSpec,
                    mChildPropertyExpression,
                    mMaxJoinedResultCount,
                    mAggregationScoringStrategy);
        }
    }
}
