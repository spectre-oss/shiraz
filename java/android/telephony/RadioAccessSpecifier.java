// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class RadioAccessSpecifier implements Parcelable {
    private final int mRadioAccessNetwork;
    private final int[] mBands;
    private final int[] mChannels;

    public RadioAccessSpecifier(int ran, int[] bands, int[] channels) {
        mRadioAccessNetwork = ran;
        mBands = bands != null ? bands.clone() : null;
        mChannels = channels != null ? channels.clone() : null;
    }

    private RadioAccessSpecifier(Parcel in) {
        mRadioAccessNetwork = in.readInt();
        mBands = in.createIntArray();
        mChannels = in.createIntArray();
    }

    public int getRadioAccessNetwork() {
        return mRadioAccessNetwork;
    }

    public int[] getBands() {
        return mBands != null ? mBands.clone() : null;
    }

    public int[] getChannels() {
        return mChannels != null ? mChannels.clone() : null;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRadioAccessNetwork);
        dest.writeIntArray(mBands);
        dest.writeIntArray(mChannels);
    }

    public boolean equals(Object o) {
        if (!(o instanceof RadioAccessSpecifier)) {
            return false;
        }
        RadioAccessSpecifier ras = (RadioAccessSpecifier) o;
        return mRadioAccessNetwork == ras.mRadioAccessNetwork
                && Arrays.equals(mBands, ras.mBands)
                && Arrays.equals(mChannels, ras.mChannels);
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mRadioAccessNetwork),
                Integer.valueOf(Arrays.hashCode(mBands)),
                Integer.valueOf(Arrays.hashCode(mChannels)));
    }

    public String toString() {
        return "RadioAccessSpecifier[mRadioAccessNetwork=" + mRadioAccessNetwork
                + ", mBands=" + Arrays.toString(mBands)
                + ", mChannels=" + Arrays.toString(mChannels) + "]";
    }

    public static final Parcelable.Creator<RadioAccessSpecifier> CREATOR =
            new Parcelable.Creator<RadioAccessSpecifier>() {
                public RadioAccessSpecifier createFromParcel(Parcel in) {
                    return new RadioAccessSpecifier(in);
                }

                public RadioAccessSpecifier[] newArray(int size) {
                    return new RadioAccessSpecifier[size];
                }
            };
}
