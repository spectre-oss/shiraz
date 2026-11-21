// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p.nsd;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class WifiP2pDnsSdServiceRequest extends WifiP2pServiceRequest {
    private WifiP2pDnsSdServiceRequest(String query) {
        super(WifiP2pServiceInfo.SERVICE_TYPE_BONJOUR, query);
    }

    private WifiP2pDnsSdServiceRequest() {
        super(WifiP2pServiceInfo.SERVICE_TYPE_BONJOUR, null);
    }

    private static String toHexQuery(String dnsName, int dnsType) {
        byte[] bytes = dnsName.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2 + 8);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format(Locale.ROOT, "%02x", Integer.valueOf(bytes[i] & 0xff)));
        }
        sb.append(String.format(Locale.ROOT, "%04x01", Integer.valueOf(dnsType & 0xffff)));
        return sb.toString();
    }

    public static WifiP2pDnsSdServiceRequest newInstance() {
        return new WifiP2pDnsSdServiceRequest();
    }

    public static WifiP2pDnsSdServiceRequest newInstance(String serviceType) {
        if (serviceType == null) {
            throw new IllegalArgumentException("service type cannot be null");
        }
        return new WifiP2pDnsSdServiceRequest(toHexQuery(serviceType + ".local.", 12));
    }

    public static WifiP2pDnsSdServiceRequest newInstance(
            String instanceName, String serviceType) {
        if (instanceName == null || serviceType == null) {
            throw new IllegalArgumentException(
                    "instance name or service type cannot be null");
        }
        return new WifiP2pDnsSdServiceRequest(
                toHexQuery(instanceName + "." + serviceType + ".local.", 16));
    }
}
