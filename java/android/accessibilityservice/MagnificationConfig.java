// SPDX-License-Identifier: GPL-3.0-only
package android.accessibilityservice;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class MagnificationConfig implements Parcelable {
    public static final int MAGNIFICATION_MODE_DEFAULT = 0;
    public static final int MAGNIFICATION_MODE_FULLSCREEN = 1;
    public static final int MAGNIFICATION_MODE_WINDOW = 2;

    private int mMode = MAGNIFICATION_MODE_DEFAULT;
    private boolean mActivated = true;
    private float mScale = Float.NaN;
    private float mCenterX = Float.NaN;
    private float mCenterY = Float.NaN;

    private MagnificationConfig() {
    }

    private MagnificationConfig(Parcel parcel) {
        mMode = parcel.readInt();
        mActivated = parcel.readBoolean();
        mScale = parcel.readFloat();
        mCenterX = parcel.readFloat();
        mCenterY = parcel.readFloat();
    }

    public int getMode() {
        return mMode;
    }

    public boolean isActivated() {
        return mActivated;
    }

    public float getScale() {
        return mScale;
    }

    public float getCenterX() {
        return mCenterX;
    }

    public float getCenterY() {
        return mCenterY;
    }

    @Override
    public String toString() {
        return "MagnificationConfig[mode:" + mMode
                + ", activated:" + mActivated
                + ", scale:" + mScale
                + ", centerX:" + mCenterX
                + ", centerY:" + mCenterY + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        Objects.requireNonNull(parcel, "parcel");
        parcel.writeInt(mMode);
        parcel.writeBoolean(mActivated);
        parcel.writeFloat(mScale);
        parcel.writeFloat(mCenterX);
        parcel.writeFloat(mCenterY);
    }

    public static final Parcelable.Creator<MagnificationConfig> CREATOR =
            new Parcelable.Creator<MagnificationConfig>() {
                @Override
                public MagnificationConfig createFromParcel(Parcel parcel) {
                    return new MagnificationConfig(parcel);
                }

                @Override
                public MagnificationConfig[] newArray(int size) {
                    return new MagnificationConfig[size];
                }
            };

    public static final class Builder {
        private int mMode = MAGNIFICATION_MODE_DEFAULT;
        private boolean mActivated = true;
        private float mScale = Float.NaN;
        private float mCenterX = Float.NaN;
        private float mCenterY = Float.NaN;

        public Builder() {
        }

        public Builder setMode(int mode) {
            if (mode != MAGNIFICATION_MODE_DEFAULT
                    && mode != MAGNIFICATION_MODE_FULLSCREEN
                    && mode != MAGNIFICATION_MODE_WINDOW) {
                throw new IllegalArgumentException("Invalid magnification mode: " + mode);
            }
            mMode = mode;
            return this;
        }

        public Builder setActivated(boolean activated) {
            mActivated = activated;
            return this;
        }

        public Builder setScale(float scale) {
            if (!Float.isNaN(scale) && (scale < 1f || scale > 8f)) {
                throw new IllegalArgumentException("Scale must be in [1, 8] or NaN");
            }
            mScale = scale;
            return this;
        }

        public Builder setCenterX(float centerX) {
            mCenterX = centerX;
            return this;
        }

        public Builder setCenterY(float centerY) {
            mCenterY = centerY;
            return this;
        }

        public MagnificationConfig build() {
            MagnificationConfig config = new MagnificationConfig();
            config.mMode = mMode;
            config.mActivated = mActivated;
            config.mScale = mScale;
            config.mCenterX = mCenterX;
            config.mCenterY = mCenterY;
            return config;
        }
    }
}
