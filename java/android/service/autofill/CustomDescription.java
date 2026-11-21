// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.widget.RemoteViews;
import java.util.ArrayList;

/** Defines a custom description for the Autofill save UI. */
public final class CustomDescription implements Parcelable {
    private final RemoteViews mPresentation;
    private final ArrayList<Integer> mTransformIds;
    private final ArrayList<Transformation> mTransformations;
    private final ArrayList<Validator> mUpdateValidators;
    private final ArrayList<BatchUpdates> mBatchUpdates;
    private final ArrayList<Integer> mActionIds;
    private final ArrayList<OnClickAction> mActions;

    CustomDescription(Builder b) {
        mPresentation = b.mPresentation;
        mTransformIds = new ArrayList<Integer>(b.mTransformIds);
        mTransformations = new ArrayList<Transformation>(b.mTransformations);
        mUpdateValidators = new ArrayList<Validator>(b.mUpdateValidators);
        mBatchUpdates = new ArrayList<BatchUpdates>(b.mBatchUpdates);
        mActionIds = new ArrayList<Integer>(b.mActionIds);
        mActions = new ArrayList<OnClickAction>(b.mActions);
    }

    CustomDescription(Parcel in) {
        mPresentation = in.readTypedObject(RemoteViews.CREATOR);
        int tn = in.readInt();
        mTransformIds = new ArrayList<Integer>(tn);
        mTransformations = new ArrayList<Transformation>(tn);
        for (int i = 0; i < tn; i++) {
            mTransformIds.add(Integer.valueOf(in.readInt()));
            Parcelable p = in.readParcelable(CustomDescription.class.getClassLoader());
            if (p instanceof Transformation) mTransformations.add((Transformation) p);
        }
        int bn = in.readInt();
        mUpdateValidators = new ArrayList<Validator>(bn);
        mBatchUpdates = new ArrayList<BatchUpdates>(bn);
        for (int i = 0; i < bn; i++) {
            Parcelable v = in.readParcelable(CustomDescription.class.getClassLoader());
            if (v instanceof Validator) mUpdateValidators.add((Validator) v);
            mBatchUpdates.add(in.readTypedObject(BatchUpdates.CREATOR));
        }
        int an = in.readInt();
        mActionIds = new ArrayList<Integer>(an);
        mActions = new ArrayList<OnClickAction>(an);
        for (int i = 0; i < an; i++) {
            mActionIds.add(Integer.valueOf(in.readInt()));
            Parcelable a = in.readParcelable(CustomDescription.class.getClassLoader());
            if (a instanceof OnClickAction) mActions.add((OnClickAction) a);
        }
    }

    @Override
    public String toString() {
        return "CustomDescription: [presentation=" + mPresentation + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mPresentation, flags);
        dest.writeInt(mTransformations.size());
        for (int i = 0; i < mTransformations.size(); i++) {
            dest.writeInt(mTransformIds.get(i).intValue());
            Transformation t = mTransformations.get(i);
            dest.writeParcelable(t instanceof Parcelable ? (Parcelable) t : null, flags);
        }
        dest.writeInt(mBatchUpdates.size());
        for (int i = 0; i < mBatchUpdates.size(); i++) {
            Validator v = i < mUpdateValidators.size() ? mUpdateValidators.get(i) : null;
            dest.writeParcelable(v instanceof Parcelable ? (Parcelable) v : null, flags);
            dest.writeTypedObject(mBatchUpdates.get(i), flags);
        }
        dest.writeInt(mActions.size());
        for (int i = 0; i < mActions.size(); i++) {
            dest.writeInt(mActionIds.get(i).intValue());
            OnClickAction a = mActions.get(i);
            dest.writeParcelable(a instanceof Parcelable ? (Parcelable) a : null, flags);
        }
    }

    public static final Parcelable.Creator<CustomDescription> CREATOR =
            new Parcelable.Creator<CustomDescription>() {
                @Override
                public CustomDescription createFromParcel(Parcel in) {
                    return new CustomDescription(in);
                }

                @Override
                public CustomDescription[] newArray(int size) {
                    return new CustomDescription[size];
                }
            };

    public static class Builder {
        final RemoteViews mPresentation;
        final ArrayList<Integer> mTransformIds = new ArrayList<Integer>();
        final ArrayList<Transformation> mTransformations = new ArrayList<Transformation>();
        final ArrayList<Validator> mUpdateValidators = new ArrayList<Validator>();
        final ArrayList<BatchUpdates> mBatchUpdates = new ArrayList<BatchUpdates>();
        final ArrayList<Integer> mActionIds = new ArrayList<Integer>();
        final ArrayList<OnClickAction> mActions = new ArrayList<OnClickAction>();
        private boolean mDestroyed;

        public Builder(RemoteViews parentPresentation) {
            if (parentPresentation == null) throw new NullPointerException("parentPresentation");
            mPresentation = parentPresentation;
        }

        public Builder addChild(int id, Transformation transformation) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (!(transformation instanceof Parcelable)) {
                throw new IllegalArgumentException("Invalid transformation: " + transformation);
            }
            mTransformIds.add(Integer.valueOf(id));
            mTransformations.add(transformation);
            return this;
        }

        public Builder batchUpdate(Validator condition, BatchUpdates updates) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (!(condition instanceof Parcelable)) {
                throw new IllegalArgumentException("Invalid condition: " + condition);
            }
            if (updates == null) throw new NullPointerException("updates");
            mUpdateValidators.add(condition);
            mBatchUpdates.add(updates);
            return this;
        }

        public Builder addOnClickAction(int id, OnClickAction action) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (!(action instanceof Parcelable)) {
                throw new IllegalArgumentException("Invalid action: " + action);
            }
            mActionIds.add(Integer.valueOf(id));
            mActions.add(action);
            return this;
        }

        public CustomDescription build() {
            if (mDestroyed) throw new IllegalStateException("Already built");
            mDestroyed = true;
            return new CustomDescription(this);
        }
    }
}
