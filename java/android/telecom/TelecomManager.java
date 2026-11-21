// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/** System telecom registry and transactional call manager. */
public class TelecomManager {
    public static final String ACTION_CHANGE_DEFAULT_DIALER =
            "android.telecom.action.CHANGE_DEFAULT_DIALER";
    public static final String ACTION_CHANGE_PHONE_ACCOUNTS =
            "android.telecom.action.CHANGE_PHONE_ACCOUNTS";
    public static final String ACTION_CONFIGURE_PHONE_ACCOUNT =
            "android.telecom.action.CONFIGURE_PHONE_ACCOUNT";
    public static final String ACTION_DEFAULT_CALL_SCREENING_APP_CHANGED =
            "android.telecom.action.DEFAULT_CALL_SCREENING_APP_CHANGED";
    public static final String ACTION_DEFAULT_DIALER_CHANGED =
            "android.telecom.action.DEFAULT_DIALER_CHANGED";
    public static final String ACTION_INCOMING_CALL = "android.telecom.action.INCOMING_CALL";
    public static final String ACTION_PHONE_ACCOUNT_REGISTERED =
            "android.telecom.action.PHONE_ACCOUNT_REGISTERED";
    public static final String ACTION_PHONE_ACCOUNT_UNREGISTERED =
            "android.telecom.action.PHONE_ACCOUNT_UNREGISTERED";
    public static final String ACTION_POST_CALL = "android.telecom.action.POST_CALL";
    public static final String ACTION_SHOW_CALL_ACCESSIBILITY_SETTINGS =
            "android.telecom.action.SHOW_CALL_ACCESSIBILITY_SETTINGS";
    public static final String ACTION_SHOW_CALL_SETTINGS =
            "android.telecom.action.SHOW_CALL_SETTINGS";
    public static final String ACTION_SHOW_MISSED_CALLS_NOTIFICATION =
            "android.telecom.action.SHOW_MISSED_CALLS_NOTIFICATION";
    public static final String ACTION_SHOW_RESPOND_VIA_SMS_SETTINGS =
            "android.telecom.action.SHOW_RESPOND_VIA_SMS_SETTINGS";

    public static final char DTMF_CHARACTER_PAUSE = ',';
    public static final char DTMF_CHARACTER_WAIT = ';';

    public static final int DURATION_VERY_SHORT = 0;
    public static final int DURATION_SHORT = 1;
    public static final int DURATION_MEDIUM = 2;
    public static final int DURATION_LONG = 3;

    public static final String EXTRA_CALL_BACK_NUMBER =
            "android.telecom.extra.CALL_BACK_NUMBER";
    public static final String EXTRA_CALL_DISCONNECT_CAUSE =
            "android.telecom.extra.CALL_DISCONNECT_CAUSE";
    public static final String EXTRA_CALL_DISCONNECT_MESSAGE =
            "android.telecom.extra.CALL_DISCONNECT_MESSAGE";
    public static final String EXTRA_CALL_DURATION = "android.telecom.extra.CALL_DURATION";
    public static final String EXTRA_CALL_LOG_URI = "android.telecom.extra.CALL_LOG_URI";
    public static final String EXTRA_CALL_NETWORK_TYPE =
            "android.telecom.extra.CALL_NETWORK_TYPE";
    public static final String EXTRA_CALL_SUBJECT = "android.telecom.extra.CALL_SUBJECT";
    public static final String EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME =
            "android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME";
    public static final String EXTRA_DEFAULT_CALL_SCREENING_APP_COMPONENT_NAME =
            "android.telecom.extra.DEFAULT_CALL_SCREENING_APP_COMPONENT_NAME";
    public static final String EXTRA_DISCONNECT_CAUSE =
            "android.telecom.extra.DISCONNECT_CAUSE";
    public static final String EXTRA_HANDLE = "android.telecom.extra.HANDLE";
    public static final String EXTRA_HAS_PICTURE = "android.telecom.extra.HAS_PICTURE";
    public static final String EXTRA_INCOMING_CALL_ADDRESS =
            "android.telecom.extra.INCOMING_CALL_ADDRESS";
    public static final String EXTRA_INCOMING_CALL_EXTRAS =
            "android.telecom.extra.INCOMING_CALL_EXTRAS";
    public static final String EXTRA_INCOMING_VIDEO_STATE =
            "android.telecom.extra.INCOMING_VIDEO_STATE";
    public static final String EXTRA_IS_DEFAULT_CALL_SCREENING_APP =
            "android.telecom.extra.IS_DEFAULT_CALL_SCREENING_APP";
    public static final String EXTRA_LOCATION = "android.telecom.extra.LOCATION";
    public static final String EXTRA_NOTIFICATION_COUNT =
            "android.telecom.extra.NOTIFICATION_COUNT";
    public static final String EXTRA_NOTIFICATION_PHONE_NUMBER =
            "android.telecom.extra.NOTIFICATION_PHONE_NUMBER";
    public static final String EXTRA_OUTGOING_CALL_EXTRAS =
            "android.telecom.extra.OUTGOING_CALL_EXTRAS";
    public static final String EXTRA_OUTGOING_PICTURE =
            "android.telecom.extra.OUTGOING_PICTURE";
    public static final String EXTRA_PHONE_ACCOUNT_HANDLE =
            "android.telecom.extra.PHONE_ACCOUNT_HANDLE";
    public static final String EXTRA_PICTURE_URI = "android.telecom.extra.PICTURE_URI";
    public static final String EXTRA_PRIORITY = "android.telecom.extra.PRIORITY";
    public static final String EXTRA_START_CALL_WITH_RTT =
            "android.telecom.extra.START_CALL_WITH_RTT";
    public static final String EXTRA_START_CALL_WITH_SPEAKERPHONE =
            "android.telecom.extra.START_CALL_WITH_SPEAKERPHONE";
    public static final String EXTRA_START_CALL_WITH_VIDEO_STATE =
            "android.telecom.extra.START_CALL_WITH_VIDEO_STATE";
    public static final String EXTRA_USE_ASSISTED_DIALING =
            "android.telecom.extra.USE_ASSISTED_DIALING";

