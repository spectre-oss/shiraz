// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.cardemulation;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import java.util.ArrayList;
import java.util.List;

public abstract class HostApduService extends Service {
    public static final String SERVICE_INTERFACE =
            "android.nfc.cardemulation.action.HOST_APDU_SERVICE";
    public static final String SERVICE_META_DATA =
            "android.nfc.cardemulation.host_apdu_service";

    public static final int DEACTIVATION_LINK_LOSS = 0;
    public static final int DEACTIVATION_DESELECTED = 1;

    /** @hide */
    public static final int MSG_COMMAND_APDU = 0;
    /** @hide */
    public static final int MSG_RESPONSE_APDU = 1;
    /** @hide */
    public static final int MSG_DEACTIVATED = 2;
    /** @hide */
    public static final int MSG_UNHANDLED = 3;
    /** @hide */
    public static final int MSG_POLLING_LOOP = 4;

    /** @hide */
    public static final String KEY_DATA = "data";
    /** @hide */
    public static final String KEY_POLLING_LOOP_FRAMES_BUNDLE =
            "android.nfc.cardemulation.POLLING_FRAMES";

    private Messenger mMessenger;
    Messenger mNfcService;

    public HostApduService() {
    }

    final class MsgHandler extends Handler {
        MsgHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_COMMAND_APDU: {
                    Bundle dataBundle = msg.getData();
                    if (dataBundle == null) {
                        return;
                    }
                    if (mNfcService == null) {
                        mNfcService = msg.replyTo;
                    }
                    byte[] apdu = dataBundle.getByteArray(KEY_DATA);
                    if (apdu != null) {
                        byte[] responseApdu = processCommandApdu(apdu, null);
                        if (responseApdu != null && mNfcService != null) {
                            sendResponseApdu(responseApdu);
                        }
                    }
                    break;
                }
                case MSG_RESPONSE_APDU: {
                    if (mNfcService == null) {
                        return;
                    }
                    try {
                        msg.replyTo = mMessenger;
                        mNfcService.send(msg);
                    } catch (RemoteException ignored) {
                    }
                    break;
                }
                case MSG_DEACTIVATED: {
                    mNfcService = null;
                    onDeactivated(msg.arg1);
                    break;
                }
                case MSG_UNHANDLED: {
                    if (mNfcService == null) {
                        return;
                    }
                    try {
                        msg.replyTo = mMessenger;
                        mNfcService.send(msg);
                    } catch (RemoteException ignored) {
                    }
                    break;
                }
                case MSG_POLLING_LOOP: {
                    Bundle dataBundle = msg.getData();
                    if (dataBundle != null) {
                        ArrayList<PollingFrame> pollingFrames =
                                dataBundle.getParcelableArrayList(KEY_POLLING_LOOP_FRAMES_BUNDLE);
                        if (pollingFrames != null) {
                            processPollingFrames(pollingFrames);
                        }
                    }
                    break;
                }
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    }

    private synchronized Messenger ensureMessenger() {
        if (mMessenger == null) {
            Looper looper = Looper.getMainLooper();
            if (looper == null) {
                looper = Looper.myLooper();
            }
            if (looper == null) {
                Looper.prepareMainLooper();
                looper = Looper.getMainLooper();
            }
            mMessenger = new Messenger(new MsgHandler(looper));
        }
        return mMessenger;
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return ensureMessenger().getBinder();
    }

    public final void sendResponseApdu(byte[] responseApdu) {
        Message responseMsg = Message.obtain(null, MSG_RESPONSE_APDU);
        Bundle dataBundle = new Bundle();
        dataBundle.putByteArray(KEY_DATA, responseApdu);
        responseMsg.setData(dataBundle);
        try {
            if (mNfcService != null) {
                responseMsg.replyTo = ensureMessenger();
                mNfcService.send(responseMsg);
            } else {
                ensureMessenger().send(responseMsg);
            }
        } catch (RemoteException ignored) {
        }
    }

    public final void notifyUnhandled() {
        Message unhandledMsg = Message.obtain(null, MSG_UNHANDLED);
        try {
            if (mNfcService != null) {
                unhandledMsg.replyTo = ensureMessenger();
                mNfcService.send(unhandledMsg);
            } else {
                ensureMessenger().send(unhandledMsg);
            }
        } catch (RemoteException ignored) {
        }
    }

    public void processPollingFrames(List<PollingFrame> frame) {
    }

    public abstract byte[] processCommandApdu(byte[] commandApdu, Bundle extras);

    public abstract void onDeactivated(int reason);
}
