// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.content.IntentSender;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.regex.Pattern;

/** Represents a set of fields used to autofill an activity. */
public final class Dataset implements Parcelable {
    private final String mId;
    private final IntentSender mAuthentication;
    private final RemoteViews mPresentation;
    private final RemoteViews mDialogPresentation;
    private final InlinePresentation mInlinePresentation;
    private final InlinePresentation mInlineTooltipPresentation;
    private final ArrayList<AutofillId> mFieldIds;
    private final ArrayList<AutofillValue> mFieldValues;

    Dataset(Builder b) {
        mId = b.mId;
        mAuthentication = b.mAuthentication;
        mPresentation = b.mPresentation;
        mDialogPresentation = b.mDialogPresentation;
        mInlinePresentation = b.mInlinePresentation;
        mInlineTooltipPresentation = b.mInlineTooltipPresentation;
        mFieldIds = new ArrayList<AutofillId>(b.mFieldIds);
        mFieldValues = new ArrayList<AutofillValue>(b.mFieldValues);
    }

    Dataset(Parcel in) {
        mId = in.readString();
        mAuthentication = in.readTypedObject(IntentSender.CREATOR);
        mPresentation = in.readTypedObject(RemoteViews.CREATOR);
        mDialogPresentation = in.readTypedObject(RemoteViews.CREATOR);
        mInlinePresentation = in.readTypedObject(InlinePresentation.CREATOR);
        mInlineTooltipPresentation = in.readTypedObject(InlinePresentation.CREATOR);
        int n = in.readInt();
        mFieldIds = new ArrayList<AutofillId>(n);
        mFieldValues = new ArrayList<AutofillValue>(n);
        for (int i = 0; i < n; i++) {
            mFieldIds.add(in.readTypedObject(AutofillId.CREATOR));
            mFieldValues.add(in.readTypedObject(AutofillValue.CREATOR));
        }
    }

