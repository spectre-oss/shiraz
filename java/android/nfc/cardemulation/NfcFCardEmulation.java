// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.cardemulation;

import android.app.Activity;
import android.content.ComponentName;
import android.nfc.NfcAdapter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class NfcFCardEmulation {
    private static final Map<NfcAdapter, NfcFCardEmulation> INSTANCES =
            new HashMap<NfcAdapter, NfcFCardEmulation>();

    private final Map<ComponentName, String> mSystemCodes =
            new HashMap<ComponentName, String>();
    private final Map<ComponentName, String> mNfcid2s =
            new HashMap<ComponentName, String>();

    private NfcFCardEmulation() {
    }

    public static synchronized NfcFCardEmulation getInstance(NfcAdapter adapter) {
        if (adapter == null) {
            throw new NullPointerException("NfcAdapter is null");
        }
        NfcFCardEmulation manager = INSTANCES.get(adapter);
        if (manager == null) {
            manager = new NfcFCardEmulation();
            INSTANCES.put(adapter, manager);
        }
        return manager;
    }

    private static boolean isValidSystemCode(String systemCode) {
        if (systemCode == null || systemCode.length() != 4) {
            return false;
        }
        if (!systemCode.toUpperCase(Locale.ROOT).startsWith("4")) {
            return false;
        }
        try {
            Integer.parseInt(systemCode, 16);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isValidNfcid2(String nfcid2) {
        if (nfcid2 == null || nfcid2.length() != 16) {
            return false;
        }
        if (!nfcid2.toUpperCase(Locale.ROOT).startsWith("02FE")) {
            return false;
        }
        try {
            Long.parseUnsignedLong(nfcid2, 16);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public String getSystemCodeForService(ComponentName service)
            throws RuntimeException {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        synchronized (this) {
            return mSystemCodes.get(service);
        }
    }

    public boolean registerSystemCodeForService(
            ComponentName service, String systemCode) throws RuntimeException {
        if (service == null || systemCode == null) {
            throw new NullPointerException("service or systemCode is null");
        }
        if (!isValidSystemCode(systemCode)) {
            throw new IllegalArgumentException(
                    "System Code " + systemCode + " is not a valid System Code.");
        }
        synchronized (this) {
            mSystemCodes.put(service, systemCode.toUpperCase(Locale.ROOT));
        }
        return true;
    }

    public boolean unregisterSystemCodeForService(ComponentName service)
            throws RuntimeException {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        synchronized (this) {
            return mSystemCodes.remove(service) != null;
        }
    }

    public String getNfcid2ForService(ComponentName service)
            throws RuntimeException {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        synchronized (this) {
            return mNfcid2s.get(service);
        }
    }

    public boolean setNfcid2ForService(ComponentName service, String nfcid2)
            throws RuntimeException {
        if (service == null || nfcid2 == null) {
            throw new NullPointerException("service or nfcid2 is null");
        }
        if (!isValidNfcid2(nfcid2)) {
            throw new IllegalArgumentException(
                    "NFCID2 " + nfcid2 + " is not a valid NFCID2.");
        }
        synchronized (this) {
            mNfcid2s.put(service, nfcid2.toUpperCase(Locale.ROOT));
        }
        return true;
    }

    public boolean enableService(Activity activity, ComponentName service)
            throws RuntimeException {
        if (activity == null || service == null) {
            throw new NullPointerException("activity or service is null");
        }
        return false;
    }

    public boolean disableService(Activity activity) throws RuntimeException {
        if (activity == null) {
            throw new NullPointerException("activity is null");
        }
        return false;
    }
}
