// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import android.widget.RemoteViews;
import java.util.ArrayList;

/** Defines batch updates to a RemoteViews template inside CustomDescription. */
public final class BatchUpdates implements Parcelable {
    private final RemoteViews mUpdates;
    private final ArrayList<Integer> mChildIds;
    private final ArrayList<Transformation> mTransformations;

    BatchUpdates(RemoteViews updates, ArrayList<Integer> childIds, ArrayList<Transformation> transformations) {
        mUpdates = updates;
        mChildIds = new ArrayList<Integer>(childIds);
        mTransformations = new ArrayList<Transformation>(transformations);
    }

    BatchUpdates(Parcel in) {
        mUpdates = in.readTypedObject(RemoteViews.CREATOR);
        int n = in.readInt();
        mChildIds = new ArrayList<Integer>(n);
        mTransformations = new ArrayList<Transformation>(n);
        for (int i = 0; i < n; i++) {
            mChildIds.add(Integer.valueOf(in.readInt()));
            Parcelable p = in.readParcelable(BatchUpdates.class.getClassLoader());
            if (p instanceof Transformation) {
                mTransformations.add((Transformation) p);
            }
        }
    }

    @Override
    public String toString() {
        return "BatchUpdates: [hasUpdates=" + (mUpdates != null)
                + ", transformations=" + mTransformations.size() + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mUpdates, flags);
        dest.writeInt(mChildIds.size());
        for (int i = 0; i < mChildIds.size(); i++) {
            dest.writeInt(mChildIds.get(i).intValue());
            Transformation t = mTransformations.get(i);
            dest.writeParcelable(t instanceof Parcelable ? (Parcelable) t : null, flags);
        }
    }

    public static final Parcelable.Creator<BatchUpdates> CREATOR =
            new Parcelable.Creator<BatchUpdates>() {
                @Override
                public BatchUpdates createFromParcel(Parcel in) {
                    return new BatchUpdates(in);
                }

                @Override
                public BatchUpdates[] newArray(int size) {
                    return new BatchUpdates[size];
                }
            };

    public static class Builder {
        private RemoteViews mUpdates;
        private final ArrayList<Integer> mChildIds = new ArrayList<Integer>();
        private final ArrayList<Transformation> mTransformations = new ArrayList<Transformation>();
        private boolean mDestroyed;

        public Builder() {
        }

        public Builder updateTemplate(RemoteViews updates) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (updates == null) throw new NullPointerException("updates");
            mUpdates = updates;
            return this;
        }

        public Builder transformChild(int id, Transformation transformation) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (transformation == null) throw new NullPointerException("transformation");
            if (!(transformation instanceof Parcelable)) {
                throw new IllegalArgumentException("Invalid transformation: " + transformation);
            }
            mChildIds.add(Integer.valueOf(id));
            mTransformations.add(transformation);
            return this;
        }

        public BatchUpdates build() {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (mUpdates == null && mTransformations.isEmpty()) {
                throw new IllegalStateException("must call either updateTemplate() or transformChild()");
            }
            mDestroyed = true;
            return new BatchUpdates(mUpdates, mChildIds, mTransformations);
        }
    }
}
