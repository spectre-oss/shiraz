// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.OutcomeReceiver;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.telecom.PhoneAccountHandle;
import android.telephony.data.NetworkSlicingConfig;
import android.telephony.emergency.EmergencyNumber;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Desktop without a modem: country information is unavailable unless configured, never inferred. */
public class TelephonyManager {
    private static String runtimeStateString(String value) {
        return value;
    }

    public static final String ACTION_CARRIER_MESSAGING_CLIENT_SERVICE = "android.telephony.action.CARRIER_MESSAGING_CLIENT_SERVICE";
    public static final String ACTION_CARRIER_SIGNAL_DEFAULT_NETWORK_AVAILABLE = "android.telephony.action.CARRIER_SIGNAL_DEFAULT_NETWORK_AVAILABLE";
    public static final String ACTION_CARRIER_SIGNAL_PCO_VALUE = "android.telephony.action.CARRIER_SIGNAL_PCO_VALUE";
    public static final String ACTION_CARRIER_SIGNAL_REDIRECTED = "android.telephony.action.CARRIER_SIGNAL_REDIRECTED";
    public static final String ACTION_CARRIER_SIGNAL_REQUEST_NETWORK_FAILED = "android.telephony.action.CARRIER_SIGNAL_REQUEST_NETWORK_FAILED";
    public static final String ACTION_CARRIER_SIGNAL_RESET = "android.telephony.action.CARRIER_SIGNAL_RESET";
    public static final String ACTION_CONFIGURE_VOICEMAIL = "android.telephony.action.CONFIGURE_VOICEMAIL";
    public static final String ACTION_MULTI_SIM_CONFIG_CHANGED = "android.telephony.action.MULTI_SIM_CONFIG_CHANGED";
    public static final String ACTION_NETWORK_COUNTRY_CHANGED = "android.telephony.action.NETWORK_COUNTRY_CHANGED";
    public static final String ACTION_PHONE_STATE_CHANGED = "android.intent.action.PHONE_STATE";
    public static final String ACTION_RESET_MOBILE_NETWORK_SETTINGS = "android.telephony.action.RESET_MOBILE_NETWORK_SETTINGS";
    public static final String ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE";
    public static final String ACTION_SECRET_CODE = "android.telephony.action.SECRET_CODE";
    public static final String ACTION_SHOW_VOICEMAIL_NOTIFICATION = "android.telephony.action.SHOW_VOICEMAIL_NOTIFICATION";
    public static final String ACTION_SUBSCRIPTION_CARRIER_IDENTITY_CHANGED = "android.telephony.action.SUBSCRIPTION_CARRIER_IDENTITY_CHANGED";
    public static final String ACTION_SUBSCRIPTION_SPECIFIC_CARRIER_IDENTITY_CHANGED = "android.telephony.action.SUBSCRIPTION_SPECIFIC_CARRIER_IDENTITY_CHANGED";
    public static final int ALLOWED_NETWORK_TYPES_REASON_CARRIER = 2;
    public static final int ALLOWED_NETWORK_TYPES_REASON_USER = 0;
    public static final int APPTYPE_CSIM = 4;
    public static final int APPTYPE_ISIM = 5;
    public static final int APPTYPE_RUIM = 3;
    public static final int APPTYPE_SIM = 1;
    public static final int APPTYPE_UNKNOWN = 0;
    public static final int APPTYPE_USIM = 2;
    public static final int AUTHTYPE_EAP_AKA = 129;
    public static final int AUTHTYPE_EAP_SIM = 128;
    public static final int AUTHTYPE_GBA_BOOTSTRAP = 132;
    public static final int AUTHTYPE_GBA_NAF_KEY_EXTERNAL = 133;
    public static final int CALL_COMPOSER_STATUS_BUSINESS_ONLY = 2;
    public static final int CALL_COMPOSER_STATUS_OFF = 0;
    public static final int CALL_COMPOSER_STATUS_ON = 1;
    public static final int CALL_STATE_IDLE = 0;
    public static final int CALL_STATE_OFFHOOK = 2;
    public static final int CALL_STATE_RINGING = 1;
    public static final String CAPABILITY_SLICING_CONFIG_SUPPORTED = "CAPABILITY_SLICING_CONFIG_SUPPORTED";
    public static final int CARRIER_RESTRICTION_STATUS_NOT_RESTRICTED = 1;
    public static final int CARRIER_RESTRICTION_STATUS_RESTRICTED = 2;
    public static final int CARRIER_RESTRICTION_STATUS_RESTRICTED_TO_CALLER = 3;
    public static final int CARRIER_RESTRICTION_STATUS_UNKNOWN = 0;
    public static final int CDMA_ROAMING_MODE_AFFILIATED = 1;
    public static final int CDMA_ROAMING_MODE_ANY = 2;
    public static final int CDMA_ROAMING_MODE_HOME = 0;
    public static final int CDMA_ROAMING_MODE_RADIO_DEFAULT = -1;
    public static final int DATA_ACTIVITY_DORMANT = 4;
    public static final int DATA_ACTIVITY_IN = 1;
    public static final int DATA_ACTIVITY_INOUT = 3;
    public static final int DATA_ACTIVITY_NONE = 0;
    public static final int DATA_ACTIVITY_OUT = 2;
    public static final int DATA_CONNECTED = 2;
    public static final int DATA_CONNECTING = 1;
    public static final int DATA_DISCONNECTED = 0;
    public static final int DATA_DISCONNECTING = 4;
    public static final int DATA_ENABLED_REASON_CARRIER = 2;
    public static final int DATA_ENABLED_REASON_OVERRIDE = 4;
    public static final int DATA_ENABLED_REASON_POLICY = 1;
    public static final int DATA_ENABLED_REASON_THERMAL = 3;
    public static final int DATA_ENABLED_REASON_UNKNOWN = -1;
    public static final int DATA_ENABLED_REASON_USER = 0;
    public static final int DATA_HANDOVER_IN_PROGRESS = 5;
    public static final int DATA_SUSPENDED = 3;
    public static final int DATA_UNKNOWN = -1;
    public static final int DEFAULT_PORT_INDEX = 0;
    public static final int ERI_FLASH = 2;
    public static final int ERI_OFF = 1;
    public static final int ERI_ON = 0;
    public static final String EVENT_DISPLAY_EMERGENCY_MESSAGE = "android.telephony.event.DISPLAY_EMERGENCY_MESSAGE";
    public static final String EXTRA_ACTIVE_SIM_SUPPORTED_COUNT = "android.telephony.extra.ACTIVE_SIM_SUPPORTED_COUNT";
    public static final String EXTRA_APN_PROTOCOL = "android.telephony.extra.APN_PROTOCOL";
    public static final String EXTRA_APN_TYPE = "android.telephony.extra.APN_TYPE";
    public static final String EXTRA_CALL_VOICEMAIL_INTENT = "android.telephony.extra.CALL_VOICEMAIL_INTENT";
    public static final String EXTRA_CARRIER_ID = "android.telephony.extra.CARRIER_ID";
    public static final String EXTRA_CARRIER_NAME = "android.telephony.extra.CARRIER_NAME";
    public static final String EXTRA_DATA_FAIL_CAUSE = "android.telephony.extra.DATA_FAIL_CAUSE";
    public static final String EXTRA_DEFAULT_NETWORK_AVAILABLE = "android.telephony.extra.DEFAULT_NETWORK_AVAILABLE";
    public static final String EXTRA_EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE = "android.telephony.extra.EMERGENCY_CALL_TO_SATELLITE_HANDOVER_TYPE";
    public static final String EXTRA_EMERGENCY_CALL_TO_SATELLITE_LAUNCH_INTENT = "android.telephony.extra.EMERGENCY_CALL_TO_SATELLITE_LAUNCH_INTENT";
    public static final String EXTRA_HIDE_PUBLIC_SETTINGS = "android.telephony.extra.HIDE_PUBLIC_SETTINGS";
    public static final String EXTRA_INCOMING_NUMBER = "incoming_number";
    public static final String EXTRA_IS_REFRESH = "android.telephony.extra.IS_REFRESH";
    public static final String EXTRA_LAST_KNOWN_NETWORK_COUNTRY = "android.telephony.extra.LAST_KNOWN_NETWORK_COUNTRY";
    public static final String EXTRA_LAUNCH_VOICEMAIL_SETTINGS_INTENT = "android.telephony.extra.LAUNCH_VOICEMAIL_SETTINGS_INTENT";
    public static final String EXTRA_NETWORK_COUNTRY = "android.telephony.extra.NETWORK_COUNTRY";
    public static final String EXTRA_NOTIFICATION_COUNT = "android.telephony.extra.NOTIFICATION_COUNT";
    public static final String EXTRA_PCO_ID = "android.telephony.extra.PCO_ID";
    public static final String EXTRA_PCO_VALUE = "android.telephony.extra.PCO_VALUE";
    public static final String EXTRA_PHONE_ACCOUNT_HANDLE = "android.telephony.extra.PHONE_ACCOUNT_HANDLE";
    public static final String EXTRA_REDIRECTION_URL = "android.telephony.extra.REDIRECTION_URL";
    public static final String EXTRA_SPECIFIC_CARRIER_ID = "android.telephony.extra.SPECIFIC_CARRIER_ID";
    public static final String EXTRA_SPECIFIC_CARRIER_NAME = "android.telephony.extra.SPECIFIC_CARRIER_NAME";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_STATE_IDLE = runtimeStateString("IDLE");
    public static final String EXTRA_STATE_OFFHOOK = runtimeStateString("OFFHOOK");
    public static final String EXTRA_STATE_RINGING = runtimeStateString("RINGING");
    public static final String EXTRA_SUBSCRIPTION_ID = "android.telephony.extra.SUBSCRIPTION_ID";
    public static final String EXTRA_VOICEMAIL_NUMBER = "android.telephony.extra.VOICEMAIL_NUMBER";
    public static final int INCLUDE_LOCATION_DATA_COARSE = 1;
    public static final int INCLUDE_LOCATION_DATA_FINE = 2;
    public static final int INCLUDE_LOCATION_DATA_NONE = 0;
    public static final String METADATA_HIDE_VOICEMAIL_SETTINGS_MENU = "android.telephony.HIDE_VOICEMAIL_SETTINGS_MENU";
    public static final int MULTISIM_ALLOWED = 0;
    public static final int MULTISIM_NOT_SUPPORTED_BY_CARRIER = 2;
    public static final int MULTISIM_NOT_SUPPORTED_BY_HARDWARE = 1;
    public static final int NETWORK_SELECTION_MODE_AUTO = 1;
    public static final int NETWORK_SELECTION_MODE_MANUAL = 2;
    public static final int NETWORK_SELECTION_MODE_UNKNOWN = 0;
    public static final int NETWORK_TYPE_1xRTT = 7;
    public static final long NETWORK_TYPE_BITMASK_1xRTT = 64L;
    public static final long NETWORK_TYPE_BITMASK_CDMA = 8L;
    public static final long NETWORK_TYPE_BITMASK_EDGE = 2L;
    public static final long NETWORK_TYPE_BITMASK_EHRPD = 8192L;
    public static final long NETWORK_TYPE_BITMASK_EVDO_0 = 16L;
    public static final long NETWORK_TYPE_BITMASK_EVDO_A = 32L;
    public static final long NETWORK_TYPE_BITMASK_EVDO_B = 2048L;
    public static final long NETWORK_TYPE_BITMASK_GPRS = 1L;
    public static final long NETWORK_TYPE_BITMASK_GSM = 32768L;
    public static final long NETWORK_TYPE_BITMASK_HSDPA = 128L;
    public static final long NETWORK_TYPE_BITMASK_HSPA = 512L;
    public static final long NETWORK_TYPE_BITMASK_HSPAP = 16384L;
    public static final long NETWORK_TYPE_BITMASK_HSUPA = 256L;
    public static final long NETWORK_TYPE_BITMASK_IWLAN = 131072L;
    public static final long NETWORK_TYPE_BITMASK_LTE = 4096L;
    public static final long NETWORK_TYPE_BITMASK_LTE_CA = 262144L;
    public static final long NETWORK_TYPE_BITMASK_NR = 524288L;
    public static final long NETWORK_TYPE_BITMASK_TD_SCDMA = 65536L;
    public static final long NETWORK_TYPE_BITMASK_UMTS = 4L;
    public static final long NETWORK_TYPE_BITMASK_UNKNOWN = 0L;
    public static final int NETWORK_TYPE_CDMA = 4;
    public static final int NETWORK_TYPE_EDGE = 2;
    public static final int NETWORK_TYPE_EHRPD = 14;
    public static final int NETWORK_TYPE_EVDO_0 = 5;
    public static final int NETWORK_TYPE_EVDO_A = 6;
    public static final int NETWORK_TYPE_EVDO_B = 12;
    public static final int NETWORK_TYPE_GPRS = 1;
    public static final int NETWORK_TYPE_GSM = 16;
    public static final int NETWORK_TYPE_HSDPA = 8;
    public static final int NETWORK_TYPE_HSPA = 10;
    public static final int NETWORK_TYPE_HSPAP = 15;
    public static final int NETWORK_TYPE_HSUPA = 9;
    public static final int NETWORK_TYPE_IDEN = 11;
    public static final int NETWORK_TYPE_IWLAN = 18;
    public static final int NETWORK_TYPE_LTE = 13;
    public static final int NETWORK_TYPE_NR = 20;
    public static final int NETWORK_TYPE_TD_SCDMA = 17;
    public static final int NETWORK_TYPE_UMTS = 3;
    public static final int NETWORK_TYPE_UNKNOWN = 0;
    public static final int PHONE_TYPE_CDMA = 2;
    public static final int PHONE_TYPE_GSM = 1;
    public static final int PHONE_TYPE_NONE = 0;
    public static final int PHONE_TYPE_SIP = 3;
    public static final int PREMIUM_CAPABILITY_PRIORITIZE_LATENCY = 34;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_IN_PROGRESS = 4;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_ALREADY_PURCHASED = 3;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_DISABLED = 7;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_CARRIER_ERROR = 8;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_ENTITLEMENT_CHECK_FAILED = 13;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_FEATURE_NOT_SUPPORTED = 10;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_NETWORK_NOT_AVAILABLE = 12;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_DEFAULT_DATA_SUBSCRIPTION = 14;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_NOT_FOREGROUND = 5;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_PENDING_NETWORK_SETUP = 15;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_REQUEST_FAILED = 11;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_SUCCESS = 1;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_THROTTLED = 2;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_TIMEOUT = 9;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_CANCELED = 6;
    public static final int PURCHASE_PREMIUM_CAPABILITY_RESULT_USER_DISABLED = 16;
    public static final int SET_OPPORTUNISTIC_SUB_INACTIVE_SUBSCRIPTION = 2;
    public static final int SET_OPPORTUNISTIC_SUB_NO_OPPORTUNISTIC_SUB_AVAILABLE = 3;
    public static final int SET_OPPORTUNISTIC_SUB_REMOTE_SERVICE_EXCEPTION = 4;
    public static final int SET_OPPORTUNISTIC_SUB_SUCCESS = 0;
    public static final int SET_OPPORTUNISTIC_SUB_VALIDATION_FAILED = 1;
    public static final int SIM_STATE_ABSENT = 1;
    public static final int SIM_STATE_CARD_IO_ERROR = 8;
    public static final int SIM_STATE_CARD_RESTRICTED = 9;
    public static final int SIM_STATE_NETWORK_LOCKED = 4;
    public static final int SIM_STATE_NOT_READY = 6;
    public static final int SIM_STATE_PERM_DISABLED = 7;
    public static final int SIM_STATE_PIN_REQUIRED = 2;
    public static final int SIM_STATE_PUK_REQUIRED = 3;
    public static final int SIM_STATE_READY = 5;
    public static final int SIM_STATE_UNKNOWN = 0;
    public static final int UNINITIALIZED_CARD_ID = -2;
    public static final int UNKNOWN_CARRIER_ID = -1;
    public static final int UNSUPPORTED_CARD_ID = -1;
    public static final int UPDATE_AVAILABLE_NETWORKS_ABORTED = 2;
    public static final int UPDATE_AVAILABLE_NETWORKS_DISABLE_MODEM_FAIL = 5;
    public static final int UPDATE_AVAILABLE_NETWORKS_ENABLE_MODEM_FAIL = 6;
    public static final int UPDATE_AVAILABLE_NETWORKS_INVALID_ARGUMENTS = 3;
    public static final int UPDATE_AVAILABLE_NETWORKS_MULTIPLE_NETWORKS_NOT_SUPPORTED = 7;
    public static final int UPDATE_AVAILABLE_NETWORKS_NO_CARRIER_PRIVILEGE = 4;
    public static final int UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE = 8;
    public static final int UPDATE_AVAILABLE_NETWORKS_REMOTE_SERVICE_EXCEPTION = 9;
    public static final int UPDATE_AVAILABLE_NETWORKS_SERVICE_IS_DISABLED = 10;
    public static final int UPDATE_AVAILABLE_NETWORKS_SUCCESS = 0;
    public static final int UPDATE_AVAILABLE_NETWORKS_UNKNOWN_FAILURE = 1;
    public static final int USSD_ERROR_SERVICE_UNAVAIL = -2;
    public static final int USSD_RETURN_FAILURE = -1;
    public static final String VVM_TYPE_CVVM = "vvm_type_cvvm";
    public static final String VVM_TYPE_OMTP = "vvm_type_omtp";

