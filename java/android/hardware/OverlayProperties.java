// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;

import android.os.Parcel;
import android.os.Parcelable;

public final class OverlayProperties implements Parcelable {
    private final boolean mMixedColorSpacesSupported;

    public OverlayProperties() {
        this(true);
    }

    private OverlayProperties(boolean mixedColorSpacesSupported) {
        mMixedColorSpacesSupported = mixedColorSpacesSupported;
    }

    public boolean isCombinationSupported(int dataspace, int format) {
        return format == HardwareBuffer.RGBA_8888
                || format == HardwareBuffer.RGBX_8888
                || format == HardwareBuffer.RGB_565
                || format == HardwareBuffer.RGBA_FP16
                || format == HardwareBuffer.RGBA_1010102;
    }

    public boolean isMixedColorSpacesSupported() {
        return mMixedColorSpacesSupported;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mMixedColorSpacesSupported ? 1 : 0);
    }

    public static final Parcelable.Creator<OverlayProperties> CREATOR =
            new Parcelable.Creator<OverlayProperties>() {
                @Override
                public OverlayProperties createFromParcel(Parcel in) {
                    return new OverlayProperties(in.readInt() != 0);
                }

                @Override
                public OverlayProperties[] newArray(int size) {
                    return new OverlayProperties[size];
                }
            };
}
