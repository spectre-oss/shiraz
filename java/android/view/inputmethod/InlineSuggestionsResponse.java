// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class InlineSuggestionsResponse implements Parcelable {
    private final List<InlineSuggestion> mInlineSuggestions;

    public InlineSuggestionsResponse(List<InlineSuggestion> inlineSuggestions) {
        Objects.requireNonNull(inlineSuggestions, "inlineSuggestions must not be null");
        mInlineSuggestions = Collections.unmodifiableList(new ArrayList<>(inlineSuggestions));
    }

    public List<InlineSuggestion> getInlineSuggestions() {
        return mInlineSuggestions;
    }

    @Override
    public String toString() {
        return "InlineSuggestionsResponse{inlineSuggestions=" + mInlineSuggestions + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InlineSuggestionsResponse)) return false;
        InlineSuggestionsResponse that = (InlineSuggestionsResponse) o;
        return Objects.equals(mInlineSuggestions, that.mInlineSuggestions);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mInlineSuggestions);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mInlineSuggestions);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<InlineSuggestionsResponse> CREATOR = new Creator<InlineSuggestionsResponse>() {
        @Override
        public InlineSuggestionsResponse createFromParcel(Parcel in) {
            List<InlineSuggestion> list = in.createTypedArrayList(InlineSuggestion.CREATOR);
            return new InlineSuggestionsResponse(
                    list != null ? list : Collections.<InlineSuggestion>emptyList());
        }

        @Override
        public InlineSuggestionsResponse[] newArray(int size) {
            return new InlineSuggestionsResponse[size];
        }
    };
}
