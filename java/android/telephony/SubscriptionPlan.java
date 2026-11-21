// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Range;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class SubscriptionPlan implements Parcelable {
    public static final long BYTES_UNKNOWN = -1L;
    public static final long BYTES_UNLIMITED = Long.MAX_VALUE;
    public static final long TIME_UNKNOWN = -1L;

    public static final int LIMIT_BEHAVIOR_UNKNOWN = -1;
    public static final int LIMIT_BEHAVIOR_DISABLED = 0;
    public static final int LIMIT_BEHAVIOR_BILLED = 1;
    public static final int LIMIT_BEHAVIOR_THROTTLED = 2;

    private static final int[] DEFAULT_NETWORK_TYPES = new int[] {
        TelephonyManager.NETWORK_TYPE_UNKNOWN,
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20
    };

    private final ZonedDateTime mStart;
    private final ZonedDateTime mEnd;
    private final Period mPeriod;
    private CharSequence mTitle;
    private CharSequence mSummary;
    private long mDataLimitBytes = BYTES_UNKNOWN;
    private int mDataLimitBehavior = LIMIT_BEHAVIOR_UNKNOWN;
    private long mDataUsageBytes = BYTES_UNKNOWN;
    private long mDataUsageTime = TIME_UNKNOWN;
    private int[] mNetworkTypes = DEFAULT_NETWORK_TYPES.clone();

    private SubscriptionPlan(ZonedDateTime start, ZonedDateTime end, Period period) {
        mStart = Objects.requireNonNull(start, "start");
        mEnd = end;
        mPeriod = period;
    }

    private SubscriptionPlan(Parcel source) {
        mStart = ZonedDateTime.parse(source.readString());
        String endStr = source.readString();
        mEnd = endStr != null ? ZonedDateTime.parse(endStr) : null;
        String periodStr = source.readString();
        mPeriod = periodStr != null ? Period.parse(periodStr) : null;
        mTitle = source.readString();
        mSummary = source.readString();
        mDataLimitBytes = source.readLong();
        mDataLimitBehavior = source.readInt();
        mDataUsageBytes = source.readLong();
        mDataUsageTime = source.readLong();
        int[] netTypes = source.createIntArray();
        mNetworkTypes = netTypes != null ? netTypes : DEFAULT_NETWORK_TYPES.clone();
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mStart.toString());
        dest.writeString(mEnd != null ? mEnd.toString() : null);
        dest.writeString(mPeriod != null ? mPeriod.toString() : null);
        dest.writeString(mTitle != null ? mTitle.toString() : null);
        dest.writeString(mSummary != null ? mSummary.toString() : null);
        dest.writeLong(mDataLimitBytes);
        dest.writeInt(mDataLimitBehavior);
        dest.writeLong(mDataUsageBytes);
        dest.writeLong(mDataUsageTime);
        dest.writeIntArray(mNetworkTypes);
    }

    public String toString() {
        return "SubscriptionPlan{"
                + "start=" + mStart
                + " end=" + mEnd
                + " period=" + mPeriod
                + " title=" + mTitle
                + " summary=" + mSummary
                + " dataLimitBytes=" + mDataLimitBytes
                + " dataLimitBehavior=" + mDataLimitBehavior
                + " dataUsageBytes=" + mDataUsageBytes
                + " dataUsageTime=" + mDataUsageTime
                + " networkTypes=" + Arrays.toString(mNetworkTypes) + "}";
    }

    public int hashCode() {
        return Objects.hash(
                mStart,
                mEnd,
                mPeriod,
                mTitle,
                mSummary,
                Long.valueOf(mDataLimitBytes),
                Integer.valueOf(mDataLimitBehavior),
                Long.valueOf(mDataUsageBytes),
                Long.valueOf(mDataUsageTime),
                Integer.valueOf(Arrays.hashCode(mNetworkTypes)));
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof SubscriptionPlan)) {
            return false;
        }
        SubscriptionPlan other = (SubscriptionPlan) obj;
        return Objects.equals(mStart, other.mStart)
                && Objects.equals(mEnd, other.mEnd)
                && Objects.equals(mPeriod, other.mPeriod)
                && Objects.equals(mTitle, other.mTitle)
                && Objects.equals(mSummary, other.mSummary)
                && mDataLimitBytes == other.mDataLimitBytes
                && mDataLimitBehavior == other.mDataLimitBehavior
                && mDataUsageBytes == other.mDataUsageBytes
                && mDataUsageTime == other.mDataUsageTime
                && Arrays.equals(mNetworkTypes, other.mNetworkTypes);
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public CharSequence getSummary() {
        return mSummary;
    }

    public long getDataLimitBytes() {
        return mDataLimitBytes;
    }

    public int getDataLimitBehavior() {
        return mDataLimitBehavior;
    }

    public long getDataUsageBytes() {
        return mDataUsageBytes;
    }

    public long getDataUsageTime() {
        return mDataUsageTime;
    }

    public int[] getNetworkTypes() {
        return mNetworkTypes.clone();
    }

    public Iterator<Range<ZonedDateTime>> cycleIterator() {
        if (mPeriod == null) {
            return Collections.singletonList(Range.create(mStart, mEnd)).iterator();
        }
        ZonedDateTime now = ZonedDateTime.now(mStart.getZone());
        int cycleIndex = 0;
        while (!mStart.plus(mPeriod.multipliedBy(cycleIndex + 1)).isAfter(now)) {
            cycleIndex++;
            if (cycleIndex > 10000) {
                break;
            }
        }
        final int initialCycle = cycleIndex;
        return new Iterator<Range<ZonedDateTime>>() {
            private int mIndex = initialCycle;

            public boolean hasNext() {
                return mIndex >= 0;
            }

            public Range<ZonedDateTime> next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                ZonedDateTime lower = mStart.plus(mPeriod.multipliedBy(mIndex));
                ZonedDateTime upper = mStart.plus(mPeriod.multipliedBy(mIndex + 1));
                mIndex--;
                return Range.create(lower, upper);
            }
        };
    }

    public static final Parcelable.Creator<SubscriptionPlan> CREATOR =
            new Parcelable.Creator<SubscriptionPlan>() {
                public SubscriptionPlan createFromParcel(Parcel source) {
                    return new SubscriptionPlan(source);
                }

                public SubscriptionPlan[] newArray(int size) {
                    return new SubscriptionPlan[size];
                }
            };

    public static class Builder {
        private final SubscriptionPlan mPlan;

        private Builder(ZonedDateTime start, ZonedDateTime end, Period period) {
            mPlan = new SubscriptionPlan(start, end, period);
        }

        public static Builder createNonrecurring(ZonedDateTime start, ZonedDateTime end) {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("End " + end + " isn't after start " + start);
            }
            return new Builder(start, end, null);
        }

        public static Builder createRecurring(ZonedDateTime start, Period period) {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(period, "period");
            if (period.isZero() || period.isNegative()) {
                throw new IllegalArgumentException("Period " + period + " must be positive");
            }
            return new Builder(start, null, period);
        }

        public SubscriptionPlan build() {
            return mPlan;
        }

        public Builder setTitle(CharSequence title) {
            mPlan.mTitle = title;
            return this;
        }

        public Builder setSummary(CharSequence summary) {
            mPlan.mSummary = summary;
            return this;
        }

        public Builder setDataLimit(long dataLimitBytes, int dataLimitBehavior) {
            if (dataLimitBytes < 0) {
                throw new IllegalArgumentException("Limit bytes must be positive");
            }
            if (dataLimitBehavior < 0) {
                throw new IllegalArgumentException("Limit behavior must be defined");
            }
            mPlan.mDataLimitBytes = dataLimitBytes;
            mPlan.mDataLimitBehavior = dataLimitBehavior;
            return this;
        }

        public Builder setDataUsage(long dataUsageBytes, long dataUsageTime) {
            if (dataUsageBytes < 0) {
                throw new IllegalArgumentException("Usage bytes must be positive");
            }
            if (dataUsageTime < 0) {
                throw new IllegalArgumentException("Usage time must be positive");
            }
            mPlan.mDataUsageBytes = dataUsageBytes;
            mPlan.mDataUsageTime = dataUsageTime;
            return this;
        }

        public Builder setNetworkTypes(int[] networkTypes) {
            Objects.requireNonNull(networkTypes, "networkTypes");
            mPlan.mNetworkTypes = networkTypes.clone();
            return this;
        }

        public Builder resetNetworkTypes() {
            mPlan.mNetworkTypes = DEFAULT_NETWORK_TYPES.clone();
            return this;
        }
    }
}
