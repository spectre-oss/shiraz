// SPDX-License-Identifier: GPL-3.0-only
package android.window;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.concurrent.atomic.AtomicLong;

public final class InputTransferToken implements Parcelable {
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    private final long mToken;

    /** @hide */
    public InputTransferToken() {
        mToken = NEXT_ID.getAndIncrement();
    }

    InputTransferToken(long token) {
        mToken = token;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mToken);
    }

    @Override
    public int hashCode() {
        return (int) (mToken ^ (mToken >>> 32));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof InputTransferToken)) {
            return false;
        }
        return mToken == ((InputTransferToken) obj).mToken;
    }

    public static final Parcelable.Creator<InputTransferToken> CREATOR =
            new Parcelable.Creator<InputTransferToken>() {
                @Override
                public InputTransferToken createFromParcel(Parcel in) {
                    return new InputTransferToken(in.readLong());
                }

                @Override
                public InputTransferToken[] newArray(int size) {
                    return new InputTransferToken[size];
                }
            };
}
