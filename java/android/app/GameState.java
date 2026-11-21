// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Parcel;
import android.os.Parcelable;

public final class GameState implements Parcelable {
    public static final int MODE_UNKNOWN = 0;
    public static final int MODE_NONE = 1;
    public static final int MODE_GAMEPLAY_INTERRUPTIBLE = 2;
    public static final int MODE_GAMEPLAY_UNINTERRUPTIBLE = 3;
    public static final int MODE_CONTENT = 4;

    private final boolean mIsLoading;
    private final int mMode;
    private final int mLabel;
    private final int mQuality;

    public GameState(boolean isLoading, int mode) {
        this(isLoading, mode, -1, -1);
    }

    public GameState(boolean isLoading, int mode, int label, int quality) {
        mIsLoading = isLoading;
        mMode = mode;
        mLabel = label;
        mQuality = quality;
    }

    private GameState(Parcel in) {
        mIsLoading = in.readBoolean();
        mMode = in.readInt();
        mLabel = in.readInt();
        mQuality = in.readInt();
    }

    public boolean isLoading() {
        return mIsLoading;
    }

    public int getMode() {
        return mMode;
    }

    public int getLabel() {
        return mLabel;
    }

    public int getQuality() {
        return mQuality;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeBoolean(mIsLoading);
        parcel.writeInt(mMode);
        parcel.writeInt(mLabel);
        parcel.writeInt(mQuality);
    }

    public static final Parcelable.Creator<GameState> CREATOR =
            new Parcelable.Creator<GameState>() {
                @Override
                public GameState createFromParcel(Parcel in) {
                    return new GameState(in);
                }

                @Override
                public GameState[] newArray(int size) {
                    return new GameState[size];
                }
            };
}
