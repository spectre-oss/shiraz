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

public abstract class HostNfcFService extends Service {
    public static final String SERVICE_INTERFACE =
            "android.nfc.cardemulation.action.HOST_NFCF_SERVICE";
    public static final String SERVICE_META_DATA =
            "android.nfc.cardemulation.host_nfcf_service";

    public static final int DEACTIVATION_LINK_LOSS = 0;

    /** @hide */
    public static final int MSG_COMMAND_PACKET = 0;
    /** @hide */
    public static final int MSG_RESPONSE_PACKET = 1;
    /** @hide */
    public static final int MSG_DEACTIVATED = 2;
    /** @hide */
    public static final String KEY_DATA = "data";

    private Messenger mMessenger;
    Messenger mNfcService;

    public HostNfcFService() {
    }

    final class MsgHandler extends Handler {
        MsgHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_COMMAND_PACKET: {
                    Bundle dataBundle = msg.getData();
                    if (dataBundle == null) {
                        return;
                    }
                    if (mNfcService == null) {
                        mNfcService = msg.replyTo;
                    }
                    byte[] packet = dataBundle.getByteArray(KEY_DATA);
                    if (packet != null) {
                        byte[] responsePacket = processNfcFPacket(packet, null);
                        if (responsePacket != null && mNfcService != null) {
                            sendResponsePacket(responsePacket);
                        }
                    }
                    break;
                }
                case MSG_RESPONSE_PACKET: {
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

    public final void sendResponsePacket(byte[] responsePacket) {
        Message responseMsg = Message.obtain(null, MSG_RESPONSE_PACKET);
        Bundle dataBundle = new Bundle();
        dataBundle.putByteArray(KEY_DATA, responsePacket);
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

    public abstract byte[] processNfcFPacket(byte[] commandPacket, Bundle extras);

    public abstract void onDeactivated(int reason);
}
