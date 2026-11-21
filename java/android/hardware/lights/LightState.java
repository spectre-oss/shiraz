// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.lights;

import android.os.Parcel;
import android.os.Parcelable;

public final class LightState implements Parcelable {
    private final int mColor;
    private final int mPlayerId;

    @Deprecated
    public LightState(int color) {
        this(color, 0);
    }

    public LightState(int color, int playerId) {
        mColor = color;
        mPlayerId = playerId;
    }

    public int getColor() {
        return mColor;
    }

    public int getPlayerId() {
        return mPlayerId;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mColor);
        dest.writeInt(mPlayerId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "LightState{Color=0x" + Integer.toHexString(mColor)
                + ", PlayerId=" + mPlayerId + "}";
    }

    public static final Parcelable.Creator<LightState> CREATOR =
            new Parcelable.Creator<LightState>() {
                @Override
                public LightState createFromParcel(Parcel in) {
                    return new LightState(in.readInt(), in.readInt());
                }

                @Override
                public LightState[] newArray(int size) {
                    return new LightState[size];
                }
            };

    public static final class Builder {
        private int mValue;
        private boolean mIsForPlayerId;

        public Builder() {
        }

        public Builder setColor(int color) {
            mIsForPlayerId = false;
            mValue = color;
            return this;
        }

        public Builder setPlayerId(int playerId) {
            mIsForPlayerId = true;
            mValue = playerId;
            return this;
        }

        public LightState build() {
            if (!mIsForPlayerId) {
                return new LightState(mValue, 0);
            }
            return new LightState(0, mValue);
        }
    }
}
