// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;

import android.net.DhcpInfo;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.WorkSource;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Read-only host interface status; Android radio control and scans are not exposed. */
public class WifiManager {
    public static final int ERROR_AUTHENTICATING = 1;

    @Deprecated public static final int WPS_OVERLAP_ERROR = 3;
    @Deprecated public static final int WPS_WEP_PROHIBITED = 4;
    @Deprecated public static final int WPS_TKIP_ONLY_PROHIBITED = 5;
    @Deprecated public static final int WPS_AUTH_FAILURE = 6;
    @Deprecated public static final int WPS_TIMED_OUT = 7;

    public static final String WIFI_STATE_CHANGED_ACTION = "android.net.wifi.WIFI_STATE_CHANGED";
    public static final String EXTRA_WIFI_STATE = "wifi_state";
    public static final String EXTRA_PREVIOUS_WIFI_STATE = "previous_wifi_state";

    public static final int WIFI_STATE_DISABLING = 0;
    public static final int WIFI_STATE_DISABLED = 1;
    public static final int WIFI_STATE_ENABLING = 2;
    public static final int WIFI_STATE_ENABLED = 3;
    public static final int WIFI_STATE_UNKNOWN = 4;

    public static final String SUPPLICANT_CONNECTION_CHANGE_ACTION =
            "android.net.wifi.supplicant.CONNECTION_CHANGE";
    public static final String EXTRA_SUPPLICANT_CONNECTED = "connected";
    public static final String NETWORK_STATE_CHANGED_ACTION = "android.net.wifi.STATE_CHANGE";
    public static final String EXTRA_NETWORK_INFO = "networkInfo";
    public static final String EXTRA_BSSID = "bssid";
    public static final String EXTRA_WIFI_INFO = "wifiInfo";
    public static final String SUPPLICANT_STATE_CHANGED_ACTION =
            "android.net.wifi.supplicant.STATE_CHANGE";
    public static final String EXTRA_NEW_STATE = "newState";
    public static final String EXTRA_SUPPLICANT_ERROR = "supplicantError";
    public static final String SCAN_RESULTS_AVAILABLE_ACTION = "android.net.wifi.SCAN_RESULTS";
    public static final String EXTRA_RESULTS_UPDATED = "resultsUpdated";
    public static final String RSSI_CHANGED_ACTION = "android.net.wifi.RSSI_CHANGED";
    public static final String EXTRA_NEW_RSSI = "newRssi";
    public static final String NETWORK_IDS_CHANGED_ACTION =
            "android.net.wifi.NETWORK_IDS_CHANGED";
    public static final String ACTION_PICK_WIFI_NETWORK = "android.net.wifi.PICK_WIFI_NETWORK";
    public static final String ACTION_REQUEST_SCAN_ALWAYS_AVAILABLE =
            "android.net.wifi.action.REQUEST_SCAN_ALWAYS_AVAILABLE";
    public static final String ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION =
            "android.net.wifi.action.WIFI_NETWORK_SUGGESTION_POST_CONNECTION";
    public static final String EXTRA_NETWORK_SUGGESTION =
            "android.net.wifi.extra.NETWORK_SUGGESTION";
    public static final String ACTION_WIFI_SCAN_AVAILABILITY_CHANGED =
            "android.net.wifi.action.WIFI_SCAN_AVAILABILITY_CHANGED";
    public static final String EXTRA_SCAN_AVAILABLE = "android.net.wifi.extra.SCAN_AVAILABLE";
    public static final String CHANNEL_DATA_KEY_FREQUENCY_MHZ = "CHANNEL_DATA_KEY_FREQUENCY_MHZ";
    public static final String CHANNEL_DATA_KEY_NUM_AP = "CHANNEL_DATA_KEY_NUM_AP";

    @Deprecated public static final int WIFI_MODE_FULL = 1;
    @Deprecated public static final int WIFI_MODE_SCAN_ONLY = 2;
    public static final int WIFI_MODE_FULL_HIGH_PERF = 3;
    public static final int WIFI_MODE_FULL_LOW_LATENCY = 4;

