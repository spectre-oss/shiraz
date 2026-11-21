// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p.nsd;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class WifiP2pUpnpServiceRequest extends WifiP2pServiceRequest {
    private WifiP2pUpnpServiceRequest(String query) {
        super(WifiP2pServiceInfo.SERVICE_TYPE_UPNP, query);
    }

    private WifiP2pUpnpServiceRequest() {
        super(WifiP2pServiceInfo.SERVICE_TYPE_UPNP, null);
    }

    public static WifiP2pUpnpServiceRequest newInstance() {
        return new WifiP2pUpnpServiceRequest();
    }

    public static WifiP2pUpnpServiceRequest newInstance(String st) {
        if (st == null) {
            throw new IllegalArgumentException("search target cannot be null");
        }
        byte[] bytes = st.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2 + 2);
        sb.append("10");
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format(Locale.ROOT, "%02x", Integer.valueOf(bytes[i] & 0xff)));
        }
        return new WifiP2pUpnpServiceRequest(sb.toString());
    }
}
