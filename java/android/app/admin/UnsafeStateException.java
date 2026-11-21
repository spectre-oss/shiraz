// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.List;

public final class UnsafeStateException extends IllegalStateException implements Parcelable {
    private final int mOperation;
    private final int mReason;

    public UnsafeStateException(int operation, int reason) {
        super("Operation " + operation + " unsafe due to reason " + reason);
        mOperation = operation;
        mReason = reason;
    }

    public List<Integer> getReasons() {
        return Collections.singletonList(Integer.valueOf(mReason));
    }

    @Override
    public String getMessage() {
        return super.getMessage();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mOperation);
        dest.writeInt(mReason);
    }

    public static final Parcelable.Creator<UnsafeStateException> CREATOR =
            new Parcelable.Creator<UnsafeStateException>() {
                @Override
                public UnsafeStateException createFromParcel(Parcel source) {
                    return new UnsafeStateException(source.readInt(), source.readInt());
                }

                @Override
                public UnsafeStateException[] newArray(int size) {
                    return new UnsafeStateException[size];
                }
            };
}