    public static final String GATEWAY_ORIGINAL_ADDRESS =
            "android.telecom.extra.GATEWAY_ORIGINAL_ADDRESS";
    public static final String GATEWAY_PROVIDER_PACKAGE =
            "android.telecom.extra.GATEWAY_PROVIDER_PACKAGE";

    public static final String METADATA_INCLUDE_EXTERNAL_CALLS =
            "android.telecom.INCLUDE_EXTERNAL_CALLS";
    public static final String METADATA_INCLUDE_SELF_MANAGED_CALLS =
            "android.telecom.INCLUDE_SELF_MANAGED_CALLS";
    public static final String METADATA_IN_CALL_SERVICE_CAR_MODE_UI =
            "android.telecom.IN_CALL_SERVICE_CAR_MODE_UI";
    public static final String METADATA_IN_CALL_SERVICE_RINGING =
            "android.telecom.IN_CALL_SERVICE_RINGING";
    public static final String METADATA_IN_CALL_SERVICE_UI =
            "android.telecom.IN_CALL_SERVICE_UI";

    public static final int PRESENTATION_ALLOWED = 1;
    public static final int PRESENTATION_RESTRICTED = 2;
    public static final int PRESENTATION_UNKNOWN = 3;
    public static final int PRESENTATION_PAYPHONE = 4;
    public static final int PRESENTATION_UNAVAILABLE = 5;

    public static final int PRIORITY_NORMAL = 0;
    public static final int PRIORITY_URGENT = 1;

    private final List<PhoneAccount> accounts = new ArrayList<PhoneAccount>();
    private final List<CallControl> activeCalls = new CopyOnWriteArrayList<CallControl>();

    public TelecomManager() {
    }

    public PhoneAccountHandle getDefaultOutgoingPhoneAccount(String uriScheme) {
        synchronized (accounts) {
            for (PhoneAccount account : accounts) {
                if (account.hasCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                        && (uriScheme == null || account.supportsUriScheme(uriScheme))) {
                    return account.getAccountHandle();
                }
            }
        }
        return null;
    }

