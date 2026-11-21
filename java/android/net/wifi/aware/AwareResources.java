// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import android.os.Parcel;
import android.os.Parcelable;

public final class AwareResources implements Parcelable {
    public static final Creator<AwareResources> CREATOR =
            new Creator<AwareResources>() {
                @Override
                public AwareResources createFromParcel(Parcel in) {
                    return new AwareResources(in.readInt(), in.readInt(), in.readInt());
                }

                @Override
                public AwareResources[] newArray(int size) {
                    return new AwareResources[size];
                }
            };

    private final int mNumOfAvailableDataPaths;
    private final int mNumOfAvailablePublishSessions;
    private final int mNumOfAvailableSubscribeSessions;

    public AwareResources(
            int availableDataPathsCount,
            int availablePublishSessionsCount,
            int availableSubscribeSessionsCount) {
        this.mNumOfAvailableDataPaths = availableDataPathsCount;
        this.mNumOfAvailablePublishSessions = availablePublishSessionsCount;
        this.mNumOfAvailableSubscribeSessions = availableSubscribeSessionsCount;
    }

    public int getAvailableDataPathsCount() {
        return mNumOfAvailableDataPaths;
    }

    public int getAvailablePublishSessionsCount() {
        return mNumOfAvailablePublishSessions;
    }

    public int getAvailableSubscribeSessionsCount() {
        return mNumOfAvailableSubscribeSessions;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mNumOfAvailableDataPaths);
        dest.writeInt(mNumOfAvailablePublishSessions);
        dest.writeInt(mNumOfAvailableSubscribeSessions);
    }
}
