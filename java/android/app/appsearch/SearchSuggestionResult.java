// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class SearchSuggestionResult implements Parcelable {
    private final String mSuggestedResult;

    private SearchSuggestionResult(String suggestedResult) {
        mSuggestedResult = Objects.requireNonNull(suggestedResult);
    }

    SearchSuggestionResult(Parcel in) {
        mSuggestedResult = in.readString();
    }

    public String getSuggestedResult() {
        return mSuggestedResult;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SearchSuggestionResult)) return false;
        return Objects.equals(
                mSuggestedResult, ((SearchSuggestionResult) other).mSuggestedResult);
    }

    @Override
    public int hashCode() {
        return mSuggestedResult.hashCode();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mSuggestedResult);
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<SearchSuggestionResult> CREATOR =
            new Parcelable.Creator<SearchSuggestionResult>() {
                @Override
                public SearchSuggestionResult createFromParcel(Parcel in) {
                    return new SearchSuggestionResult(in);
                }

                @Override
                public SearchSuggestionResult[] newArray(int size) {
                    return new SearchSuggestionResult[size];
                }
            };

    public static final class Builder {
        private String mSuggestedResult = "";

        public Builder() {
        }

        public Builder setSuggestedResult(String suggestedResult) {
            Objects.requireNonNull(suggestedResult);
            if (suggestedResult.isEmpty()) {
                throw new IllegalArgumentException("suggestedResult cannot be empty");
            }
            mSuggestedResult = suggestedResult;
            return this;
        }

        public SearchSuggestionResult build() {
            return new SearchSuggestionResult(mSuggestedResult);
        }
    }
}
