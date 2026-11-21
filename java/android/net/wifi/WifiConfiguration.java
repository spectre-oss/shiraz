// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;

import android.net.IpConfiguration;
import android.net.MacAddress;
import android.net.ProxyInfo;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.BitSet;

@Deprecated
public class WifiConfiguration implements Parcelable {
    public static final int SECURITY_TYPE_OPEN = 0;
    public static final int SECURITY_TYPE_WEP = 1;
    public static final int SECURITY_TYPE_PSK = 2;
    public static final int SECURITY_TYPE_EAP = 3;
    public static final int SECURITY_TYPE_SAE = 4;
    public static final int SECURITY_TYPE_EAP_SUITE_B = 5;
    public static final int SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT = 5;
    public static final int SECURITY_TYPE_OWE = 6;
    public static final int SECURITY_TYPE_WAPI_PSK = 7;
    public static final int SECURITY_TYPE_WAPI_CERT = 8;
    public static final int SECURITY_TYPE_EAP_WPA3_ENTERPRISE = 9;
    public static final int SECURITY_TYPE_DPP = 13;

    public static final int RANDOMIZATION_NONE = 0;
    public static final int RANDOMIZATION_PERSISTENT = 1;
    public static final int RANDOMIZATION_NON_PERSISTENT = 2;
    public static final int RANDOMIZATION_AUTO = 3;

    static final Creator<WifiConfiguration> CREATOR =
            new Creator<WifiConfiguration>() {
                @Override
                public WifiConfiguration createFromParcel(Parcel in) {
                    WifiConfiguration wc = new WifiConfiguration();
                    wc.networkId = in.readInt();
                    wc.status = in.readInt();
                    wc.SSID = in.readString();
                    wc.BSSID = in.readString();
                    wc.FQDN = in.readString();
                    wc.preSharedKey = in.readString();
                    wc.hiddenSSID = in.readInt() != 0;
                    wc.enterpriseConfig = in.readTypedObject(WifiEnterpriseConfig.CREATOR);
                    wc.mMacRandomizationSetting = in.readInt();
                    return wc;
                }

                @Override
                public WifiConfiguration[] newArray(int size) {
                    return new WifiConfiguration[size];
                }
            };

    public int networkId;
    public int status;
    public String SSID;
    public String BSSID;
    public String preSharedKey;
    @Deprecated public String[] wepKeys;
    @Deprecated public int wepTxKeyIndex;
    @Deprecated public int priority;
    public boolean hiddenSSID;
    public BitSet allowedKeyManagement;
    public BitSet allowedProtocols;
    public BitSet allowedAuthAlgorithms;
    public BitSet allowedPairwiseCiphers;
    public BitSet allowedGroupCiphers;
    public BitSet allowedGroupManagementCiphers;
    public BitSet allowedSuiteBCiphers;
    public WifiEnterpriseConfig enterpriseConfig;
    public String FQDN;
    public String providerFriendlyName;
    public boolean isHomeProviderNetwork;
    public long[] roamingConsortiumIds;

    private int mMacRandomizationSetting = RANDOMIZATION_AUTO;
    private IpConfiguration mIpConfiguration;
    private ProxyInfo mHttpProxy;

    public WifiConfiguration() {
        this.networkId = -1;
        this.SSID = null;
        this.BSSID = null;
        this.FQDN = null;
        this.roamingConsortiumIds = new long[0];
        this.priority = 0;
        this.hiddenSSID = false;
        this.allowedKeyManagement = new BitSet();
        this.allowedProtocols = new BitSet();
        this.allowedAuthAlgorithms = new BitSet();
        this.allowedPairwiseCiphers = new BitSet();
        this.allowedGroupCiphers = new BitSet();
        this.allowedGroupManagementCiphers = new BitSet();
        this.allowedSuiteBCiphers = new BitSet();
        this.wepKeys = new String[4];
        this.enterpriseConfig = new WifiEnterpriseConfig();
    }

