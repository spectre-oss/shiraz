// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class PictureInPictureUiState implements Parcelable {
    private final boolean mIsStashed;
    private final boolean mIsTransitioningToPip;

    PictureInPictureUiState(boolean isStashed, boolean isTransitioningToPip) {
        mIsStashed = isStashed;
        mIsTransitioningToPip = isTransitioningToPip;
    }

    private PictureInPictureUiState(Parcel in) {
        mIsStashed = in.readBoolean();
        mIsTransitioningToPip = in.readBoolean();
    }

    public boolean isStashed() {
        return mIsStashed;
    }

    public boolean isTransitioningToPip() {
        return mIsTransitioningToPip;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PictureInPictureUiState)) {
            return false;
        }
        PictureInPictureUiState that = (PictureInPictureUiState) o;
        return mIsStashed == that.mIsStashed
                && mIsTransitioningToPip == that.mIsTransitioningToPip;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Boolean.valueOf(mIsStashed), Boolean.valueOf(mIsTransitioningToPip));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeBoolean(mIsStashed);
        out.writeBoolean(mIsTransitioningToPip);
    }

    public static final Parcelable.Creator<PictureInPictureUiState> CREATOR =
            new Parcelable.Creator<PictureInPictureUiState>() {
                @Override
                public PictureInPictureUiState createFromParcel(Parcel in) {
                    return new PictureInPictureUiState(in);
                }

                @Override
                public PictureInPictureUiState[] newArray(int size) {
                    return new PictureInPictureUiState[size];
                }
            };
}
