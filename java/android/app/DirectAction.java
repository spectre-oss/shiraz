// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.LocusId;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Represents an abstract action that can be performed on an app. */
public final class DirectAction implements Parcelable {
    private final String mId;
    private final Bundle mExtras;
    private final LocusId mLocusId;

    private DirectAction(String id, Bundle extras, LocusId locusId) {
        if (id == null) throw new NullPointerException("id");
        mId = id;
        mExtras = extras;
        mLocusId = locusId;
    }

    public String getId() {
        return mId;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public LocusId getLocusId() {
        return mLocusId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public int hashCode() {
        return mId.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DirectAction)) return false;
        return Objects.equals(mId, ((DirectAction) other).mId);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeBundle(mExtras);
        dest.writeTypedObject(mLocusId, flags);
    }

    public static final Parcelable.Creator<DirectAction> CREATOR =
            new Parcelable.Creator<DirectAction>() {
                @Override
                public DirectAction createFromParcel(Parcel in) {
                    String id = in.readString();
                    Bundle extras = in.readBundle(DirectAction.class.getClassLoader());
                    LocusId locusId = in.readTypedObject(LocusId.CREATOR);
                    return new DirectAction(id, extras, locusId);
                }

                @Override
                public DirectAction[] newArray(int size) {
                    return new DirectAction[size];
                }
            };

    public static final class Builder {
        private final String mId;
        private Bundle mExtras;
        private LocusId mLocusId;

        public Builder(String id) {
            if (id == null) throw new NullPointerException("id");
            mId = id;
        }

        public Builder setExtras(Bundle extras) {
            mExtras = extras;
            return this;
        }

        public Builder setLocusId(LocusId locusId) {
            mLocusId = locusId;
            return this;
        }

        public DirectAction build() {
            return new DirectAction(mId, mExtras, mLocusId);
        }
    }
}