    private static final AtomicInteger NEXT_SCAN_ID = new AtomicInteger(1);

    private final int mSubId;
    private final Map<PhoneStateListener, Object> callListeners =
            new IdentityHashMap<PhoneStateListener, Object>();
    private final Map<TelephonyCallback, Object> telephonyCallbacks =
            new IdentityHashMap<TelephonyCallback, Object>();
    private final Map<Integer, Long> mAllowedNetworkTypes = new HashMap<Integer, Long>();
    private final Map<Integer, Boolean> mDataEnabledReasons = new HashMap<Integer, Boolean>();
    private boolean mDataEnabled = false;
    private int mCallComposerStatus = CALL_COMPOSER_STATUS_OFF;
    private Uri mVoicemailRingtoneUri;
    private boolean mVoicemailVibrationEnabled;

    public TelephonyManager() {
        this(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID);
    }

    private TelephonyManager(int subId) {
        mSubId = subId;
    }

    private String configured(String operation, String key) {
        shiraz.runtime.HostAppInstaller.recordAccess("telephony:" + operation);
        return shiraz.runtime.DeviceProfile.get(key, "");
    }

    /** No voice, SMS, or cellular data modem is provided by the desktop runtime. */
    public int getPhoneCount() {
        return 0;
    }

    public int getActiveModemCount() {
        return 0;
    }

