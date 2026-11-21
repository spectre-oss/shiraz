// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public class AudioDescriptor implements Parcelable {
    public static final int STANDARD_NONE = 0;
    public static final int STANDARD_EDID = 1;
    public static final int STANDARD_SADB = 2;
    public static final int STANDARD_VSADB = 3;

    private final int mStandard;
    private final int mEncapsulationType;
    private final byte[] mDescriptor;

    AudioDescriptor(int standard, int encapsulationType, byte[] descriptor) {
        mStandard = standard;
        mEncapsulationType = encapsulationType;
        mDescriptor = descriptor != null ? descriptor.clone() : new byte[0];
    }

    private AudioDescriptor(Parcel in) {
        mStandard = in.readInt();
        mEncapsulationType = in.readInt();
        byte[] data = in.createByteArray();
        mDescriptor = data != null ? data : new byte[0];
    }

    public int getStandard() {
        return mStandard;
    }

    public byte[] getDescriptor() {
        return mDescriptor.clone();
    }

    public int getEncapsulationType() {
        return mEncapsulationType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStandard, mEncapsulationType, Arrays.hashCode(mDescriptor));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AudioDescriptor)) return false;
        AudioDescriptor that = (AudioDescriptor) o;
        return mStandard == that.mStandard
                && mEncapsulationType == that.mEncapsulationType
                && Arrays.equals(mDescriptor, that.mDescriptor);
    }

    @Override
    public String toString() {
        return "AudioDescriptor{standard=" + mStandard
                + ", encapsulationType=" + mEncapsulationType
                + ", descriptor=" + Arrays.toString(mDescriptor) + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mStandard);
        dest.writeInt(mEncapsulationType);
        dest.writeByteArray(mDescriptor);
    }

    public static final Parcelable.Creator<AudioDescriptor> CREATOR =
            new Parcelable.Creator<AudioDescriptor>() {
                @Override
                public AudioDescriptor createFromParcel(Parcel in) {
                    return new AudioDescriptor(in);
                }

                @Override
                public AudioDescriptor[] newArray(int size) {
                    return new AudioDescriptor[size];
                }
            };
}
