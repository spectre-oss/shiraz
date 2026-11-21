// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.MacAddress;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.os.Handler;
import android.os.Looper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class WifiP2pManager {
    public static final String WIFI_P2P_STATE_CHANGED_ACTION =
            "android.net.wifi.p2p.STATE_CHANGED";
    public static final String EXTRA_WIFI_STATE = "wifi_p2p_state";
    public static final int WIFI_P2P_STATE_DISABLED = 1;
    public static final int WIFI_P2P_STATE_ENABLED = 2;

    public static final String WIFI_P2P_CONNECTION_CHANGED_ACTION =
            "android.net.wifi.p2p.CONNECTION_STATE_CHANGE";
    public static final String EXTRA_WIFI_P2P_INFO = "wifiP2pInfo";
    public static final String EXTRA_NETWORK_INFO = "networkInfo";
    public static final String EXTRA_WIFI_P2P_GROUP = "p2pGroupInfo";

    public static final String WIFI_P2P_PEERS_CHANGED_ACTION =
            "android.net.wifi.p2p.PEERS_CHANGED";
    public static final String EXTRA_P2P_DEVICE_LIST = "wifiP2pDeviceList";

    public static final String WIFI_P2P_DISCOVERY_CHANGED_ACTION =
            "android.net.wifi.p2p.DISCOVERY_STATE_CHANGE";
    public static final String EXTRA_DISCOVERY_STATE = "discoveryState";
    public static final int WIFI_P2P_DISCOVERY_STOPPED = 1;
    public static final int WIFI_P2P_DISCOVERY_STARTED = 2;

    public static final String WIFI_P2P_THIS_DEVICE_CHANGED_ACTION =
            "android.net.wifi.p2p.THIS_DEVICE_CHANGED";
    public static final String EXTRA_WIFI_P2P_DEVICE = "wifiP2pDevice";

    public static final String ACTION_WIFI_P2P_LISTEN_STATE_CHANGED =
            "android.net.wifi.p2p.action.WIFI_P2P_LISTEN_STATE_CHANGED";
    public static final String EXTRA_LISTEN_STATE =
            "android.net.wifi.p2p.extra.LISTEN_STATE";
    public static final int WIFI_P2P_LISTEN_STOPPED = 1;
    public static final int WIFI_P2P_LISTEN_STARTED = 2;

    public static final String ACTION_WIFI_P2P_REQUEST_RESPONSE_CHANGED =
            "android.net.wifi.p2p.action.WIFI_P2P_REQUEST_RESPONSE_CHANGED";
    public static final String EXTRA_REQUEST_CONFIG =
            "android.net.wifi.p2p.extra.REQUEST_CONFIG";
    public static final String EXTRA_REQUEST_RESPONSE =
            "android.net.wifi.p2p.extra.REQUEST_RESPONSE";

    public static final int ERROR = 0;
    public static final int P2P_UNSUPPORTED = 1;
    public static final int BUSY = 2;
    public static final int NO_SERVICE_REQUESTS = 3;

    public static final int WIFI_P2P_SCAN_FULL = 0;
    public static final int WIFI_P2P_SCAN_SOCIAL = 1;
    public static final int WIFI_P2P_SCAN_SINGLE_FREQ = 2;

    public static final int CONNECTION_REQUEST_ACCEPT = 0;
    public static final int CONNECTION_REQUEST_REJECT = 1;
    public static final int CONNECTION_REQUEST_DEFER_TO_SERVICE = 2;
    public static final int CONNECTION_REQUEST_DEFER_SHOW_PIN_TO_SERVICE = 3;

    public static final int GROUP_CREATION_FAILURE_REASON_CONNECTION_CANCELLED = 0;
    public static final int GROUP_CREATION_FAILURE_REASON_TIMED_OUT = 1;
    public static final int GROUP_CREATION_FAILURE_REASON_USER_REJECTED = 2;
    public static final int GROUP_CREATION_FAILURE_REASON_PROVISION_DISCOVERY_FAILED = 3;
    public static final int GROUP_CREATION_FAILURE_REASON_GROUP_REMOVED = 4;
    public static final int GROUP_CREATION_FAILURE_REASON_INVITATION_FAILED = 5;

    private final Map<WifiP2pListener, Executor> mListeners =
            new ConcurrentHashMap<WifiP2pListener, Executor>();

    WifiP2pManager() {}

    public interface ChannelListener {
        void onChannelDisconnected();
    }

    public interface ActionListener {
        void onSuccess();

        void onFailure(int reason);
    }

    public interface PeerListListener {
        void onPeersAvailable(WifiP2pDeviceList peers);
    }

    public interface ConnectionInfoListener {
        void onConnectionInfoAvailable(WifiP2pInfo info);
    }

    public interface GroupInfoListener {
        void onGroupInfoAvailable(WifiP2pGroup group);
    }

    public interface ServiceResponseListener {
        void onServiceAvailable(int protocolType, byte[] responseData, WifiP2pDevice srcDevice);
    }

    public interface DnsSdServiceResponseListener {
        void onDnsSdServiceAvailable(
                String instanceName, String registrationType, WifiP2pDevice srcDevice);
    }

    public interface DnsSdTxtRecordListener {
        void onDnsSdTxtRecordAvailable(
                String fullDomainName,
                Map<String, String> txtRecordMap,
                WifiP2pDevice srcDevice);
    }

    public interface UpnpServiceResponseListener {
        void onUpnpServiceAvailable(List<String> uniqueServiceNames, WifiP2pDevice srcDevice);
    }

    public interface P2pStateListener {
        void onP2pStateAvailable(int state);
    }

    public interface DiscoveryStateListener {
        void onDiscoveryStateAvailable(int state);
    }

    public interface NetworkInfoListener {
        void onNetworkInfoAvailable(NetworkInfo networkInfo);
    }

    public interface DeviceInfoListener {
        void onDeviceInfoAvailable(WifiP2pDevice wifiP2pDevice);
    }

    public interface ExternalApproverRequestListener {
        int REQUEST_TYPE_NEGOTIATION = 0;
        int REQUEST_TYPE_INVITATION = 1;
        int REQUEST_TYPE_JOIN = 2;

        int APPROVER_DETACH_REASON_REMOVE = 0;
        int APPROVER_DETACH_REASON_FAILURE = 1;
        int APPROVER_DETACH_REASON_REPLACE = 2;
        int APPROVER_DETACH_REASON_CLOSE = 3;

        void onAttached(MacAddress deviceAddress);

        void onDetached(MacAddress deviceAddress, int reason);

        void onConnectionRequested(
                int requestType, WifiP2pConfig config, WifiP2pDevice device);

        void onPinGenerated(MacAddress deviceAddress, String pin);
    }

    public interface WifiP2pListener {
        default void onP2pStateChanged(int state) {}

        default void onDiscoveryStateChanged(int state) {}

        default void onListenStateChanged(int state) {}

        default void onDeviceConfigurationChanged(WifiP2pDevice p2pDevice) {}

        default void onPeerListChanged(WifiP2pDeviceList p2pDeviceList) {}

        default void onGroupCreating() {}

        default void onGroupNegotiationRejectedByUser() {}

        default void onGroupCreationFailed(int reason) {}

        default void onGroupCreated(WifiP2pInfo wifiP2pInfo, WifiP2pGroup wifiP2pGroup) {}

        default void onPeerClientJoined(WifiP2pInfo wifiP2pInfo, WifiP2pGroup wifiP2pGroup) {}

        default void onPeerClientDisconnected(
                WifiP2pInfo wifiP2pInfo, WifiP2pGroup wifiP2pGroup) {}

        default void onFrequencyChanged(WifiP2pInfo wifiP2pInfo, WifiP2pGroup wifiP2pGroup) {}

        default void onGroupRemoved() {}
    }

    public static class Channel implements AutoCloseable {
        final Context mContext;
        final Handler mHandler;
        final ChannelListener mChannelListener;
        ServiceResponseListener mServRspListener;
        DnsSdServiceResponseListener mDnsSdServRspListener;
        DnsSdTxtRecordListener mDnsSdTxtListener;
        UpnpServiceResponseListener mUpnpServRspListener;
        boolean mClosed = false;

        Channel(Context context, Looper looper, ChannelListener l) {
            mContext = context;
            mHandler = looper == null ? null : new Handler(looper);
            mChannelListener = l;
        }

        void postOrRun(Runnable r) {
            if (mHandler != null) {
                mHandler.post(r);
            } else {
                r.run();
            }
        }

        @Override
        public void close() {
            mClosed = true;
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                if (!mClosed) {
                    close();
                }
            } finally {
                super.finalize();
            }
        }
    }

    private static void notifyUnsupported(Channel c, final ActionListener listener) {
        Objects.requireNonNull(c, "Channel needs to be initialized");
        if (listener == null) {
            return;
        }
        c.postOrRun(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onFailure(P2P_UNSUPPORTED);
                    }
                });
    }

    public void registerWifiP2pListener(Executor executor, WifiP2pListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        mListeners.put(listener, executor);
    }

    public void unregisterWifiP2pListener(WifiP2pListener listener) {
        Objects.requireNonNull(listener, "listener");
        mListeners.remove(listener);
    }

    public Channel initialize(
            Context srcContext, Looper srcLooper, ChannelListener listener) {
        return new Channel(srcContext, srcLooper, listener);
    }

    public void discoverPeers(Channel c, ActionListener listener) {
        notifyUnsupported(c, listener);
    }

    public void discoverPeersOnSocialChannels(Channel c, ActionListener listener) {
        notifyUnsupported(c, listener);
    }

    public void discoverPeersOnSpecificFrequency(
            Channel c, int frequencyMhz, ActionListener listener) {
        if (frequencyMhz <= 0) {
            throw new IllegalArgumentException("frequencyMhz must be positive");
        }
        notifyUnsupported(c, listener);
    }

    public void startPeerDiscovery(
            Channel c, WifiP2pDiscoveryConfig config, ActionListener listener) {
        Objects.requireNonNull(config, "config");
        notifyUnsupported(c, listener);
    }

    public void stopPeerDiscovery(Channel c, ActionListener listener) {
        notifyUnsupported(c, listener);
    }

    public void connect(Channel c, WifiP2pConfig config, ActionListener listener) {
        Objects.requireNonNull(config, "config");
        notifyUnsupported(c, listener);
    }

    public void cancelConnect(Channel c, ActionListener listener) {
        notifyUnsupported(c, listener);
    }

    public void createGroup(Channel c, ActionListener listener) {
        notifyUnsupported(c, listener);
    }

    public void createGroup(Channel c, WifiP2pConfig config, ActionListener listener) {
        notifyUnsupported(c, listener);
    }

    public void removeGroup(Channel c, ActionListener listener) {
        notifyUnsupported(c, listener);
    }

    public void startListening(Channel c, ActionListener listener) {
        notifyUnsupported(c, listener);
    }

    public void stopListening(Channel c, ActionListener listener) {
        notifyUnsupported(c, listener);
    }

    public void addLocalService(
            Channel c, WifiP2pServiceInfo servInfo, ActionListener listener) {
        Objects.requireNonNull(servInfo, "servInfo");
        notifyUnsupported(c, listener);
    }

    public void removeLocalService(
            Channel c, WifiP2pServiceInfo servInfo, ActionListener listener) {
        Objects.requireNonNull(servInfo, "servInfo");
        notifyUnsupported(c, listener);
    }

    public void clearLocalServices(Channel c, ActionListener listener) {
        notifyUnsupported(c, listener);
    }

    public void setServiceResponseListener(Channel c, ServiceResponseListener listener) {
        Objects.requireNonNull(c, "Channel needs to be initialized");
        c.mServRspListener = listener;
    }

    public void setDnsSdResponseListeners(
            Channel c,
            DnsSdServiceResponseListener servListener,
            DnsSdTxtRecordListener txtListener) {
        Objects.requireNonNull(c, "Channel needs to be initialized");
        c.mDnsSdServRspListener = servListener;
        c.mDnsSdTxtListener = txtListener;
    }

    public void setUpnpServiceResponseListener(
            Channel c, UpnpServiceResponseListener listener) {
        Objects.requireNonNull(c, "Channel needs to be initialized");
        c.mUpnpServRspListener = listener;
    }

    public void discoverServices(Channel c, ActionListener listener) {
        notifyUnsupported(c, listener);
    }

    public void addServiceRequest(
            Channel c, WifiP2pServiceRequest req, ActionListener listener) {
        Objects.requireNonNull(req, "req");
        notifyUnsupported(c, listener);
    }

    public void removeServiceRequest(
            Channel c, WifiP2pServiceRequest req, ActionListener listener) {
        Objects.requireNonNull(req, "req");
        notifyUnsupported(c, listener);
    }

    public void clearServiceRequests(Channel c, ActionListener listener) {
        notifyUnsupported(c, listener);
    }

    public void requestPeers(Channel c, final PeerListListener listener) {
        Objects.requireNonNull(c, "Channel needs to be initialized");
        if (listener == null) {
            return;
        }
        c.postOrRun(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onPeersAvailable(new WifiP2pDeviceList());
                    }
                });
    }

    public void requestConnectionInfo(Channel c, final ConnectionInfoListener listener) {
        Objects.requireNonNull(c, "Channel needs to be initialized");
        if (listener == null) {
            return;
        }
        c.postOrRun(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onConnectionInfoAvailable(new WifiP2pInfo());
                    }
                });
    }

    public void requestGroupInfo(Channel c, final GroupInfoListener listener) {
        Objects.requireNonNull(c, "Channel needs to be initialized");
        if (listener == null) {
            return;
        }
        c.postOrRun(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onGroupInfoAvailable(null);
                    }
                });
    }

    public void setWfdInfo(Channel c, WifiP2pWfdInfo wfdInfo, ActionListener listener) {
        Objects.requireNonNull(wfdInfo, "wfdInfo");
        notifyUnsupported(c, listener);
    }

    public void removeClient(Channel c, MacAddress peerAddress, ActionListener listener) {
        Objects.requireNonNull(peerAddress, "peerAddress");
        notifyUnsupported(c, listener);
    }

    public boolean isSetVendorElementsSupported() {
        return false;
    }

    public boolean isChannelConstrainedDiscoverySupported() {
        return false;
    }

    public boolean isGroupClientRemovalSupported() {
        return false;
    }

    public boolean isGroupOwnerIPv6LinkLocalAddressProvided() {
        return false;
    }

    public void requestP2pState(Channel c, final P2pStateListener listener) {
        Objects.requireNonNull(c, "Channel needs to be initialized");
        Objects.requireNonNull(listener, "listener");
        c.postOrRun(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onP2pStateAvailable(WIFI_P2P_STATE_DISABLED);
                    }
                });
    }

    public void requestDiscoveryState(Channel c, final DiscoveryStateListener listener) {
        Objects.requireNonNull(c, "Channel needs to be initialized");
        Objects.requireNonNull(listener, "listener");
        c.postOrRun(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onDiscoveryStateAvailable(WIFI_P2P_DISCOVERY_STOPPED);
                    }
                });
    }

    public void getListenState(
            Channel c, Executor executor, final Consumer<Integer> resultsCallback) {
        Objects.requireNonNull(c, "Channel needs to be initialized");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(resultsCallback, "resultsCallback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        resultsCallback.accept(Integer.valueOf(WIFI_P2P_LISTEN_STOPPED));
                    }
                });
    }

    public void requestNetworkInfo(Channel c, final NetworkInfoListener listener) {
        Objects.requireNonNull(c, "Channel needs to be initialized");
        Objects.requireNonNull(listener, "listener");
        c.postOrRun(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onNetworkInfoAvailable(
                                new NetworkInfo(
                                        ConnectivityManager.TYPE_WIFI, 0, "WIFI_P2P", ""));
                    }
                });
    }

    public void requestDeviceInfo(Channel c, final DeviceInfoListener listener) {
        Objects.requireNonNull(c, "Channel needs to be initialized");
        Objects.requireNonNull(listener, "listener");
        c.postOrRun(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onDeviceInfoAvailable(null);
                    }
                });
    }

    public void addExternalApprover(
            Channel c,
            final MacAddress deviceAddress,
            final ExternalApproverRequestListener listener) {
        Objects.requireNonNull(c, "Channel needs to be initialized");
        Objects.requireNonNull(deviceAddress, "deviceAddress");
        Objects.requireNonNull(listener, "listener");
        c.postOrRun(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onDetached(
                                deviceAddress,
                                ExternalApproverRequestListener.APPROVER_DETACH_REASON_FAILURE);
                    }
                });
    }

    public void removeExternalApprover(
            Channel c, MacAddress deviceAddress, ActionListener listener) {
        Objects.requireNonNull(deviceAddress, "deviceAddress");
        notifyUnsupported(c, listener);
    }

    public void setConnectionRequestResult(
            Channel c, MacAddress deviceAddress, int result, ActionListener listener) {
        setConnectionRequestResult(c, deviceAddress, result, null, listener);
    }

    public void setConnectionRequestResult(
            Channel c,
            MacAddress deviceAddress,
            int result,
            String pin,
            ActionListener listener) {
        Objects.requireNonNull(deviceAddress, "deviceAddress");
        notifyUnsupported(c, listener);
    }

    public void setVendorElements(
            Channel c,
            List<ScanResult.InformationElement> vendorElements,
            ActionListener listener) {
        Objects.requireNonNull(vendorElements, "vendorElements");
        notifyUnsupported(c, listener);
    }

    public static int getP2pMaxAllowedVendorElementsLengthBytes() {
        return 512;
    }
}