    public static final String UNKNOWN_SSID = "<unknown ssid>";

    public static final int STATUS_NETWORK_SUGGESTIONS_SUCCESS = 0;
    public static final int STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL = 1;
    public static final int STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED = 2;
    public static final int STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE = 3;
    public static final int STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_EXCEEDS_MAX_PER_APP = 4;
    public static final int STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID = 5;
    public static final int STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED = 6;
    public static final int STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID = 7;
    public static final int STATUS_NETWORK_SUGGESTIONS_ERROR_RESTRICTED_BY_ADMIN = 8;

    public static final int ACTION_REMOVE_SUGGESTION_LINGER = 1;
    public static final int ACTION_REMOVE_SUGGESTION_DISCONNECT = 2;

    public static final int STATUS_SUGGESTION_CONNECTION_FAILURE_UNKNOWN = 0;
    public static final int STATUS_SUGGESTION_CONNECTION_FAILURE_ASSOCIATION = 1;
    public static final int STATUS_SUGGESTION_CONNECTION_FAILURE_AUTHENTICATION = 2;
    public static final int STATUS_SUGGESTION_CONNECTION_FAILURE_IP_PROVISIONING = 3;

    public static final int STATUS_LOCAL_ONLY_CONNECTION_FAILURE_UNKNOWN = 0;
    public static final int STATUS_LOCAL_ONLY_CONNECTION_FAILURE_ASSOCIATION = 1;
    public static final int STATUS_LOCAL_ONLY_CONNECTION_FAILURE_AUTHENTICATION = 2;
    public static final int STATUS_LOCAL_ONLY_CONNECTION_FAILURE_IP_PROVISIONING = 3;
    public static final int STATUS_LOCAL_ONLY_CONNECTION_FAILURE_NOT_FOUND = 4;
    public static final int STATUS_LOCAL_ONLY_CONNECTION_FAILURE_NO_RESPONSE = 5;

    public static final int STATUS_SUGGESTION_APPROVAL_UNKNOWN = 0;
    public static final int STATUS_SUGGESTION_APPROVAL_PENDING = 1;
    public static final int STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER = 2;
    public static final int STATUS_SUGGESTION_APPROVAL_REJECTED_BY_USER = 3;
    public static final int STATUS_SUGGESTION_APPROVAL_APPROVED_BY_CARRIER_PRIVILEGE = 4;

    public static final int WIFI_MULTI_INTERNET_MODE_DISABLED = 0;
    public static final int WIFI_MULTI_INTERNET_MODE_DBS_AP = 1;
    public static final int WIFI_MULTI_INTERNET_MODE_MULTI_AP = 2;

    public static final int WIFI_INTERFACE_TYPE_STA = 0;
    public static final int WIFI_INTERFACE_TYPE_AP = 1;
    public static final int WIFI_INTERFACE_TYPE_AWARE = 2;
    public static final int WIFI_INTERFACE_TYPE_DIRECT = 3;

    public static final int FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_OPEN = 1;
    public static final int FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_SECURE = 2;

    public static final int ROAMING_MODE_NONE = 0;
    public static final int ROAMING_MODE_NORMAL = 1;
    public static final int ROAMING_MODE_AGGRESSIVE = 2;

    private static final int MIN_RSSI = -100;
    private static final int MAX_RSSI = -55;

    private final shiraz.runtime.CapturePermissions audit;
    private final List<WifiNetworkSuggestion> mSuggestions =
            new ArrayList<WifiNetworkSuggestion>();
    private final Map<String, PasspointConfiguration> mPasspointConfigs =
            new LinkedHashMap<String, PasspointConfiguration>();
    private final Map<String, Integer> mPerSsidRoamingModes =
            new LinkedHashMap<String, Integer>();
    private int mDhcpHostnameRestriction = 0;
    private boolean mAutojoinGlobal = true;

