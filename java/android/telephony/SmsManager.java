// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.CursorWindow;
import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

public final class SmsManager {
    public static final String EXTRA_MMS_DATA = "android.telephony.extra.MMS_DATA";
    public static final String EXTRA_MMS_HTTP_STATUS = "android.telephony.extra.MMS_HTTP_STATUS";
    public static final String MMS_CONFIG_ALIAS_ENABLED = "aliasEnabled";
    public static final String MMS_CONFIG_ALIAS_MAX_CHARS = "aliasMaxChars";
    public static final String MMS_CONFIG_ALIAS_MIN_CHARS = "aliasMinChars";
    public static final String MMS_CONFIG_ALLOW_ATTACH_AUDIO = "allowAttachAudio";
    public static final String MMS_CONFIG_APPEND_TRANSACTION_ID = "enabledTransID";
    public static final String MMS_CONFIG_EMAIL_GATEWAY_NUMBER = "emailGatewayNumber";
    public static final String MMS_CONFIG_GROUP_MMS_ENABLED = "enableGroupMms";
    public static final String MMS_CONFIG_HTTP_PARAMS = "httpParams";
    public static final String MMS_CONFIG_HTTP_SOCKET_TIMEOUT = "httpSocketTimeout";
    public static final String MMS_CONFIG_MAX_IMAGE_HEIGHT = "maxImageHeight";
    public static final String MMS_CONFIG_MAX_IMAGE_WIDTH = "maxImageWidth";
    public static final String MMS_CONFIG_MAX_MESSAGE_SIZE = "maxMessageSize";
    public static final String MMS_CONFIG_MESSAGE_TEXT_MAX_SIZE = "maxMessageTextSize";
    public static final String MMS_CONFIG_MMS_DELIVERY_REPORT_ENABLED = "enableMMSDeliveryReports";
    public static final String MMS_CONFIG_MMS_ENABLED = "enabledMMS";
    public static final String MMS_CONFIG_MMS_READ_REPORT_ENABLED = "enableMMSReadReports";
    public static final String MMS_CONFIG_MULTIPART_SMS_ENABLED = "enableMultipartSMS";
    public static final String MMS_CONFIG_NAI_SUFFIX = "naiSuffix";
    public static final String MMS_CONFIG_NOTIFY_WAP_MMSC_ENABLED = "enabledNotifyWapMMSC";
    public static final String MMS_CONFIG_RECIPIENT_LIMIT = "recipientLimit";
    public static final String MMS_CONFIG_SEND_MULTIPART_SMS_AS_SEPARATE_MESSAGES = "sendMultipartSmsAsSeparateMessages";
    public static final String MMS_CONFIG_SHOW_CELL_BROADCAST_APP_LINKS = "config_cellBroadcastAppLinks";
    public static final String MMS_CONFIG_SMS_DELIVERY_REPORT_ENABLED = "enableSMSDeliveryReports";
    public static final String MMS_CONFIG_SMS_TO_MMS_TEXT_LENGTH_THRESHOLD = "smsToMmsTextLengthThreshold";
    public static final String MMS_CONFIG_SMS_TO_MMS_TEXT_THRESHOLD = "smsToMmsTextThreshold";
    public static final String MMS_CONFIG_SUBJECT_MAX_LENGTH = "maxSubjectLength";
    public static final String MMS_CONFIG_SUPPORT_HTTP_CHARSET_HEADER = "supportHttpCharsetHeader";
    public static final String MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION = "supportMmsContentDisposition";
    public static final String MMS_CONFIG_UA_PROF_TAG_NAME = "uaProfTagName";
    public static final String MMS_CONFIG_UA_PROF_URL = "uaProfUrl";
    public static final String MMS_CONFIG_USER_AGENT = "userAgent";
    public static final int MMS_ERROR_CONFIGURATION_ERROR = 7;
    public static final int MMS_ERROR_DATA_DISABLED = 11;
    public static final int MMS_ERROR_HTTP_FAILURE = 4;
    public static final int MMS_ERROR_INACTIVE_SUBSCRIPTION = 10;
    public static final int MMS_ERROR_INVALID_APN = 2;
    public static final int MMS_ERROR_INVALID_SUBSCRIPTION_ID = 9;
    public static final int MMS_ERROR_IO_ERROR = 5;
    public static final int MMS_ERROR_MMS_DISABLED_BY_CARRIER = 12;
    public static final int MMS_ERROR_NO_DATA_NETWORK = 8;
    public static final int MMS_ERROR_RETRY = 6;
    public static final int MMS_ERROR_UNABLE_CONNECT_MMS = 3;
    public static final int MMS_ERROR_UNSPECIFIED = 1;
    public static final int RESULT_BLUETOOTH_DISCONNECTED = 27;
    public static final int RESULT_CANCELLED = 23;
    public static final int RESULT_ENCODING_ERROR = 18;
    public static final int RESULT_ERROR_FDN_CHECK_FAILURE = 6;
    public static final int RESULT_ERROR_GENERIC_FAILURE = 1;
    public static final int RESULT_ERROR_LIMIT_EXCEEDED = 5;
    public static final int RESULT_ERROR_NONE = 0;
    public static final int RESULT_ERROR_NO_SERVICE = 4;
    public static final int RESULT_ERROR_NULL_PDU = 3;
    public static final int RESULT_ERROR_RADIO_OFF = 2;
    public static final int RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED = 8;
    public static final int RESULT_ERROR_SHORT_CODE_NOT_ALLOWED = 7;
    public static final int RESULT_INTERNAL_ERROR = 21;
    public static final int RESULT_INVALID_ARGUMENTS = 11;
    public static final int RESULT_INVALID_BLUETOOTH_ADDRESS = 26;
    public static final int RESULT_INVALID_SMSC_ADDRESS = 19;
    public static final int RESULT_INVALID_SMS_FORMAT = 14;
    public static final int RESULT_INVALID_STATE = 12;
    public static final int RESULT_MODEM_ERROR = 16;
    public static final int RESULT_NETWORK_ERROR = 17;
    public static final int RESULT_NETWORK_REJECT = 10;
    public static final int RESULT_NO_BLUETOOTH_SERVICE = 25;
    public static final int RESULT_NO_DEFAULT_SMS_APP = 32;
    public static final int RESULT_NO_MEMORY = 13;
    public static final int RESULT_NO_RESOURCES = 22;
    public static final int RESULT_OPERATION_NOT_ALLOWED = 20;
    public static final int RESULT_RADIO_NOT_AVAILABLE = 9;
    public static final int RESULT_RECEIVE_DISPATCH_FAILURE = 500;
    public static final int RESULT_RECEIVE_INJECTED_NULL_PDU = 501;
    public static final int RESULT_RECEIVE_NULL_MESSAGE_FROM_RIL = 503;
    public static final int RESULT_RECEIVE_RUNTIME_EXCEPTION = 502;
    public static final int RESULT_RECEIVE_SQL_EXCEPTION = 505;
    public static final int RESULT_RECEIVE_URI_EXCEPTION = 506;
    public static final int RESULT_RECEIVE_WHILE_ENCRYPTED = 504;
    public static final int RESULT_REMOTE_EXCEPTION = 31;
    public static final int RESULT_REQUEST_NOT_SUPPORTED = 24;
    public static final int RESULT_RIL_ABORTED = 137;
    public static final int RESULT_RIL_ACCESS_BARRED = 122;
    public static final int RESULT_RIL_BLOCKED_DUE_TO_CALL = 123;
    public static final int RESULT_RIL_CANCELLED = 119;
    public static final int RESULT_RIL_DEVICE_IN_USE = 136;
    public static final int RESULT_RIL_ENCODING_ERR = 109;
    public static final int RESULT_RIL_GENERIC_ERROR = 124;
    public static final int RESULT_RIL_INTERNAL_ERR = 113;
    public static final int RESULT_RIL_INVALID_ARGUMENTS = 104;
    public static final int RESULT_RIL_INVALID_MODEM_STATE = 115;
    public static final int RESULT_RIL_INVALID_RESPONSE = 125;
    public static final int RESULT_RIL_INVALID_SIM_STATE = 130;
    public static final int RESULT_RIL_INVALID_SMSC_ADDRESS = 110;
    public static final int RESULT_RIL_INVALID_SMS_FORMAT = 107;
    public static final int RESULT_RIL_INVALID_STATE = 103;
    public static final int RESULT_RIL_MODEM_ERR = 111;
    public static final int RESULT_RIL_NETWORK_ERR = 112;
    public static final int RESULT_RIL_NETWORK_NOT_READY = 116;
    public static final int RESULT_RIL_NETWORK_REJECT = 102;
    public static final int RESULT_RIL_NO_MEMORY = 105;
    public static final int RESULT_RIL_NO_NETWORK_FOUND = 135;
    public static final int RESULT_RIL_NO_RESOURCES = 118;
    public static final int RESULT_RIL_NO_SMS_TO_ACK = 131;
    public static final int RESULT_RIL_NO_SUBSCRIPTION = 134;
    public static final int RESULT_RIL_OPERATION_NOT_ALLOWED = 117;
    public static final int RESULT_RIL_RADIO_NOT_AVAILABLE = 100;
    public static final int RESULT_RIL_REQUEST_NOT_SUPPORTED = 114;
    public static final int RESULT_RIL_REQUEST_RATE_LIMITED = 106;
    public static final int RESULT_RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED = 121;
    public static final int RESULT_RIL_SIM_ABSENT = 120;
    public static final int RESULT_RIL_SIM_BUSY = 132;
    public static final int RESULT_RIL_SIM_ERROR = 129;
    public static final int RESULT_RIL_SIM_FULL = 133;
    public static final int RESULT_RIL_SIM_PIN2 = 126;
    public static final int RESULT_RIL_SIM_PUK2 = 127;
    public static final int RESULT_RIL_SMS_SEND_FAIL_RETRY = 101;
    public static final int RESULT_RIL_SUBSCRIPTION_NOT_AVAILABLE = 128;
    public static final int RESULT_RIL_SYSTEM_ERR = 108;
    public static final int RESULT_SMS_BLOCKED_DURING_EMERGENCY = 29;
    public static final int RESULT_SMS_SEND_RETRY_FAILED = 30;
    public static final int RESULT_SYSTEM_ERROR = 15;
    public static final int RESULT_UNEXPECTED_EVENT_STOP_SENDING = 28;
    public static final int RESULT_USER_NOT_ALLOWED = 33;
    public static final int SMS_RP_CAUSE_CALL_BARRING = 10;
    public static final int SMS_RP_CAUSE_CONGESTION = 42;
    public static final int SMS_RP_CAUSE_DESTINATION_OUT_OF_ORDER = 27;
    public static final int SMS_RP_CAUSE_FACILITY_NOT_IMPLEMENTED = 69;
    public static final int SMS_RP_CAUSE_FACILITY_NOT_SUBSCRIBED = 50;
    public static final int SMS_RP_CAUSE_FACILITY_REJECTED = 29;
    public static final int SMS_RP_CAUSE_INFORMATION_ELEMENT_NON_EXISTENT = 99;
    public static final int SMS_RP_CAUSE_INTERWORKING_UNSPECIFIED = 127;
    public static final int SMS_RP_CAUSE_INVALID_MANDATORY_INFORMATION = 96;
    public static final int SMS_RP_CAUSE_INVALID_MESSAGE_REFERENCE_VALUE = 81;
    public static final int SMS_RP_CAUSE_MESSAGE_INCOMPATIBLE_WITH_PROTOCOL_STATE = 98;
    public static final int SMS_RP_CAUSE_MESSAGE_TYPE_NON_EXISTENT = 97;
    public static final int SMS_RP_CAUSE_NETWORK_OUT_OF_ORDER = 38;
    public static final int SMS_RP_CAUSE_OPERATOR_DETERMINED_BARRING = 8;
    public static final int SMS_RP_CAUSE_PROTOCOL_ERROR = 111;
    public static final int SMS_RP_CAUSE_RESERVED = 11;
    public static final int SMS_RP_CAUSE_RESOURCES_UNAVAILABLE = 47;
    public static final int SMS_RP_CAUSE_SEMANTICALLY_INCORRECT_MESSAGE = 95;
    public static final int SMS_RP_CAUSE_SHORT_MESSAGE_TRANSFER_REJECTED = 21;
    public static final int SMS_RP_CAUSE_TEMPORARY_FAILURE = 41;
    public static final int SMS_RP_CAUSE_UNALLOCATED_NUMBER = 1;
    public static final int SMS_RP_CAUSE_UNIDENTIFIED_SUBSCRIBER = 28;
    public static final int SMS_RP_CAUSE_UNKNOWN_SUBSCRIBER = 30;
    public static final int STATUS_ON_ICC_FREE = 0;
    public static final int STATUS_ON_ICC_READ = 1;
    public static final int STATUS_ON_ICC_SENT = 5;
    public static final int STATUS_ON_ICC_UNREAD = 3;
    public static final int STATUS_ON_ICC_UNSENT = 7;

