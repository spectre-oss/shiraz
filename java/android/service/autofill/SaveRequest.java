// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Represents a request to an AutofillService to save user-entered data. */
public final class SaveRequest implements Parcelable {
    private final List<FillContext> mFillContexts;
    private final Bundle mClientState;
    private final List<String> mDatasetIds;

    public SaveRequest(
            ArrayList<FillContext> fillContexts, Bundle clientState, ArrayList<String> datasetIds) {
        mFillContexts = fillContexts != null
                ? Collections.unmodifiableList(new ArrayList<FillContext>(fillContexts))
                : Collections.<FillContext>emptyList();
        mClientState = clientState;
        mDatasetIds = datasetIds != null
                ? Collections.unmodifiableList(new ArrayList<String>(datasetIds))
                : null;
    }

    SaveRequest(Parcel in) {
        ArrayList<FillContext> ctxs = in.createTypedArrayList(FillContext.CREATOR);
        mFillContexts = ctxs != null ? Collections.unmodifiableList(ctxs) : Collections.<FillContext>emptyList();
        mClientState = in.readBundle(SaveRequest.class.getClassLoader());
        int dn = in.readInt();
        if (dn < 0) {
            mDatasetIds = null;
        } else {
            ArrayList<String> ids = new ArrayList<String>(dn);
            for (int i = 0; i < dn; i++) ids.add(in.readString());
            mDatasetIds = Collections.unmodifiableList(ids);
        }
    }

    public List<FillContext> getFillContexts() {
        return mFillContexts;
    }

    public Bundle getClientState() {
        return mClientState;
    }

    public List<String> getDatasetIds() {
        return mDatasetIds;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedList(mFillContexts);
        parcel.writeBundle(mClientState);
        if (mDatasetIds == null) {
            parcel.writeInt(-1);
        } else {
            parcel.writeInt(mDatasetIds.size());
            for (int i = 0; i < mDatasetIds.size(); i++) {
                parcel.writeString(mDatasetIds.get(i));
            }
        }
    }

    public static final Parcelable.Creator<SaveRequest> CREATOR =
            new Parcelable.Creator<SaveRequest>() {
                @Override
                public SaveRequest createFromParcel(Parcel parcel) {
                    return new SaveRequest(parcel);
                }

                @Override
                public SaveRequest[] newArray(int size) {
                    return new SaveRequest[size];
                }
            };
}
