// SPDX-License-Identifier: GPL-3.0-only
package android.app.usage;

import android.os.Handler;
import android.os.RemoteException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class NetworkStatsManager {
    private final Map<UsageCallback, Handler> mCallbacks = new LinkedHashMap<UsageCallback, Handler>();

    public NetworkStatsManager() {
    }

    public NetworkStats.Bucket querySummaryForDevice(
            int networkType, String subscriberId, long startTime, long endTime)
            throws RemoteException, SecurityException {
        NetworkStats.Bucket bucket = new NetworkStats.Bucket();
        bucket.mBeginTimeStamp = startTime;
        bucket.mEndTimeStamp = endTime;
        return bucket;
    }

    public NetworkStats.Bucket querySummaryForUser(
            int networkType, String subscriberId, long startTime, long endTime)
            throws RemoteException, SecurityException {
        NetworkStats.Bucket bucket = new NetworkStats.Bucket();
        bucket.mBeginTimeStamp = startTime;
        bucket.mEndTimeStamp = endTime;
        return bucket;
    }

    public NetworkStats querySummary(
            int networkType, String subscriberId, long startTime, long endTime)
            throws RemoteException, SecurityException {
        return new NetworkStats(Collections.<NetworkStats.Bucket>emptyList());
    }

    public NetworkStats queryDetailsForUid(
            int networkType, String subscriberId, long startTime, long endTime, int uid)
            throws SecurityException {
        return new NetworkStats(Collections.<NetworkStats.Bucket>emptyList());
    }

    public NetworkStats queryDetailsForUidTag(
            int networkType, String subscriberId, long startTime, long endTime, int uid, int tag)
            throws SecurityException {
        return new NetworkStats(Collections.<NetworkStats.Bucket>emptyList());
    }

    public NetworkStats queryDetailsForUidTagState(
            int networkType,
            String subscriberId,
            long startTime,
            long endTime,
            int uid,
            int tag,
            int state)
            throws SecurityException {
        return new NetworkStats(Collections.<NetworkStats.Bucket>emptyList());
    }

    public NetworkStats queryDetails(
            int networkType, String subscriberId, long startTime, long endTime)
            throws RemoteException, SecurityException {
        return new NetworkStats(Collections.<NetworkStats.Bucket>emptyList());
    }

    public void registerUsageCallback(
            int networkType, String subscriberId, long thresholdBytes, UsageCallback callback) {
        registerUsageCallback(networkType, subscriberId, thresholdBytes, callback, null);
    }

    public synchronized void registerUsageCallback(
            int networkType,
            String subscriberId,
            long thresholdBytes,
            UsageCallback callback,
            Handler handler) {
        if (callback == null) throw new NullPointerException("callback");
        mCallbacks.put(callback, handler);
    }

    public synchronized void unregisterUsageCallback(UsageCallback callback) {
        if (callback == null) throw new IllegalArgumentException("Callback is null");
        mCallbacks.remove(callback);
    }

    public abstract static class UsageCallback {
        public UsageCallback() {
        }

        public abstract void onThresholdReached(int networkType, String subscriberId);
    }
}
