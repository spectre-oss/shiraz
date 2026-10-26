// SPDX-License-Identifier: GPL-3.0-only
package android.net;
import java.util.BitSet;
/** Capability and transport flags reported for the host default network. */
public final class NetworkCapabilities {
 public static final int NET_CAPABILITY_MMS=0,NET_CAPABILITY_SUPL=1,NET_CAPABILITY_DUN=2,NET_CAPABILITY_FOTA=3,NET_CAPABILITY_IMS=4,NET_CAPABILITY_CBS=5,NET_CAPABILITY_WIFI_P2P=6,NET_CAPABILITY_IA=7,NET_CAPABILITY_RCS=8,NET_CAPABILITY_XCAP=9,NET_CAPABILITY_EIMS=10,NET_CAPABILITY_NOT_METERED=11,NET_CAPABILITY_INTERNET=12,NET_CAPABILITY_NOT_RESTRICTED=13,NET_CAPABILITY_TRUSTED=14,NET_CAPABILITY_NOT_VPN=15,NET_CAPABILITY_VALIDATED=16,NET_CAPABILITY_CAPTIVE_PORTAL=17,NET_CAPABILITY_NOT_ROAMING=18,NET_CAPABILITY_FOREGROUND=19,NET_CAPABILITY_NOT_CONGESTED=20,NET_CAPABILITY_NOT_SUSPENDED=21;
 public static final int TRANSPORT_CELLULAR=0,TRANSPORT_WIFI=1,TRANSPORT_BLUETOOTH=2,TRANSPORT_ETHERNET=3,TRANSPORT_VPN=4,TRANSPORT_WIFI_AWARE=5,TRANSPORT_LOWPAN=6;
 private final BitSet capabilities=new BitSet();private final BitSet transports=new BitSet();
 public NetworkCapabilities(){}
 public NetworkCapabilities(NetworkCapabilities other){capabilities.or(other.capabilities);transports.or(other.transports);}
 public static NetworkCapabilities shirazHost(int transport){NetworkCapabilities value=new NetworkCapabilities();for(int c:new int[]{NET_CAPABILITY_NOT_METERED,NET_CAPABILITY_INTERNET,NET_CAPABILITY_NOT_RESTRICTED,NET_CAPABILITY_TRUSTED,NET_CAPABILITY_NOT_VPN,NET_CAPABILITY_VALIDATED,NET_CAPABILITY_NOT_ROAMING,NET_CAPABILITY_FOREGROUND,NET_CAPABILITY_NOT_CONGESTED,NET_CAPABILITY_NOT_SUSPENDED})value.capabilities.set(c);value.transports.set(transport);return value;}
 public NetworkCapabilities addCapability(int capability){capabilities.set(capability);return this;}
 public NetworkCapabilities removeCapability(int capability){capabilities.clear(capability);return this;}
 public NetworkCapabilities addTransportType(int transport){transports.set(transport);return this;}
 public boolean hasCapability(int capability){return capabilities.get(capability);}
 public boolean hasTransport(int transport){return transports.get(transport);}
 public int getLinkDownstreamBandwidthKbps(){return 1000000;}
 public int getLinkUpstreamBandwidthKbps(){return 1000000;}
 public int getSignalStrength(){return Integer.MIN_VALUE;}
}
