// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p.nsd;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WifiP2pDnsSdServiceInfo extends WifiP2pServiceInfo {
    private WifiP2pDnsSdServiceInfo(List<String> queryList) {
        super(queryList);
    }

    public static WifiP2pDnsSdServiceInfo newInstance(
            String instanceName, String serviceType, Map<String, String> txtMap) {
        if (instanceName == null || instanceName.isEmpty()
                || serviceType == null || serviceType.isEmpty()) {
            throw new IllegalArgumentException(
                    "instance name or service type cannot be empty");
        }
        List<String> queries = new ArrayList<String>();
        queries.add(
                "bonjour ptr "
                        + instanceName.toLowerCase(Locale.ROOT)
                        + "."
                        + serviceType.toLowerCase(Locale.ROOT)
                        + ".local.");
        StringBuilder txt = new StringBuilder("bonjour txt ");
        txt.append(instanceName.toLowerCase(Locale.ROOT))
                .append(".")
                .append(serviceType.toLowerCase(Locale.ROOT))
                .append(".local.");
        if (txtMap != null) {
            for (Map.Entry<String, String> entry : txtMap.entrySet()) {
                String key = entry.getKey();
                if (key == null) {
                    throw new IllegalArgumentException("txt record key cannot be null");
                }
                txt.append(" ").append(key).append("=").append(entry.getValue());
            }
        }
        queries.add(txt.toString());
        return new WifiP2pDnsSdServiceInfo(queries);
    }
}