    public int getSupportedModemCount() {
        return 0;
    }

    public TelephonyManager createForSubscriptionId(int subId) {
        return new TelephonyManager(subId);
    }

    public TelephonyManager createForPhoneAccountHandle(PhoneAccountHandle phoneAccountHandle) {
        if (phoneAccountHandle == null) {
            return null;
        }
        return new TelephonyManager(mSubId);
    }

    public static long getMaximumCallComposerPictureSize() {
        return 65536L;
    }

    public String getDeviceSoftwareVersion() {
        return null;
    }

    @Deprecated
    public String getDeviceId() {
        return null;
    }

    @Deprecated
    public String getDeviceId(int slotIndex) {
        return null;
    }

    public String getImei() {
        return null;
    }

    public String getImei(int slotIndex) {
        return null;
    }

    public String getPrimaryImei() {
        return null;
    }

    public String getTypeAllocationCode() {
        return null;
    }

    public String getTypeAllocationCode(int slotIndex) {
        return null;
    }

    public String getMeid() {
        return null;
    }

    public String getMeid(int slotIndex) {
        return null;
    }

    public String getManufacturerCode() {
        return null;
    }

    public String getManufacturerCode(int slotIndex) {
        return null;
    }

    public String getNai() {
        return null;
    }

    @Deprecated
    public CellLocation getCellLocation() {
        return null;
    }

