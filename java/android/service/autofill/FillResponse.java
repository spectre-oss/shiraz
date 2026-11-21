// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.content.IntentSender;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.Set;

/** Response for AutofillService.onFillRequest. */
public final class FillResponse implements Parcelable {
    public static final int FLAG_TRACK_CONTEXT_COMMITED = 1;
    public static final int FLAG_DISABLE_ACTIVITY_ONLY = 2;
    public static final int FLAG_DELAY_FILL = 4;

    private final ArrayList<Dataset> mDatasets;
    private final SaveInfo mSaveInfo;
    private final Bundle mClientState;
    private final int mFlags;
    private final long mDisableDuration;

    FillResponse(Builder b) {
        mDatasets = new ArrayList<Dataset>(b.mDatasets);
        mSaveInfo = b.mSaveInfo;
        mClientState = b.mClientState;
        mFlags = b.mFlags;
        mDisableDuration = b.mDisableDuration;
    }

    FillResponse(Parcel in) {
        ArrayList<Dataset> ds = in.createTypedArrayList(Dataset.CREATOR);
        mDatasets = ds != null ? ds : new ArrayList<Dataset>();
        mSaveInfo = in.readTypedObject(SaveInfo.CREATOR);
        mClientState = in.readBundle(FillResponse.class.getClassLoader());
        mFlags = in.readInt();
        mDisableDuration = in.readLong();
    }

    @Override
    public String toString() {
        return "FillResponse[datasets=" + mDatasets.size() + ", flags=" + mFlags + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedList(mDatasets);
        parcel.writeTypedObject(mSaveInfo, flags);
        parcel.writeBundle(mClientState);
        parcel.writeInt(mFlags);
        parcel.writeLong(mDisableDuration);
    }

    public static final Parcelable.Creator<FillResponse> CREATOR =
            new Parcelable.Creator<FillResponse>() {
                @Override
                public FillResponse createFromParcel(Parcel parcel) {
                    return new FillResponse(parcel);
                }

                @Override
                public FillResponse[] newArray(int size) {
                    return new FillResponse[size];
                }
            };

    public static final class Builder {
        final ArrayList<Dataset> mDatasets = new ArrayList<Dataset>();
        SaveInfo mSaveInfo;
        Bundle mClientState;
        int mFlags;
        long mDisableDuration;
        private boolean mHasContent;
        private boolean mDestroyed;

        public Builder() {
        }

        public Builder setDetectedFieldClassifications(
                Set<android.service.assist.classification.FieldClassification> fieldInfos) {
            if (fieldInfos == null) throw new NullPointerException("fieldInfos");
            mHasContent = true;
            return this;
        }

        @Deprecated
        public Builder setAuthentication(
                AutofillId[] ids, IntentSender authentication, RemoteViews presentation) {
            if (ids == null || ids.length == 0) throw new IllegalArgumentException("ids");
            mHasContent = true;
            return this;
        }

        @Deprecated
        public Builder setAuthentication(
                AutofillId[] ids,
                IntentSender authentication,
                RemoteViews presentation,
                InlinePresentation inlinePresentation) {
            if (ids == null || ids.length == 0) throw new IllegalArgumentException("ids");
            mHasContent = true;
            return this;
        }

        @Deprecated
        public Builder setAuthentication(
                AutofillId[] ids,
                IntentSender authentication,
                RemoteViews presentation,
                InlinePresentation inlinePresentation,
                InlinePresentation inlineTooltipPresentation) {
            if (ids == null || ids.length == 0) throw new IllegalArgumentException("ids");
            mHasContent = true;
            return this;
        }

        public Builder setAuthentication(
                AutofillId[] ids, IntentSender authentication, Presentations presentations) {
            if (ids == null || ids.length == 0) throw new IllegalArgumentException("ids");
            mHasContent = true;
            return this;
        }

        public Builder setIgnoredIds(AutofillId... ids) {
            mHasContent = true;
            return this;
        }

        public Builder addDataset(Dataset dataset) {
            if (dataset != null) {
                mDatasets.add(dataset);
            }
            mHasContent = true;
            return this;
        }

        public Builder setSaveInfo(SaveInfo saveInfo) {
            mSaveInfo = saveInfo;
            mHasContent = true;
            return this;
        }

        public Builder setClientState(Bundle clientState) {
            mClientState = clientState;
            mHasContent = true;
            return this;
        }

        public Builder setFieldClassificationIds(AutofillId... ids) {
            if (ids == null || ids.length == 0) throw new IllegalArgumentException("ids");
            mHasContent = true;
            return this;
        }

        public Builder setFlags(int flags) {
            mFlags = flags;
            mHasContent = true;
            return this;
        }

        public Builder disableAutofill(long duration) {
            if (duration <= 0) throw new IllegalArgumentException("duration must be > 0");
            mDisableDuration = duration;
            mHasContent = true;
            return this;
        }

        public Builder setIconResourceId(int id) {
            mHasContent = true;
            return this;
        }

        public Builder setServiceDisplayNameResourceId(int id) {
            mHasContent = true;
            return this;
        }

        public Builder setShowFillDialogIcon(boolean show) {
            mHasContent = true;
            return this;
        }

        public Builder setShowSaveDialogIcon(boolean show) {
            mHasContent = true;
            return this;
        }

        public Builder setHeader(RemoteViews header) {
            mHasContent = true;
            return this;
        }

        public Builder setFooter(RemoteViews footer) {
            mHasContent = true;
            return this;
        }

        public Builder setUserData(UserData userData) {
            mHasContent = true;
            return this;
        }

        public Builder setPresentationCancelIds(int[] ids) {
            mHasContent = true;
            return this;
        }

        public Builder setDialogHeader(RemoteViews header) {
            mHasContent = true;
            return this;
        }

        public Builder setFillDialogTriggerIds(AutofillId... ids) {
            mHasContent = true;
            return this;
        }

        public FillResponse build() {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (!mHasContent) {
                throw new IllegalStateException("need to provide at least one dataset, saveInfo, or authentication");
            }
            mDestroyed = true;
            return new FillResponse(this);
        }
    }
}
