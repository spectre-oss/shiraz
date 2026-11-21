// SPDX-License-Identifier: GPL-3.0-only
package android.app.usage;

import android.content.res.Configuration;
import android.os.Parcel;
import android.os.Parcelable;

public final class ConfigurationStats implements Parcelable {
    public Configuration mConfiguration;
    public long mBeginTimeStamp;
    public long mEndTimeStamp;
    public long mLastTimeActive;
    public long mTotalTimeActive;
    public int mActivationCount;

    public ConfigurationStats() {
    }

    public ConfigurationStats(ConfigurationStats stats) {
        if (stats.mConfiguration != null) {
            mConfiguration = new Configuration(stats.mConfiguration);
        }
        mBeginTimeStamp = stats.mBeginTimeStamp;
        mEndTimeStamp = stats.mEndTimeStamp;
        mLastTimeActive = stats.mLastTimeActive;
        mTotalTimeActive = stats.mTotalTimeActive;
        mActivationCount = stats.mActivationCount;
    }

    ConfigurationStats(Parcel source) {
        if (source.readInt() != 0) {
            mConfiguration = Configuration.CREATOR.createFromParcel(source);
        }
        mBeginTimeStamp = source.readLong();
        mEndTimeStamp = source.readLong();
        mLastTimeActive = source.readLong();
        mTotalTimeActive = source.readLong();
        mActivationCount = source.readInt();
    }

    public Configuration getConfiguration() {
        return mConfiguration;
    }

    public long getFirstTimeStamp() {
        return mBeginTimeStamp;
    }

    public long getLastTimeStamp() {
        return mEndTimeStamp;
    }

    public long getLastTimeActive() {
        return mLastTimeActive;
    }

    public long getTotalTimeActive() {
        return mTotalTimeActive;
    }

    public int getActivationCount() {
        return mActivationCount;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (mConfiguration != null) {
            dest.writeInt(1);
            mConfiguration.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeLong(mBeginTimeStamp);
        dest.writeLong(mEndTimeStamp);
        dest.writeLong(mLastTimeActive);
        dest.writeLong(mTotalTimeActive);
        dest.writeInt(mActivationCount);
    }

    public static final Parcelable.Creator<ConfigurationStats> CREATOR =
            new Parcelable.Creator<ConfigurationStats>() {
                @Override
                public ConfigurationStats createFromParcel(Parcel source) {
                    return new ConfigurationStats(source);
                }

                @Override
                public ConfigurationStats[] newArray(int size) {
                    return new ConfigurationStats[size];
                }
            };
}
