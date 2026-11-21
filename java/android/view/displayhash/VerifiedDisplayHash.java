// SPDX-License-Identifier: GPL-3.0-only

package android.view.displayhash;

import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class VerifiedDisplayHash implements Parcelable {
    private final long mTimeMillis;
    private final Rect mBoundsInWindow;
    private final String mHashAlgorithm;
    private final byte[] mImageHash;

    public VerifiedDisplayHash(
            long timeMillis,
            Rect boundsInWindow,
            String hashAlgorithm,
            byte[] imageHash) {
        mTimeMillis = timeMillis;
        mBoundsInWindow = new Rect(Objects.requireNonNull(boundsInWindow, "boundsInWindow"));
        mHashAlgorithm = Objects.requireNonNull(hashAlgorithm, "hashAlgorithm");
        mImageHash = Objects.requireNonNull(imageHash, "imageHash").clone();
    }

    private VerifiedDisplayHash(Parcel in) {
        mTimeMillis = in.readLong();
        mBoundsInWindow = in.readTypedObject(Rect.CREATOR);
        mHashAlgorithm = in.readString();
        mImageHash = in.createByteArray();
    }

    public long getTimeMillis() {
        return mTimeMillis;
    }

    public Rect getBoundsInWindow() {
        return new Rect(mBoundsInWindow);
    }

    public String getHashAlgorithm() {
        return mHashAlgorithm;
    }

    public byte[] getImageHash() {
        return mImageHash.clone();
    }

    @Override
    public String toString() {
        return "VerifiedDisplayHash { timeMillis = " + mTimeMillis
                + ", boundsInWindow = " + mBoundsInWindow
                + ", hashAlgorithm = " + mHashAlgorithm
                + ", imageHash = " + Arrays.toString(mImageHash) + " }";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mTimeMillis);
        dest.writeTypedObject(mBoundsInWindow, flags);
        dest.writeString(mHashAlgorithm);
        dest.writeByteArray(mImageHash);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<VerifiedDisplayHash> CREATOR =
            new Parcelable.Creator<VerifiedDisplayHash>() {
                @Override
                public VerifiedDisplayHash createFromParcel(Parcel in) {
                    return new VerifiedDisplayHash(in);
                }

                @Override
                public VerifiedDisplayHash[] newArray(int size) {
                    return new VerifiedDisplayHash[size];
                }
            };
}
