// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;
/** Read-only host interface status; Android radio control and scans are not exposed. */
public final class WifiManager {
 public static final String UNKNOWN_SSID="<unknown ssid>";
 public static final int WIFI_STATE_DISABLING=0,WIFI_STATE_DISABLED=1,WIFI_STATE_ENABLING=2,WIFI_STATE_ENABLED=3,WIFI_STATE_UNKNOWN=4;
 private final shiraz.runtime.CapturePermissions audit;
 private WifiManager(shiraz.runtime.CapturePermissions audit){this.audit=audit;}
 public static WifiManager shirazForHost(shiraz.runtime.CapturePermissions audit){return new WifiManager(audit);}
 private void record(String action){if(audit!=null)audit.wifiAccess(action);}
 public boolean isWifiEnabled(){record("isWifiEnabled:requested");try{boolean enabled=shiraz.runtime.HostConnectivity.wifiEnabled();record(enabled?"isWifiEnabled:enabled":"isWifiEnabled:disabled");return enabled;}catch(RuntimeException error){record("isWifiEnabled:unavailable");return false;}}
 public int getWifiState(){record("getWifiState:requested");try{int state=shiraz.runtime.HostConnectivity.wifiEnabled()?WIFI_STATE_ENABLED:WIFI_STATE_DISABLED;record(state==WIFI_STATE_ENABLED?"getWifiState:enabled":"getWifiState:disabled");return state;}catch(RuntimeException error){record("getWifiState:unavailable");return WIFI_STATE_UNKNOWN;}}
 public WifiInfo getConnectionInfo(){record("getConnectionInfo:redacted");return new WifiInfo(audit);}
}
