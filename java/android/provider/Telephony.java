// SPDX-License-Identifier: GPL-3.0-only
package android.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.SmsMessage;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** The Telephony provider contains data related to phone operation, specifically SMS and MMS. */
public final class Telephony {
    private Telephony() {
    }

    public interface TextBasedSmsColumns {
        String TYPE = "type";
        int MESSAGE_TYPE_ALL = 0;
        int MESSAGE_TYPE_INBOX = 1;
        int MESSAGE_TYPE_SENT = 2;
        int MESSAGE_TYPE_DRAFT = 3;
        int MESSAGE_TYPE_OUTBOX = 4;
        int MESSAGE_TYPE_FAILED = 5;
        int MESSAGE_TYPE_QUEUED = 6;

        String THREAD_ID = "thread_id";
        String ADDRESS = "address";
        String PERSON = "person";
        String DATE = "date";
        String DATE_SENT = "date_sent";
        String READ = "read";
        String SEEN = "seen";
        String STATUS = "status";
        int STATUS_NONE = -1;
        int STATUS_COMPLETE = 0;
        int STATUS_PENDING = 32;
        int STATUS_FAILED = 64;

        String SUBJECT = "subject";
        String BODY = "body";
        String SERVICE_CENTER = "service_center";
        String LOCKED = "locked";
        String SUBSCRIPTION_ID = "sub_id";
        String ERROR_CODE = "error_code";
        String CREATOR = "creator";
        String PROTOCOL = "protocol";
        String REPLY_PATH_PRESENT = "reply_path_present";
    }

    public static final class Sms implements BaseColumns, TextBasedSmsColumns {
        private Sms() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://sms");
        public static final String DEFAULT_SORT_ORDER = "date DESC";

        public static String getDefaultSmsPackage(Context context) {
            if (context == null) throw new NullPointerException("context");
            return null;
        }

        public static final class Inbox implements BaseColumns, TextBasedSmsColumns {
            private Inbox() {
            }