    public WifiConfiguration(WifiConfiguration source) {
        this();
        if (source != null) {
            this.networkId = source.networkId;
            this.status = source.status;
            this.SSID = source.SSID;
            this.BSSID = source.BSSID;
            this.FQDN = source.FQDN;
            this.roamingConsortiumIds =
                    source.roamingConsortiumIds == null
                            ? new long[0]
                            : source.roamingConsortiumIds.clone();
            this.providerFriendlyName = source.providerFriendlyName;
            this.isHomeProviderNetwork = source.isHomeProviderNetwork;
            this.preSharedKey = source.preSharedKey;
            this.wepKeys = source.wepKeys == null ? new String[4] : source.wepKeys.clone();
            this.wepTxKeyIndex = source.wepTxKeyIndex;
            this.priority = source.priority;
            this.hiddenSSID = source.hiddenSSID;
            this.allowedKeyManagement = (BitSet) source.allowedKeyManagement.clone();
            this.allowedProtocols = (BitSet) source.allowedProtocols.clone();
            this.allowedAuthAlgorithms = (BitSet) source.allowedAuthAlgorithms.clone();
            this.allowedPairwiseCiphers = (BitSet) source.allowedPairwiseCiphers.clone();
            this.allowedGroupCiphers = (BitSet) source.allowedGroupCiphers.clone();
            this.allowedGroupManagementCiphers =
                    (BitSet) source.allowedGroupManagementCiphers.clone();
            this.allowedSuiteBCiphers = (BitSet) source.allowedSuiteBCiphers.clone();
            this.enterpriseConfig = new WifiEnterpriseConfig(source.enterpriseConfig);
            this.mMacRandomizationSetting = source.mMacRandomizationSetting;
            this.mIpConfiguration = source.mIpConfiguration;
            this.mHttpProxy =
                    source.mHttpProxy == null ? null : new ProxyInfo(source.mHttpProxy);
        }
    }

    public void setSecurityParams(int securityType) {
        allowedKeyManagement.clear();
        allowedProtocols.clear();
        allowedAuthAlgorithms.clear();
        allowedPairwiseCiphers.clear();
        allowedGroupCiphers.clear();
        allowedGroupManagementCiphers.clear();
        allowedSuiteBCiphers.clear();
        switch (securityType) {
            case SECURITY_TYPE_OPEN:
                allowedKeyManagement.set(KeyMgmt.NONE);
                break;
            case SECURITY_TYPE_WEP:
                allowedKeyManagement.set(KeyMgmt.NONE);
                allowedAuthAlgorithms.set(AuthAlgorithm.OPEN);
                allowedAuthAlgorithms.set(AuthAlgorithm.SHARED);
                break;
            case SECURITY_TYPE_PSK:
                allowedKeyManagement.set(KeyMgmt.WPA_PSK);
                break;
            case SECURITY_TYPE_EAP:
            case SECURITY_TYPE_EAP_WPA3_ENTERPRISE:
                allowedKeyManagement.set(KeyMgmt.WPA_EAP);
                allowedKeyManagement.set(KeyMgmt.IEEE8021X);
                break;
            case SECURITY_TYPE_SAE:
                allowedKeyManagement.set(KeyMgmt.SAE);
                break;
            case SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT:
                allowedKeyManagement.set(KeyMgmt.SUITE_B_192);
                allowedKeyManagement.set(KeyMgmt.WPA_EAP);
                allowedKeyManagement.set(KeyMgmt.IEEE8021X);
                break;
            case SECURITY_TYPE_OWE:
                allowedKeyManagement.set(KeyMgmt.OWE);
                break;
            case SECURITY_TYPE_WAPI_PSK:
                allowedKeyManagement.set(KeyMgmt.WAPI_PSK);
                allowedProtocols.set(Protocol.WAPI);
                break;
            case SECURITY_TYPE_WAPI_CERT:
                allowedKeyManagement.set(KeyMgmt.WAPI_CERT);
                allowedProtocols.set(Protocol.WAPI);
                break;
            case SECURITY_TYPE_DPP:
                allowedKeyManagement.set(KeyMgmt.DPP);
                break;
            default:
                throw new IllegalArgumentException("unknown security type " + securityType);
        }
    }

    public void setMacRandomizationSetting(int macRandomizationSetting) {
        this.mMacRandomizationSetting = macRandomizationSetting;
    }

    public int getMacRandomizationSetting() {
        return mMacRandomizationSetting;
    }

    public MacAddress getRandomizedMacAddress() {
        return MacAddress.fromString("02:00:00:00:00:00");
    }

    public boolean isDppConfigurator() {
        return false;
    }

    public boolean isPasspoint() {
        return FQDN != null
                && !FQDN.isEmpty()
                && providerFriendlyName != null
                && !providerFriendlyName.isEmpty()
                && enterpriseConfig != null
                && enterpriseConfig.getEapMethod() != WifiEnterpriseConfig.Eap.NONE;
    }

    @Override
    public String toString() {
        return "ID: " + networkId + " SSID: " + SSID + " BSSID: " + BSSID;
    }

    public String getKey() {
        if (isPasspoint()) {
            return FQDN + "-" + KeyMgmt.strings[KeyMgmt.WPA_EAP];
        }
        String ssid = SSID == null ? "" : SSID;
        if (allowedKeyManagement.get(KeyMgmt.WPA_PSK)) {
            return ssid + KeyMgmt.strings[KeyMgmt.WPA_PSK];
        }
        if (allowedKeyManagement.get(KeyMgmt.SAE)) {
            return ssid + KeyMgmt.strings[KeyMgmt.SAE];
        }
        if (allowedKeyManagement.get(KeyMgmt.WPA_EAP)
                || allowedKeyManagement.get(KeyMgmt.IEEE8021X)) {
            return ssid + KeyMgmt.strings[KeyMgmt.WPA_EAP];
        }
        return ssid + KeyMgmt.strings[KeyMgmt.NONE];
    }

