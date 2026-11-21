// SPDX-License-Identifier: GPL-3.0-only
package android.window;

import android.os.Parcel;
import android.os.Parcelable;

public final class TrustedPresentationThresholds implements Parcelable {
    private final float mMinAlpha;
    private final float mMinFractionRendered;
    private final int mStabilityRequirementMillis;

    public TrustedPresentationThresholds(
            float minAlpha, float minFractionRendered, int stabilityRequirementMillis) {
        if (Float.isNaN(minAlpha) || minAlpha <= 0f || minAlpha > 1f) {
            throw new IllegalArgumentException(
                    "minAlpha must be in (0, 1], got " + minAlpha);
        }
        if (Float.isNaN(minFractionRendered)
                || minFractionRendered <= 0f
                || minFractionRendered > 1f) {
            throw new IllegalArgumentException(
                    "minFractionRendered must be in (0, 1], got " + minFractionRendered);
        }
        if (stabilityRequirementMillis <= 0) {
            throw new IllegalArgumentException(
                    "stabilityRequirementMillis must be > 0, got " + stabilityRequirementMillis);
        }
        mMinAlpha = minAlpha;
        mMinFractionRendered = minFractionRendered;
        mStabilityRequirementMillis = stabilityRequirementMillis;
    }

    public float getMinAlpha() {
        return mMinAlpha;
    }

    public float getMinFractionRendered() {
        return mMinFractionRendered;
    }

    public int getStabilityRequirementMillis() {
        return mStabilityRequirementMillis;
    }

    @Override
    public String toString() {
        return "TrustedPresentationThresholds{minAlpha=" + mMinAlpha
                + ", minFractionRendered=" + mMinFractionRendered
                + ", stabilityRequirementMillis=" + mStabilityRequirementMillis + "}";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(mMinAlpha);
        dest.writeFloat(mMinFractionRendered);
        dest.writeInt(mStabilityRequirementMillis);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(mMinAlpha);
        result = 31 * result + Float.floatToIntBits(mMinFractionRendered);
        result = 31 * result + mStabilityRequirementMillis;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TrustedPresentationThresholds)) {
            return false;
        }
        TrustedPresentationThresholds other = (TrustedPresentationThresholds) o;
        return Float.compare(mMinAlpha, other.mMinAlpha) == 0
                && Float.compare(mMinFractionRendered, other.mMinFractionRendered) == 0
                && mStabilityRequirementMillis == other.mStabilityRequirementMillis;
    }

    public static final Parcelable.Creator<TrustedPresentationThresholds> CREATOR =
            new Parcelable.Creator<TrustedPresentationThresholds>() {
                @Override
                public TrustedPresentationThresholds createFromParcel(Parcel in) {
                    float minAlpha = in.readFloat();
                    float minFractionRendered = in.readFloat();
                    int stabilityRequirementMillis = in.readInt();
                    return new TrustedPresentationThresholds(
                            minAlpha, minFractionRendered, stabilityRequirementMillis);
                }

                @Override
                public TrustedPresentationThresholds[] newArray(int size) {
                    return new TrustedPresentationThresholds[size];
                }
            };
}
