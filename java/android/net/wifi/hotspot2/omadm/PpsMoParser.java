// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi.hotspot2.omadm;

import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;

public final class PpsMoParser {
    private PpsMoParser() {}

    public static PasspointConfiguration parseMoText(String xmlString) {
        if (xmlString == null || xmlString.trim().isEmpty()) {
            return null;
        }
        String fqdn = extractNodeValue(xmlString, "FQDN");
        String friendlyName = extractNodeValue(xmlString, "FriendlyName");
        String realm = extractNodeValue(xmlString, "Realm");
        if (fqdn == null || realm == null) {
            return null;
        }
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(fqdn);
        if (friendlyName != null) {
            homeSp.setFriendlyName(friendlyName);
        }
        config.setHomeSp(homeSp);

        Credential cred = new Credential();
        cred.setRealm(realm);
        String username = extractNodeValue(xmlString, "Username");
        String userPass = extractNodeValue(xmlString, "Password");
        if (username != null) {
            Credential.UserCredential uc = new Credential.UserCredential();
            uc.setUsername(username);
            uc.setPassword(userPass);
            String eapStr = extractNodeValue(xmlString, "EAPType");
            if (eapStr != null) {
                try {
                    uc.setEapType(Integer.parseInt(eapStr.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            uc.setNonEapInnerMethod(extractNodeValue(xmlString, "NonEAPInnerMethod"));
            cred.setUserCredential(uc);
        }
        config.setCredential(cred);
        return config;
    }

    private static String extractNodeValue(String xml, String nodeName) {
        String nameTag = "<NodeName>" + nodeName + "</NodeName>";
        int idx = xml.indexOf(nameTag);
        if (idx >= 0) {
            int valStart = xml.indexOf("<Value>", idx + nameTag.length());
            int valEnd = valStart >= 0 ? xml.indexOf("</Value>", valStart + 7) : -1;
            if (valStart >= 0 && valEnd > valStart) {
                return xml.substring(valStart + 7, valEnd).trim();
            }
        }
        String directOpen = "<" + nodeName + ">";
        String directClose = "</" + nodeName + ">";
        int dStart = xml.indexOf(directOpen);
        int dEnd = dStart >= 0 ? xml.indexOf(directClose, dStart + directOpen.length()) : -1;
        if (dStart >= 0 && dEnd > dStart) {
            return xml.substring(dStart + directOpen.length(), dEnd).trim();
        }
        return null;
    }
}
