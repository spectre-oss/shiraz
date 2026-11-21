// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Provides information about the colors of a wallpaper. */
public final class WallpaperColors implements Parcelable {
    public static final int HINT_SUPPORTS_DARK_TEXT = 1;
    public static final int HINT_SUPPORTS_DARK_THEME = 2;

    private final Color mPrimaryColor;
    private final Color mSecondaryColor;
    private final Color mTertiaryColor;
    private final int mColorHints;

    public WallpaperColors(Parcel parcel) {
        mPrimaryColor = Color.valueOf(parcel.readInt());
        mSecondaryColor = parcel.readInt() != 0 ? Color.valueOf(parcel.readInt()) : null;
        mTertiaryColor = parcel.readInt() != 0 ? Color.valueOf(parcel.readInt()) : null;
        mColorHints = parcel.readInt();
    }

    public WallpaperColors(Color primaryColor, Color secondaryColor, Color tertiaryColor) {
        this(primaryColor, secondaryColor, tertiaryColor, calculateHints(primaryColor));
    }

    public WallpaperColors(Color primaryColor, Color secondaryColor, Color tertiaryColor, int colorHints) {
        if (primaryColor == null) {
            throw new IllegalArgumentException("Primary color cannot be null.");
        }
        mPrimaryColor = primaryColor;
        mSecondaryColor = secondaryColor;
        mTertiaryColor = tertiaryColor;
        mColorHints = colorHints;
    }

    private static int calculateHints(Color primary) {
        if (primary == null) return 0;
        float lum = 0.2126f * primary.red() + 0.7152f * primary.green() + 0.0722f * primary.blue();
        if (lum > 0.7f) return HINT_SUPPORTS_DARK_TEXT;
        if (lum < 0.25f) return HINT_SUPPORTS_DARK_THEME;
        return 0;
    }

    public static WallpaperColors fromDrawable(Drawable drawable) {
        if (drawable == null) {
            throw new IllegalArgumentException("Drawable cannot be null");
        }
        int w = Math.max(1, Math.min(112, drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 16));
        int h = Math.max(1, Math.min(112, drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 16));
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, w, h);
        drawable.draw(canvas);
        return fromBitmap(bmp);
    }

    public static WallpaperColors fromBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap cannot be null");
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        long rSum = 0, gSum = 0, bSum = 0;
        int count = 0;
        int stepX = Math.max(1, w / 16);
        int stepY = Math.max(1, h / 16);
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int c = bitmap.getPixel(x, y);
                rSum += Color.red(c);
                gSum += Color.green(c);
                bSum += Color.blue(c);
                count++;
            }
        }
        int avg = count > 0
                ? Color.rgb((int) (rSum / count), (int) (gSum / count), (int) (bSum / count))
                : Color.BLACK;
        return new WallpaperColors(Color.valueOf(avg), null, null);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPrimaryColor.toArgb());
        if (mSecondaryColor != null) {
            dest.writeInt(1);
            dest.writeInt(mSecondaryColor.toArgb());
        } else {
            dest.writeInt(0);
        }
        if (mTertiaryColor != null) {
            dest.writeInt(1);
            dest.writeInt(mTertiaryColor.toArgb());
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mColorHints);
    }

    public Color getPrimaryColor() {
        return mPrimaryColor;
    }

    public Color getSecondaryColor() {
        return mSecondaryColor;
    }

    public Color getTertiaryColor() {
        return mTertiaryColor;
    }

    public int getColorHints() {
        return mColorHints;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WallpaperColors)) return false;
        WallpaperColors other = (WallpaperColors) o;
        return mColorHints == other.mColorHints
                && Objects.equals(mPrimaryColor, other.mPrimaryColor)
                && Objects.equals(mSecondaryColor, other.mSecondaryColor)
                && Objects.equals(mTertiaryColor, other.mTertiaryColor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPrimaryColor, mSecondaryColor, mTertiaryColor, Integer.valueOf(mColorHints));
    }

    @Override
    public String toString() {
        return "[WallpaperColors: primary=" + mPrimaryColor + ", hints=" + mColorHints + "]";
    }

    public static final Parcelable.Creator<WallpaperColors> CREATOR =
            new Parcelable.Creator<WallpaperColors>() {
                @Override
                public WallpaperColors createFromParcel(Parcel in) {
                    return new WallpaperColors(in);
                }

                @Override
                public WallpaperColors[] newArray(int size) {
                    return new WallpaperColors[size];
                }
            };
}
