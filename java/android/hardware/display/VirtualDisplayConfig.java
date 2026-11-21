// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.display;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.Surface;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class VirtualDisplayConfig implements Parcelable {
    private final String mName;
    private final int mWidth;
    private final int mHeight;
    private final int mDensityDpi;
    private final int mFlags;
    private final Surface mSurface;
    private final Set<String> mDisplayCategories;
    private final float mRequestedRefreshRate;

    VirtualDisplayConfig(
            String name,
            int width,
            int height,
            int densityDpi,
            int flags,
            Surface surface,
            Set<String> displayCategories,
            float requestedRefreshRate) {
        mName = Objects.requireNonNull(name, "name");
        mWidth = width;
        mHeight = height;
        mDensityDpi = densityDpi;
        mFlags = flags;
        mSurface = surface;
        mDisplayCategories = displayCategories == null || displayCategories.isEmpty()
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<String>(displayCategories));
        mRequestedRefreshRate = requestedRefreshRate;
    }

    public String getName() {
        return mName;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getDensityDpi() {
        return mDensityDpi;
    }

    public int getFlags() {
        return mFlags;
    }

    public Surface getSurface() {
        return mSurface;
    }

    public Set<String> getDisplayCategories() {
        return mDisplayCategories;
    }

    public float getRequestedRefreshRate() {
        return mRequestedRefreshRate;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeInt(mWidth);
        dest.writeInt(mHeight);
        dest.writeInt(mDensityDpi);
        dest.writeInt(mFlags);
        dest.writeInt(mSurface != null ? 1 : 0);
        if (mSurface != null) {
            dest.writeInt(mSurface.shirazWidth());
            dest.writeInt(mSurface.shirazHeight());
        }
        dest.writeInt(mDisplayCategories.size());
        for (String cat : mDisplayCategories) {
            dest.writeString(cat);
        }
        dest.writeFloat(mRequestedRefreshRate);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VirtualDisplayConfig)) return false;
        VirtualDisplayConfig that = (VirtualDisplayConfig) o;
        return Objects.equals(mName, that.mName)
                && mWidth == that.mWidth
                && mHeight == that.mHeight
                && mDensityDpi == that.mDensityDpi
                && mFlags == that.mFlags
                && Objects.equals(mSurface, that.mSurface)
                && Objects.equals(mDisplayCategories, that.mDisplayCategories)
                && Float.compare(that.mRequestedRefreshRate, mRequestedRefreshRate) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mName,
                Integer.valueOf(mWidth),
                Integer.valueOf(mHeight),
                Integer.valueOf(mDensityDpi),
                Integer.valueOf(mFlags),
                mSurface,
                mDisplayCategories,
                Float.valueOf(mRequestedRefreshRate));
    }

    @Override
    public String toString() {
        return "VirtualDisplayConfig("
                + " mName=" + mName
                + " mHeight=" + mHeight
                + " mWidth=" + mWidth
                + " mDensityDpi=" + mDensityDpi
                + " mFlags=" + mFlags
                + " mRequestedRefreshRate=" + mRequestedRefreshRate
                + " mDisplayCategories=" + mDisplayCategories
                + ")";
    }

    public static final Parcelable.Creator<VirtualDisplayConfig> CREATOR =
            new Parcelable.Creator<VirtualDisplayConfig>() {
                @Override
                public VirtualDisplayConfig createFromParcel(Parcel in) {
                    String name = in.readString();
                    int width = in.readInt();
                    int height = in.readInt();
                    int densityDpi = in.readInt();
                    int flags = in.readInt();
                    Surface surface = null;
                    if (in.readInt() != 0) {
                        int sw = in.readInt();
                        int sh = in.readInt();
                        surface = new Surface();
                        if (sw > 0 && sh > 0) {
                            surface.shirazConfigure(sw, sh, 1);
                        }
                    }
                    int catCount = in.readInt();
                    LinkedHashSet<String> cats = new LinkedHashSet<String>();
                    for (int i = 0; i < catCount; i++) {
                        String cat = in.readString();
                        if (cat != null) {
                            cats.add(cat);
                        }
                    }
                    float refreshRate = in.readFloat();
                    return new VirtualDisplayConfig(
                            name == null ? "" : name,
                            width,
                            height,
                            densityDpi,
                            flags,
                            surface,
                            cats,
                            refreshRate);
                }

                @Override
                public VirtualDisplayConfig[] newArray(int size) {
                    return new VirtualDisplayConfig[size];
                }
            };

    public static final class Builder {
        private final String mName;
        private final int mWidth;
        private final int mHeight;
        private final int mDensityDpi;
        private int mFlags;
        private Surface mSurface;
        private LinkedHashSet<String> mDisplayCategories = new LinkedHashSet<String>();
        private float mRequestedRefreshRate;

        public Builder(String name, int width, int height, int densityDpi) {
            Objects.requireNonNull(name, "name must not be null");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("name must be non-empty");
            }
            if (width <= 0) {
                throw new IllegalArgumentException("width must be greater than 0");
            }
            if (height <= 0) {
                throw new IllegalArgumentException("height must be greater than 0");
            }
            if (densityDpi <= 0) {
                throw new IllegalArgumentException("densityDpi must be greater than 0");
            }
            mName = name;
            mWidth = width;
            mHeight = height;
            mDensityDpi = densityDpi;
        }

        public Builder setFlags(int value) {
            mFlags = value;
            return this;
        }

        public Builder setSurface(Surface value) {
            mSurface = value;
            return this;
        }

        public Builder setDisplayCategories(Set<String> value) {
            Objects.requireNonNull(value, "displayCategories must not be null");
            mDisplayCategories = new LinkedHashSet<String>(value);
            return this;
        }

        public Builder addDisplayCategory(String displayCategory) {
            Objects.requireNonNull(displayCategory, "displayCategory must not be null");
            mDisplayCategories.add(displayCategory);
            return this;
        }

        public Builder setRequestedRefreshRate(float value) {
            if (value < 0.0f) {
                throw new IllegalArgumentException(
                        "VirtualDisplay requestedRefreshRate must be non-negative");
            }
            mRequestedRefreshRate = value;
            return this;
        }

        public VirtualDisplayConfig build() {
            return new VirtualDisplayConfig(
                    mName,
                    mWidth,
                    mHeight,
                    mDensityDpi,
                    mFlags,
                    mSurface,
                    mDisplayCategories,
                    mRequestedRefreshRate);
        }
    }
}
