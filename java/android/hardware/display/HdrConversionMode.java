// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.display;

import android.os.Parcel;
import android.os.Parcelable;

public final class HdrConversionMode implements Parcelable {
    public static final int HDR_CONVERSION_UNSUPPORTED = 0;
    public static final int HDR_CONVERSION_PASSTHROUGH = 1;
    public static final int HDR_CONVERSION_SYSTEM = 2;
    public static final int HDR_CONVERSION_FORCE = 3;

    private final int mConversionMode;
    private final int mPreferredHdrOutputType;

    public HdrConversionMode(int conversionMode, int preferredHdrOutputType) {
        if ((conversionMode == HDR_CONVERSION_PASSTHROUGH
                        || conversionMode == HDR_CONVERSION_UNSUPPORTED)
                && preferredHdrOutputType != -1) {
            throw new IllegalArgumentException(
                    "preferredHdrOutputType must not be set if the conversion mode is "
                            + conversionMode);
        }
        mConversionMode = conversionMode;
        mPreferredHdrOutputType = preferredHdrOutputType;
    }

    public HdrConversionMode(int conversionMode) {
        mConversionMode = conversionMode;
        mPreferredHdrOutputType = -1;
    }

    public int getConversionMode() {
        return mConversionMode;
    }

    public int getPreferredHdrOutputType() {
        return mPreferredHdrOutputType;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mConversionMode);
        dest.writeInt(mPreferredHdrOutputType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HdrConversionMode)) return false;
        HdrConversionMode that = (HdrConversionMode) o;
        return mConversionMode == that.mConversionMode
                && mPreferredHdrOutputType == that.mPreferredHdrOutputType;
    }

    @Override
    public int hashCode() {
        return 31 * mConversionMode + mPreferredHdrOutputType;
    }

    @Override
    public String toString() {
        return "HdrConversionMode{ConversionMode=" + mConversionMode
                + ", PreferredHdrOutputType=" + mPreferredHdrOutputType + "}";
    }

    public static final Parcelable.Creator<HdrConversionMode> CREATOR =
            new Parcelable.Creator<HdrConversionMode>() {
                @Override
                public HdrConversionMode createFromParcel(Parcel source) {
                    return new HdrConversionMode(source.readInt(), source.readInt());
                }

                @Override
                public HdrConversionMode[] newArray(int size) {
                    return new HdrConversionMode[size];
                }
            };
}
