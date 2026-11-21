// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.app.PendingIntent;
import android.os.Handler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/** Read-only desktop connectivity snapshots; no Android network management daemon. */
public class ConnectivityManager {
    @Deprecated
    public static final String CONNECTIVITY_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";
    @Deprecated
    public static final String ACTION_BACKGROUND_DATA_SETTING_CHANGED =
            "android.net.conn.BACKGROUND_DATA_SETTING_CHANGED";
    public static final String ACTION_CAPTIVE_PORTAL_SIGN_IN = "android.net.conn.CAPTIVE_PORTAL";
    public static final String ACTION_RESTRICT_BACKGROUND_CHANGED =
            "android.net.conn.RESTRICT_BACKGROUND_CHANGED";

    @Deprecated
    public static final String EXTRA_NETWORK_INFO = "networkInfo";
    @Deprecated
    public static final String EXTRA_NETWORK_TYPE = "networkType";
    @Deprecated
    public static final String EXTRA_IS_FAILOVER = "isFailover";
    @Deprecated
    public static final String EXTRA_OTHER_NETWORK_INFO = "otherNetwork";
    public static final String EXTRA_NO_CONNECTIVITY = "noConnectivity";
    public static final String EXTRA_REASON = "reason";
    @Deprecated
    public static final String EXTRA_EXTRA_INFO = "extraInfo";
    public static final String EXTRA_CAPTIVE_PORTAL = "android.net.extra.CAPTIVE_PORTAL";
    public static final String EXTRA_CAPTIVE_PORTAL_URL = "android.net.extra.CAPTIVE_PORTAL_URL";
    public static final String EXTRA_NETWORK = "android.net.extra.NETWORK";
    public static final String EXTRA_NETWORK_REQUEST = "android.net.extra.NETWORK_REQUEST";

    public static final int TYPE_NONE = -1,
            TYPE_MOBILE = 0,
            TYPE_WIFI = 1,
            TYPE_MOBILE_MMS = 2,
            TYPE_MOBILE_SUPL = 3,
            TYPE_MOBILE_DUN = 4,
            TYPE_MOBILE_HIPRI = 5,
            TYPE_WIMAX = 6,
            TYPE_BLUETOOTH = 7,
            TYPE_DUMMY = 8,
            TYPE_ETHERNET = 9,
            TYPE_MOBILE_FOTA = 10,
            TYPE_MOBILE_IMS = 11,
            TYPE_MOBILE_CBS = 12,
            TYPE_WIFI_P2P = 13,
            TYPE_MOBILE_IA = 14,
            TYPE_MOBILE_EMERGENCY = 15,
            TYPE_PROXY = 16,
            TYPE_VPN = 17;

    @Deprecated
    public static final int DEFAULT_NETWORK_PREFERENCE = TYPE_WIFI;

    public static final int MULTIPATH_PREFERENCE_HANDOVER = 1;
    public static final int MULTIPATH_PREFERENCE_RELIABILITY = 2;
    public static final int MULTIPATH_PREFERENCE_PERFORMANCE = 4;

    public static final int RESTRICT_BACKGROUND_STATUS_DISABLED = 1;
    public static final int RESTRICT_BACKGROUND_STATUS_WHITELISTED = 2;
    public static final int RESTRICT_BACKGROUND_STATUS_ENABLED = 3;

    private static volatile Network sBoundNetwork;

    /** Shiraz does not apply Android Data Saver background-network restrictions. */
    public int getRestrictBackgroundStatus() {
        return RESTRICT_BACKGROUND_STATUS_DISABLED;
    }

    private ConnectivityManager() {
    }

    /** No Android-managed proxy is configured in this host session. */
    public ProxyInfo getDefaultProxy() {
        return null;
    }

    public static ConnectivityManager shirazForHost() {
        return new ConnectivityManager();
    }

    @Deprecated
    public static boolean isNetworkTypeValid(int type) {
        return type >= 0 && type <= TYPE_VPN;
    }

    @Deprecated
    public void setNetworkPreference(int preference) {
    }

    @Deprecated
    public int getNetworkPreference() {
        return DEFAULT_NETWORK_PREFERENCE;
    }

    /** Link/default-route availability, not a captive-portal or Internet reachability probe. */
    @Deprecated
    public NetworkInfo getActiveNetworkInfo() {
        return shiraz.runtime.HostConnectivity.activeNetwork();
    }

