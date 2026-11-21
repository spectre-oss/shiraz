// SPDX-License-Identifier: GPL-3.0-only
package android.nfc;

import android.os.Parcel;
import android.os.Parcelable;

public final class AvailableNfcAntenna implements Parcelable {
    private final int mLocationX;
    private final int mLocationY;

    public AvailableNfcAntenna(int locationX, int locationY) {
        mLocationX = locationX;
        mLocationY = locationY;
    }

    public int getLocationX() {
        return mLocationX;
    }

    public int getLocationY() {
        return mLocationY;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mLocationX);
        dest.writeInt(mLocationY);
    }

    @Override
    public int hashCode() {
        return 31 * mLocationX + mLocationY;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AvailableNfcAntenna)) {
            return false;
        }
        AvailableNfcAntenna other = (AvailableNfcAntenna) obj;
        return mLocationX == other.mLocationX && mLocationY == other.mLocationY;
    }

    @Override
    public String toString() {
        return "AvailableNfcAntenna x: " + mLocationX + " y: " + mLocationY;
    }

    public static final Parcelable.Creator<AvailableNfcAntenna> CREATOR =
            new Parcelable.Creator<AvailableNfcAntenna>() {
                @Override
                public AvailableNfcAntenna createFromParcel(Parcel in) {
                    int x = in.readInt();
                    int y = in.readInt();
                    return new AvailableNfcAntenna(x, y);
                }

                @Override
                public AvailableNfcAntenna[] newArray(int size) {
                    return new AvailableNfcAntenna[size];
                }
            };
}
