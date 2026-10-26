// SPDX-License-Identifier: GPL-3.0-only
package android.net;

/** Read-only desktop connectivity snapshots; no Android network management daemon. */
public final class ConnectivityManager {
    public static final int TYPE_NONE = -1, TYPE_MOBILE = 0, TYPE_WIFI = 1,
            TYPE_MOBILE_MMS = 2, TYPE_MOBILE_SUPL = 3, TYPE_MOBILE_DUN = 4,
            TYPE_MOBILE_HIPRI = 5, TYPE_WIMAX = 6, TYPE_BLUETOOTH = 7,
            TYPE_DUMMY = 8, TYPE_ETHERNET = 9, TYPE_MOBILE_FOTA = 10,
            TYPE_MOBILE_IMS = 11, TYPE_MOBILE_CBS = 12, TYPE_WIFI_P2P = 13,
            TYPE_MOBILE_IA = 14, TYPE_MOBILE_EMERGENCY = 15, TYPE_PROXY = 16,
            TYPE_VPN = 17;

    public static final int RESTRICT_BACKGROUND_STATUS_DISABLED=1, RESTRICT_BACKGROUND_STATUS_WHITELISTED=2, RESTRICT_BACKGROUND_STATUS_ENABLED=3;
    /** Shiraz does not apply Android Data Saver background-network restrictions. */
    public int getRestrictBackgroundStatus(){return RESTRICT_BACKGROUND_STATUS_DISABLED;}
    private ConnectivityManager() {}
    /** No Android-managed proxy is configured in this host session. */
    public ProxyInfo getDefaultProxy() { return null; }
    public static ConnectivityManager shirazForHost() { return new ConnectivityManager(); }
    public static boolean isNetworkTypeValid(int type) { return type >= 0 && type <= TYPE_VPN; }

    /** Link/default-route availability, not a captive-portal or Internet reachability probe. */
    public NetworkInfo getActiveNetworkInfo() {
        return shiraz.runtime.HostConnectivity.activeNetwork();
    }

    public static class NetworkCallback {
        public NetworkCallback() {}
        public void onAvailable(Network network) {}
        public void onLosing(Network network, int maxMsToLive) {}
        public void onLost(Network network) {}
        public void onUnavailable() {}
        public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {}
        public void onBlockedStatusChanged(Network network, boolean blocked) {}
    }

    private static final Network HOST_NETWORK = new Network(100);
    private final java.util.ArrayList<NetworkCallback> callbacks = new java.util.ArrayList<NetworkCallback>();

    private NetworkInfo connected() {
        NetworkInfo info = getActiveNetworkInfo();
        return info != null && info.isConnected() ? info : null;
    }
    public Network getActiveNetwork() { return connected() == null ? null : HOST_NETWORK; }
    public NetworkInfo getNetworkInfo(Network network) { return network == null ? null : connected(); }
    public NetworkCapabilities getNetworkCapabilities(Network network) {
        NetworkInfo info = network == null ? null : connected();
        if (info == null) return null;
        return NetworkCapabilities.shirazHost(info.getType() == TYPE_WIFI ? NetworkCapabilities.TRANSPORT_WIFI : info.getType() == TYPE_MOBILE ? NetworkCapabilities.TRANSPORT_CELLULAR : NetworkCapabilities.TRANSPORT_ETHERNET);
    }
    public boolean isActiveNetworkMetered() { return false; }
    public void registerNetworkCallback(NetworkRequest request, NetworkCallback callback) { register(callback); }
    public void registerDefaultNetworkCallback(NetworkCallback callback) { register(callback); }
    public void requestNetwork(NetworkRequest request, NetworkCallback callback) { register(callback); }
    private void register(NetworkCallback callback) {
        if (callback == null) throw new NullPointerException("callback");
        synchronized (callbacks) { if (callbacks.contains(callback)) throw new IllegalArgumentException("NetworkCallback already registered"); callbacks.add(callback); }
        Network network = getActiveNetwork();
        if (network != null) {
            callback.onAvailable(network);
            callback.onCapabilitiesChanged(network, getNetworkCapabilities(network));
        }
    }
    public void unregisterNetworkCallback(NetworkCallback callback) {
        synchronized (callbacks) { if (!callbacks.remove(callback)) throw new IllegalArgumentException("NetworkCallback was not registered"); }
    }
}
