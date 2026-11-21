// SPDX-License-Identifier: GPL-3.0-only

package android.view.contentcapture;

import android.content.LocusId;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DataRemovalRequest implements Parcelable {
    public static final int FLAG_IS_PREFIX = 1;

    private final String mPackageName;
    private final boolean mForEverything;
    private final List<LocusIdRequest> mLocusIdRequests;

    private DataRemovalRequest(Builder builder) {
        mPackageName = "android";
        mForEverything = builder.mForEverything;
        if (builder.mLocusIds != null) {
            int size = builder.mLocusIds.size();
            List<LocusIdRequest> list = new ArrayList<LocusIdRequest>(size);
            for (int i = 0; i < size; i++) {
                list.add(new LocusIdRequest(builder.mLocusIds.get(i), builder.mFlags.get(i).intValue()));
            }
            mLocusIdRequests = Collections.unmodifiableList(list);
        } else {
            mLocusIdRequests = Collections.emptyList();
        }
    }

    private DataRemovalRequest(Parcel parcel) {
        mPackageName = parcel.readString();
        mForEverything = parcel.readBoolean();
        if (!mForEverything) {
            int size = parcel.readInt();
            List<LocusIdRequest> list = new ArrayList<LocusIdRequest>(size);
            for (int i = 0; i < size; i++) {
                LocusId id = parcel.readParcelable(LocusId.class.getClassLoader());
                int flags = parcel.readInt();
                list.add(new LocusIdRequest(id, flags));
            }
            mLocusIdRequests = Collections.unmodifiableList(list);
        } else {
            mLocusIdRequests = Collections.emptyList();
        }
    }

    public String getPackageName() {
        return mPackageName;
    }

    public boolean isForEverything() {
        return mForEverything;
    }

    public List<LocusIdRequest> getLocusIdRequests() {
        return mLocusIdRequests;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mPackageName);
        parcel.writeBoolean(mForEverything);
        if (!mForEverything) {
            int size = mLocusIdRequests.size();
            parcel.writeInt(size);
            for (int i = 0; i < size; i++) {
                LocusIdRequest req = mLocusIdRequests.get(i);
                parcel.writeParcelable(req.getLocusId(), flags);
                parcel.writeInt(req.getFlags());
            }
        }
    }

    public static final Parcelable.Creator<DataRemovalRequest> CREATOR =
            new Parcelable.Creator<DataRemovalRequest>() {
                @Override
                public DataRemovalRequest createFromParcel(Parcel parcel) {
                    return new DataRemovalRequest(parcel);
                }

                @Override
                public DataRemovalRequest[] newArray(int size) {
                    return new DataRemovalRequest[size];
                }
            };

    public static final class Builder {
        boolean mForEverything;
        List<LocusId> mLocusIds;
        List<Integer> mFlags;
        private boolean mDestroyed;

        public Builder() {
        }

        public Builder forEverything() {
            throwIfDestroyed();
            if (mLocusIds != null) {
                throw new IllegalStateException("Already added LocusIds");
            }
            mForEverything = true;
            return this;
        }

        public Builder addLocusId(LocusId locusId, int flags) {
            throwIfDestroyed();
            if (mForEverything) {
                throw new IllegalStateException("Already is for everything");
            }
            Objects.requireNonNull(locusId, "locusId");
            if (mLocusIds == null) {
                mLocusIds = new ArrayList<LocusId>();
                mFlags = new ArrayList<Integer>();
            }
            mLocusIds.add(locusId);
            mFlags.add(Integer.valueOf(flags));
            return this;
        }

        public DataRemovalRequest build() {
            throwIfDestroyed();
            if (!mForEverything && mLocusIds == null) {
                throw new IllegalStateException(
                        "must call either #forEverything() or add one #addLocusId()");
            }
            mDestroyed = true;
            return new DataRemovalRequest(this);
        }

        private void throwIfDestroyed() {
            if (mDestroyed) {
                throw new IllegalStateException("Already destroyed!");
            }
        }
    }

    public final class LocusIdRequest {
        private final LocusId mLocusId;
        private final int mFlags;

        private LocusIdRequest(LocusId locusId, int flags) {
            mLocusId = locusId;
            mFlags = flags;
        }

        public LocusId getLocusId() {
            return mLocusId;
        }

        public int getFlags() {
            return mFlags;
        }
    }
}
