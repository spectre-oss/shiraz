// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Subscription service for the desktop runtime, which has no modem or SIM subscriptions. */
public class SubscriptionManager {
    public static final int INVALID_SUBSCRIPTION_ID = -1;
    public static final int INVALID_SIM_SLOT_INDEX = -1;
    public static final int DEFAULT_SUBSCRIPTION_ID = Integer.MAX_VALUE;

    public static final int DATA_ROAMING_ENABLE = 1;
    public static final int DATA_ROAMING_DISABLE = 0;

    public static final int SUBSCRIPTION_TYPE_LOCAL_SIM = 0;
    public static final int SUBSCRIPTION_TYPE_REMOTE_SIM = 1;

    public static final String EXTRA_SUBSCRIPTION_INDEX =
            "android.telephony.extra.SUBSCRIPTION_INDEX";
    public static final String EXTRA_SLOT_INDEX =
            "android.telephony.extra.SLOT_INDEX";

    public static final String ACTION_DEFAULT_SUBSCRIPTION_CHANGED =
            "android.telephony.action.DEFAULT_SUBSCRIPTION_CHANGED";
    public static final String ACTION_DEFAULT_SMS_SUBSCRIPTION_CHANGED =
            "android.telephony.action.DEFAULT_SMS_SUBSCRIPTION_CHANGED";
    public static final String ACTION_MANAGE_SUBSCRIPTION_PLANS =
            "android.telephony.action.MANAGE_SUBSCRIPTION_PLANS";
    public static final String ACTION_REFRESH_SUBSCRIPTION_PLANS =
            "android.telephony.action.REFRESH_SUBSCRIPTION_PLANS";

    public static final int D2D_SHARING_DISABLED = 0;
    public static final int D2D_SHARING_ALL_CONTACTS = 1;
    public static final int D2D_SHARING_SELECTED_CONTACTS = 2;
    public static final int D2D_SHARING_ALL = 3;

    public static final String D2D_STATUS_SHARING = "d2d_sharing_status";
    public static final String D2D_STATUS_SHARING_SELECTED_CONTACTS = "d2d_sharing_contacts";

    public static final int PHONE_NUMBER_SOURCE_UICC = 1;
    public static final int PHONE_NUMBER_SOURCE_CARRIER = 2;
    public static final int PHONE_NUMBER_SOURCE_IMS = 3;

    public static final int USAGE_SETTING_UNKNOWN = -1;
    public static final int USAGE_SETTING_DEFAULT = 0;
    public static final int USAGE_SETTING_VOICE_CENTRIC = 1;
    public static final int USAGE_SETTING_DATA_CENTRIC = 2;

    public static final int SERVICE_CAPABILITY_VOICE = 1;
    public static final int SERVICE_CAPABILITY_SMS = 2;
    public static final int SERVICE_CAPABILITY_DATA = 3;

    private final IdentityHashMap<OnSubscriptionsChangedListener, Object> listeners =
            new IdentityHashMap<OnSubscriptionsChangedListener, Object>();
    private final IdentityHashMap<OnOpportunisticSubscriptionsChangedListener, Object>
            opportunisticListeners =
                    new IdentityHashMap<OnOpportunisticSubscriptionsChangedListener, Object>();
    private final Map<Integer, List<SubscriptionPlan>> mPlansBySubId =
            new HashMap<Integer, List<SubscriptionPlan>>();
    private final Map<Integer, Integer> mD2dSharingPref = new HashMap<Integer, Integer>();
    private final Map<Integer, List<Uri>> mD2dSharingContacts = new HashMap<Integer, List<Uri>>();
    private final Map<Integer, String> mCarrierNumbers = new HashMap<Integer, String>();

    public SubscriptionManager() {
    }