    public void setIpConfiguration(IpConfiguration ipConfiguration) {
        this.mIpConfiguration = ipConfiguration;
        if (ipConfiguration != null) {
            this.mHttpProxy = ipConfiguration.getHttpProxy();
        }
    }

    public ProxyInfo getHttpProxy() {
        return mHttpProxy;
    }

    public void setHttpProxy(ProxyInfo httpProxy) {
        this.mHttpProxy = httpProxy == null ? null : new ProxyInfo(httpProxy);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(networkId);
        dest.writeInt(status);
        dest.writeString(SSID);
        dest.writeString(BSSID);
        dest.writeString(FQDN);
        dest.writeString(preSharedKey);
        dest.writeInt(hiddenSSID ? 1 : 0);
        dest.writeTypedObject(enterpriseConfig, flags);
        dest.writeInt(mMacRandomizationSetting);
    }

    public static class KeyMgmt {
        public static final int NONE = 0;
        public static final int WPA_PSK = 1;
        public static final int WPA_EAP = 2;
        public static final int IEEE8021X = 3;
        public static final int WPA2_PSK = 4;
        public static final int OSEN = 5;
        public static final int FT_PSK = 6;
        public static final int FT_EAP = 7;
        public static final int SAE = 8;
        public static final int OWE = 9;
        public static final int SUITE_B_192 = 10;
        public static final int WPA_PSK_SHA256 = 11;
        public static final int WPA_EAP_SHA256 = 12;
        public static final int WAPI_PSK = 13;
        public static final int WAPI_CERT = 14;
        public static final int FILS_SHA256 = 15;
        public static final int FILS_SHA384 = 16;
        public static final int DPP = 17;

        public static final String varName = "key_mgmt";
        public static final String[] strings = {
            "NONE",
            "WPA_PSK",
            "WPA_EAP",
            "IEEE8021X",
            "WPA2_PSK",
            "OSEN",
            "FT_PSK",
            "FT_EAP",
            "SAE",
            "OWE",
            "SUITE_B_192",
            "WPA_PSK_SHA256",
            "WPA_EAP_SHA256",
            "WAPI_PSK",
            "WAPI_CERT",
            "FILS_SHA256",
            "FILS_SHA384",
            "DPP"
        };

        private KeyMgmt() {}
    }

    public static class Protocol {
        public static final int WPA = 0;
        public static final int RSN = 1;
        public static final int WAPI = 3;

        public static final String varName = "proto";
        public static final String[] strings = {"WPA", "RSN", "OSEN", "WAPI"};

        private Protocol() {}
    }

    public static class AuthAlgorithm {
        public static final int OPEN = 0;
        public static final int SHARED = 1;
        @Deprecated public static final int LEAP = 2;
        public static final int SAE = 3;

        public static final String varName = "auth_alg";
        public static final String[] strings = {"OPEN", "SHARED", "LEAP", "SAE"};

        private AuthAlgorithm() {}
    }

    public static class PairwiseCipher {
        public static final int NONE = 0;
        @Deprecated public static final int TKIP = 1;
        public static final int CCMP = 2;
        public static final int GCMP_256 = 3;
        public static final int SMS4 = 4;
        public static final int GCMP_128 = 5;

        public static final String varName = "pairwise";
        public static final String[] strings = {
            "NONE", "TKIP", "CCMP", "GCMP_256", "SMS4", "GCMP_128"
        };

        private PairwiseCipher() {}
    }

    public static class GroupCipher {
        @Deprecated public static final int WEP40 = 0;
        @Deprecated public static final int WEP104 = 1;
        @Deprecated public static final int TKIP = 2;
        public static final int CCMP = 3;
        public static final int GCMP_256 = 5;
        public static final int SMS4 = 6;
        public static final int GCMP_128 = 7;

        public static final String varName = "group";
        public static final String[] strings = {
            "WEP40", "WEP104", "TKIP", "CCMP", "GTK_NOT_USED", "GCMP_256", "SMS4", "GCMP_128"
        };

        private GroupCipher() {}
    }

    public static class GroupMgmtCipher {
        public static final int BIP_CMAC_256 = 0;
        public static final int BIP_GMAC_128 = 1;
        public static final int BIP_GMAC_256 = 2;

        private GroupMgmtCipher() {}
    }

    public static class Status {
        public static final int CURRENT = 0;
        public static final int DISABLED = 1;
        public static final int ENABLED = 2;

        public static final String[] strings = {"current", "disabled", "enabled"};

        private Status() {}
    }
}