    /**
     * Legacy type lookup over the host adapter's single exposed default network. Unexposed
     * transports (including cellular radios) have no NetworkInfo record.
     */
    @Deprecated
    public NetworkInfo getNetworkInfo(int type) {
        if (!isNetworkTypeValid(type)) {
            return null;
        }
        NetworkInfo info = getActiveNetworkInfo();
        return info != null && info.getType() == type ? info : null;
    }

    @Deprecated
    public NetworkInfo[] getAllNetworkInfo() {
        NetworkInfo info = getActiveNetworkInfo();
        return info == null ? new NetworkInfo[0] : new NetworkInfo[] {info};
    }

    public static class NetworkCallback {
        public static final int FLAG_INCLUDE_LOCATION_INFO = 1;

        public NetworkCallback() {
        }

        public NetworkCallback(int flags) {
            if (flags != 0 && flags != FLAG_INCLUDE_LOCATION_INFO) {
                throw new IllegalArgumentException("Invalid NetworkCallback flags: " + flags);
            }
        }

        public void onAvailable(Network network) {
        }

        public void onLosing(Network network, int maxMsToLive) {
        }

        public void onLost(Network network) {
        }

        public void onUnavailable() {
        }

        public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
        }

        public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
        }

        public void onBlockedStatusChanged(Network network, boolean blocked) {
        }
    }

    public interface OnNetworkActiveListener {
        void onNetworkActive();
    }

    private final Set<OnNetworkActiveListener> activeListeners =
            Collections.newSetFromMap(new IdentityHashMap<OnNetworkActiveListener, Boolean>());

    /** No Android radio idle timers: active means no radio wake-up deferral, not Internet reachability. */
    public boolean isDefaultNetworkActive() {
        return true;
    }

    public void addDefaultNetworkActiveListener(OnNetworkActiveListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        synchronized (activeListeners) {
            activeListeners.add(listener);
        }
    }

    public void removeDefaultNetworkActiveListener(OnNetworkActiveListener listener) {
        synchronized (activeListeners) {
            if (!activeListeners.remove(listener)) {
                throw new IllegalArgumentException("Network active listener was not registered");
            }
        }
    }

    @Deprecated
    public boolean getBackgroundDataSetting() {
        return true;
    }

    private static final Network HOST_NETWORK = new Network(100);

    private final ConnectivityMonitor monitor =
            new ConnectivityMonitor(
                    new ConnectivityMonitor.Source() {
                        public ConnectivityMonitor.Snapshot read() {
                            NetworkInfo info = connected();
                            if (info == null) {
                                return null;
                            }
                            int transport = transport(info);
                            return new ConnectivityMonitor.Snapshot(
                                    NetworkCapabilities.shirazHost(transport),
                                    shiraz.runtime.HostConnectivity.linkProperties());
                        }
                    },
                    true);

    public void shirazClose() {
        monitor.close();
        synchronized (activeListeners) {
            activeListeners.clear();
        }
    }

    private static int transport(NetworkInfo info) {
        return info.getType() == TYPE_WIFI
                ? NetworkCapabilities.TRANSPORT_WIFI
                : info.getType() == TYPE_MOBILE
                        ? NetworkCapabilities.TRANSPORT_CELLULAR
                        : info.getType() == TYPE_ETHERNET
                                ? NetworkCapabilities.TRANSPORT_ETHERNET
                                : -1;
    }

    private NetworkInfo connected() {
        NetworkInfo info = getActiveNetworkInfo();
        return info != null && info.isConnected() ? info : null;
    }

    @Deprecated
    public Network[] getAllNetworks() {
        Network network = getActiveNetwork();
        return network == null ? new Network[0] : new Network[] {network};
    }

    public Network getActiveNetwork() {
        return connected() == null ? null : HOST_NETWORK;
    }

    @Deprecated
    public NetworkInfo getNetworkInfo(Network network) {
        return !HOST_NETWORK.equals(network) ? null : connected();
    }

    public NetworkCapabilities getNetworkCapabilities(Network network) {
        NetworkInfo info = !HOST_NETWORK.equals(network) ? null : connected();
        if (info == null) {
            return null;
        }
        return NetworkCapabilities.shirazHost(transport(info));
    }

    public LinkProperties getLinkProperties(Network network) {
        return !HOST_NETWORK.equals(network) || connected() == null
                ? null
                : shiraz.runtime.HostConnectivity.linkProperties();
    }

    public SocketKeepalive createSocketKeepalive(
            Network network,
            IpSecManager.UdpEncapsulationSocket socket,
            InetAddress source,
            InetAddress destination,
            final Executor executor,
            final SocketKeepalive.Callback callback) {
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(socket, "socket");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        return new SocketKeepalive(executor, callback) {
            @Override
            void startImpl(int intervalSec) {
                executor.execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(SocketKeepalive.ERROR_UNSUPPORTED);
                            }
                        });
            }

            @Override
            void stopImpl() {
            }
        };
    }

    @Deprecated
    public void reportBadNetwork(Network network) {
        reportNetworkConnectivity(network, false);
    }

    public void reportNetworkConnectivity(Network network, boolean hasConnectivity) {
    }

    /** Unknown cost must not authorize unrestricted large transfers. */
    public boolean isActiveNetworkMetered() {
        NetworkCapabilities capabilities = getNetworkCapabilities(getActiveNetwork());
        return capabilities == null
                || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
    }

    public void registerNetworkCallback(NetworkRequest request, NetworkCallback callback) {
        register(callback, null, Objects.requireNonNull(request));
    }

    public void registerDefaultNetworkCallback(NetworkCallback callback) {
        register(callback);
    }

    public void requestNetwork(NetworkRequest request, NetworkCallback callback) {
        register(callback, null, Objects.requireNonNull(request));
    }

    public void requestNetwork(NetworkRequest request, NetworkCallback callback, Handler handler) {
        if (request == null) {
            throw new NullPointerException("request");
        }
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        register(callback, handler, request);
    }

    public void requestNetwork(
            NetworkRequest request, final NetworkCallback callback, int timeoutMs) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(callback, "callback");
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }
        register(callback, null, request);
    }

    public void requestNetwork(
            NetworkRequest request,
            final NetworkCallback callback,
            Handler handler,
            int timeoutMs) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(callback, "callback");
        Objects.requireNonNull(handler, "handler");
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }
        register(callback, handler, request);
    }

    public void requestNetwork(NetworkRequest request, PendingIntent operation) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(operation, "operation");
    }

    public void releaseNetworkRequest(PendingIntent operation) {
        Objects.requireNonNull(operation, "operation");
    }

    public void registerNetworkCallback(
            NetworkRequest request, NetworkCallback callback, Handler handler) {
        if (request == null) {
            throw new NullPointerException("request");
        }
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        register(callback, handler, request);
    }

    public void registerNetworkCallback(NetworkRequest request, PendingIntent operation) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(operation, "operation");
    }

    public void registerDefaultNetworkCallback(NetworkCallback callback, Handler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        register(callback, handler);
    }

    public void registerBestMatchingNetworkCallback(
            NetworkRequest request, NetworkCallback callback, Handler handler) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(handler, "handler");
        register(callback, handler, request);
    }

    public boolean requestBandwidthUpdate(Network network) {
        Objects.requireNonNull(network, "network");
        return HOST_NETWORK.equals(network);
    }

    private void register(NetworkCallback callback) {
        register(callback, null);
    }

    private void register(NetworkCallback callback, Handler handler) {
        register(callback, handler, null);
    }

    private void register(final NetworkCallback callback, Handler handler, NetworkRequest request) {
        monitor.register(callback, handler, request);
    }

    public void unregisterNetworkCallback(NetworkCallback callback) {
        monitor.unregister(callback);
    }

    public void unregisterNetworkCallback(PendingIntent operation) {
        Objects.requireNonNull(operation, "operation");
    }

    public int getMultipathPreference(Network network) {
        return 0;
    }

    public boolean bindProcessToNetwork(Network network) {
        if (network != null && !HOST_NETWORK.equals(network)) {
            return false;
        }
        sBoundNetwork = network;
        return true;
    }

    @Deprecated
    public static boolean setProcessDefaultNetwork(Network network) {
        if (network != null && !HOST_NETWORK.equals(network)) {
            return false;
        }
        sBoundNetwork = network;
        return true;
    }

    public Network getBoundNetworkForProcess() {
        return sBoundNetwork;
    }

    @Deprecated
    public static Network getProcessDefaultNetwork() {
        return sBoundNetwork;
    }

    public byte[] getNetworkWatchlistConfigHash() {
        return null;
    }

    public int getConnectionOwnerUid(
            int protocol, InetSocketAddress local, InetSocketAddress remote) {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(remote, "remote");
        return -1;
    }
}