            public static final Uri CONTENT_URI = Uri.parse("content://sms/inbox");
            public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Sent implements BaseColumns, TextBasedSmsColumns {
            private Sent() {
            }

            public static final Uri CONTENT_URI = Uri.parse("content://sms/sent");
            public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Draft implements BaseColumns, TextBasedSmsColumns {
            private Draft() {
            }

            public static final Uri CONTENT_URI = Uri.parse("content://sms/draft");
            public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Outbox implements BaseColumns, TextBasedSmsColumns {
            private Outbox() {
            }

            public static final Uri CONTENT_URI = Uri.parse("content://sms/outbox");
            public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Conversations implements BaseColumns, TextBasedSmsColumns {
            private Conversations() {
            }

            public static final Uri CONTENT_URI = Uri.parse("content://sms/conversations");
            public static final String DEFAULT_SORT_ORDER = "date DESC";
            public static final String SNIPPET = "snippet";
            public static final String MESSAGE_COUNT = "msg_count";
        }

        public static final class Intents {
            private Intents() {
            }

            public static final int RESULT_SMS_HANDLED = 1;
            public static final int RESULT_SMS_GENERIC_ERROR = 2;
            public static final int RESULT_SMS_OUT_OF_MEMORY = 3;
            public static final int RESULT_SMS_UNSUPPORTED = 4;
            public static final int RESULT_SMS_DUPLICATED = 5;
            public static final int RESULT_SMS_DISPATCH_FAILURE = 6;
            public static final int RESULT_SMS_NULL_PDU = 7;
            public static final int RESULT_SMS_NULL_MESSAGE = 8;
            public static final int RESULT_SMS_RECEIVED_WHILE_ENCRYPTED = 9;
            public static final int RESULT_SMS_DATABASE_ERROR = 10;
            public static final int RESULT_SMS_INVALID_URI = 11;

            public static final String ACTION_CHANGE_DEFAULT =
                    "android.provider.Telephony.ACTION_CHANGE_DEFAULT";
            public static final String EXTRA_PACKAGE_NAME = "package";
            public static final String ACTION_DEFAULT_SMS_PACKAGE_CHANGED =
                    "android.provider.action.DEFAULT_SMS_PACKAGE_CHANGED";
            public static final String EXTRA_IS_DEFAULT_SMS_APP =
                    "android.provider.extra.IS_DEFAULT_SMS_APP";
            public static final String ACTION_EXTERNAL_PROVIDER_CHANGE =
                    "android.provider.action.EXTERNAL_PROVIDER_CHANGE";

            public static final String SMS_DELIVER_ACTION =
                    "android.provider.Telephony.SMS_DELIVER";
            public static final String SMS_RECEIVED_ACTION =
                    "android.provider.Telephony.SMS_RECEIVED";
            public static final String DATA_SMS_RECEIVED_ACTION =
                    "android.intent.action.DATA_SMS_RECEIVED";
            public static final String WAP_PUSH_DELIVER_ACTION =
                    "android.provider.Telephony.WAP_PUSH_DELIVER";
            public static final String WAP_PUSH_RECEIVED_ACTION =
                    "android.provider.Telephony.WAP_PUSH_RECEIVED";
            public static final String SMS_CB_RECEIVED_ACTION =
                    "android.provider.Telephony.SMS_CB_RECEIVED";
            public static final String SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION =
                    "android.provider.Telephony.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED";
            public static final String SIM_FULL_ACTION =
                    "android.provider.Telephony.SIM_FULL";
            public static final String SMS_REJECTED_ACTION =
                    "android.provider.Telephony.SMS_REJECTED";
            public static final String SECRET_CODE_ACTION =
                    "android.provider.Telephony.SECRET_CODE";

            public static SmsMessage[] getMessagesFromIntent(Intent intent) {
                if (intent == null) {
                    return null;
                }
                Object[] messages = null;
                try {
                    Serializable raw = intent.getSerializableExtra("pdus");
                    if (raw instanceof Object[]) {
                        messages = (Object[]) raw;
                    }
                } catch (ClassCastException e) {
                    return null;
                }
                if (messages == null) {
                    return null;
                }
                String format = intent.getStringExtra("format");
                if (format == null) {
                    format = SmsMessage.FORMAT_3GPP;
                }
                int pduCount = messages.length;
                SmsMessage[] msgs = new SmsMessage[pduCount];
                for (int i = 0; i < pduCount; i++) {
                    byte[] pdu = (byte[]) messages[i];
                    msgs[i] = SmsMessage.createFromPdu(pdu, format);
                }
                return msgs;
            }
        }
    }

    public interface ThreadsColumns extends BaseColumns {
        String DATE = "date";
        String MESSAGE_COUNT = "message_count";
        String RECIPIENT_IDS = "recipient_ids";
        String SNIPPET = "snippet";
        String SNIPPET_CHARSET = "snippet_cs";
        String READ = "read";
        String ARCHIVED = "archived";
        String TYPE = "type";
        String ERROR = "error";
        String HAS_ATTACHMENT = "has_attachment";
    }

    public static final class Threads implements ThreadsColumns {
        private Threads() {
        }

        public static final Uri CONTENT_URI =
                Uri.withAppendedPath(MmsSms.CONTENT_URI, "conversations");
        public static final Uri OBSOLETE_THREADS_URI =
                Uri.withAppendedPath(CONTENT_URI, "obsolete");

        public static final int COMMON_THREAD = 0;
        public static final int BROADCAST_THREAD = 1;

        private static final Uri THREAD_ID_CONTENT_URI = Uri.parse("content://mms-sms/threadID");

        public static long getOrCreateThreadId(Context context, String recipient) {
            Set<String> recipients = new TreeSet<String>();
            if (recipient != null) {
                recipients.add(recipient);
            }
            return getOrCreateThreadId(context, recipients);
        }

        public static long getOrCreateThreadId(Context context, Set<String> recipients) {
            if (context == null) throw new NullPointerException("context");
            if (recipients == null || recipients.isEmpty()) {
                throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
            }
            Uri.Builder uriBuilder = THREAD_ID_CONTENT_URI.buildUpon();
            TreeSet<String> sorted = new TreeSet<String>();
            for (String recipient : recipients) {
                if (recipient != null) {
                    sorted.add(recipient.trim());
                    uriBuilder.appendQueryParameter("recipient", recipient);
                }
            }
            if (sorted.isEmpty()) {
                throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
            }
            ContentResolver resolver = context.getContentResolver();
            if (resolver != null) {
                Cursor cursor = resolver.query(
                        uriBuilder.build(), new String[] { _ID }, null, null, null);
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            return cursor.getLong(0);
                        }
                    } finally {
                        cursor.close();
                    }
                }
            }
            long hash = 1125899906842597L;
            for (String item : sorted) {
                for (int i = 0; i < item.length(); i++) {
                    hash = 31L * hash + item.charAt(i);
                }
                hash = 31L * hash + ';';
            }
            long positive = hash & 0x7fffffffffffffffL;
            return positive == 0L ? 1L : positive;
        }
    }

