// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

/** Serial service work on a dedicated looper, stopped after the latest start. */
public abstract class IntentService extends Service {
    private final String name;
    private boolean redelivery;
    private volatile Looper workerLooper;
    private volatile Handler workerHandler;

    public IntentService(String name) { this.name = name; }

    public void setIntentRedelivery(boolean enabled) {
        redelivery = enabled;
    }

    @Override public void onCreate() {
        super.onCreate();
        HandlerThread worker = new HandlerThread("IntentService[" + name + "]");
        worker.start();
        workerLooper = worker.getLooper();
        workerHandler = new Handler(workerLooper) {
            @Override public void handleMessage(Message message) {
                onHandleIntent((Intent) message.obj);
                stopSelf(message.arg1);
            }
        };
    }

    @Override public void onStart(Intent intent, int startId) {
        Handler handler = workerHandler;
        if (handler == null) throw new IllegalStateException("IntentService is not created");
        Message message = handler.obtainMessage();
        message.arg1 = startId;
        message.obj = intent;
        if (!handler.sendMessage(message))
            throw new IllegalStateException("IntentService worker has stopped");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        onStart(intent, startId);
        return redelivery ? START_REDELIVER_INTENT : START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        Looper looper = workerLooper;
        if (looper != null) looper.quit();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    protected abstract void onHandleIntent(Intent intent);
}