    @Override
    public String toString() {
        return "Dataset[id=" + mId + ", fields=" + mFieldIds.size() + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mId);
        parcel.writeTypedObject(mAuthentication, flags);
        parcel.writeTypedObject(mPresentation, flags);
        parcel.writeTypedObject(mDialogPresentation, flags);
        parcel.writeTypedObject(mInlinePresentation, flags);
        parcel.writeTypedObject(mInlineTooltipPresentation, flags);
        parcel.writeInt(mFieldIds.size());
        for (int i = 0; i < mFieldIds.size(); i++) {
            parcel.writeTypedObject(mFieldIds.get(i), flags);
            parcel.writeTypedObject(mFieldValues.get(i), flags);
        }
    }

    public static final Parcelable.Creator<Dataset> CREATOR =
            new Parcelable.Creator<Dataset>() {
                @Override
                public Dataset createFromParcel(Parcel parcel) {
                    return new Dataset(parcel);
                }

                @Override
                public Dataset[] newArray(int size) {
                    return new Dataset[size];
                }
            };

    public static final class Builder {
        String mId;
        IntentSender mAuthentication;
        RemoteViews mPresentation;
        RemoteViews mDialogPresentation;
        InlinePresentation mInlinePresentation;
        InlinePresentation mInlineTooltipPresentation;
        final ArrayList<AutofillId> mFieldIds = new ArrayList<AutofillId>();
        final ArrayList<AutofillValue> mFieldValues = new ArrayList<AutofillValue>();
        private boolean mHasData;
        private boolean mDestroyed;

        @Deprecated
        public Builder(RemoteViews presentation) {
            if (presentation == null) throw new NullPointerException("presentation must be non-null");
            mPresentation = presentation;
        }

        public Builder(Presentations presentations) {
            if (presentations == null) throw new NullPointerException("presentations must be non-null");
            mPresentation = presentations.getMenuPresentation();
            mInlinePresentation = presentations.getInlinePresentation();
            mInlineTooltipPresentation = presentations.getInlineTooltipPresentation();
            mDialogPresentation = presentations.getDialogPresentation();
        }

        public Builder() {
        }

        @Deprecated
        public Builder setInlinePresentation(InlinePresentation inlinePresentation) {
            if (inlinePresentation == null) throw new NullPointerException("inlinePresentation");
            mInlinePresentation = inlinePresentation;
            return this;
        }

        @Deprecated
        public Builder setInlinePresentation(
                InlinePresentation inlinePresentation,
                InlinePresentation inlineTooltipPresentation) {
            if (inlinePresentation == null) throw new NullPointerException("inlinePresentation");
            if (inlineTooltipPresentation == null) throw new NullPointerException("inlineTooltipPresentation");
            mInlinePresentation = inlinePresentation;
            mInlineTooltipPresentation = inlineTooltipPresentation;
            return this;
        }

        public Builder setAuthentication(IntentSender authentication) {
            mAuthentication = authentication;
            return this;
        }

        public Builder setId(String id) {
            mId = id;
            return this;
        }

        @Deprecated
        public Builder setValue(AutofillId id, AutofillValue value) {
            return setValue(id, value, null, null, null, null);
        }

        @Deprecated
        public Builder setValue(AutofillId id, AutofillValue value, RemoteViews presentation) {
            if (presentation == null) throw new NullPointerException("presentation");
            return setValue(id, value, null, presentation, null, null);
        }

        @Deprecated
        public Builder setValue(AutofillId id, AutofillValue value, Pattern filter) {
            return setValue(id, value, filter, null, null, null);
        }

        @Deprecated
        public Builder setValue(
                AutofillId id, AutofillValue value, Pattern filter, RemoteViews presentation) {
            if (presentation == null) throw new NullPointerException("presentation");
            return setValue(id, value, filter, presentation, null, null);
        }

        @Deprecated
        public Builder setValue(
                AutofillId id,
                AutofillValue value,
                RemoteViews presentation,
                InlinePresentation inlinePresentation) {
            if (presentation == null || inlinePresentation == null) throw new NullPointerException();
            return setValue(id, value, null, presentation, inlinePresentation, null);
        }

        @Deprecated
        public Builder setValue(
                AutofillId id,
                AutofillValue value,
                RemoteViews presentation,
                InlinePresentation inlinePresentation,
                InlinePresentation inlineTooltipPresentation) {
            if (presentation == null || inlinePresentation == null || inlineTooltipPresentation == null) {
                throw new NullPointerException();
            }
            return setValue(id, value, null, presentation, inlinePresentation, inlineTooltipPresentation);
        }

        @Deprecated
        public Builder setValue(
                AutofillId id,
                AutofillValue value,
                Pattern filter,
                RemoteViews presentation,
                InlinePresentation inlinePresentation) {
            if (presentation == null || inlinePresentation == null) throw new NullPointerException();
            return setValue(id, value, filter, presentation, inlinePresentation, null);
        }

        @Deprecated
        public Builder setValue(
                AutofillId id,
                AutofillValue value,
                Pattern filter,
                RemoteViews presentation,
                InlinePresentation inlinePresentation,
                InlinePresentation inlineTooltipPresentation) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (id == null) throw new NullPointerException("id");
            mFieldIds.add(id);
            mFieldValues.add(value);
            mHasData = true;
            return this;
        }

        public Builder setField(AutofillId id, Field field) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (id == null) throw new NullPointerException("id");
            mFieldIds.add(id);
            mFieldValues.add(field != null ? field.getValue() : null);
            mHasData = true;
            return this;
        }

        public Builder setField(String hint, Field field) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (hint == null) throw new NullPointerException("hint");
            mHasData = true;
            return this;
        }

        public Builder setFieldForAllHints(Field field) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (field == null) throw new NullPointerException("field");
            mHasData = true;
            return this;
        }

        public Dataset build() {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (!mHasData) throw new IllegalStateException("at least one value must be set");
            mDestroyed = true;
            return new Dataset(this);
        }
    }
}
