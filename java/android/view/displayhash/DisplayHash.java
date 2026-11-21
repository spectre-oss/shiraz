// SPDX-License-Identifier: GPL-3.0-only

package android.view.displayhash;

import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;

public final class DisplayHash implements Parcelable {
    private final long mTimeMillis;
    private final Rect mBoundsInWindow;
    private final String mHashAlgorithm;
    private final byte[] mImageHash;
    private final byte[] mHmac;

    DisplayHash(
            long timeMillis,
            Rect boundsInWindow,
            String hashAlgorithm,
            byte[] imageHash,
            byte[] hmac) {
        mTimeMillis = timeMillis;
        mBoundsInWindow = boundsInWindow != null ? new Rect(boundsInWindow) : new Rect();
        mHashAlgorithm = hashAlgorithm != null ? hashAlgorithm : "";
        mImageHash = imageHash != null ? imageHash.clone() : new byte[0];
        mHmac = hmac != null ? hmac.clone() : new byte[0];
    }

    private DisplayHash(Parcel in) {
        mTimeMillis = in.readLong();
        mBoundsInWindow = in.readTypedObject(Rect.CREATOR);
        mHashAlgorithm = in.readString();
        mImageHash = in.createByteArray();
        mHmac = in.createByteArray();
    }

    @Override
    public String toString() {
        return "DisplayHash { timeMillis = " + mTimeMillis
                + ", boundsInWindow = " + mBoundsInWindow
                + ", hashAlgorithm = " + mHashAlgorithm
                + ", imageHash = " + Arrays.toString(mImageHash)
                + ", hmac = " + Arrays.toString(mHmac) + " }";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mTimeMillis);
        dest.writeTypedObject(mBoundsInWindow, flags);
        dest.writeString(mHashAlgorithm);
        dest.writeByteArray(mImageHash);
        dest.writeByteArray(mHmac);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<DisplayHash> CREATOR =
            new Parcelable.Creator<DisplayHash>() {
                @Override
                public DisplayHash createFromParcel(Parcel in) {
                    return new DisplayHash(in);
                }

                @Override
                public DisplayHash[] newArray(int size) {
                    return new DisplayHash[size];
                }
            };
}
