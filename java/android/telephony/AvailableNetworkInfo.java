// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class AvailableNetworkInfo implements Parcelable {
    public static final int PRIORITY_HIGH = 1;
    public static final int PRIORITY_MED = 2;
    public static final int PRIORITY_LOW = 3;

    private final int mSubId;
    private final int mPriority;
    private final ArrayList<String> mMccMncs;
    private final ArrayList<Integer> mBands;
    private final ArrayList<RadioAccessSpecifier> mRadioAccessSpecifiers;

    public AvailableNetworkInfo(int subId, int priority, List<String> mccMncs,
            List<Integer> bands) {
        this(subId, priority, mccMncs, bands, new ArrayList<RadioAccessSpecifier>());
    }

    private AvailableNetworkInfo(int subId, int priority, List<String> mccMncs,
            List<Integer> bands, List<RadioAccessSpecifier> radioAccessSpecifiers) {
        mSubId = subId;
        mPriority = priority;
        mMccMncs = mccMncs != null ? new ArrayList<String>(mccMncs) : new ArrayList<String>();
        mBands = bands != null ? new ArrayList<Integer>(bands) : new ArrayList<Integer>();
        mRadioAccessSpecifiers = radioAccessSpecifiers != null
                ? new ArrayList<RadioAccessSpecifier>(radioAccessSpecifiers)
                : new ArrayList<RadioAccessSpecifier>();
    }

    private AvailableNetworkInfo(Parcel in) {
        mSubId = in.readInt();
        mPriority = in.readInt();
        int mccMncCount = in.readInt();
        mMccMncs = new ArrayList<String>(Math.max(0, mccMncCount));
        for (int i = 0; i < mccMncCount; i++) {
            mMccMncs.add(in.readString());
        }
        mBands = new ArrayList<Integer>();
        int bandsCount = in.readInt();
        for (int i = 0; i < bandsCount; i++) {
            mBands.add(Integer.valueOf(in.readInt()));
        }
        mRadioAccessSpecifiers = new ArrayList<RadioAccessSpecifier>();
        in.readTypedList(mRadioAccessSpecifiers, RadioAccessSpecifier.CREATOR);
    }

    public int getSubId() {
        return mSubId;
    }

    public int getPriority() {
        return mPriority;
    }

    public List<String> getMccMncs() {
        return Collections.unmodifiableList(mMccMncs);
    }

    @Deprecated
    public List<Integer> getBands() {
        return Collections.unmodifiableList(mBands);
    }

    public List<RadioAccessSpecifier> getRadioAccessSpecifiers() {
        return Collections.unmodifiableList(mRadioAccessSpecifiers);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSubId);
        dest.writeInt(mPriority);
        dest.writeInt(mMccMncs.size());
        for (int i = 0; i < mMccMncs.size(); i++) {
            dest.writeString(mMccMncs.get(i));
        }
        dest.writeInt(mBands.size());
        for (int i = 0; i < mBands.size(); i++) {
            dest.writeInt(mBands.get(i).intValue());
        }
        dest.writeTypedList(mRadioAccessSpecifiers);
    }

    public boolean equals(Object o) {
        if (!(o instanceof AvailableNetworkInfo)) {
            return false;
        }
        AvailableNetworkInfo ani = (AvailableNetworkInfo) o;
        return mSubId == ani.mSubId
                && mPriority == ani.mPriority
                && Objects.equals(mMccMncs, ani.mMccMncs)
                && Objects.equals(mBands, ani.mBands)
                && Objects.equals(mRadioAccessSpecifiers, ani.mRadioAccessSpecifiers);
    }

    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mSubId),
                Integer.valueOf(mPriority),
                mMccMncs,
                mBands,
                mRadioAccessSpecifiers);
    }

    public String toString() {
        return "AvailableNetworkInfo: mSubId: " + mSubId
                + " mPriority: " + mPriority
                + " mMccMncs: " + mMccMncs
                + " mBands: " + mBands
                + " mRadioAccessSpecifiers: " + mRadioAccessSpecifiers;
    }

    public static final Parcelable.Creator<AvailableNetworkInfo> CREATOR =
            new Parcelable.Creator<AvailableNetworkInfo>() {
                public AvailableNetworkInfo createFromParcel(Parcel in) {
                    return new AvailableNetworkInfo(in);
                }

                public AvailableNetworkInfo[] newArray(int size) {
                    return new AvailableNetworkInfo[size];
                }
            };

    public static final class Builder {
        private final int mSubId;
        private int mPriority = PRIORITY_LOW;
        private List<String> mMccMncs = new ArrayList<String>();
        private List<RadioAccessSpecifier> mRadioAccessSpecifiers =
                new ArrayList<RadioAccessSpecifier>();

        public Builder(int subId) {
            mSubId = subId;
        }

        public Builder setPriority(int priority) {
            if (priority < PRIORITY_HIGH || priority > PRIORITY_LOW) {
                throw new IllegalArgumentException("Invalid priority: " + priority);
            }
            mPriority = priority;
            return this;
        }

        public Builder setMccMncs(List<String> mccMncs) {
            Objects.requireNonNull(mccMncs, "mccMncs must not be null");
            mMccMncs = new ArrayList<String>(mccMncs);
            return this;
        }

        public Builder setRadioAccessSpecifiers(
                List<RadioAccessSpecifier> radioAccessSpecifiers) {
            Objects.requireNonNull(radioAccessSpecifiers,
                    "radioAccessSpecifiers must not be null");
            mRadioAccessSpecifiers = new ArrayList<RadioAccessSpecifier>(radioAccessSpecifiers);
            return this;
        }

        public AvailableNetworkInfo build() {
            if (mSubId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                throw new IllegalArgumentException("Invalid subId");
            }
            return new AvailableNetworkInfo(
                    mSubId,
                    mPriority,
                    mMccMncs,
                    new ArrayList<Integer>(),
                    mRadioAccessSpecifiers);
        }
    }
}