    private WifiManager(shiraz.runtime.CapturePermissions audit) {
        this.audit = audit;
    }

    public static WifiManager shirazForHost(shiraz.runtime.CapturePermissions audit) {
        return new WifiManager(audit);
    }

    private void record(String action) {
        if (audit != null) {
            audit.wifiAccess(action);
        }
    }

    public boolean isWifiEnabled() {
        record("isWifiEnabled:requested");
        try {
            boolean enabled = shiraz.runtime.HostConnectivity.wifiEnabled();
            record(enabled ? "isWifiEnabled:enabled" : "isWifiEnabled:disabled");
            return enabled;
        } catch (RuntimeException error) {
            record("isWifiEnabled:unavailable");
            return false;
        }
    }

    public int getWifiState() {
        record("getWifiState:requested");
        try {
            int state =
                    shiraz.runtime.HostConnectivity.wifiEnabled()
                            ? WIFI_STATE_ENABLED
                            : WIFI_STATE_DISABLED;
            record(state == WIFI_STATE_ENABLED ? "getWifiState:enabled" : "getWifiState:disabled");
            return state;
        } catch (RuntimeException error) {
            record("getWifiState:unavailable");
            return WIFI_STATE_UNKNOWN;
        }
    }

    @Deprecated
    public WifiInfo getConnectionInfo() {
        record("getConnectionInfo:redacted");
        return new WifiInfo(audit);
    }

    @Deprecated
    public List<WifiConfiguration> getConfiguredNetworks() {
        return Collections.emptyList();
    }

    public List<WifiConfiguration> getCallerConfiguredNetworks() {
        return Collections.emptyList();
    }

    @Deprecated
    public int addNetwork(WifiConfiguration config) {
        return -1;
    }

    public AddNetworkResult addNetworkPrivileged(WifiConfiguration config) {
        return new AddNetworkResult(AddNetworkResult.STATUS_NO_PERMISSION, -1);
    }

    @Deprecated
    public int updateNetwork(WifiConfiguration config) {
        return -1;
    }

    public synchronized int addNetworkSuggestions(
            List<WifiNetworkSuggestion> networkSuggestions) {
        Objects.requireNonNull(networkSuggestions, "networkSuggestions");
        for (int i = 0; i < networkSuggestions.size(); i++) {
            WifiNetworkSuggestion s = networkSuggestions.get(i);
            if (s != null && !mSuggestions.contains(s)) {
                mSuggestions.add(s);
            }
        }
        return STATUS_NETWORK_SUGGESTIONS_SUCCESS;
    }

    public synchronized int removeNetworkSuggestions(
            List<WifiNetworkSuggestion> networkSuggestions) {
        return removeNetworkSuggestions(networkSuggestions, ACTION_REMOVE_SUGGESTION_DISCONNECT);
    }

    public synchronized int removeNetworkSuggestions(
            List<WifiNetworkSuggestion> networkSuggestions, int action) {
        Objects.requireNonNull(networkSuggestions, "networkSuggestions");
        if (networkSuggestions.isEmpty()) {
            mSuggestions.clear();
        } else {
            mSuggestions.removeAll(networkSuggestions);
        }
        return STATUS_NETWORK_SUGGESTIONS_SUCCESS;
    }

    public synchronized List<WifiNetworkSuggestion> getNetworkSuggestions() {
        return Collections.unmodifiableList(new ArrayList<WifiNetworkSuggestion>(mSuggestions));
    }

    public int getMaxNumberOfNetworkSuggestionsPerApp() {
        return 1024;
    }

    public synchronized void addOrUpdatePasspointConfiguration(PasspointConfiguration config) {
        Objects.requireNonNull(config, "config");
        if (config.getHomeSp() == null || config.getHomeSp().getFqdn() == null) {
            throw new IllegalArgumentException("PasspointConfiguration missing HomeSp FQDN");
        }
        mPasspointConfigs.put(config.getHomeSp().getFqdn(), new PasspointConfiguration(config));
    }

