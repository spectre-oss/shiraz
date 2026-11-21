// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Parcel;
import android.os.Parcelable;

@Deprecated
public class Criteria implements Parcelable {
    public static final int NO_REQUIREMENT = 0;

    public static final int POWER_LOW = 1;
    public static final int POWER_MEDIUM = 2;
    public static final int POWER_HIGH = 3;

    public static final int ACCURACY_FINE = 1;
    public static final int ACCURACY_COARSE = 2;

    public static final int ACCURACY_LOW = 1;
    public static final int ACCURACY_MEDIUM = 2;
    public static final int ACCURACY_HIGH = 3;

    private int mHorizontalAccuracy = NO_REQUIREMENT;
    private int mVerticalAccuracy = NO_REQUIREMENT;
    private int mSpeedAccuracy = NO_REQUIREMENT;
    private int mBearingAccuracy = NO_REQUIREMENT;
    private int mPowerRequirement = NO_REQUIREMENT;
    private boolean mAltitudeRequired = false;
    private boolean mBearingRequired = false;
    private boolean mSpeedRequired = false;
    private boolean mCostAllowed = false;

    public Criteria() {
    }

    public Criteria(Criteria criteria) {
        mHorizontalAccuracy = criteria.mHorizontalAccuracy;
        mVerticalAccuracy = criteria.mVerticalAccuracy;
        mSpeedAccuracy = criteria.mSpeedAccuracy;
        mBearingAccuracy = criteria.mBearingAccuracy;
        mPowerRequirement = criteria.mPowerRequirement;
        mAltitudeRequired = criteria.mAltitudeRequired;
        mBearingRequired = criteria.mBearingRequired;
        mSpeedRequired = criteria.mSpeedRequired;
        mCostAllowed = criteria.mCostAllowed;
    }

    public void setHorizontalAccuracy(int accuracy) {
        if (accuracy < NO_REQUIREMENT || accuracy > ACCURACY_HIGH) {
            throw new IllegalArgumentException("accuracy=" + accuracy);
        }
        mHorizontalAccuracy = accuracy;
    }

    public int getHorizontalAccuracy() {
        return mHorizontalAccuracy;
    }

    public void setVerticalAccuracy(int accuracy) {
        if (accuracy < NO_REQUIREMENT || accuracy > ACCURACY_HIGH) {
            throw new IllegalArgumentException("accuracy=" + accuracy);
        }
        mVerticalAccuracy = accuracy;
    }

    public int getVerticalAccuracy() {
        return mVerticalAccuracy;
    }

    public void setSpeedAccuracy(int accuracy) {
        if (accuracy < NO_REQUIREMENT || accuracy > ACCURACY_HIGH) {
            throw new IllegalArgumentException("accuracy=" + accuracy);
        }
        mSpeedAccuracy = accuracy;
    }

    public int getSpeedAccuracy() {
        return mSpeedAccuracy;
    }

    public void setBearingAccuracy(int accuracy) {
        if (accuracy < NO_REQUIREMENT || accuracy > ACCURACY_HIGH) {
            throw new IllegalArgumentException("accuracy=" + accuracy);
        }
        mBearingAccuracy = accuracy;
    }

    public int getBearingAccuracy() {
        return mBearingAccuracy;
    }

    public void setAccuracy(int accuracy) {
        if (accuracy < NO_REQUIREMENT || accuracy > ACCURACY_COARSE) {
            throw new IllegalArgumentException("accuracy=" + accuracy);
        }
        switch (accuracy) {
            case NO_REQUIREMENT:
                setHorizontalAccuracy(NO_REQUIREMENT);
                break;
            case ACCURACY_FINE:
                setHorizontalAccuracy(ACCURACY_HIGH);
                break;
            case ACCURACY_COARSE:
                setHorizontalAccuracy(ACCURACY_LOW);
                break;
        }
    }

    public int getAccuracy() {
        if (mHorizontalAccuracy >= ACCURACY_HIGH) {
            return ACCURACY_FINE;
        } else if (mHorizontalAccuracy != NO_REQUIREMENT) {
            return ACCURACY_COARSE;
        } else {
            return NO_REQUIREMENT;
        }
    }

    public void setPowerRequirement(int level) {
        if (level < NO_REQUIREMENT || level > POWER_HIGH) {
            throw new IllegalArgumentException("level=" + level);
        }
        mPowerRequirement = level;
    }

    public int getPowerRequirement() {
        return mPowerRequirement;
    }

    public void setCostAllowed(boolean costAllowed) {
        mCostAllowed = costAllowed;
    }

    public boolean isCostAllowed() {
        return mCostAllowed;
    }

    public void setAltitudeRequired(boolean altitudeRequired) {
        mAltitudeRequired = altitudeRequired;
    }

    public boolean isAltitudeRequired() {
        return mAltitudeRequired;
    }

    public void setSpeedRequired(boolean speedRequired) {
        mSpeedRequired = speedRequired;
    }

    public boolean isSpeedRequired() {
        return mSpeedRequired;
    }

    public void setBearingRequired(boolean bearingRequired) {
        mBearingRequired = bearingRequired;
    }

    public boolean isBearingRequired() {
        return mBearingRequired;
    }

    public static final Parcelable.Creator<Criteria> CREATOR =
            new Parcelable.Creator<Criteria>() {
                @Override
                public Criteria createFromParcel(Parcel in) {
                    Criteria c = new Criteria();
                    c.mHorizontalAccuracy = in.readInt();
                    c.mVerticalAccuracy = in.readInt();
                    c.mSpeedAccuracy = in.readInt();
                    c.mBearingAccuracy = in.readInt();
                    c.mPowerRequirement = in.readInt();
                    c.mAltitudeRequired = in.readBoolean();
                    c.mBearingRequired = in.readBoolean();
                    c.mSpeedRequired = in.readBoolean();
                    c.mCostAllowed = in.readBoolean();
                    return c;
                }

                @Override
                public Criteria[] newArray(int size) {
                    return new Criteria[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mHorizontalAccuracy);
        parcel.writeInt(mVerticalAccuracy);
        parcel.writeInt(mSpeedAccuracy);
        parcel.writeInt(mBearingAccuracy);
        parcel.writeInt(mPowerRequirement);
        parcel.writeBoolean(mAltitudeRequired);
        parcel.writeBoolean(mBearingRequired);
        parcel.writeBoolean(mSpeedRequired);
        parcel.writeBoolean(mCostAllowed);
    }

    @Override
    public String toString() {
        return "Criteria[power=" + mPowerRequirement
                + ", acc=" + mHorizontalAccuracy + ']';
    }
}
