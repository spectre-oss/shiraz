// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.p2p.nsd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WifiP2pUpnpServiceInfo extends WifiP2pServiceInfo {
    private WifiP2pUpnpServiceInfo(List<String> queryList) {
        super(queryList);
    }

    public static WifiP2pUpnpServiceInfo newInstance(
            String uuid, String device, List<String> services) {
        if (uuid == null || device == null) {
            throw new IllegalArgumentException("uuid or device cannot be null");
        }
        UUID.fromString(uuid);
        List<String> info = new ArrayList<String>();
        info.add("upnp 10 uuid:" + uuid);
        info.add("upnp 10 uuid:" + uuid + "::upnp:rootdevice");
        info.add("upnp 10 uuid:" + uuid + "::" + device);
        if (services != null) {
            for (int i = 0; i < services.size(); i++) {
                info.add("upnp 10 uuid:" + uuid + "::" + services.get(i));
            }
        }
        return new WifiP2pUpnpServiceInfo(info);
    }
}
