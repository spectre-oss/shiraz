// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import android.net.NetworkCapabilities;
import android.os.Parcel;
import android.os.Parcelable;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**Contextual user and device signals provided to an {@link IsolatedService}. */
public final class UserData implements Parcelable {
    private final Duration mTimezoneUtcOffset;
    private final int mOrientation;
    private final long mAvailableStorageBytes;
    private final int mBatteryPercentage;
    private final String mCarrier;
    private final NetworkCapabilities mNetworkCapabilities;
    private final int mDataNetworkType;
    private final Map<String, AppInfo> mAppInfos;

    UserData(
            Duration timezoneUtcOffset,
            int orientation,
            long availableStorageBytes,
            int batteryPercentage,
            String carrier,
            NetworkCapabilities networkCapabilities,
            int dataNetworkType,
            Map<String, AppInfo> appInfos) {
        this.mTimezoneUtcOffset =
                timezoneUtcOffset == null ? Duration.ZERO : timezoneUtcOffset;
        this.mOrientation = orientation;
        this.mAvailableStorageBytes = availableStorageBytes;
        this.mBatteryPercentage = batteryPercentage;
        this.mCarrier = carrier == null ? "" : carrier;
        this.mNetworkCapabilities = networkCapabilities;
        this.mDataNetworkType = dataNetworkType;
        Map<String, AppInfo> copy = new LinkedHashMap<String, AppInfo>();
        if (appInfos != null) {
            copy.putAll(appInfos);
        }
        this.mAppInfos = Collections.unmodifiableMap(copy);
    }

    static UserData createDefault() {
        return new UserData(
                Duration.ZERO,
                0,
                0L,
                100,
                "",
                new NetworkCapabilities(),
                0,
                Collections.<String, AppInfo>emptyMap());
    }

    public Duration getTimezoneUtcOffset() {
        return mTimezoneUtcOffset;
    }

    public int getOrientation() {
        return mOrientation;
    }

    public long getAvailableStorageBytes() {
        return mAvailableStorageBytes;
    }

    public int getBatteryPercentage() {
        return mBatteryPercentage;
    }

    public String getCarrier() {
        return mCarrier;
    }

    public NetworkCapabilities getNetworkCapabilities() {
        return mNetworkCapabilities;
    }

    public int getDataNetworkType() {
        return mDataNetworkType;
    }

    public Map<String, AppInfo> getAppInfos() {
        return mAppInfos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserData)) {
            return false;
        }
        UserData that = (UserData) o;
        return mOrientation == that.mOrientation
                && mAvailableStorageBytes == that.mAvailableStorageBytes
                && mBatteryPercentage == that.mBatteryPercentage
                && mDataNetworkType == that.mDataNetworkType
                && Objects.equals(mTimezoneUtcOffset, that.mTimezoneUtcOffset)
                && Objects.equals(mCarrier, that.mCarrier)
                && RequestToken.networkCapabilitiesEqual(
                        mNetworkCapabilities, that.mNetworkCapabilities)
                && Objects.equals(mAppInfos, that.mAppInfos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mTimezoneUtcOffset,
                Integer.valueOf(mOrientation),
                Long.valueOf(mAvailableStorageBytes),
                Integer.valueOf(mBatteryPercentage),
                mCarrier,
                Integer.valueOf(mDataNetworkType),
                mAppInfos);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Objects.requireNonNull(dest, "dest");
        dest.writeLong(mTimezoneUtcOffset.getSeconds());
        dest.writeInt(mTimezoneUtcOffset.getNano());
        dest.writeInt(mOrientation);
        dest.writeLong(mAvailableStorageBytes);
        dest.writeInt(mBatteryPercentage);
        dest.writeString(mCarrier);
        RequestToken.writeNetworkCapabilities(dest, mNetworkCapabilities);
        dest.writeInt(mDataNetworkType);
        dest.writeInt(mAppInfos.size());
        for (Map.Entry<String, AppInfo> entry : mAppInfos.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeTypedObject(entry.getValue(), flags);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<UserData> CREATOR =
            new Parcelable.Creator<UserData>() {
                @Override
                public UserData createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    Duration offset = Duration.ofSeconds(in.readLong(), in.readInt());
                    int orientation = in.readInt();
                    long storage = in.readLong();
                    int battery = in.readInt();
                    String carrier = in.readString();
                    NetworkCapabilities nc = RequestToken.readNetworkCapabilities(in);
                    int netType = in.readInt();
                    int count = in.readInt();
                    Map<String, AppInfo> apps = new LinkedHashMap<String, AppInfo>(count);
                    for (int i = 0; i < count; i++) {
                        String pkg = in.readString();
                        AppInfo info = in.readTypedObject(AppInfo.CREATOR);
                        apps.put(pkg, info);
                    }
                    return new UserData(
                            offset,
                            orientation,
                            storage,
                            battery,
                            carrier,
                            nc,
                            netType,
                            apps);
                }

                @Override
                public UserData[] newArray(int size) {
                    return new UserData[size];
                }
            };
}
