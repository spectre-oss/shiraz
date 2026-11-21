// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class GnssMeasurementsEvent implements Parcelable {
    private static final int HAS_IS_FULL_TRACKING = 1 << 0;

    public abstract static class Callback {
        public static final int STATUS_NOT_SUPPORTED = 0;
        public static final int STATUS_READY = 1;
        public static final int STATUS_LOCATION_DISABLED = 2;
        public static final int STATUS_NOT_ALLOWED = 3;

        public Callback() {}

        public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {}

        public void onStatusChanged(int status) {}
    }

    private final int mFlag;
    private final GnssClock mClock;
    private final List<GnssMeasurement> mMeasurements;
    private final List<GnssAutomaticGainControl> mGnssAgcs;
    private final boolean mIsFullTracking;

    private GnssMeasurementsEvent(
            int flag,
            GnssClock clock,
            List<GnssMeasurement> measurements,
            List<GnssAutomaticGainControl> agcs,
            boolean isFullTracking) {
        if (clock == null) {
            throw new NullPointerException("clock must not be null");
        }
        mFlag = flag;
        mClock = clock;
        mMeasurements = measurements;
        mGnssAgcs = agcs;
        mIsFullTracking = isFullTracking;
    }

    public GnssClock getClock() {
        return mClock;
    }

    public Collection<GnssMeasurement> getMeasurements() {
        return mMeasurements;
    }

    public Collection<GnssAutomaticGainControl> getGnssAutomaticGainControls() {
        return mGnssAgcs;
    }

    public boolean isFullTracking() {
        return mIsFullTracking;
    }

    public boolean hasIsFullTracking() {
        return (mFlag & HAS_IS_FULL_TRACKING) != 0;
    }

    public static final Parcelable.Creator<GnssMeasurementsEvent> CREATOR =
            new Parcelable.Creator<GnssMeasurementsEvent>() {
                @Override
                public GnssMeasurementsEvent createFromParcel(Parcel in) {
                    int flag = in.readInt();
                    GnssClock clock = in.readTypedObject(GnssClock.CREATOR);
                    List<GnssMeasurement> measurements =
                            in.createTypedArrayList(GnssMeasurement.CREATOR);
                    List<GnssAutomaticGainControl> agcs =
                            in.createTypedArrayList(GnssAutomaticGainControl.CREATOR);
                    boolean isFullTracking = in.readBoolean();
                    return new GnssMeasurementsEvent(
                            flag,
                            clock != null ? clock : new GnssClock(),
                            measurements != null
                                    ? Collections.unmodifiableList(measurements)
                                    : Collections.<GnssMeasurement>emptyList(),
                            agcs != null
                                    ? Collections.unmodifiableList(agcs)
                                    : Collections.<GnssAutomaticGainControl>emptyList(),
                            isFullTracking);
                }

                @Override
                public GnssMeasurementsEvent[] newArray(int size) {
                    return new GnssMeasurementsEvent[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mFlag);
        parcel.writeTypedObject(mClock, flags);
        parcel.writeTypedList(mMeasurements);
        parcel.writeTypedList(mGnssAgcs);
        parcel.writeBoolean(mIsFullTracking);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("GnssMeasurementsEvent[");
        builder.append(mClock);
        builder.append(' ').append(mMeasurements);
        builder.append(' ').append(mGnssAgcs);
        if (hasIsFullTracking()) {
            builder.append(" isFullTracking=").append(mIsFullTracking);
        }
        builder.append(']');
        return builder.toString();
    }

    public static final class Builder {
        private int mFlag;
        private GnssClock mClock;
        private List<GnssMeasurement> mMeasurements;
        private List<GnssAutomaticGainControl> mGnssAgcs;
        private boolean mIsFullTracking;

        public Builder() {
            mClock = new GnssClock();
            mMeasurements = Collections.<GnssMeasurement>emptyList();
            mGnssAgcs = Collections.<GnssAutomaticGainControl>emptyList();
        }

        public Builder(GnssMeasurementsEvent event) {
            if (event == null) {
                throw new NullPointerException("event must not be null");
            }
            mFlag = event.mFlag;
            mClock = event.getClock();
            mMeasurements = new ArrayList<GnssMeasurement>(event.getMeasurements());
            mGnssAgcs = new ArrayList<GnssAutomaticGainControl>(event.getGnssAutomaticGainControls());
            mIsFullTracking = event.isFullTracking();
        }

        public Builder setClock(GnssClock clock) {
            if (clock == null) {
                throw new NullPointerException("clock must not be null");
            }
            mClock = clock;
            return this;
        }

        public Builder setMeasurements(Collection<GnssMeasurement> measurements) {
            if (measurements == null) {
                throw new NullPointerException("measurements must not be null");
            }
            mMeasurements = new ArrayList<GnssMeasurement>(measurements);
            return this;
        }

        public Builder setGnssAutomaticGainControls(Collection<GnssAutomaticGainControl> agcs) {
            if (agcs == null) {
                throw new NullPointerException("agcs must not be null");
            }
            mGnssAgcs = new ArrayList<GnssAutomaticGainControl>(agcs);
            return this;
        }

        public Builder setIsFullTracking(boolean isFullTracking) {
            mFlag |= HAS_IS_FULL_TRACKING;
            mIsFullTracking = isFullTracking;
            return this;
        }

        public Builder clearIsFullTracking() {
            mFlag &= ~HAS_IS_FULL_TRACKING;
            mIsFullTracking = false;
            return this;
        }

        public GnssMeasurementsEvent build() {
            return new GnssMeasurementsEvent(
                    mFlag,
                    mClock,
                    Collections.unmodifiableList(new ArrayList<GnssMeasurement>(mMeasurements)),
                    Collections.unmodifiableList(new ArrayList<GnssAutomaticGainControl>(mGnssAgcs)),
                    mIsFullTracking);
        }
    }
}
