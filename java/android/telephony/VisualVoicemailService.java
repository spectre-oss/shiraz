// SPDX-License-Identifier: GPL-3.0-only
package android.telephony;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.telecom.PhoneAccountHandle;
import java.util.Objects;

public abstract class VisualVoicemailService extends Service {
    public static final String SERVICE_INTERFACE = "android.telephony.VisualVoicemailService";

    static final int MSG_ON_CELL_SERVICE_CONNECTED = 1;
    static final int MSG_ON_SMS_RECEIVED = 2;
    static final int MSG_ON_SIM_REMOVED = 3;
    static final int MSG_TASK_ENDED = 4;
    static final int MSG_TASK_STOPPED = 5;

    static final String DATA_PHONE_ACCOUNT_HANDLE = "data_phone_account_handle";
    static final String DATA_SMS = "data_sms";

    private final Messenger mMessenger = new Messenger(new Handler(Looper.getMainLooper()) {
        public void handleMessage(Message msg) {
            PhoneAccountHandle handle = msg.getData().getParcelable(DATA_PHONE_ACCOUNT_HANDLE);
            VisualVoicemailTask task = new VisualVoicemailTask(msg.replyTo, msg.arg1);
            switch (msg.what) {
                case MSG_ON_CELL_SERVICE_CONNECTED:
                    onCellServiceConnected(task, handle);
                    break;
                case MSG_ON_SMS_RECEIVED:
                    VisualVoicemailSms sms = msg.getData().getParcelable(DATA_SMS);
                    onSmsReceived(task, sms);
                    break;
                case MSG_ON_SIM_REMOVED:
                    onSimRemoved(task, handle);
                    break;
                case MSG_TASK_STOPPED:
                    onStopped(task);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    });

    public VisualVoicemailService() {
    }

    public IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            IBinder binder = mMessenger.getBinder();
            return binder != null ? binder : new Binder();
        }
        return null;
    }

    public abstract void onCellServiceConnected(
            VisualVoicemailTask task, PhoneAccountHandle phoneAccountHandle);

    public abstract void onSmsReceived(VisualVoicemailTask task, VisualVoicemailSms sms);

    public abstract void onSimRemoved(
            VisualVoicemailTask task, PhoneAccountHandle phoneAccountHandle);

    public abstract void onStopped(VisualVoicemailTask task);

    public static class VisualVoicemailTask {
        private final int mTaskId;
        private final Messenger mReplyTo;

        private VisualVoicemailTask(Messenger replyTo, int taskId) {
            mTaskId = taskId;
            mReplyTo = replyTo;
        }

        public final void finish() {
            if (mReplyTo == null) {
                return;
            }
            Message message = Message.obtain();
            try {
                message.what = MSG_TASK_ENDED;
                message.arg1 = mTaskId;
                mReplyTo.send(message);
            } catch (RemoteException ignored) {
            }
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof VisualVoicemailTask)) {
                return false;
            }
            return mTaskId == ((VisualVoicemailTask) obj).mTaskId;
        }

        public int hashCode() {
            return Objects.hash(Integer.valueOf(mTaskId));
        }
    }
}