    public interface BaseMmsColumns extends BaseColumns {
        int MESSAGE_BOX_ALL = 0;
        int MESSAGE_BOX_INBOX = 1;
        int MESSAGE_BOX_SENT = 2;
        int MESSAGE_BOX_DRAFTS = 3;
        int MESSAGE_BOX_OUTBOX = 4;
        int MESSAGE_BOX_FAILED = 5;

        String THREAD_ID = "thread_id";
        String DATE = "date";
        String DATE_SENT = "date_sent";
        String MESSAGE_BOX = "msg_box";
        String READ = "read";
        String SEEN = "seen";
        String TEXT_ONLY = "text_only";
        String MESSAGE_ID = "m_id";
        String SUBJECT = "sub";
        String SUBJECT_CHARSET = "sub_cs";
        String CONTENT_TYPE = "ct_t";
        String CONTENT_LOCATION = "ct_l";
        String EXPIRY = "exp";
        String MESSAGE_CLASS = "m_cls";
        String MESSAGE_TYPE = "m_type";
        String MMS_VERSION = "v";
        String MESSAGE_SIZE = "m_size";
        String PRIORITY = "pri";
        String READ_REPORT = "rr";
        String REPORT_ALLOWED = "rpt_a";
        String RESPONSE_STATUS = "resp_st";
        String STATUS = "st";
        String TRANSACTION_ID = "tr_id";
        String RETRIEVE_STATUS = "retr_st";
        String RETRIEVE_TEXT = "retr_txt";
        String RETRIEVE_TEXT_CHARSET = "retr_txt_cs";
        String READ_STATUS = "read_status";
        String CONTENT_CLASS = "ct_cls";
        String DELIVERY_TIME = "d_tm";
        String DELIVERY_REPORT = "d_rpt";
        String LOCKED = "locked";
        String SUBSCRIPTION_ID = "sub_id";
        String RESPONSE_TEXT = "resp_txt";
        String CREATOR = "creator";
    }

    public interface CanonicalAddressesColumns extends BaseColumns {
        String ADDRESS = "address";
    }

    public static final class Mms implements BaseMmsColumns {
        private Mms() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://mms");
        public static final Uri REPORT_REQUEST_URI =
                Uri.withAppendedPath(CONTENT_URI, "report-request");
        public static final Uri REPORT_STATUS_URI =
                Uri.withAppendedPath(CONTENT_URI, "report-status");
        public static final String DEFAULT_SORT_ORDER = "date DESC";

        public static final class Inbox implements BaseMmsColumns {
            private Inbox() {
            }

