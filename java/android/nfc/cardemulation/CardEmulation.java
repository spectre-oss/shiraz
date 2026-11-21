// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.cardemulation;

import android.app.Activity;
import android.content.ComponentName;
import android.nfc.NfcAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class CardEmulation {
    public static final String ACTION_CHANGE_DEFAULT =
            "android.nfc.cardemulation.action.ACTION_CHANGE_DEFAULT";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_SERVICE_COMPONENT = "component";

    public static final String CATEGORY_PAYMENT = "payment";
    public static final String CATEGORY_OTHER = "other";

    public static final int SELECTION_MODE_PREFER_DEFAULT = 0;
    public static final int SELECTION_MODE_ALWAYS_ASK = 1;
    public static final int SELECTION_MODE_ASK_IF_CONFLICT = 2;

    private static final Pattern AID_PATTERN =
            Pattern.compile("[0-9A-Fa-f]{10,32}\\*?\\#?");

    private static final Map<NfcAdapter, CardEmulation> INSTANCES =
            new HashMap<NfcAdapter, CardEmulation>();

    private final Map<String, List<String>> mDynamicAids =
            new HashMap<String, List<String>>();
    private final Map<ComponentName, String> mOffHostSe =
            new HashMap<ComponentName, String>();
    private final Map<ComponentName, Boolean> mDefaultObserveMode =
            new HashMap<ComponentName, Boolean>();

    private CardEmulation() {
    }

    public static synchronized CardEmulation getInstance(NfcAdapter adapter) {
        if (adapter == null) {
            throw new NullPointerException("NfcAdapter is null");
        }
        CardEmulation manager = INSTANCES.get(adapter);
        if (manager == null) {
            manager = new CardEmulation();
            INSTANCES.put(adapter, manager);
        }
        return manager;
    }

    private static void checkCategory(String category) {
        if (!CATEGORY_PAYMENT.equals(category) && !CATEGORY_OTHER.equals(category)) {
            throw new IllegalArgumentException("Invalid category: " + category);
        }
    }

    private static boolean isValidAid(String aid) {
        if (aid == null) {
            return false;
        }
        if ((aid.endsWith("*") || aid.endsWith("#")) && ((aid.length() % 2) == 0)) {
            return false;
        }
        if (!aid.endsWith("*") && !aid.endsWith("#") && ((aid.length() % 2) != 0)) {
            return false;
        }
        return AID_PATTERN.matcher(aid).matches();
    }

    private static String serviceCategoryKey(ComponentName service, String category) {
        return service.flattenToString() + "#" + category;
    }

    public boolean isDefaultServiceForCategory(ComponentName service, String category) {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        checkCategory(category);
        return false;
    }

    public boolean isDefaultServiceForAid(ComponentName service, String aid) {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        if (!isValidAid(aid)) {
            throw new IllegalArgumentException("Aid " + aid + " is not a valid AID.");
        }
        return false;
    }

    public boolean categoryAllowsForegroundPreference(String category) {
        checkCategory(category);
        return true;
    }

    public int getSelectionModeForCategory(String category) {
        checkCategory(category);
        return CATEGORY_PAYMENT.equals(category)
                ? SELECTION_MODE_PREFER_DEFAULT
                : SELECTION_MODE_ASK_IF_CONFLICT;
    }

    public boolean setShouldDefaultToObserveModeForService(
            ComponentName service, boolean enable) {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        synchronized (this) {
            mDefaultObserveMode.put(service, Boolean.valueOf(enable));
        }
        return true;
    }

    public boolean registerPollingLoopFilterForService(
            ComponentName service, String pollingLoopFilter, boolean autoTransact) {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        if (pollingLoopFilter == null || pollingLoopFilter.isEmpty()) {
            throw new IllegalArgumentException("pollingLoopFilter is empty");
        }
        return false;
    }

    public boolean removePollingLoopFilterForService(
            ComponentName service, String pollingLoopFilter) {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        if (pollingLoopFilter == null || pollingLoopFilter.isEmpty()) {
            throw new IllegalArgumentException("pollingLoopFilter is empty");
        }
        return false;
    }

    public boolean registerPollingLoopPatternFilterForService(
            ComponentName service, String pollingLoopPatternFilter, boolean autoTransact) {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        if (pollingLoopPatternFilter == null || pollingLoopPatternFilter.isEmpty()) {
            throw new IllegalArgumentException("pollingLoopPatternFilter is empty");
        }
        return false;
    }

    public boolean removePollingLoopPatternFilterForService(
            ComponentName service, String pollingLoopPatternFilter) {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        if (pollingLoopPatternFilter == null || pollingLoopPatternFilter.isEmpty()) {
            throw new IllegalArgumentException("pollingLoopPatternFilter is empty");
        }
        return false;
    }

    public boolean registerAidsForService(
            ComponentName service, String category, List<String> aids) {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        checkCategory(category);
        if (aids == null || aids.isEmpty()) {
            throw new IllegalArgumentException("aids cannot be null or empty");
        }
        ArrayList<String> normalized = new ArrayList<String>(aids.size());
        for (int i = 0; i < aids.size(); i++) {
            String aid = aids.get(i);
            if (!isValidAid(aid)) {
                throw new IllegalArgumentException("Aid " + aid + " is not a valid AID.");
            }
            normalized.add(aid.toUpperCase(Locale.ROOT));
        }
        synchronized (this) {
            mDynamicAids.put(serviceCategoryKey(service, category), normalized);
        }
        return true;
    }

    public boolean unsetOffHostForService(ComponentName service) {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        synchronized (this) {
            mOffHostSe.remove(service);
        }
        return true;
    }

    public boolean setOffHostForService(
            ComponentName service, String offHostSecureElement) {
        if (service == null || offHostSecureElement == null) {
            throw new NullPointerException("service or offHostSecureElement is null");
        }
        if (!offHostSecureElement.startsWith("eSE")
                && !offHostSecureElement.startsWith("SIM")) {
            throw new IllegalArgumentException("Invalid offHostSecureElement");
        }
        synchronized (this) {
            mOffHostSe.put(service, offHostSecureElement);
        }
        return true;
    }

    public List<String> getAidsForService(ComponentName service, String category) {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        checkCategory(category);
        synchronized (this) {
            List<String> list = mDynamicAids.get(serviceCategoryKey(service, category));
            return list != null ? new ArrayList<String>(list) : null;
        }
    }

    public boolean removeAidsForService(ComponentName service, String category) {
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        checkCategory(category);
        synchronized (this) {
            return mDynamicAids.remove(serviceCategoryKey(service, category)) != null;
        }
    }

    public boolean setPreferredService(Activity activity, ComponentName service) {
        if (activity == null || service == null) {
            throw new NullPointerException("activity or service or category is null");
        }
        return false;
    }

    public boolean unsetPreferredService(Activity activity) {
        if (activity == null) {
            throw new NullPointerException("activity is null");
        }
        return false;
    }

    public boolean supportsAidPrefixRegistration() {
        return false;
    }

    public List<String> getAidsForPreferredPaymentService() {
        return Collections.emptyList();
    }

    public String getRouteDestinationForPreferredPaymentService() {
        return "Host";
    }

    public CharSequence getDescriptionForPreferredPaymentService() {
        return null;
    }
}
