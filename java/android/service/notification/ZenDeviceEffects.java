// SPDX-License-Identifier: GPL-3.0-only
package android.service.notification;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ZenDeviceEffects implements Parcelable {
    public static final Parcelable.Creator<ZenDeviceEffects> CREATOR =
            new Parcelable.Creator<ZenDeviceEffects>() {
                @Override
                public ZenDeviceEffects createFromParcel(Parcel in) {
                    return new ZenDeviceEffects(
                            in.readInt() != 0,
                            in.readInt() != 0,
                            in.readInt() != 0,
                            in.readInt() != 0);
                }

                @Override
                public ZenDeviceEffects[] newArray(int size) {
                    return new ZenDeviceEffects[size];
                }
            };

    private final boolean mGrayscale;
    private final boolean mSuppressAmbientDisplay;
    private final boolean mDimWallpaper;
    private final boolean mNightMode;

    ZenDeviceEffects(boolean grayscale, boolean suppressAmbientDisplay,
            boolean dimWallpaper, boolean nightMode) {
        mGrayscale = grayscale;
        mSuppressAmbientDisplay = suppressAmbientDisplay;
        mDimWallpaper = dimWallpaper;
        mNightMode = nightMode;
    }

    public boolean shouldDisplayGrayscale() { return mGrayscale; }
    public boolean shouldSuppressAmbientDisplay() { return mSuppressAmbientDisplay; }
    public boolean shouldDimWallpaper() { return mDimWallpaper; }
    public boolean shouldUseNightMode() { return mNightMode; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ZenDeviceEffects)) return false;
        ZenDeviceEffects that = (ZenDeviceEffects) obj;
        return mGrayscale == that.mGrayscale
                && mSuppressAmbientDisplay == that.mSuppressAmbientDisplay
                && mDimWallpaper == that.mDimWallpaper
                && mNightMode == that.mNightMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mGrayscale, mSuppressAmbientDisplay, mDimWallpaper, mNightMode);
    }

    @Override
    public String toString() {
        return "ZenDeviceEffects{grayscale=" + mGrayscale
                + ", suppressAmbientDisplay=" + mSuppressAmbientDisplay
                + ", dimWallpaper=" + mDimWallpaper
                + ", nightMode=" + mNightMode + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mGrayscale ? 1 : 0);
        dest.writeInt(mSuppressAmbientDisplay ? 1 : 0);
        dest.writeInt(mDimWallpaper ? 1 : 0);
        dest.writeInt(mNightMode ? 1 : 0);
    }

    public static final class Builder {
        private boolean mGrayscale;
        private boolean mSuppressAmbientDisplay;
        private boolean mDimWallpaper;
        private boolean mNightMode;

        public Builder() {
        }

        public Builder(ZenDeviceEffects zenDeviceEffects) {
            if (zenDeviceEffects != null) {
                mGrayscale = zenDeviceEffects.mGrayscale;
                mSuppressAmbientDisplay = zenDeviceEffects.mSuppressAmbientDisplay;
                mDimWallpaper = zenDeviceEffects.mDimWallpaper;
                mNightMode = zenDeviceEffects.mNightMode;
            }
        }

        public Builder setShouldDisplayGrayscale(boolean grayscale) {
            mGrayscale = grayscale;
            return this;
        }

        public Builder setShouldSuppressAmbientDisplay(boolean suppressAmbientDisplay) {
            mSuppressAmbientDisplay = suppressAmbientDisplay;
            return this;
        }

        public Builder setShouldDimWallpaper(boolean dimWallpaper) {
            mDimWallpaper = dimWallpaper;
            return this;
        }

        public Builder setShouldUseNightMode(boolean nightMode) {
            mNightMode = nightMode;
            return this;
        }

        public ZenDeviceEffects build() {
            return new ZenDeviceEffects(
                    mGrayscale, mSuppressAmbientDisplay, mDimWallpaper, mNightMode);
        }
    }
}