    public PhoneAccountHandle getUserSelectedOutgoingPhoneAccount() {
        return getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL);
    }

    public PhoneAccountHandle getSimCallManager() {
        synchronized (accounts) {
            for (PhoneAccount account : accounts) {
                if (account.hasCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER)) {
                    return account.getAccountHandle();
                }
            }
        }
        return null;
    }

    public PhoneAccountHandle getSimCallManagerForSubscription(int subscriptionId) {
        return getSimCallManager();
    }

    public List<PhoneAccountHandle> getCallCapablePhoneAccounts() {
        List<PhoneAccountHandle> result = new ArrayList<PhoneAccountHandle>();
        synchronized (accounts) {
            for (PhoneAccount account : accounts) {
                if (account.hasCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)) {
                    result.add(account.getAccountHandle());
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<PhoneAccountHandle> getSelfManagedPhoneAccounts() {
        List<PhoneAccountHandle> result = new ArrayList<PhoneAccountHandle>();
        synchronized (accounts) {
            for (PhoneAccount account : accounts) {
                if (account.hasCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)) {
                    result.add(account.getAccountHandle());
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<PhoneAccountHandle> getOwnSelfManagedPhoneAccounts() {
        List<PhoneAccountHandle> result = new ArrayList<PhoneAccountHandle>();
        synchronized (accounts) {
            for (PhoneAccount account : accounts) {
                if (account.hasCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                        || account.hasCapabilities(
                                PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS)) {
                    result.add(account.getAccountHandle());
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<PhoneAccount> getRegisteredPhoneAccounts() {
        synchronized (accounts) {
            return Collections.unmodifiableList(new ArrayList<PhoneAccount>(accounts));
        }
    }

    public PhoneAccount getPhoneAccount(PhoneAccountHandle account) {
        if (account == null) {
            return null;
        }
        synchronized (accounts) {
            for (PhoneAccount a : accounts) {
                if (a.getAccountHandle().equals(account)) {
                    return a;
                }
            }
        }
        return null;
    }

    public void registerPhoneAccount(PhoneAccount account) {
        if (account == null) {
            throw new NullPointerException("account");
        }
        synchronized (accounts) {
            remove(account.getAccountHandle());
            accounts.add(account);
        }
    }

    public void unregisterPhoneAccount(PhoneAccountHandle accountHandle) {
        if (accountHandle == null) {
            return;
        }
        synchronized (accounts) {
            remove(accountHandle);
        }
    }

    private void remove(PhoneAccountHandle handle) {
        if (handle == null) {
            return;
        }
        for (int i = accounts.size() - 1; i >= 0; i--) {
            if (accounts.get(i).getAccountHandle().equals(handle)) {
                accounts.remove(i);
            }
        }
    }

    public String getDefaultDialerPackage() {
        return null;
    }

    public String getSystemDialerPackage() {
        return null;
    }

    public boolean isVoiceMailNumber(PhoneAccountHandle accountHandle, String number) {
        if (number == null || number.isEmpty()) {
            return false;
        }
        String vm = getVoiceMailNumber(accountHandle);
        return vm != null && vm.equals(number);
    }

    public String getVoiceMailNumber(PhoneAccountHandle accountHandle) {
        return null;
    }

    public String getLine1Number(PhoneAccountHandle accountHandle) {
        PhoneAccount account = getPhoneAccount(accountHandle);
        if (account != null && account.getAddress() != null) {
            return account.getAddress().getSchemeSpecificPart();
        }
        return null;
    }

    public boolean isInCall() {
        for (CallControl call : activeCalls) {
            if (call.isTracked()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasManageOngoingCallsPermission() {
        return false;
    }

    public boolean isInManagedCall() {
        return false;
    }

    public boolean endCall() {
        boolean ended = false;
        for (CallControl call : activeCalls) {
            if (call.isTracked()) {
                removeTrackedCall(call);
                ended = true;
            }
        }
        return ended;
    }

    public void acceptRingingCall() {
        acceptRingingCall(VideoProfile.STATE_AUDIO_ONLY);
    }

    public void acceptRingingCall(int videoState) {
    }

    public void silenceRinger() {
    }

    public boolean isTtySupported() {
        return false;
    }

    public void addNewIncomingCall(PhoneAccountHandle phoneAccount, Bundle extras) {
        throw new UnsupportedOperationException("Host call UI is not implemented");
    }

    public void addNewIncomingConference(PhoneAccountHandle phoneAccount, Bundle extras) {
        throw new UnsupportedOperationException("Host call UI is not implemented");
    }

    public boolean handleMmi(String dialString) {
        return false;
    }

    public boolean handleMmi(String dialString, PhoneAccountHandle accountHandle) {
        return false;
    }

    public Uri getAdnUriForPhoneAccount(PhoneAccountHandle accountHandle) {
        return Uri.parse("content://icc/adn");
    }

    public void cancelMissedCallsNotification() {
    }

    public void showInCallScreen(boolean showDialpad) {
    }

    public void placeCall(Uri address, Bundle extras) {
        throw new UnsupportedOperationException("Host call UI is not implemented");
    }

    public void startConference(List<Uri> participants, Bundle extras) {
        throw new UnsupportedOperationException("Host call UI is not implemented");
    }

    public Intent createManageBlockedNumbersIntent() {
        return new Intent("android.telecom.action.MANAGE_BLOCKED_NUMBERS");
    }

    public boolean isIncomingCallPermitted(PhoneAccountHandle phoneAccountHandle) {
        return phoneAccountHandle != null && getPhoneAccount(phoneAccountHandle) != null;
    }

    public boolean isOutgoingCallPermitted(PhoneAccountHandle phoneAccountHandle) {
        return phoneAccountHandle != null && getPhoneAccount(phoneAccountHandle) != null;
    }

    public void acceptHandover(
            Uri srcAddr, int videoState, PhoneAccountHandle destAcct) {
        Objects.requireNonNull(srcAddr, "srcAddr");
        Objects.requireNonNull(destAcct, "destAcct");
    }

    public void addCall(
            CallAttributes callAttributes,
            Executor executor,
            final OutcomeReceiver<CallControl, CallException> pendingControl,
            CallControlCallback handshakes,
            CallEventCallback events) {
        Objects.requireNonNull(callAttributes, "callAttributes");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(pendingControl, "pendingControl");
        Objects.requireNonNull(handshakes, "handshakes");
        Objects.requireNonNull(events, "events");
        final CallControl control =
                new CallControl(callAttributes, handshakes, events, this);
        activeCalls.add(control);
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        pendingControl.onResult(control);
                    }
                });
    }

    void removeTrackedCall(CallControl control) {
        if (control != null) {
            activeCalls.remove(control);
        }
    }
}
