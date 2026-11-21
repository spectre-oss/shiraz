// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.app.assist.AssistStructure;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;

/** Snapshot of an autofillable activity hierarchy for a specific FillRequest. */
public final class FillContext implements Parcelable {
    private final int mRequestId;
    private final AssistStructure mStructure;
    private final AutofillId mFocusedId;

    public FillContext(int requestId, AssistStructure structure, AutofillId focusedId) {
        mRequestId = requestId;
        mStructure = structure;
        mFocusedId = focusedId;
    }

    FillContext(Parcel in) {
        mRequestId = in.readInt();
        mStructure = in.readTypedObject(AssistStructure.CREATOR);
        mFocusedId = in.readTypedObject(AutofillId.CREATOR);
    }

    @Override
    public String toString() {
        return "FillContext [reqId=" + mRequestId + ", focusedId=" + mFocusedId + "]";
    }

    public int getRequestId() {
        return mRequestId;
    }

    public AssistStructure getStructure() {
        return mStructure;
    }

    public AutofillId getFocusedId() {
        return mFocusedId;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRequestId);
        dest.writeTypedObject(mStructure, flags);
        dest.writeTypedObject(mFocusedId, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<FillContext> CREATOR =
            new Parcelable.Creator<FillContext>() {
                @Override
                public FillContext createFromParcel(Parcel in) {
                    return new FillContext(in);
                }

                @Override
                public FillContext[] newArray(int size) {
                    return new FillContext[size];
                }
            };
}