    @Deprecated
    public static SubscriptionManager from(Context context) {
        return (SubscriptionManager) context.getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE);
    }

    public static int getDefaultSubscriptionId() {
        return INVALID_SUBSCRIPTION_ID;
    }

    public static int getDefaultDataSubscriptionId() {
        return INVALID_SUBSCRIPTION_ID;
    }

    public static int getDefaultVoiceSubscriptionId() {
        return INVALID_SUBSCRIPTION_ID;
    }

    public static int getDefaultSmsSubscriptionId() {
        return INVALID_SUBSCRIPTION_ID;
    }

    public static int getActiveDataSubscriptionId() {
        return INVALID_SUBSCRIPTION_ID;
    }

    public static int getSlotIndex(int subscriptionId) {
        return INVALID_SIM_SLOT_INDEX;
    }

    public int[] getSubscriptionIds(int slotIndex) {
        return null;
    }

    public static int getSubscriptionId(int slotIndex) {
        return INVALID_SUBSCRIPTION_ID;
    }

    public int getActiveSubscriptionInfoCount() {
        return 0;
    }

    public int getActiveSubscriptionInfoCountMax() {
        return 0;
    }

    public List<SubscriptionInfo> getAllSubscriptionInfoList() {
        return new ArrayList<SubscriptionInfo>();
    }

    public List<SubscriptionInfo> getActiveSubscriptionInfoList() {
        return new ArrayList<SubscriptionInfo>();
    }

    public List<SubscriptionInfo> getCompleteActiveSubscriptionInfoList() {
        return new ArrayList<SubscriptionInfo>();
    }

    public List<SubscriptionInfo> getAccessibleSubscriptionInfoList() {
        return new ArrayList<SubscriptionInfo>();
    }

    public SubscriptionInfo getActiveSubscriptionInfo(int id) {
        return null;
    }

    public SubscriptionInfo getActiveSubscriptionInfoForSimSlotIndex(int slot) {
        return null;
    }

    public boolean isActiveSubscriptionId(int id) {
        return false;
    }

    public static boolean isValidSubscriptionId(int id) {
        return id > INVALID_SUBSCRIPTION_ID;
    }

    public static boolean isUsableSubscriptionId(int subscriptionId) {
        return isValidSubscriptionId(subscriptionId) && subscriptionId != DEFAULT_SUBSCRIPTION_ID;
    }

    public boolean isNetworkRoaming(int subId) {
        return false;
    }

    public List<SubscriptionPlan> getSubscriptionPlans(int subId) {
        synchronized (mPlansBySubId) {
            List<SubscriptionPlan> list = mPlansBySubId.get(Integer.valueOf(subId));
            return list == null
                    ? Collections.<SubscriptionPlan>emptyList()
                    : new ArrayList<SubscriptionPlan>(list);
        }
    }

    public void setSubscriptionPlans(int subId, List<SubscriptionPlan> plans) {
        Objects.requireNonNull(plans, "plans");
        synchronized (mPlansBySubId) {
            mPlansBySubId.put(Integer.valueOf(subId), new ArrayList<SubscriptionPlan>(plans));
        }
    }

    public void setSubscriptionPlans(
            int subId, List<SubscriptionPlan> plans, long expirationDurationMillis) {
        if (expirationDurationMillis <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        setSubscriptionPlans(subId, plans);
    }

    public void setSubscriptionOverrideUnmetered(
            int subId, boolean overrideUnmetered, long timeoutMillis) {
    }

    public void setSubscriptionOverrideUnmetered(
            int subId, boolean overrideUnmetered, int[] networkTypes, long timeoutMillis) {
        Objects.requireNonNull(networkTypes, "networkTypes");
    }

    public void setSubscriptionOverrideCongested(
            int subId, boolean overrideCongested, long timeoutMillis) {
    }

    public void setSubscriptionOverrideCongested(
            int subId, boolean overrideCongested, int[] networkTypes, long timeoutMillis) {
        Objects.requireNonNull(networkTypes, "networkTypes");
    }

    public boolean canManageSubscription(SubscriptionInfo info) {
        return false;
    }

    public List<SubscriptionInfo> getOpportunisticSubscriptions() {
        return new ArrayList<SubscriptionInfo>();
    }

    public void switchToSubscription(int subId, PendingIntent callbackIntent) {
        Objects.requireNonNull(callbackIntent, "callbackIntent");
        try {
            callbackIntent.shirazSend(new Intent());
        } catch (PendingIntent.CanceledException ignored) {
        }
    }

    public boolean setOpportunistic(boolean opportunistic, int subId) {
        return false;
    }

    public ParcelUuid createSubscriptionGroup(List<Integer> subIdList) {
        Objects.requireNonNull(subIdList, "subIdList can't be null");
        return new ParcelUuid(UUID.randomUUID());
    }

    public void addSubscriptionsIntoGroup(List<Integer> subIdList, ParcelUuid groupUuid) {
        Objects.requireNonNull(subIdList, "subIdList can't be null");
        Objects.requireNonNull(groupUuid, "groupUuid can't be null");
    }

    public void removeSubscriptionsFromGroup(List<Integer> subIdList, ParcelUuid groupUuid) {
        Objects.requireNonNull(subIdList, "subIdList can't be null");
        Objects.requireNonNull(groupUuid, "groupUuid can't be null");
    }

    public List<SubscriptionInfo> getSubscriptionsInGroup(ParcelUuid groupUuid) {
        Objects.requireNonNull(groupUuid, "groupUuid can't be null");
        return new ArrayList<SubscriptionInfo>();
    }

    public void setDeviceToDeviceStatusSharingPreference(int subscriptionId, int sharing) {
        synchronized (mD2dSharingPref) {
            mD2dSharingPref.put(Integer.valueOf(subscriptionId), Integer.valueOf(sharing));
        }
    }

    public int getDeviceToDeviceStatusSharingPreference(int subscriptionId) {
        synchronized (mD2dSharingPref) {
            Integer v = mD2dSharingPref.get(Integer.valueOf(subscriptionId));
            return v != null ? v.intValue() : D2D_SHARING_DISABLED;
        }
    }

    public void setDeviceToDeviceStatusSharingContacts(
            int subscriptionId, List<Uri> contacts) {
        Objects.requireNonNull(contacts, "contacts");
        synchronized (mD2dSharingContacts) {
            mD2dSharingContacts.put(Integer.valueOf(subscriptionId), new ArrayList<Uri>(contacts));
        }
    }

    public List<Uri> getDeviceToDeviceStatusSharingContacts(int subscriptionId) {
        synchronized (mD2dSharingContacts) {
            List<Uri> list = mD2dSharingContacts.get(Integer.valueOf(subscriptionId));
            return list == null ? Collections.<Uri>emptyList() : new ArrayList<Uri>(list);
        }
    }

    public String getPhoneNumber(int subscriptionId, int source) {
        if (source < PHONE_NUMBER_SOURCE_UICC || source > PHONE_NUMBER_SOURCE_IMS) {
            throw new IllegalArgumentException("invalid source " + source);
        }
        if (source == PHONE_NUMBER_SOURCE_CARRIER) {
            synchronized (mCarrierNumbers) {
                String n = mCarrierNumbers.get(Integer.valueOf(subscriptionId));
                if (n != null) {
                    return n;
                }
            }
        }
        return "";
    }

    public String getPhoneNumber(int subscriptionId) {
        synchronized (mCarrierNumbers) {
            String n = mCarrierNumbers.get(Integer.valueOf(subscriptionId));
            if (n != null) {
                return n;
            }
        }
        return "";
    }

    public void setCarrierPhoneNumber(int subscriptionId, String number) {
        Objects.requireNonNull(number, "number");
        synchronized (mCarrierNumbers) {
            mCarrierNumbers.put(Integer.valueOf(subscriptionId), number);
        }
    }

    public static class OnSubscriptionsChangedListener {
        final Handler handler;

        public OnSubscriptionsChangedListener() {
            this(Looper.myLooper());
        }

        public OnSubscriptionsChangedListener(Looper looper) {
            handler = looper == null ? null : new Handler(looper);
        }

        public void onSubscriptionsChanged() {
        }
    }

    public static class OnOpportunisticSubscriptionsChangedListener {
        public OnOpportunisticSubscriptionsChangedListener() {
        }

        public void onOpportunisticSubscriptionsChanged() {
        }
    }

    @Deprecated
    public void addOnSubscriptionsChangedListener(final OnSubscriptionsChangedListener listener) {
        Objects.requireNonNull(listener);
        if (listener.handler == null) {
            throw new IllegalStateException(
                    "Subscription listener requires a Looper or explicit executor");
        }
        addOnSubscriptionsChangedListener(new Executor() {
            public void execute(Runnable callback) {
                if (!listener.handler.post(callback)) {
                    throw new IllegalStateException("Subscription listener Looper stopped");
                }
            }
        }, listener);
    }

    public void addOnSubscriptionsChangedListener(
            Executor executor, final OnSubscriptionsChangedListener listener) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(listener);
        final Object token = new Object();
        final Object previous;
        synchronized (listeners) {
            if (!listeners.containsKey(listener) && listeners.size() >= 128) {
                throw new IllegalStateException("Subscription listener limit");
            }
            previous = listeners.put(listener, token);
        }
        try {
            executor.execute(new Runnable() {
                public void run() {
                    synchronized (listeners) {
                        if (listeners.get(listener) != token) {
                            return;
                        }
                    }
                    listener.onSubscriptionsChanged();
                }
            });
        } catch (RuntimeException failure) {
            synchronized (listeners) {
                if (listeners.get(listener) == token) {
                    if (previous == null) {
                        listeners.remove(listener);
                    } else {
                        listeners.put(listener, previous);
                    }
                }
            }
            throw failure;
        }
    }

    public void removeOnSubscriptionsChangedListener(OnSubscriptionsChangedListener listener) {
        Objects.requireNonNull(listener);
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void addOnOpportunisticSubscriptionsChangedListener(
            Executor executor, final OnOpportunisticSubscriptionsChangedListener listener) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(listener);
        final Object token = new Object();
        final Object previous;
        synchronized (opportunisticListeners) {
            previous = opportunisticListeners.put(listener, token);
        }
        try {
            executor.execute(new Runnable() {
                public void run() {
                    synchronized (opportunisticListeners) {
                        if (opportunisticListeners.get(listener) != token) {
                            return;
                        }
                    }
                    listener.onOpportunisticSubscriptionsChanged();
                }
            });
        } catch (RuntimeException failure) {
            synchronized (opportunisticListeners) {
                if (opportunisticListeners.get(listener) == token) {
                    if (previous == null) {
                        opportunisticListeners.remove(listener);
                    } else {
                        opportunisticListeners.put(listener, previous);
                    }
                }
            }
            throw failure;
        }
    }

    public void removeOnOpportunisticSubscriptionsChangedListener(
            OnOpportunisticSubscriptionsChangedListener listener) {
        Objects.requireNonNull(listener);
        synchronized (opportunisticListeners) {
            opportunisticListeners.remove(listener);
        }
    }
}
