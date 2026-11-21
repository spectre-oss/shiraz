// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.aware;

import android.net.MacAddress;

public class IdentityChangedListener {
    public static final int CLUSTER_CHANGE_EVENT_STARTED = 0;
    public static final int CLUSTER_CHANGE_EVENT_JOINED = 1;

    public IdentityChangedListener() {}

    public void onIdentityChanged(byte[] mac) {}

    public void onClusterIdChanged(int clusterEventType, MacAddress clusterId) {}
}