    public int getPhoneType() {
        return shiraz.runtime.DeviceProfile.integer("telephony.phoneType", PHONE_TYPE_NONE);
    }

    public String getNetworkOperatorName() {
        return configured("getNetworkOperatorName", "telephony.networkOperatorName");
    }

    public String getNetworkOperator() {
        return configured("getNetworkOperator", "telephony.networkOperator");
    }

    public String getNetworkSpecifier() {
        return "";
    }

    public PersistableBundle getCarrierConfig() {
        return CarrierConfigManager.getDefaultConfig();
    }

    public boolean isNetworkRoaming() {
        return false;
    }

    public String getNetworkCountryIso() {
        return configured("getNetworkCountryIso", "telephony.networkCountryIso");
    }

    public String getNetworkCountryIso(int slotIndex) {
        return configured("getNetworkCountryIso", "telephony.networkCountryIso");
    }

    @Deprecated
    public int getNetworkType() {
        return shiraz.runtime.DeviceProfile.integer("telephony.networkType", NETWORK_TYPE_UNKNOWN);
    }

    public int getDataNetworkType() {
        return shiraz.runtime.DeviceProfile.integer("telephony.networkType", NETWORK_TYPE_UNKNOWN);
    }

    public int getVoiceNetworkType() {
        return shiraz.runtime.DeviceProfile.integer("telephony.networkType", NETWORK_TYPE_UNKNOWN);
    }

    public boolean hasIccCard() {
        return getSimState() != SIM_STATE_ABSENT && getSimState() != SIM_STATE_UNKNOWN;
    }

    public int getSimState() {
        return shiraz.runtime.DeviceProfile.integer("telephony.simState", SIM_STATE_ABSENT);
    }

    public int getSimState(int slotIndex) {
        return shiraz.runtime.DeviceProfile.integer("telephony.simState", SIM_STATE_ABSENT);
    }

    public String getSimOperator() {
        return configured("getSimOperator", "telephony.simOperator");
    }

    public String getSimOperatorName() {
        return configured("getSimOperatorName", "telephony.simOperatorName");
    }

    public String getSimCountryIso() {
        return configured("getSimCountryIso", "telephony.simCountryIso");
    }

    public String getSimSerialNumber() {
        return null;
    }

    public int getCardIdForDefaultEuicc() {
        return UNSUPPORTED_CARD_ID;
    }

