// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.hotspot2;

import android.net.wifi.hotspot2.omadm.PpsMoParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ConfigParser {
    private static final String TYPE_WIFI_CONFIG = "application/x-wifi-config";

    private ConfigParser() {}

    public static PasspointConfiguration parsePasspointConfig(String mimeType, byte[] data) {
        if (!TYPE_WIFI_CONFIG.equalsIgnoreCase(mimeType) || data == null || data.length == 0) {
            return null;
        }
        try {
            byte[] decoded = Base64.getMimeDecoder().decode(data);
            String text = new String(decoded, StandardCharsets.UTF_8);
            return PpsMoParser.parseMoText(text);
        } catch (IllegalArgumentException e) {
            String text = new String(data, StandardCharsets.UTF_8);
            return PpsMoParser.parseMoText(text);
        }
    }
}
