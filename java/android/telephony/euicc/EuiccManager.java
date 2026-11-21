// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.euicc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;

public class EuiccManager {
    public static final String ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS =
            "android.telephony.euicc.action.MANAGE_EMBEDDED_SUBSCRIPTIONS";
    public static final String ACTION_NOTIFY_CARRIER_SETUP_INCOMPLETE =
            "android.telephony.euicc.action.NOTIFY_CARRIER_SETUP_INCOMPLETE";
    public static final String ACTION_START_EUICC_ACTIVATION =
            "android.telephony.euicc.action.START_EUICC_ACTIVATION";

    public static final String META_DATA_CARRIER_ICON = "android.telephony.euicc.carriericon";
    public static final String EXTRA_USE_QR_SCANNER =
            "android.telephony.euicc.extra.USE_QR_SCANNER";

    public static final int EMBEDDED_SUBSCRIPTION_RESULT_OK = 0;
    public static final int EMBEDDED_SUBSCRIPTION_RESULT_RESOLVABLE_ERROR = 1;
    public static final int EMBEDDED_SUBSCRIPTION_RESULT_ERROR = 2;

    public static final String EXTRA_EMBEDDED_SUBSCRIPTION_DETAILED_CODE =
            "android.telephony.euicc.extra.EMBEDDED_SUBSCRIPTION_DETAILED_CODE";
    public static final String EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE =
            "android.telephony.euicc.extra.EMBEDDED_SUBSCRIPTION_OPERATION_CODE";
    public static final String EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE =
            "android.telephony.euicc.extra.EMBEDDED_SUBSCRIPTION_ERROR_CODE";
    public static final String EXTRA_EMBEDDED_SUBSCRIPTION_SMDX_SUBJECT_CODE =
            "android.telephony.euicc.extra.EMBEDDED_SUBSCRIPTION_SMDX_SUBJECT_CODE";
    public static final String EXTRA_EMBEDDED_SUBSCRIPTION_SMDX_REASON_CODE =
            "android.telephony.euicc.extra.EMBEDDED_SUBSCRIPTION_SMDX_REASON_CODE";
    public static final String EXTRA_EMBEDDED_SUBSCRIPTION_DOWNLOADABLE_SUBSCRIPTION =
            "android.telephony.euicc.extra.EMBEDDED_SUBSCRIPTION_DOWNLOADABLE_SUBSCRIPTION";

    public static final long EUICC_MEMORY_FIELD_UNAVAILABLE = -1L;

    public static final int OPERATION_SYSTEM = 1;
    public static final int OPERATION_SIM_SLOT = 2;
    public static final int OPERATION_EUICC_CARD = 3;
    public static final int OPERATION_SWITCH = 4;
    public static final int OPERATION_DOWNLOAD = 5;
    public static final int OPERATION_METADATA = 6;
    public static final int OPERATION_EUICC_GSMA = 7;
    public static final int OPERATION_APDU = 8;
    public static final int OPERATION_SMDX = 9;
    public static final int OPERATION_SMDX_SUBJECT_REASON_CODE = 10;
    public static final int OPERATION_HTTP = 11;

    public static final int ERROR_CARRIER_LOCKED = 10000;
    public static final int ERROR_INVALID_ACTIVATION_CODE = 10001;
    public static final int ERROR_INVALID_CONFIRMATION_CODE = 10002;
    public static final int ERROR_INCOMPATIBLE_CARRIER = 10003;
    public static final int ERROR_EUICC_INSUFFICIENT_MEMORY = 10004;
    public static final int ERROR_TIME_OUT = 10005;
    public static final int ERROR_EUICC_MISSING = 10006;
    public static final int ERROR_UNSUPPORTED_VERSION = 10007;
    public static final int ERROR_SIM_MISSING = 10008;
    public static final int ERROR_INSTALL_PROFILE = 10009;
    public static final int ERROR_DISALLOWED_BY_PPR = 10010;
    public static final int ERROR_ADDRESS_MISSING = 10011;
    public static final int ERROR_CERTIFICATE_ERROR = 10012;
    public static final int ERROR_NO_PROFILES_AVAILABLE = 10013;
    public static final int ERROR_CONNECTION_ERROR = 10014;
    public static final int ERROR_INVALID_RESPONSE = 10015;
    public static final int ERROR_OPERATION_BUSY = 10016;
    public static final int ERROR_INVALID_PORT = 10017;

    private final Context mContext;
    private final int mCardId;

    EuiccManager(Context context) {
        this(context, -1);
    }

    private EuiccManager(Context context, int cardId) {
        mContext = context;
        mCardId = cardId;
    }

    public EuiccManager createForCardId(int cardId) {
        return new EuiccManager(mContext, cardId);
    }

    public boolean isEnabled() {
        return false;
    }

    public String getEid() {
        return null;
    }

    public long getAvailableMemoryInBytes() {
        return EUICC_MEMORY_FIELD_UNAVAILABLE;
    }

    public void downloadSubscription(
            DownloadableSubscription subscription,
            boolean switchAfterDownload,
            PendingIntent callbackIntent) {
        sendUnavailableError(callbackIntent);
    }

    public void startResolutionActivity(
            Activity activity,
            int requestCode,
            Intent resultIntent,
            PendingIntent callbackIntent) throws IntentSender.SendIntentException {
        throw new IntentSender.SendIntentException("eUICC hardware is not available on host");
    }

    public EuiccInfo getEuiccInfo() {
        return null;
    }

    public void deleteSubscription(int subscriptionId, PendingIntent callbackIntent) {
        sendUnavailableError(callbackIntent);
    }

    public void switchToSubscription(int subscriptionId, PendingIntent callbackIntent) {
        sendUnavailableError(callbackIntent);
    }

    public void switchToSubscription(int subscriptionId, int portIndex, PendingIntent callbackIntent) {
        sendUnavailableError(callbackIntent);
    }

    public void updateSubscriptionNickname(
            int subscriptionId, String nickname, PendingIntent callbackIntent) {
        sendUnavailableError(callbackIntent);
    }

    public boolean isSimPortAvailable(int portIndex) {
        return false;
    }

    private void sendUnavailableError(PendingIntent callbackIntent) {
        if (callbackIntent == null) {
            return;
        }
        Intent extras = new Intent();
        extras.putExtra(EXTRA_EMBEDDED_SUBSCRIPTION_OPERATION_CODE, OPERATION_EUICC_CARD);
        extras.putExtra(EXTRA_EMBEDDED_SUBSCRIPTION_ERROR_CODE, ERROR_EUICC_MISSING);
        try {
            callbackIntent.shirazSend(extras);
        } catch (PendingIntent.CanceledException ignored) {
        }
    }
}