    public List<UiccCardInfo> getUiccCardsInfo() {
        return Collections.emptyList();
    }

    public String getSubscriberId() {
        return null;
    }

    public void uploadCallComposerPicture(Path pictureToUpload, String contentType,
            Executor executor,
            final OutcomeReceiver<ParcelUuid, CallComposerException> callback) {
        Objects.requireNonNull(pictureToUpload, "pictureToUpload");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        executor.execute(new Runnable() {
            public void run() {
                callback.onError(new CallComposerException(
                        CallComposerException.ERROR_NETWORK_UNAVAILABLE, null));
            }
        });
    }

    public void uploadCallComposerPicture(InputStream pictureToUpload, String contentType,
            Executor executor,
            final OutcomeReceiver<ParcelUuid, CallComposerException> callback) {
        Objects.requireNonNull(pictureToUpload, "pictureToUpload");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        executor.execute(new Runnable() {
            public void run() {
                callback.onError(new CallComposerException(
                        CallComposerException.ERROR_NETWORK_UNAVAILABLE, null));
            }
        });
    }

    public String getGroupIdLevel1() {
        return null;
    }

    @Deprecated
    public String getLine1Number() {
        return null;
    }

    public boolean setLine1NumberForDisplay(String alphaTag, String number) {
        return false;
    }

    public String getVoiceMailNumber() {
        return null;
    }

    public boolean setVoiceMailNumber(String alphaTag, String number) {
        return false;
    }

    public String getVisualVoicemailPackageName() {
        return null;
    }

    public void setVisualVoicemailSmsFilterSettings(VisualVoicemailSmsFilterSettings settings) {
    }

    public void sendVisualVoicemailSms(String number, int port, String text,
            PendingIntent sentIntent) {
        if (sentIntent != null) {
            Intent fill = new Intent();
            fill.putExtra("errorCode", SmsManager.RESULT_ERROR_NO_SERVICE);
            try {
                sentIntent.shirazSend(fill);
            } catch (PendingIntent.CanceledException ignored) {
            }
        }
    }

    public String getVoiceMailAlphaTag() {
        return null;
    }

    public void sendDialerSpecialCode(String inputCode) {
    }

    @Deprecated
    public int getCallState() {
        return CALL_STATE_IDLE;
    }

    public int getCallStateForSubscription() {
        return CALL_STATE_IDLE;
    }

    public int getDataActivity() {
        return DATA_ACTIVITY_NONE;
    }

    public int getDataState() {
        return DATA_DISCONNECTED;
    }

