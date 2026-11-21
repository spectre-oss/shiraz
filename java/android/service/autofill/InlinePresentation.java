// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.app.slice.Slice;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.inline.InlinePresentationSpec;
import java.util.Objects;

/** Presentation metadata for an inline autofill suggestion. */
public final class InlinePresentation implements Parcelable {
    private final Slice mSlice;
    private final InlinePresentationSpec mInlinePresentationSpec;
    private final boolean mPinned;

    public InlinePresentation(
            Slice slice, InlinePresentationSpec inlinePresentationSpec, boolean pinned) {
        if (slice == null) throw new NullPointerException("slice");
        if (inlinePresentationSpec == null) throw new NullPointerException("inlinePresentationSpec");
        mSlice = slice;
        mInlinePresentationSpec = inlinePresentationSpec;
        mPinned = pinned;
    }

    public static InlinePresentation createTooltipPresentation(
            Slice slice, InlinePresentationSpec spec) {
        return new InlinePresentation(slice, spec, false);
    }

    public Slice getSlice() {
        return mSlice;
    }

    public InlinePresentationSpec getInlinePresentationSpec() {
        return mInlinePresentationSpec;
    }

    public boolean isPinned() {
        return mPinned;
    }

    @Override
    public String toString() {
        return "InlinePresentation { slice = " + mSlice
                + ", inlinePresentationSpec = " + mInlinePresentationSpec
                + ", pinned = " + mPinned + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InlinePresentation)) return false;
        InlinePresentation that = (InlinePresentation) o;
        return mPinned == that.mPinned
                && Objects.equals(mSlice, that.mSlice)
                && Objects.equals(mInlinePresentationSpec, that.mInlinePresentationSpec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSlice, mInlinePresentationSpec, Boolean.valueOf(mPinned));
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mSlice, flags);
        dest.writeTypedObject(mInlinePresentationSpec, flags);
        dest.writeInt(mPinned ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<InlinePresentation> CREATOR =
            new Parcelable.Creator<InlinePresentation>() {
                @Override
                public InlinePresentation createFromParcel(Parcel in) {
                    Slice slice = in.readTypedObject(Slice.CREATOR);
                    InlinePresentationSpec spec = in.readTypedObject(InlinePresentationSpec.CREATOR);
                    boolean pinned = in.readInt() != 0;
                    return new InlinePresentation(slice, spec, pinned);
                }

                @Override
                public InlinePresentation[] newArray(int size) {
                    return new InlinePresentation[size];
                }
            };
}
