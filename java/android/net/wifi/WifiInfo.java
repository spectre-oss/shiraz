// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;
/** Network identifiers are unavailable without a host location/SSID permission backend. */
public final class WifiInfo {
 private final shiraz.runtime.CapturePermissions audit;
 WifiInfo(shiraz.runtime.CapturePermissions audit){this.audit=audit;}
 public String getSSID(){if(audit!=null)audit.wifiAccess("getSSID:redacted");return WifiManager.UNKNOWN_SSID;}
 public int getIpAddress(){if(audit!=null)audit.wifiAccess("getIpAddress:unavailable");return 0;}
}