    private static final Object sLock = new Object();
    private static final Map<Integer, SmsManager> sSubInstances =
            new HashMap<Integer, SmsManager>();
    private static final SmsManager sDefaultInstance =
            new SmsManager(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);

    private final int mSubId;
    private String mSmscAddress;

    private SmsManager(int subId) {
        mSubId = subId;
    }

    @Deprecated
    public static SmsManager getDefault() {
        return sDefaultInstance;
    }

    @Deprecated
    public static SmsManager getSmsManagerForSubscriptionId(int subId) {
        synchronized (sLock) {
            SmsManager smsManager = sSubInstances.get(Integer.valueOf(subId));
            if (smsManager == null) {
                smsManager = new SmsManager(subId);
                sSubInstances.put(Integer.valueOf(subId), smsManager);
            }
            return smsManager;
        }
    }

    public SmsManager createForSubscriptionId(int subId) {
        return getSmsManagerForSubscriptionId(subId);
    }

    public int getSubscriptionId() {
        return mSubId;
    }

    public static int getDefaultSmsSubscriptionId() {
        return SubscriptionManager.getDefaultSmsSubscriptionId();
    }

    public int getSmsCapacityOnIcc() {
        return 0;
    }

    public ArrayList<String> divideMessage(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text is null");
        }
        ArrayList<String> result = new ArrayList<String>();
        int len = text.length();
        if (len == 0) {
            result.add("");
            return result;
        }
        int[] params = SmsMessage.calculateLength(text, false);
        int msgCount = params[0];
        int encoding = params[3];
        if (msgCount <= 1) {
            result.add(text);
            return result;
        }
        int step = (encoding == SmsMessage.ENCODING_7BIT)
                ? SmsMessage.MAX_USER_DATA_SEPTETS_WITH_HEADER
                : (SmsMessage.MAX_USER_DATA_BYTES_WITH_HEADER / 2);
        int pos = 0;
        while (pos < len) {
            int nextPos = Math.min(len, pos + step);
            if (nextPos < len && nextPos > pos + 1
                    && Character.isHighSurrogate(text.charAt(nextPos - 1))
                    && Character.isLowSurrogate(text.charAt(nextPos))) {
                nextPos--;
            }
            result.add(text.substring(pos, nextPos));
            pos = nextPos;
        }
        return result;
    }

    public void sendTextMessage(String destinationAddress, String scAddress, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent) {
        sendTextMessage(destinationAddress, scAddress, text, sentIntent, deliveryIntent, 0L);
    }

    public void sendTextMessage(String destinationAddress, String scAddress, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent, long messageId) {
        if (destinationAddress == null || destinationAddress.isEmpty()) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Invalid message body");
        }
        notifyNoService(sentIntent);
    }

    public void sendTextMessageWithoutPersisting(String destinationAddress, String scAddress,
            String text, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        sendTextMessage(destinationAddress, scAddress, text, sentIntent, deliveryIntent, 0L);
    }

    public void injectSmsPdu(byte[] pdu, String format, PendingIntent receivedIntent) {
        if (!SmsMessage.FORMAT_3GPP.equals(format) && !SmsMessage.FORMAT_3GPP2.equals(format)) {
            throw new IllegalArgumentException("Invalid pdu format. format must be either 3gpp or 3gpp2");
        }
        notifyNoService(receivedIntent);
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress,
            ArrayList<String> parts, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
        sendMultipartTextMessage(
                destinationAddress, scAddress, parts, sentIntents, deliveryIntents, 0L);
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress,
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents, long messageId) {
        if (destinationAddress == null || destinationAddress.isEmpty()) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("Invalid message body");
        }
        if (sentIntents != null) {
            for (int i = 0; i < sentIntents.size(); i++) {
                notifyNoService(sentIntents.get(i));
            }
        }
    }

    public void sendMultipartTextMessage(String destinationAddress, String scAddress,
            List<String> parts, List<PendingIntent> sentIntents,
            List<PendingIntent> deliveryIntents, String packageName, String attributionTag) {
        sendMultipartTextMessage(
                destinationAddress, scAddress, parts, sentIntents, deliveryIntents, 0L);
    }

    public void sendDataMessage(String destinationAddress, String scAddress, short destinationPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (destinationAddress == null || destinationAddress.isEmpty()) {
            throw new IllegalArgumentException("Invalid destinationAddress");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid message data");
        }
        notifyNoService(sentIntent);
    }

    public void sendMultimediaMessage(Context context, Uri contentUri, String locationUrl,
            Bundle configOverrides, PendingIntent sentIntent) {
        sendMultimediaMessage(context, contentUri, locationUrl, configOverrides, sentIntent, 0L);
    }

    public void sendMultimediaMessage(Context context, Uri contentUri, String locationUrl,
            Bundle configOverrides, PendingIntent sentIntent, long messageId) {
        if (contentUri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        }
        notifyNoService(sentIntent);
    }

    public void downloadMultimediaMessage(Context context, String locationUrl, Uri contentUri,
            Bundle configOverrides, PendingIntent downloadedIntent) {
        downloadMultimediaMessage(
                context, locationUrl, contentUri, configOverrides, downloadedIntent, 0L);
    }

    public void downloadMultimediaMessage(Context context, String locationUrl, Uri contentUri,
            Bundle configOverrides, PendingIntent downloadedIntent, long messageId) {
        if (locationUrl == null || locationUrl.isEmpty()) {
            throw new IllegalArgumentException("Empty MMS location URL");
        }
        if (contentUri == null) {
            throw new IllegalArgumentException("Uri contentUri null");
        }
        notifyNoService(downloadedIntent);
    }

    public Bundle getCarrierConfigValues() {
        Bundle b = new Bundle();
        b.putBoolean(MMS_CONFIG_MMS_ENABLED, true);
        b.putInt(MMS_CONFIG_MAX_MESSAGE_SIZE, 307200);
        b.putInt(MMS_CONFIG_MAX_IMAGE_HEIGHT, 480);
        b.putInt(MMS_CONFIG_MAX_IMAGE_WIDTH, 640);
        b.putInt(MMS_CONFIG_RECIPIENT_LIMIT, Integer.MAX_VALUE);
        b.putBoolean(MMS_CONFIG_GROUP_MMS_ENABLED, true);
        b.putBoolean(MMS_CONFIG_SUPPORT_MMS_CONTENT_DISPOSITION, true);
        return b;
    }

    public String createAppSpecificSmsToken(PendingIntent intent) {
        Objects.requireNonNull(intent, "intent cannot be null");
        return UUID.randomUUID().toString().substring(0, 11);
    }

    public String createAppSpecificSmsTokenWithPackageInfo(
            String prefixes, PendingIntent intent) {
        Objects.requireNonNull(intent, "intent cannot be null");
        return UUID.randomUUID().toString().substring(0, 11);
    }

    @Deprecated
    public void getSmsMessagesForFinancialApp(
            Bundle params, Executor executor, final FinancialSmsCallback callback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        executor.execute(new Runnable() {
            public void run() {
                callback.onFinancialSmsMessages(new CursorWindow(false));
            }
        });
    }

    public String getSmscAddress() {
        return mSmscAddress;
    }

    public boolean setSmscAddress(String smsc) {
        mSmscAddress = smsc;
        return false;
    }

    private static void notifyNoService(PendingIntent pi) {
        if (pi == null) {
            return;
        }
        Intent fill = new Intent();
        fill.putExtra("errorCode", RESULT_ERROR_NO_SERVICE);
        try {
            pi.shirazSend(fill);
        } catch (PendingIntent.CanceledException ignored) {
        }
    }

    public abstract static class FinancialSmsCallback {
        public FinancialSmsCallback() {
        }

        public abstract void onFinancialSmsMessages(CursorWindow msgs);
    }
}
