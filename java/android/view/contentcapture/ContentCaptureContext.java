// SPDX-License-Identifier: GPL-3.0-only

package android.view.contentcapture;

import android.content.LocusId;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ContentCaptureContext implements Parcelable {
    private final LocusId mId;
    private final Bundle mExtras;

    ContentCaptureContext(LocusId id, Bundle extras) {
        mId = id;
        mExtras = extras;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    public LocusId getLocusId() {
        return mId;
    }

    public static ContentCaptureContext forLocusId(String id) {
        return new Builder(new LocusId(id)).build();
    }

    @Override
    public String toString() {
        return "Context[id=" + mId + (mExtras != null ? ", hasExtras" : "") + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mId != null ? 1 : 0);
        if (mId != null) {
            parcel.writeParcelable(mId, flags);
        }
        parcel.writeBundle(mExtras);
    }

    public static final Parcelable.Creator<ContentCaptureContext> CREATOR =
            new Parcelable.Creator<ContentCaptureContext>() {
                @Override
                public ContentCaptureContext createFromParcel(Parcel parcel) {
                    boolean hasId = parcel.readInt() == 1;
                    LocusId id = hasId
                            ? (LocusId) parcel.readParcelable(LocusId.class.getClassLoader())
                            : null;
                    Bundle extras = parcel.readBundle();
                    return new ContentCaptureContext(id, extras);
                }

                @Override
                public ContentCaptureContext[] newArray(int size) {
                    return new ContentCaptureContext[size];
                }
            };

    public static final class Builder {
        private final LocusId mId;
        private Bundle mExtras;
        private boolean mDestroyed;

        public Builder(LocusId id) {
            mId = Objects.requireNonNull(id, "id");
        }

        public Builder setExtras(Bundle extras) {
            if (mDestroyed) {
                throw new IllegalStateException("Already called #build()");
            }
            mExtras = Objects.requireNonNull(extras, "extras");
            return this;
        }

        public ContentCaptureContext build() {
            if (mDestroyed) {
                throw new IllegalStateException("Already called #build()");
            }
            mDestroyed = true;
            return new ContentCaptureContext(mId, mExtras);
        }
    }
}
