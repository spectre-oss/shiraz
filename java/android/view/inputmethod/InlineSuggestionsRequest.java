// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;

import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.inline.InlinePresentationSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class InlineSuggestionsRequest implements Parcelable {
    public static final int SUGGESTION_COUNT_UNLIMITED = Integer.MAX_VALUE;

    private final int mMaxSuggestionCount;
    private final List<InlinePresentationSpec> mInlinePresentationSpecs;
    private final String mHostPackageName;
    private final LocaleList mSupportedLocales;
    private final Bundle mExtras;
    private final InlinePresentationSpec mInlineTooltipPresentationSpec;

    InlineSuggestionsRequest(int maxSuggestionCount, List<InlinePresentationSpec> specs,
            String hostPackageName, LocaleList supportedLocales, Bundle extras,
            InlinePresentationSpec tooltipSpec) {
        mMaxSuggestionCount = maxSuggestionCount;
        mInlinePresentationSpecs = Collections.unmodifiableList(new ArrayList<>(specs));
        mHostPackageName = hostPackageName != null ? hostPackageName : "";
        mSupportedLocales = supportedLocales != null ? supportedLocales : LocaleList.getDefault();
        mExtras = extras != null ? new Bundle(extras) : new Bundle();
        mInlineTooltipPresentationSpec = tooltipSpec;
    }

    public int getMaxSuggestionCount() {
        return mMaxSuggestionCount;
    }

    public List<InlinePresentationSpec> getInlinePresentationSpecs() {
        return mInlinePresentationSpecs;
    }

    public String getHostPackageName() {
        return mHostPackageName;
    }

    public LocaleList getSupportedLocales() {
        return mSupportedLocales;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public InlinePresentationSpec getInlineTooltipPresentationSpec() {
        return mInlineTooltipPresentationSpec;
    }

    @Override
    public String toString() {
        return "InlineSuggestionsRequest{maxSuggestionCount=" + mMaxSuggestionCount
                + ", specs=" + mInlinePresentationSpecs
                + ", hostPackageName=" + mHostPackageName + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InlineSuggestionsRequest)) return false;
        InlineSuggestionsRequest that = (InlineSuggestionsRequest) o;
        return mMaxSuggestionCount == that.mMaxSuggestionCount
                && Objects.equals(mInlinePresentationSpecs, that.mInlinePresentationSpecs)
                && Objects.equals(mHostPackageName, that.mHostPackageName)
                && Objects.equals(mSupportedLocales, that.mSupportedLocales)
                && Objects.equals(mInlineTooltipPresentationSpec, that.mInlineTooltipPresentationSpec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mMaxSuggestionCount, mInlinePresentationSpecs,
                mHostPackageName, mSupportedLocales, mInlineTooltipPresentationSpec);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mMaxSuggestionCount);
        dest.writeTypedList(mInlinePresentationSpecs);
        dest.writeString(mHostPackageName);
        dest.writeString(mSupportedLocales != null ? mSupportedLocales.toLanguageTags() : "");
        dest.writeBundle(mExtras);
        dest.writeTypedObject(mInlineTooltipPresentationSpec, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<InlineSuggestionsRequest> CREATOR = new Creator<InlineSuggestionsRequest>() {
        @Override
        public InlineSuggestionsRequest createFromParcel(Parcel in) {
            int maxCount = in.readInt();
            List<InlinePresentationSpec> specs = in.createTypedArrayList(InlinePresentationSpec.CREATOR);
            String hostPkg = in.readString();
            String localeTags = in.readString();
            LocaleList locales = (localeTags != null && !localeTags.isEmpty())
                    ? LocaleList.forLanguageTags(localeTags)
                    : LocaleList.getEmptyLocaleList();
            Bundle extras = in.readBundle();
            InlinePresentationSpec tooltip = in.readTypedObject(InlinePresentationSpec.CREATOR);
            return new InlineSuggestionsRequest(maxCount, specs != null ? specs : Collections.<InlinePresentationSpec>emptyList(),
                    hostPkg, locales, extras, tooltip);
        }

        @Override
        public InlineSuggestionsRequest[] newArray(int size) {
            return new InlineSuggestionsRequest[size];
        }
    };

    public static final class Builder {
        private int mMaxSuggestionCount = SUGGESTION_COUNT_UNLIMITED;
        private List<InlinePresentationSpec> mSpecs;
        private LocaleList mSupportedLocales = LocaleList.getDefault();
        private Bundle mExtras = new Bundle();
        private InlinePresentationSpec mTooltipSpec;

        public Builder(List<InlinePresentationSpec> presentationSpecs) {
            Objects.requireNonNull(presentationSpecs, "presentationSpecs must not be null");
            mSpecs = new ArrayList<>(presentationSpecs);
        }

        public Builder setMaxSuggestionCount(int value) {
            mMaxSuggestionCount = value;
            return this;
        }

        public Builder setInlinePresentationSpecs(List<InlinePresentationSpec> value) {
            Objects.requireNonNull(value, "value must not be null");
            mSpecs = new ArrayList<>(value);
            return this;
        }

        public Builder addInlinePresentationSpecs(InlinePresentationSpec value) {
            Objects.requireNonNull(value, "value must not be null");
            mSpecs.add(value);
            return this;
        }

        public Builder setSupportedLocales(LocaleList value) {
            mSupportedLocales = Objects.requireNonNull(value, "value must not be null");
            return this;
        }

        public Builder setExtras(Bundle value) {
            mExtras = Objects.requireNonNull(value, "value must not be null");
            return this;
        }

        public Builder setInlineTooltipPresentationSpec(InlinePresentationSpec value) {
            mTooltipSpec = value;
            return this;
        }

        public InlineSuggestionsRequest build() {
            if (mSpecs.isEmpty()) {
                throw new IllegalStateException("InlinePresentationSpecs must not be empty");
            }
            return new InlineSuggestionsRequest(mMaxSuggestionCount, mSpecs, "",
                    mSupportedLocales, mExtras, mTooltipSpec);
        }
    }
}