    @Deprecated
    public void listen(final PhoneStateListener listener, final int events) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
        final Object registration = new Object();
        synchronized (callListeners) {
            if (events == PhoneStateListener.LISTEN_NONE) {
                callListeners.remove(listener);
                return;
            }
            callListeners.put(listener, registration);
        }
        listener.postCallback(new Runnable() {
            public void run() {
                synchronized (callListeners) {
                    if (callListeners.get(listener) != registration) {
                        return;
                    }
                }
                dispatchPhoneStateEvents(listener, events);
            }
        });
    }

    private void dispatchPhoneStateEvents(PhoneStateListener listener, int events) {
        if ((events & PhoneStateListener.LISTEN_SERVICE_STATE) != 0) {
            listener.onServiceStateChanged(getServiceState());
        }
        if ((events & PhoneStateListener.LISTEN_SIGNAL_STRENGTH) != 0) {
            listener.onSignalStrengthChanged(99);
        }
        if ((events & PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR) != 0) {
            listener.onMessageWaitingIndicatorChanged(false);
        }
        if ((events & PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR) != 0) {
            listener.onCallForwardingIndicatorChanged(false);
        }
        if ((events & PhoneStateListener.LISTEN_CELL_LOCATION) != 0) {
            listener.onCellLocationChanged(CellLocation.getEmpty());
        }
        if ((events & PhoneStateListener.LISTEN_CALL_STATE) != 0) {
            listener.onCallStateChanged(CALL_STATE_IDLE, "");
        }
        if ((events & PhoneStateListener.LISTEN_DATA_CONNECTION_STATE) != 0) {
            int netType = getDataNetworkType();
            listener.onDataConnectionStateChanged(DATA_DISCONNECTED);
            listener.onDataConnectionStateChanged(DATA_DISCONNECTED, netType);
        }
        if ((events & PhoneStateListener.LISTEN_DATA_ACTIVITY) != 0) {
            listener.onDataActivity(DATA_ACTIVITY_NONE);
        }
        if ((events & PhoneStateListener.LISTEN_SIGNAL_STRENGTHS) != 0) {
            listener.onSignalStrengthsChanged(getSignalStrength());
        }
        if ((events & PhoneStateListener.LISTEN_CELL_INFO) != 0) {
            listener.onCellInfoChanged(Collections.<CellInfo>emptyList());
        }
        if ((events & PhoneStateListener.LISTEN_PRECISE_DATA_CONNECTION_STATE) != 0) {
            listener.onPreciseDataConnectionStateChanged(new PreciseDataConnectionState());
        }
        if ((events & PhoneStateListener.LISTEN_USER_MOBILE_DATA_STATE) != 0) {
            listener.onUserMobileDataStateChanged(mDataEnabled);
        }
        if ((events & PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED) != 0) {
            listener.onDisplayInfoChanged(new TelephonyDisplayInfo(
                    getDataNetworkType(), TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE, false));
        }
        if ((events & PhoneStateListener.LISTEN_ACTIVE_DATA_SUBSCRIPTION_ID_CHANGE) != 0) {
            listener.onActiveDataSubscriptionIdChanged(
                    SubscriptionManager.getActiveDataSubscriptionId());
        }
        if ((events & PhoneStateListener.LISTEN_EMERGENCY_NUMBER_LIST) != 0) {
            listener.onEmergencyNumberListChanged(getEmergencyNumberList());
        }
        if ((events & PhoneStateListener.LISTEN_BARRING_INFO) != 0) {
            listener.onBarringInfoChanged(new BarringInfo());
        }
    }

    @Deprecated
    public boolean isVoiceCapable() {
        return false;
    }

    public boolean isDeviceVoiceCapable() {
        return false;
    }

    @Deprecated
    public boolean isSmsCapable() {
        return false;
    }

    public boolean isDeviceSmsCapable() {
        return false;
    }

    public List<CellInfo> getAllCellInfo() {
        return Collections.emptyList();
    }

    public void requestCellInfoUpdate(Executor executor, final CellInfoCallback callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        executor.execute(new Runnable() {
            public void run() {
                callback.onCellInfo(Collections.<CellInfo>emptyList());
            }
        });
    }

    public String getMmsUserAgent() {
        return "";
    }

    public String getMmsUAProfUrl() {
        return "";
    }

    public IccOpenLogicalChannelResponse iccOpenLogicalChannel(String AID) {
        return new IccOpenLogicalChannelResponse(
                IccOpenLogicalChannelResponse.INVALID_CHANNEL,
                IccOpenLogicalChannelResponse.STATUS_MISSING_RESOURCE,
                null);
    }

    public IccOpenLogicalChannelResponse iccOpenLogicalChannel(String AID, int p2) {
        return iccOpenLogicalChannel(AID);
    }

    public boolean iccCloseLogicalChannel(int channel) {
        return false;
    }

    public String iccTransmitApduLogicalChannel(
            int channel, int cla, int instruction, int p1, int p2, int p3, String data) {
        return "";
    }

    public String iccTransmitApduBasicChannel(
            int cla, int instruction, int p1, int p2, int p3, String data) {
        return "";
    }

    public byte[] iccExchangeSimIO(
            int fileID, int command, int p1, int p2, int p3, String filePath) {
        return null;
    }

    public String sendEnvelopeWithStatus(String content) {
        return "";
    }

    public void rebootModem() {
    }

    public int getSubscriptionId() {
        return mSubId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID
                ? SubscriptionManager.INVALID_SUBSCRIPTION_ID : mSubId;
    }

    public String getIccAuthentication(int appType, int authType, String data) {
        return null;
    }

    public String[] getForbiddenPlmns() {
        return new String[0];
    }

    public int setForbiddenPlmns(List<String> fplmns) {
        Objects.requireNonNull(fplmns, "fplmns");
        return 0;
    }

    public void setNetworkSelectionModeAutomatic() {
    }

    public NetworkScan requestNetworkScan(
            NetworkScanRequest request,
            Executor executor,
            final TelephonyScanManager.NetworkScanCallback callback) {
        return requestNetworkScan(INCLUDE_LOCATION_DATA_NONE, request, executor, callback);
    }

    public NetworkScan requestNetworkScan(
            int includeLocationData,
            NetworkScanRequest request,
            Executor executor,
            final TelephonyScanManager.NetworkScanCallback callback) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        NetworkScan scan = new NetworkScan(NEXT_SCAN_ID.getAndIncrement(), getSubscriptionId());
        executor.execute(new Runnable() {
            public void run() {
                callback.onError(NetworkScan.ERROR_MODEM_UNAVAILABLE);
            }
        });
        return scan;
    }

    public boolean setNetworkSelectionModeManual(String operatorNumeric, boolean persistSelection) {
        return false;
    }

    public boolean setNetworkSelectionModeManual(
            String operatorNumeric, boolean persistSelection, int ran) {
        return false;
    }

    public int getNetworkSelectionMode() {
        return NETWORK_SELECTION_MODE_UNKNOWN;
    }

    public String getManualNetworkSelectionPlmn() {
        return "";
    }

    public void setAllowedNetworkTypesForReason(int reason, long allowedNetworkTypes) {
        synchronized (mAllowedNetworkTypes) {
            mAllowedNetworkTypes.put(Integer.valueOf(reason), Long.valueOf(allowedNetworkTypes));
        }
    }

    public long getAllowedNetworkTypesForReason(int reason) {
        synchronized (mAllowedNetworkTypes) {
            Long v = mAllowedNetworkTypes.get(Integer.valueOf(reason));
            return v != null ? v.longValue() : 0L;
        }
    }

    public boolean setPreferredNetworkTypeToGlobal() {
        return false;
    }

    public boolean hasCarrierPrivileges() {
        return false;
    }

    public boolean setOperatorBrandOverride(String brand) {
        return false;
    }

    public void setCallComposerStatus(int status) {
        mCallComposerStatus = status;
    }

    public int getCallComposerStatus() {
        return mCallComposerStatus;
    }

    public void sendUssdRequest(
            final String ussdRequest,
            final UssdResponseCallback callback,
            Handler handler) {
        Objects.requireNonNull(callback, "callback");
        final TelephonyManager self = this;
        Runnable r = new Runnable() {
            public void run() {
                callback.onReceiveUssdResponseFailed(self, ussdRequest, USSD_RETURN_FAILURE);
            }
        };
        if (handler != null) {
            handler.post(r);
        } else {
            r.run();
        }
    }

    public boolean isConcurrentVoiceAndDataSupported() {
        return false;
    }

    @Deprecated
    public void setDataEnabled(boolean enable) {
        setDataEnabledForReason(DATA_ENABLED_REASON_USER, enable);
    }

    public boolean isDataEnabled() {
        return isDataEnabledForReason(DATA_ENABLED_REASON_USER);
    }

    public boolean isDataRoamingEnabled() {
        return false;
    }

    public boolean canChangeDtmfToneLength() {
        return false;
    }

    public boolean isWorldPhone() {
        return false;
    }

    @Deprecated
    public boolean isTtyModeSupported() {
        return false;
    }

    public boolean isRttSupported() {
        return false;
    }

    public boolean isHearingAidCompatibilitySupported() {
        return false;
    }

    public PhoneAccountHandle getPhoneAccountHandle() {
        return null;
    }

    public int getSubscriptionId(PhoneAccountHandle phoneAccountHandle) {
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    public ServiceState getServiceState() {
        ServiceState ss = new ServiceState();
        ss.setStateOutOfService();
        return ss;
    }

    public ServiceState getServiceState(int includeLocationData) {
        return getServiceState();
    }

    public Uri getVoicemailRingtoneUri(PhoneAccountHandle accountHandle) {
        return mVoicemailRingtoneUri;
    }

    @Deprecated
    public void setVoicemailRingtoneUri(PhoneAccountHandle phoneAccountHandle, Uri uri) {
        mVoicemailRingtoneUri = uri;
    }

    public boolean isVoicemailVibrationEnabled(PhoneAccountHandle accountHandle) {
        return mVoicemailVibrationEnabled;
    }

    @Deprecated
    public void setVoicemailVibrationEnabled(
            PhoneAccountHandle phoneAccountHandle, boolean enabled) {
        mVoicemailVibrationEnabled = enabled;
    }

    public int getSimCarrierId() {
        return UNKNOWN_CARRIER_ID;
    }

    public CharSequence getSimCarrierIdName() {
        return null;
    }

    public int getSimSpecificCarrierId() {
        return UNKNOWN_CARRIER_ID;
    }

    public CharSequence getSimSpecificCarrierIdName() {
        return null;
    }

    public int getCarrierIdFromSimMccMnc() {
        return UNKNOWN_CARRIER_ID;
    }

    public void getCarrierRestrictionStatus(
            Executor executor, final Consumer<Integer> resultListener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(resultListener, "resultListener");
        executor.execute(new Runnable() {
            public void run() {
                resultListener.accept(Integer.valueOf(CARRIER_RESTRICTION_STATUS_UNKNOWN));
            }
        });
    }

    public void setDataEnabledForReason(int reason, boolean enabled) {
        synchronized (mDataEnabledReasons) {
            mDataEnabledReasons.put(Integer.valueOf(reason), Boolean.valueOf(enabled));
            if (reason == DATA_ENABLED_REASON_USER) {
                mDataEnabled = enabled;
            }
        }
    }

    public boolean isDataEnabledForReason(int reason) {
        synchronized (mDataEnabledReasons) {
            Boolean b = mDataEnabledReasons.get(Integer.valueOf(reason));
            return b != null ? b.booleanValue() : false;
        }
    }

    public boolean isManualNetworkSelectionAllowed() {
        return false;
    }

    public SignalStrength getSignalStrength() {
        return new SignalStrength();
    }

    public boolean isDataConnectionAllowed() {
        return false;
    }

    public boolean isDataCapable() {
        return false;
    }

    public long getSupportedRadioAccessFamily() {
        return NETWORK_TYPE_BITMASK_UNKNOWN;
    }

    public Map<Integer, List<EmergencyNumber>> getEmergencyNumberList() {
        return Collections.emptyMap();
    }

    public Map<Integer, List<EmergencyNumber>> getEmergencyNumberList(int categories) {
        return Collections.emptyMap();
    }

    public boolean isEmergencyNumber(String number) {
        return PhoneNumberUtils.isEmergencyNumber(number);
    }

    public void setPreferredOpportunisticDataSubscription(
            int subId, boolean needValidation, Executor executor,
            final Consumer<Integer> callback) {
        if (executor != null && callback != null) {
            executor.execute(new Runnable() {
                public void run() {
                    callback.accept(Integer.valueOf(SET_OPPORTUNISTIC_SUB_REMOTE_SERVICE_EXCEPTION));
                }
            });
        }
    }

    public int getPreferredOpportunisticDataSubscription() {
        return SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
    }

    public void updateAvailableNetworks(
            List<AvailableNetworkInfo> availableNetworks,
            Executor executor,
            final Consumer<Integer> callback) {
        Objects.requireNonNull(availableNetworks, "availableNetworks");
        if (executor != null && callback != null) {
            executor.execute(new Runnable() {
                public void run() {
                    callback.accept(Integer.valueOf(
                            UPDATE_AVAILABLE_NETWORKS_SERVICE_IS_DISABLED));
                }
            });
        }
    }

    public boolean isModemEnabledForSlot(int slotIndex) {
        return false;
    }

    public int isMultiSimSupported() {
        return MULTISIM_NOT_SUPPORTED_BY_HARDWARE;
    }

    public void switchMultiSimConfig(int numOfSims) {
    }

    public boolean doesSwitchMultiSimConfigTriggerReboot() {
        return false;
    }

    public List<String> getEquivalentHomePlmns() {
        return Collections.emptyList();
    }

    public boolean isRadioInterfaceCapabilitySupported(String capabilityName) {
        Objects.requireNonNull(capabilityName, "capabilityName cannot be null");
        return false;
    }

    public void registerTelephonyCallback(Executor executor, TelephonyCallback callback) {
        registerTelephonyCallback(INCLUDE_LOCATION_DATA_NONE, executor, callback);
    }

    public void registerTelephonyCallback(
            int includeLocationData, Executor executor, final TelephonyCallback callback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        final Object token = new Object();
        synchronized (telephonyCallbacks) {
            telephonyCallbacks.put(callback, token);
        }
        executor.execute(new Runnable() {
            public void run() {
                synchronized (telephonyCallbacks) {
                    if (telephonyCallbacks.get(callback) != token) {
                        return;
                    }
                }
                dispatchTelephonyCallback(callback);
            }
        });
    }

    public void unregisterTelephonyCallback(TelephonyCallback callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
        synchronized (telephonyCallbacks) {
            telephonyCallbacks.remove(callback);
        }
    }

    private void dispatchTelephonyCallback(TelephonyCallback cb) {
        if (cb instanceof TelephonyCallback.CallStateListener) {
            ((TelephonyCallback.CallStateListener) cb).onCallStateChanged(CALL_STATE_IDLE);
        }
        if (cb instanceof TelephonyCallback.ServiceStateListener) {
            ((TelephonyCallback.ServiceStateListener) cb).onServiceStateChanged(getServiceState());
        }
        if (cb instanceof TelephonyCallback.SignalStrengthsListener) {
            ((TelephonyCallback.SignalStrengthsListener) cb)
                    .onSignalStrengthsChanged(getSignalStrength());
        }
        if (cb instanceof TelephonyCallback.DataConnectionStateListener) {
            ((TelephonyCallback.DataConnectionStateListener) cb)
                    .onDataConnectionStateChanged(DATA_DISCONNECTED, getDataNetworkType());
        }
        if (cb instanceof TelephonyCallback.DataActivityListener) {
            ((TelephonyCallback.DataActivityListener) cb).onDataActivity(DATA_ACTIVITY_NONE);
        }
        if (cb instanceof TelephonyCallback.CellInfoListener) {
            ((TelephonyCallback.CellInfoListener) cb)
                    .onCellInfoChanged(Collections.<CellInfo>emptyList());
        }
        if (cb instanceof TelephonyCallback.DisplayInfoListener) {
            ((TelephonyCallback.DisplayInfoListener) cb).onDisplayInfoChanged(
                    new TelephonyDisplayInfo(
                            getDataNetworkType(),
                            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
                            false));
        }
        if (cb instanceof TelephonyCallback.ActiveDataSubscriptionIdListener) {
            ((TelephonyCallback.ActiveDataSubscriptionIdListener) cb)
                    .onActiveDataSubscriptionIdChanged(
                            SubscriptionManager.getActiveDataSubscriptionId());
        }
        if (cb instanceof TelephonyCallback.UserMobileDataStateListener) {
            ((TelephonyCallback.UserMobileDataStateListener) cb)
                    .onUserMobileDataStateChanged(mDataEnabled);
        }
        if (cb instanceof TelephonyCallback.BarringInfoListener) {
            ((TelephonyCallback.BarringInfoListener) cb)
                    .onBarringInfoChanged(new BarringInfo());
        }
    }

    public void setSignalStrengthUpdateRequest(SignalStrengthUpdateRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
    }

    public void clearSignalStrengthUpdateRequest(SignalStrengthUpdateRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
    }

    public void getNetworkSlicingConfiguration(
            Executor executor,
            final OutcomeReceiver<NetworkSlicingConfig, NetworkSlicingException> callback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        executor.execute(new Runnable() {
            public void run() {
                callback.onError(new ModemErrorException());
            }
        });
    }

    public boolean isPremiumCapabilityAvailableForPurchase(int capability) {
        return false;
    }

    public void purchasePremiumCapability(
            int capability, Executor executor, final Consumer<Integer> callback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        executor.execute(new Runnable() {
            public void run() {
                callback.accept(Integer.valueOf(
                        PURCHASE_PREMIUM_CAPABILITY_RESULT_FEATURE_NOT_SUPPORTED));
            }
        });
    }

    public static class CallComposerException extends Exception {
        public static final int ERROR_AUTHENTICATION_FAILED = 3;
        public static final int ERROR_FILE_TOO_LARGE = 2;
        public static final int ERROR_INPUT_CLOSED = 4;
        public static final int ERROR_IO_EXCEPTION = 5;
        public static final int ERROR_NETWORK_UNAVAILABLE = 6;
        public static final int ERROR_REMOTE_END_CLOSED = 1;
        public static final int ERROR_UNKNOWN = 0;

        private final int mErrorCode;
        private final IOException mIOException;

        public CallComposerException(int errorCode, IOException ioException) {
            super("CallComposer error: " + errorCode, ioException);
            mErrorCode = errorCode;
            mIOException = ioException;
        }

        public int getErrorCode() {
            return mErrorCode;
        }

        public IOException getIOException() {
            return mIOException;
        }
    }

    public abstract static class CellInfoCallback {
        public static final int ERROR_MODEM_ERROR = 2;
        public static final int ERROR_TIMEOUT = 1;

        public CellInfoCallback() {
        }

        public abstract void onCellInfo(List<CellInfo> cellInfo);

        public void onError(int errorCode, Throwable detail) {
            onCellInfo(new ArrayList<CellInfo>());
        }
    }

    public class NetworkSlicingException extends Exception {
        NetworkSlicingException() {
            super();
        }

        NetworkSlicingException(String message) {
            super(message);
        }

        public String toString() {
            return getClass().getSimpleName() + ": " + getMessage();
        }
    }

    public class ModemErrorException extends NetworkSlicingException {
        ModemErrorException() {
            super("Modem error");
        }
    }

    public class TimeoutException extends NetworkSlicingException {
        TimeoutException() {
            super("Timeout");
        }
    }

    public abstract static class UssdResponseCallback {
        public UssdResponseCallback() {
        }

        public void onReceiveUssdResponse(
                TelephonyManager telephonyManager, String request, CharSequence response) {
        }

        public void onReceiveUssdResponseFailed(
                TelephonyManager telephonyManager, String request, int failureCode) {
        }
    }
}
