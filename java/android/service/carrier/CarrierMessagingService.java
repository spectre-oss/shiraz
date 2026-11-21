// SPDX-License-Identifier: GPL-3.0-only
package android.service.carrier;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import java.util.List;

public abstract class CarrierMessagingService extends Service {
    public static final String SERVICE_INTERFACE =
            "android.service.carrier.CarrierMessagingService";
    public static final int RECEIVE_OPTIONS_DEFAULT = 0;
    public static final int RECEIVE_OPTIONS_DROP = 1;
    public static final int RECEIVE_OPTIONS_SKIP_NOTIFY_WHEN_CREDENTIAL_PROTECTED_STORAGE_UNAVAILABLE = 2;
    public static final int SEND_FLAG_REQUEST_DELIVERY_STATUS = 1;
    public static final int SEND_STATUS_OK = 0;
    public static final int SEND_STATUS_RETRY_ON_CARRIER_NETWORK = 1;
    public static final int SEND_STATUS_ERROR = 2;
    public static final int DOWNLOAD_STATUS_OK = 0;
    public static final int DOWNLOAD_STATUS_RETRY_ON_CARRIER_NETWORK = 1;
    public static final int DOWNLOAD_STATUS_ERROR = 2;

    private final IBinder mBinder = new Binder();

    public CarrierMessagingService() {
    }

    public void onFilterSms(MessagePdu pdu, String format, int destPort, int subId,
            ResultCallback<Boolean> callback) {
        try {
            callback.onReceiveResult(Boolean.TRUE);
        } catch (RemoteException ignored) {
        }
    }

    public void onReceiveTextSms(MessagePdu pdu, String format, int destPort, int subId,
            ResultCallback<Integer> callback) {
        onFilterSms(pdu, format, destPort, subId, new ResultCallback<Boolean>() {
            @Override
            public void onReceiveResult(Boolean shouldAllow) throws RemoteException {
                callback.onReceiveResult(Integer.valueOf(
                        Boolean.TRUE.equals(shouldAllow) ? RECEIVE_OPTIONS_DEFAULT : RECEIVE_OPTIONS_DROP));
            }
        });
    }

    public void onSendTextSms(String text, int subId, String destAddress,
            ResultCallback<SendSmsResult> callback) {
        try {
            callback.onReceiveResult(new SendSmsResult(SEND_STATUS_RETRY_ON_CARRIER_NETWORK, 0));
        } catch (RemoteException ignored) {
        }
    }

    public void onSendTextSms(String text, int subId, String destAddress, int sendSmsFlag,
            ResultCallback<SendSmsResult> callback) {
        onSendTextSms(text, subId, destAddress, callback);
    }

    public void onSendDataSms(byte[] data, int subId, String destAddress, int destPort,
            ResultCallback<SendSmsResult> callback) {
        try {
            callback.onReceiveResult(new SendSmsResult(SEND_STATUS_RETRY_ON_CARRIER_NETWORK, 0));
        } catch (RemoteException ignored) {
        }
    }

    public void onSendDataSms(byte[] data, int subId, String destAddress, int destPort,
            int sendSmsFlag, ResultCallback<SendSmsResult> callback) {
        onSendDataSms(data, subId, destAddress, destPort, callback);
    }

    public void onSendMultipartTextSms(List<String> parts, int subId, String destAddress,
            ResultCallback<SendMultipartSmsResult> callback) {
        try {
            callback.onReceiveResult(
                    new SendMultipartSmsResult(SEND_STATUS_RETRY_ON_CARRIER_NETWORK, null));
        } catch (RemoteException ignored) {
        }
    }

    public void onSendMultipartTextSms(List<String> parts, int subId, String destAddress,
            int sendSmsFlag, ResultCallback<SendMultipartSmsResult> callback) {
        onSendMultipartTextSms(parts, subId, destAddress, callback);
    }

    public void onSendMms(Uri pduUri, int subId, Uri location,
            ResultCallback<SendMmsResult> callback) {
        try {
            callback.onReceiveResult(
                    new SendMmsResult(SEND_STATUS_RETRY_ON_CARRIER_NETWORK, null));
        } catch (RemoteException ignored) {
        }
    }

    public void onDownloadMms(Uri contentUri, int subId, Uri location,
            ResultCallback<Integer> callback) {
        try {
            callback.onReceiveResult(Integer.valueOf(DOWNLOAD_STATUS_RETRY_ON_CARRIER_NETWORK));
        } catch (RemoteException ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (!SERVICE_INTERFACE.equals(intent != null ? intent.getAction() : null)) {
            return null;
        }
        return mBinder;
    }

    public interface ResultCallback<T> {
        void onReceiveResult(T result) throws RemoteException;
    }

    public static final class SendMmsResult {
        private final int mSendStatus;
        private final byte[] mSendConfPdu;

        public SendMmsResult(int sendStatus, byte[] sendConfPdu) {
            mSendStatus = sendStatus;
            mSendConfPdu = sendConfPdu;
        }

        public int getSendStatus() {
            return mSendStatus;
        }

        public byte[] getSendConfPdu() {
            return mSendConfPdu;
        }
    }

    public static final class SendMultipartSmsResult {
        private final int mSendStatus;
        private final int[] mMessageRefs;

        public SendMultipartSmsResult(int sendStatus, int[] messageRefs) {
            mSendStatus = sendStatus;
            mMessageRefs = messageRefs;
        }

        public int[] getMessageRefs() {
            return mMessageRefs;
        }

        public int getSendStatus() {
            return mSendStatus;
        }
    }

    public static final class SendSmsResult {
        private final int mSendStatus;
        private final int mMessageRef;

        public SendSmsResult(int sendStatus, int messageRef) {
            mSendStatus = sendStatus;
            mMessageRef = messageRef;
        }

        public int getMessageRef() {
            return mMessageRef;
        }

        public int getSendStatus() {
            return mSendStatus;
        }
    }
}