    @Deprecated
    public synchronized void removePasspointConfiguration(String fqdn) {
        if (fqdn == null || mPasspointConfigs.remove(fqdn) == null) {
            throw new IllegalArgumentException("Unknown Passpoint FQDN: " + fqdn);
        }
    }

    @Deprecated
    public synchronized List<PasspointConfiguration> getPasspointConfigurations() {
        return Collections.unmodifiableList(
                new ArrayList<PasspointConfiguration>(mPasspointConfigs.values()));
    }

    @Deprecated
    public boolean removeNetwork(int netId) {
        return false;
    }

    public boolean removeNonCallerConfiguredNetworks() {
        return false;
    }

    @Deprecated
    public boolean enableNetwork(int netId, boolean attemptConnect) {
        return false;
    }

    @Deprecated
    public boolean disableNetwork(int netId) {
        return false;
    }

    @Deprecated
    public boolean disconnect() {
        return false;
    }

    @Deprecated
    public boolean reconnect() {
        return false;
    }

    @Deprecated
    public boolean reassociate() {
        return false;
    }

    @Deprecated
    public boolean pingSupplicant() {
        return isWifiEnabled();
    }

    public boolean isP2pSupported() {
        return false;
    }

    public boolean isStaApConcurrencySupported() {
        return false;
    }

    public boolean isStaConcurrencyForLocalOnlyConnectionsSupported() {
        return false;
    }

    public boolean isMakeBeforeBreakWifiSwitchingSupported() {
        return false;
    }

    public boolean isStaConcurrencyForMultiInternetSupported() {
        return false;
    }

    public boolean isDeviceToApRttSupported() {
        return false;
    }

    public boolean isPreferredNetworkOffloadSupported() {
        return false;
    }

    public boolean isTdlsSupported() {
        return false;
    }

    public boolean isEnhancedPowerReportingSupported() {
        return false;
    }

    public boolean is24GHzBandSupported() {
        return true;
    }

    public boolean is5GHzBandSupported() {
        return true;
    }

    public boolean is60GHzBandSupported() {
        return false;
    }

    public boolean is6GHzBandSupported() {
        return false;
    }

    public boolean isWifiStandardSupported(int standard) {
        return standard == ScanResult.WIFI_STANDARD_LEGACY
                || standard == ScanResult.WIFI_STANDARD_11N
                || standard == ScanResult.WIFI_STANDARD_11AC
                || standard == ScanResult.WIFI_STANDARD_11AX;
    }

    public boolean isStaBridgedApConcurrencySupported() {
        return false;
    }

    public boolean isBridgedApConcurrencySupported() {
        return false;
    }

    public boolean isD2dSupportedWhenInfraStaDisabled() {
        return false;
    }

    @Deprecated
    public boolean startScan() {
        return false;
    }

    public List<ScanResult> getScanResults() {
        return Collections.emptyList();
    }

    @Deprecated
    public boolean isScanAlwaysAvailable() {
        return false;
    }

