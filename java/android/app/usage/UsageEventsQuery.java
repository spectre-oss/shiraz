// SPDX-License-Identifier: GPL-3.0-only
package android.app.usage;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class UsageEventsQuery implements Parcelable {
    private final long mBeginTimeMillis;
    private final long mEndTimeMillis;
    private final int[] mEventTypes;
    private final String[] mPackageNames;

    private UsageEventsQuery(Builder b) {
        mBeginTimeMillis = b.mBeginTimeMillis;
        mEndTimeMillis = b.mEndTimeMillis;
        mEventTypes = b.mEventTypes.clone();
        mPackageNames = b.mPackageNames.clone();
    }

    UsageEventsQuery(Parcel in) {
        mBeginTimeMillis = in.readLong();
        mEndTimeMillis = in.readLong();
        mEventTypes = in.createIntArray();
        int pkgLen = in.readInt();
        if (pkgLen <= 0) {
            mPackageNames = new String[0];
        } else {
            mPackageNames = new String[pkgLen];
            for (int i = 0; i < pkgLen; i++) {
                mPackageNames[i] = in.readString();
            }
        }
    }

    public long getBeginTimeMillis() {
        return mBeginTimeMillis;
    }

    public long getEndTimeMillis() {
        return mEndTimeMillis;
    }

    public int[] getEventTypes() {
        return mEventTypes == null ? new int[0] : mEventTypes.clone();
    }

    public Set<String> getPackageNames() {
        if (mPackageNames == null || mPackageNames.length == 0) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(mPackageNames)));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mBeginTimeMillis);
        dest.writeLong(mEndTimeMillis);
        dest.writeIntArray(mEventTypes);
        dest.writeInt(mPackageNames.length);
        for (String pkg : mPackageNames) {
            dest.writeString(pkg);
        }
    }

    public static final Parcelable.Creator<UsageEventsQuery> CREATOR =
            new Parcelable.Creator<UsageEventsQuery>() {
                @Override
                public UsageEventsQuery createFromParcel(Parcel in) {
                    return new UsageEventsQuery(in);
                }

                @Override
                public UsageEventsQuery[] newArray(int size) {
                    return new UsageEventsQuery[size];
                }
            };

    public static final class Builder {
        final long mBeginTimeMillis;
        final long mEndTimeMillis;
        int[] mEventTypes = new int[0];
        String[] mPackageNames = new String[0];

        public Builder(long beginTimeMillis, long endTimeMillis) {
            if (beginTimeMillis < 0 || endTimeMillis < beginTimeMillis) {
                throw new IllegalArgumentException("Invalid period: [" + beginTimeMillis + ", " + endTimeMillis + "]");
            }
            mBeginTimeMillis = beginTimeMillis;
            mEndTimeMillis = endTimeMillis;
        }

        public UsageEventsQuery build() {
            return new UsageEventsQuery(this);
        }

        public Builder setEventTypes(int... eventTypes) {
            if (eventTypes == null) throw new NullPointerException("eventTypes");
            HashSet<Integer> unique = new HashSet<Integer>();
            for (int t : eventTypes) {
                if (t < UsageEvents.Event.NONE || t > 31) {
                    throw new IllegalArgumentException("Invalid usage event type: " + t);
                }
                unique.add(Integer.valueOf(t));
            }
            int[] arr = new int[unique.size()];
            int idx = 0;
            for (Integer val : unique) {
                arr[idx++] = val.intValue();
            }
            Arrays.sort(arr);
            mEventTypes = arr;
            return this;
        }

        public Builder setPackageNames(String... pkgNames) {
            if (pkgNames == null) throw new NullPointerException("pkgNames");
            HashSet<String> unique = new HashSet<String>();
            for (String p : pkgNames) {
                if (p != null && !p.trim().isEmpty()) {
                    unique.add(p.trim());
                }
            }
            mPackageNames = unique.toArray(new String[unique.size()]);
            return this;
        }
    }
}
