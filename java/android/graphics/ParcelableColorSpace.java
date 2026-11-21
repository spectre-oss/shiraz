// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import android.os.Parcel;
import android.os.Parcelable;

/** Parcelable wrapper around named or parametric RGB ColorSpace instances. */
public final class ParcelableColorSpace implements Parcelable {
    private final ColorSpace mColorSpace;

    public static boolean isParcelable(ColorSpace colorSpace) {
        if (colorSpace == null) {
            throw new NullPointerException("colorSpace");
        }
        if (colorSpace.getId() == ColorSpace.MIN_ID) {
            if (!(colorSpace instanceof ColorSpace.Rgb)) {
                return false;
            }
            ColorSpace.Rgb rgb = (ColorSpace.Rgb) colorSpace;
            return rgb.getTransferParameters() != null;
        }
        return true;
    }

    public ParcelableColorSpace(ColorSpace colorSpace) {
        if (colorSpace == null) {
            throw new NullPointerException("colorSpace");
        }
        this.mColorSpace = colorSpace;
        if (mColorSpace.getId() == ColorSpace.MIN_ID) {
            if (!(mColorSpace instanceof ColorSpace.Rgb)) {
                throw new IllegalArgumentException(
                        "Unable to parcel unknown ColorSpaces that are not ColorSpace.Rgb");
            }
            ColorSpace.Rgb rgb = (ColorSpace.Rgb) mColorSpace;
            if (rgb.getTransferParameters() == null) {
                throw new IllegalArgumentException(
                        "ColorSpace must use an ICC parametric transfer function to be parcelable");
            }
        }
    }

    public ColorSpace getColorSpace() {
        return mColorSpace;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        int id = mColorSpace.getId();
        dest.writeInt(id);
        if (id == ColorSpace.MIN_ID) {
            ColorSpace.Rgb rgb = (ColorSpace.Rgb) mColorSpace;
            dest.writeString(rgb.getName());
            dest.writeFloatArray(rgb.getPrimaries());
            dest.writeFloatArray(rgb.getWhitePoint());
            ColorSpace.Rgb.TransferParameters params = rgb.getTransferParameters();
            dest.writeDouble(params.a);
            dest.writeDouble(params.b);
            dest.writeDouble(params.c);
            dest.writeDouble(params.d);
            dest.writeDouble(params.e);
            dest.writeDouble(params.f);
            dest.writeDouble(params.g);
        }
    }

    public static final Parcelable.Creator<ParcelableColorSpace> CREATOR =
            new Parcelable.Creator<ParcelableColorSpace>() {
                @Override
                public ParcelableColorSpace createFromParcel(Parcel source) {
                    int id = source.readInt();
                    if (id == ColorSpace.MIN_ID) {
                        String name = source.readString();
                        float[] primaries = source.createFloatArray();
                        float[] whitePoint = source.createFloatArray();
                        double a = source.readDouble();
                        double b = source.readDouble();
                        double c = source.readDouble();
                        double d = source.readDouble();
                        double e = source.readDouble();
                        double f = source.readDouble();
                        double g = source.readDouble();
                        ColorSpace.Rgb.TransferParameters function =
                                new ColorSpace.Rgb.TransferParameters(a, b, c, d, e, f, g);
                        return new ParcelableColorSpace(
                                new ColorSpace.Rgb(name, primaries, whitePoint, function));
                    }
                    ColorSpace.Named[] named = ColorSpace.Named.values();
                    if (id < 0 || id >= named.length) {
                        throw new IllegalArgumentException("Invalid ColorSpace id: " + id);
                    }
                    return new ParcelableColorSpace(ColorSpace.get(named[id]));
                }

                @Override
                public ParcelableColorSpace[] newArray(int size) {
                    return new ParcelableColorSpace[size];
                }
            };

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ParcelableColorSpace other = (ParcelableColorSpace) o;
        return mColorSpace.equals(other.mColorSpace);
    }

    @Override
    public int hashCode() {
        return mColorSpace.hashCode();
    }
}