    public void getChannelData(
            Executor executor, final Consumer<List<Bundle>> resultsCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(resultsCallback, "resultsCallback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        resultsCallback.accept(Collections.<Bundle>emptyList());
                    }
                });
    }

    @Deprecated
    public boolean saveConfiguration() {
        return false;
    }

    @Deprecated
    public DhcpInfo getDhcpInfo() {
        return new DhcpInfo();
    }

    @Deprecated
    public boolean setWifiEnabled(boolean enabled) {
        return false;
    }

    public void registerSubsystemRestartTrackingCallback(
            Executor executor, SubsystemRestartTrackingCallback callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
    }

    public void unregisterSubsystemRestartTrackingCallback(
            SubsystemRestartTrackingCallback callback) {
        Objects.requireNonNull(callback, "callback");
    }

    @Deprecated
    public static int calculateSignalLevel(int rssi, int numLevels) {
        if (rssi <= MIN_RSSI) {
            return 0;
        }
        if (rssi >= MAX_RSSI) {
            return numLevels - 1;
        }
        float inputRange = (MAX_RSSI - MIN_RSSI);
        float outputRange = (numLevels - 1);
        return (int) ((float) (rssi - MIN_RSSI) * outputRange / inputRange);
    }

    public int calculateSignalLevel(int rssi) {
        return calculateSignalLevel(rssi, getMaxSignalLevel() + 1);
    }

    public int getMaxSignalLevel() {
        return 4;
    }

    public static int compareSignalLevel(int rssiA, int rssiB) {
        return rssiA - rssiB;
    }

    public boolean validateSoftApConfiguration(SoftApConfiguration config) {
        return config != null;
    }

    public void startLocalOnlyHotspot(
            final LocalOnlyHotspotCallback callback, Handler handler) {
        if (callback == null) {
            return;
        }
        if (handler != null) {
            handler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailed(LocalOnlyHotspotCallback.ERROR_GENERIC);
                        }
                    });
        } else {
            callback.onFailed(LocalOnlyHotspotCallback.ERROR_GENERIC);
        }
    }

    public void setTdlsEnabled(InetAddress remoteIPAddress, boolean enable) {}

    public void setTdlsEnabled(
            InetAddress remoteIPAddress,
            boolean enable,
            Executor executor,
            final Consumer<Boolean> resultsCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(resultsCallback, "resultsCallback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        resultsCallback.accept(Boolean.FALSE);
                    }
                });
    }

    public void setTdlsEnabledWithMacAddress(String remoteMacAddress, boolean enable) {}

    public void setTdlsEnabledWithMacAddress(
            String remoteMacAddress,
            boolean enable,
            Executor executor,
            final Consumer<Boolean> resultsCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(resultsCallback, "resultsCallback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        resultsCallback.accept(Boolean.FALSE);
                    }
                });
    }

    public void isTdlsOperationCurrentlyAvailable(
            Executor executor, final Consumer<Boolean> resultsCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(resultsCallback, "resultsCallback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        resultsCallback.accept(Boolean.FALSE);
                    }
                });
    }

    public void getMaxSupportedConcurrentTdlsSessions(
            Executor executor, final Consumer<Integer> resultsCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(resultsCallback, "resultsCallback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        resultsCallback.accept(Integer.valueOf(0));
                    }
                });
    }

    public void getNumberOfEnabledTdlsSessions(
            Executor executor, final Consumer<Integer> resultsCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(resultsCallback, "resultsCallback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        resultsCallback.accept(Integer.valueOf(0));
                    }
                });
    }

    public void allowAutojoinGlobal(boolean allowAutojoin) {
        this.mAutojoinGlobal = allowAutojoin;
    }

    public void queryAutojoinGlobal(
            Executor executor, final Consumer<Boolean> resultsCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(resultsCallback, "resultsCallback");
        final boolean val = mAutojoinGlobal;
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        resultsCallback.accept(Boolean.valueOf(val));
                    }
                });
    }

    @Deprecated
    public void startWps(WpsInfo config, WpsCallback listener) {
        if (listener != null) {
            listener.onFailed(0);
        }
    }

    @Deprecated
    public void cancelWps(WpsCallback listener) {
        if (listener != null) {
            listener.onFailed(0);
        }
    }

    public WifiLock createWifiLock(int lockType, String tag) {
        return new WifiLock(lockType, tag);
    }

    @Deprecated
    public WifiLock createWifiLock(String tag) {
        return new WifiLock(WIFI_MODE_FULL, tag);
    }

    public MulticastLock createMulticastLock(String tag) {
        return new MulticastLock(tag);
    }

    public boolean isWpa3SaeSupported() {
        return true;
    }

    public boolean isWpa3SuiteBSupported() {
        return false;
    }

    public boolean isEnhancedOpenSupported() {
        return false;
    }

    public boolean isEasyConnectSupported() {
        return false;
    }

    public boolean isEasyConnectEnrolleeResponderModeSupported() {
        return false;
    }

    public boolean isWapiSupported() {
        return false;
    }

    public boolean isWpa3SaePublicKeySupported() {
        return false;
    }

    public boolean isPasspointTermsAndConditionsSupported() {
        return false;
    }

    public boolean isWpa3SaeH2eSupported() {
        return true;
    }

    public boolean isWifiDisplayR2Supported() {
        return false;
    }

    public boolean isDecoratedIdentitySupported() {
        return false;
    }

    public boolean isTrustOnFirstUseSupported() {
        return false;
    }

    public boolean isEasyConnectDppAkmSupported() {
        return false;
    }

    public boolean isTlsMinimumVersionSupported() {
        return true;
    }

    public boolean isTlsV13Supported() {
        return true;
    }

    public boolean isDualBandSimultaneousSupported() {
        return false;
    }

    public boolean isTidToLinkMappingNegotiationSupported() {
        return false;
    }

    public boolean isWepSupported() {
        return false;
    }

    public boolean isWpaPersonalSupported() {
        return true;
    }

    public void registerScanResultsCallback(Executor executor, ScanResultsCallback callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
    }

    public void unregisterScanResultsCallback(ScanResultsCallback callback) {
        Objects.requireNonNull(callback, "callback");
    }

    public void addSuggestionConnectionStatusListener(
            Executor executor, SuggestionConnectionStatusListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
    }

    public void removeSuggestionConnectionStatusListener(
            SuggestionConnectionStatusListener listener) {
        Objects.requireNonNull(listener, "listener");
    }

    public void addLocalOnlyConnectionFailureListener(
            Executor executor, LocalOnlyConnectionFailureListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
    }

    public void removeLocalOnlyConnectionFailureListener(
            LocalOnlyConnectionFailureListener listener) {
        Objects.requireNonNull(listener, "listener");
    }

    public boolean isScanThrottleEnabled() {
        return true;
    }

    public boolean isAutoWakeupEnabled() {
        return false;
    }

    public boolean isCarrierNetworkOffloadEnabled(int subId, boolean merged) {
        return false;
    }

    public void addSuggestionUserApprovalStatusListener(
            Executor executor, SuggestionUserApprovalStatusListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
    }

    public void removeSuggestionUserApprovalStatusListener(
            SuggestionUserApprovalStatusListener listener) {
        Objects.requireNonNull(listener, "listener");
    }

    public void flushPasspointAnqpCache() {}

    public List<WifiAvailableChannel> getAllowedChannels(int band, int mode) {
        return Collections.emptyList();
    }

    public List<WifiAvailableChannel> getUsableChannels(int band, int mode) {
        return Collections.emptyList();
    }

    public boolean isWifiPasspointEnabled() {
        return false;
    }

    public int getStaConcurrencyForMultiInternetMode() {
        return WIFI_MULTI_INTERNET_MODE_DISABLED;
    }

    public void reportCreateInterfaceImpact(
            int interfaceType,
            boolean requireNewInterface,
            Executor executor,
            final BiConsumer<Boolean, Set<InterfaceCreationImpact>> impactCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(impactCallback, "impactCallback");
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        impactCallback.accept(
                                Boolean.FALSE, Collections.<InterfaceCreationImpact>emptySet());
                    }
                });
    }

    public int getMaxNumberOfChannelsPerNetworkSpecifierRequest() {
        return 32;
    }

    public void setSendDhcpHostnameRestriction(int restriction) {
        this.mDhcpHostnameRestriction = restriction;
    }

    public void querySendDhcpHostnameRestriction(
            Executor executor, final IntConsumer resultsCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(resultsCallback, "resultsCallback");
        final int val = mDhcpHostnameRestriction;
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        resultsCallback.accept(val);
                    }
                });
    }

    public boolean isAggressiveRoamingModeSupported() {
        return false;
    }

    public synchronized void setPerSsidRoamingMode(WifiSsid ssid, int roamingMode) {
        Objects.requireNonNull(ssid, "ssid");
        mPerSsidRoamingModes.put(ssid.toString(), Integer.valueOf(roamingMode));
    }

    public synchronized void removePerSsidRoamingMode(WifiSsid ssid) {
        Objects.requireNonNull(ssid, "ssid");
        mPerSsidRoamingModes.remove(ssid.toString());
    }

    public synchronized void getPerSsidRoamingModes(
            Executor executor, final Consumer<Map<String, Integer>> resultsCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(resultsCallback, "resultsCallback");
        final Map<String, Integer> snapshot =
                Collections.unmodifiableMap(
                        new LinkedHashMap<String, Integer>(mPerSsidRoamingModes));
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        resultsCallback.accept(snapshot);
                    }
                });
    }

    public static final class AddNetworkResult implements Parcelable {
        public static final int STATUS_SUCCESS = 0;
        public static final int STATUS_FAILURE_UNKNOWN = 1;
        public static final int STATUS_NO_PERMISSION = 2;
        public static final int STATUS_ADD_PASSPOINT_FAILURE = 3;
        public static final int STATUS_ADD_WIFI_CONFIG_FAILURE = 4;
        public static final int STATUS_INVALID_CONFIGURATION = 5;
        public static final int STATUS_NO_PERMISSION_MODIFY_CONFIG = 6;
        public static final int STATUS_NO_PERMISSION_MODIFY_PROXY_SETTING = 7;
        public static final int STATUS_NO_PERMISSION_MODIFY_MAC_RANDOMIZATION = 8;
        public static final int STATUS_FAILURE_UPDATE_NETWORK_KEYS = 9;
        public static final int STATUS_INVALID_CONFIGURATION_ENTERPRISE = 10;

        public static final Creator<AddNetworkResult> CREATOR =
                new Creator<AddNetworkResult>() {
                    @Override
                    public AddNetworkResult createFromParcel(Parcel in) {
                        return new AddNetworkResult(in.readInt(), in.readInt());
                    }

                    @Override
                    public AddNetworkResult[] newArray(int size) {
                        return new AddNetworkResult[size];
                    }
                };

        public final int statusCode;
        public final int networkId;

        public AddNetworkResult(int statusCode, int networkId) {
            this.statusCode = statusCode;
            this.networkId = networkId;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(statusCode);
            dest.writeInt(networkId);
        }
    }

    public static class InterfaceCreationImpact {
        private final int mInterfaceType;
        private final Set<String> mPackages;

        public InterfaceCreationImpact(int interfaceType, Set<String> packages) {
            this.mInterfaceType = interfaceType;
            this.mPackages =
                    Collections.unmodifiableSet(
                            new LinkedHashSet<String>(Objects.requireNonNull(packages)));
        }

        public int getInterfaceType() {
            return mInterfaceType;
        }

        public Set<String> getPackages() {
            return mPackages;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Integer.valueOf(mInterfaceType), mPackages);
        }

        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            }
            if (!(that instanceof InterfaceCreationImpact)) {
                return false;
            }
            InterfaceCreationImpact o = (InterfaceCreationImpact) that;
            return mInterfaceType == o.mInterfaceType && Objects.equals(mPackages, o.mPackages);
        }
    }

    public interface LocalOnlyConnectionFailureListener {
        void onConnectionFailed(WifiNetworkSpecifier wifiNetworkSpecifier, int failureReason);
    }

    public static class LocalOnlyHotspotCallback {
        public static final int ERROR_NO_CHANNEL = 1;
        public static final int ERROR_GENERIC = 2;
        public static final int ERROR_INCOMPATIBLE_MODE = 3;
        public static final int ERROR_TETHERING_DISALLOWED = 4;

        public LocalOnlyHotspotCallback() {}

        public void onStarted(LocalOnlyHotspotReservation reservation) {}

        public void onStopped() {}

        public void onFailed(int reason) {}
    }

    public class LocalOnlyHotspotReservation implements AutoCloseable {
        private final SoftApConfiguration mSoftApConfig;
        private final WifiConfiguration mWifiConfig;

        LocalOnlyHotspotReservation(
                SoftApConfiguration softApConfig, WifiConfiguration wifiConfig) {
            this.mSoftApConfig = softApConfig;
            this.mWifiConfig = wifiConfig;
        }

        @Deprecated
        public WifiConfiguration getWifiConfiguration() {
            return mWifiConfig;
        }

        public SoftApConfiguration getSoftApConfiguration() {
            return mSoftApConfig;
        }

        @Override
        public void close() {}

        @Override
        protected void finalize() throws Throwable {
            close();
            super.finalize();
        }
    }

    public class MulticastLock {
        private final String mTag;
        private int mRefCount;
        private boolean mRefCounted = true;
        private boolean mHeld;

        private MulticastLock(String tag) {
            this.mTag = tag;
        }

        public synchronized void acquire() {
            if (mRefCounted) {
                mRefCount++;
                mHeld = true;
            } else {
                mHeld = true;
            }
        }

        public synchronized void release() {
            if (mRefCounted) {
                if (--mRefCount == 0) {
                    mHeld = false;
                }
                if (mRefCount < 0) {
                    mRefCount = 0;
                    throw new RuntimeException("MulticastLock under-locked " + mTag);
                }
            } else {
                mHeld = false;
            }
        }

        public synchronized void setReferenceCounted(boolean refCounted) {
            this.mRefCounted = refCounted;
        }

        public synchronized boolean isHeld() {
            return mHeld;
        }

        @Override
        public synchronized String toString() {
            return "MulticastLock{ " + mTag + "; held=" + mHeld + "; refCount=" + mRefCount + " }";
        }

        @Override
        protected void finalize() throws Throwable {
            setReferenceCounted(false);
            release();
            super.finalize();
        }
    }

    public abstract static class ScanResultsCallback {
        public ScanResultsCallback() {}

        public abstract void onScanResultsAvailable();
    }

    public abstract static class SubsystemRestartTrackingCallback {
        public SubsystemRestartTrackingCallback() {}

        public abstract void onSubsystemRestarting();

        public abstract void onSubsystemRestarted();
    }

    public interface SuggestionConnectionStatusListener {
        void onConnectionStatus(
                WifiNetworkSuggestion wifiNetworkSuggestion, int failureReason);
    }

    public interface SuggestionUserApprovalStatusListener {
        void onUserApprovalStatusChange(int status);
    }

    public class WifiLock {
        private final String mTag;
        private final int mLockType;
        private int mRefCount;
        private boolean mRefCounted = true;
        private boolean mHeld;
        private WorkSource mWorkSource;

        private WifiLock(int lockType, String tag) {
            this.mLockType = lockType;
            this.mTag = tag;
        }

        public synchronized void acquire() {
            if (mRefCounted) {
                mRefCount++;
                mHeld = true;
            } else {
                mHeld = true;
            }
        }

        public synchronized void release() {
            if (mRefCounted) {
                if (--mRefCount == 0) {
                    mHeld = false;
                }
                if (mRefCount < 0) {
                    mRefCount = 0;
                    throw new RuntimeException("WifiLock under-locked " + mTag);
                }
            } else {
                mHeld = false;
            }
        }

        public synchronized void setReferenceCounted(boolean refCounted) {
            this.mRefCounted = refCounted;
        }

        public synchronized boolean isHeld() {
            return mHeld;
        }

        public synchronized void setWorkSource(WorkSource ws) {
            this.mWorkSource = ws;
        }

        @Override
        public synchronized String toString() {
            return "WifiLock{ "
                    + mTag
                    + " type="
                    + mLockType
                    + " held="
                    + mHeld
                    + " refCount="
                    + mRefCount
                    + " }";
        }

        @Override
        protected void finalize() throws Throwable {
            synchronized (this) {
                mHeld = false;
                mRefCount = 0;
            }
            super.finalize();
        }
    }

    @Deprecated
    public abstract static class WpsCallback {
        public WpsCallback() {}

        public abstract void onStarted(String pin);

        public abstract void onSucceeded();

        public abstract void onFailed(int reason);
    }
}