            public static final Uri CONTENT_URI = Uri.parse("content://mms/inbox");
            public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Sent implements BaseMmsColumns {
            private Sent() {
            }

            public static final Uri CONTENT_URI = Uri.parse("content://mms/sent");
            public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Draft implements BaseMmsColumns {
            private Draft() {
            }

            public static final Uri CONTENT_URI = Uri.parse("content://mms/drafts");
            public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Outbox implements BaseMmsColumns {
            private Outbox() {
            }

            public static final Uri CONTENT_URI = Uri.parse("content://mms/outbox");
            public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Addr implements BaseColumns {
            private Addr() {
            }

            public static final String MSG_ID = "msg_id";
            public static final String CONTACT_ID = "contact_id";
            public static final String ADDRESS = "address";
            public static final String TYPE = "type";
            public static final String CHARSET = "charset";

            public static Uri getAddrUriForMessage(String messageId) {
                if (messageId == null) throw new NullPointerException("messageId");
                return CONTENT_URI.buildUpon().appendPath(messageId).appendPath("addr").build();
            }
        }

        public static final class Part implements BaseColumns {
            private Part() {
            }

            public static final Uri CONTENT_URI = Uri.withAppendedPath(Mms.CONTENT_URI, "part");
            public static final String MSG_ID = "mid";
            public static final String SEQ = "seq";
            public static final String CONTENT_TYPE = "ct";
            public static final String NAME = "name";
            public static final String CHARSET = "chset";
            public static final String CONTENT_DISPOSITION = "cd";
            public static final String FILENAME = "fn";
            public static final String CONTENT_ID = "cid";
            public static final String CONTENT_LOCATION = "cl";
            public static final String CT_START = "ctt_s";
            public static final String CT_TYPE = "ctt_t";
            public static final String _DATA = "_data";
            public static final String TEXT = "text";

            public static Uri getPartUriForMessage(String messageId) {
                if (messageId == null) throw new NullPointerException("messageId");
                return Mms.CONTENT_URI.buildUpon().appendPath(messageId).appendPath("part").build();
            }
        }

        public static final class Rate {
            private Rate() {
            }

            public static final Uri CONTENT_URI = Uri.withAppendedPath(Mms.CONTENT_URI, "rate");
            public static final String SENT_TIME = "sent_time";
        }

        public static final class Intents {
            private Intents() {
            }

            public static final String CONTENT_CHANGED_ACTION =
                    "android.intent.action.CONTENT_CHANGED";
            public static final String DELETED_CONTENTS = "deleted_contents";
        }
    }

    public static final class MmsSms implements BaseColumns {
        private MmsSms() {
        }

        public static final String TYPE_DISCRIMINATOR_COLUMN = "transport_type";
        public static final Uri CONTENT_URI = Uri.parse("content://mms-sms/");
        public static final Uri CONTENT_CONVERSATIONS_URI =
                Uri.parse("content://mms-sms/conversations");
        public static final Uri CONTENT_FILTER_BYPHONE_URI =
                Uri.parse("content://mms-sms/messages/byphone");
        public static final Uri CONTENT_UNDELIVERED_URI =
                Uri.parse("content://mms-sms/undelivered");
        public static final Uri CONTENT_DRAFT_URI =
                Uri.parse("content://mms-sms/draft");
        public static final Uri CONTENT_LOCKED_URI =
                Uri.parse("content://mms-sms/locked");
        public static final Uri SEARCH_URI =
                Uri.parse("content://mms-sms/search");

        public static final int SMS_PROTO = 0;
        public static final int MMS_PROTO = 1;

        public static final int NO_ERROR = 0;
        public static final int ERR_TYPE_GENERIC = 1;
        public static final int ERR_TYPE_SMS_PROTO_TRANSIENT = 2;
        public static final int ERR_TYPE_MMS_PROTO_TRANSIENT = 3;
        public static final int ERR_TYPE_TRANSPORT_FAILURE = 4;
        public static final int ERR_TYPE_GENERIC_PERMANENT = 10;
        public static final int ERR_TYPE_SMS_PROTO_PERMANENT = 11;
        public static final int ERR_TYPE_MMS_PROTO_PERMANENT = 12;

        public static final class PendingMessages implements BaseColumns {
            private PendingMessages() {
            }

            public static final Uri CONTENT_URI =
                    Uri.withAppendedPath(MmsSms.CONTENT_URI, "pending");
            public static final String PROTO_TYPE = "proto_type";
            public static final String MSG_ID = "msg_id";
            public static final String MSG_TYPE = "msg_type";
            public static final String ERROR_TYPE = "err_type";
            public static final String ERROR_CODE = "err_code";
            public static final String RETRY_INDEX = "retry_index";
            public static final String DUE_TIME = "due_time";
            public static final String LAST_TRY = "last_try";
            public static final String SUBSCRIPTION_ID = "pending_sub_id";
        }
    }

    public static final class Carriers implements BaseColumns {
        private Carriers() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://telephony/carriers");
        public static final Uri SIM_APN_URI = Uri.parse("content://telephony/carriers/sim_apn_list");
        public static final String DEFAULT_SORT_ORDER = "name ASC";

        public static final String NAME = "name";
        public static final String APN = "apn";
        public static final String PROXY = "proxy";
        public static final String PORT = "port";
        public static final String MMSPROXY = "mmsproxy";
        public static final String MMSPORT = "mmsport";
        public static final String SERVER = "server";
        public static final String USER = "user";
        public static final String PASSWORD = "password";
        public static final String MMSC = "mmsc";
        public static final String MCC = "mcc";
        public static final String MNC = "mnc";
        public static final String NUMERIC = "numeric";
        public static final String AUTH_TYPE = "authtype";
        public static final String TYPE = "type";
        public static final String PROTOCOL = "protocol";
        public static final String ROAMING_PROTOCOL = "roaming_protocol";
        public static final String CURRENT = "current";
        public static final String CARRIER_ENABLED = "carrier_enabled";
        @Deprecated public static final String BEARER = "bearer";
        public static final String NETWORK_TYPE_BITMASK = "network_type_bitmask";
        public static final String SUBSCRIPTION_ID = "sub_id";
        public static final String MVNO_TYPE = "mvno_type";
        public static final String MVNO_MATCH_DATA = "mvno_match_data";
        public static final String CARRIER_ID = "carrier_id";
        public static final String MTU_V4 = "mtu_v4";
        public static final String MTU_V6 = "mtu_v6";
        public static final String USER_VISIBLE = "user_visible";
        public static final String USER_EDITABLE = "user_editable";
        public static final String ALWAYS_ON = "always_on";
    }

    public static final class CarrierId implements BaseColumns {
        private CarrierId() {
        }

        public static final Uri CONTENT_URI = Uri.parse("content://carrier_information/carrier");
        public static final String CARRIER_NAME = "carrier_name";
        public static final String CARRIER_ID = "carrier_id";
        public static final String SPECIFIC_CARRIER_ID = "specific_carrier_id";
        public static final String SPECIFIC_CARRIER_ID_NAME = "specific_carrier_id_name";

        public static Uri getUriForSubscriptionId(int subscriptionId) {
            return CONTENT_URI.buildUpon()
                    .appendEncodedPath(String.valueOf(subscriptionId))
                    .build();
        }

        public static Uri getSpecificCarrierIdUriForSubscriptionId(int subscriptionId) {
            return Uri.withAppendedPath(
                    Uri.withAppendedPath(CONTENT_URI, "specific"),
                    String.valueOf(subscriptionId));
        }
    }

    public static final class ServiceStateTable {
        private ServiceStateTable() {
        }

        public static final String AUTHORITY = "service-state";
        public static final Uri CONTENT_URI = Uri.parse("content://service-state/");

        public static final String VOICE_REG_STATE = "voice_reg_state";
        public static final String DATA_REG_STATE = "data_reg_state";
        public static final String VOICE_OPERATOR_NUMERIC = "voice_operator_numeric";
        public static final String IS_MANUAL_NETWORK_SELECTION = "is_manual_network_selection";
        public static final String DATA_NETWORK_TYPE = "data_network_type";
        public static final String DUPLEX_MODE = "duplex_mode";

        public static Uri getUriForSubscriptionIdAndField(int subscriptionId, String field) {
            return CONTENT_URI.buildUpon()
                    .appendEncodedPath(String.valueOf(subscriptionId))
                    .appendEncodedPath(field)
                    .build();
        }

        public static Uri getUriForSubscriptionId(int subscriptionId) {
            return CONTENT_URI.buildUpon()
                    .appendEncodedPath(String.valueOf(subscriptionId))
                    .build();
        }
    }
}
