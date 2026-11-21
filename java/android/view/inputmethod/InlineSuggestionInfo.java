// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;

import android.os.Parcel;
import android.os.Parcelable;
import android.widget.inline.InlinePresentationSpec;
import java.util.Arrays;
import java.util.Objects;

public final class InlineSuggestionInfo implements Parcelable {
    public static final String SOURCE_AUTOFILL = "android:autofill";
    public static final String SOURCE_PLATFORM = "android:platform";
    public static final String TYPE_ACTION = "android:autofill:action";
    public static final String TYPE_SUGGESTION = "android:autofill:suggestion";

    private final InlinePresentationSpec mInlinePresentationSpec;
    private final String mSource;
    private final String[] mAutofillHints;
    private final String mType;
    private final boolean mPinned;

    public InlineSuggestionInfo(InlinePresentationSpec spec, String source,
            String[] autofillHints, String type, boolean pinned) {
        mInlinePresentationSpec = Objects.requireNonNull(spec, "spec must not be null");
        mSource = Objects.requireNonNull(source, "source must not be null");
        mAutofillHints = autofillHints != null ? autofillHints.clone() : null;
        mType = Objects.requireNonNull(type, "type must not be null");
        mPinned = pinned;
    }

    public InlinePresentationSpec getInlinePresentationSpec() {
        return mInlinePresentationSpec;
    }

    public String getSource() {
        return mSource;
    }

    public String[] getAutofillHints() {
        return mAutofillHints != null ? mAutofillHints.clone() : null;
    }

    public String getType() {
        return mType;
    }

    public boolean isPinned() {
        return mPinned;
    }

    @Override
    public String toString() {
        return "InlineSuggestionInfo{spec=" + mInlinePresentationSpec
                + ", source=" + mSource
                + ", autofillHints=" + Arrays.toString(mAutofillHints)
                + ", type=" + mType
                + ", pinned=" + mPinned + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InlineSuggestionInfo)) return false;
        InlineSuggestionInfo that = (InlineSuggestionInfo) o;
        return mPinned == that.mPinned
                && Objects.equals(mInlinePresentationSpec, that.mInlinePresentationSpec)
                && Objects.equals(mSource, that.mSource)
                && Arrays.equals(mAutofillHints, that.mAutofillHints)
                && Objects.equals(mType, that.mType);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mInlinePresentationSpec, mSource, mType, mPinned);
        return 31 * result + Arrays.hashCode(mAutofillHints);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mInlinePresentationSpec, flags);
        dest.writeString(mSource);
        if (mAutofillHints == null) {
            dest.writeInt(-1);
        } else {
            dest.writeInt(mAutofillHints.length);
            for (int i = 0; i < mAutofillHints.length; i++) {
                dest.writeString(mAutofillHints[i]);
            }
        }
        dest.writeString(mType);
        dest.writeBoolean(mPinned);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<InlineSuggestionInfo> CREATOR = new Creator<InlineSuggestionInfo>() {
        @Override
        public InlineSuggestionInfo createFromParcel(Parcel in) {
            InlinePresentationSpec spec = in.readTypedObject(InlinePresentationSpec.CREATOR);
            String source = in.readString();
            int hintsCount = in.readInt();
            String[] hints = null;
            if (hintsCount >= 0) {
                hints = new String[hintsCount];
                for (int i = 0; i < hintsCount; i++) {
                    hints[i] = in.readString();
                }
            }
            String type = in.readString();
            boolean pinned = in.readBoolean();
            return new InlineSuggestionInfo(spec, source, hints, type, pinned);
        }

        @Override
        public InlineSuggestionInfo[] newArray(int size) {
            return new InlineSuggestionInfo[size];
        }
    };
}
