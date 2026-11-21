// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import java.util.Objects;

public abstract class SettingInjectorService extends Service {
    public static final String ACTION_SERVICE_INTENT = "android.location.SettingInjectorService";
    public static final String META_DATA_NAME = "android.location.SettingInjectorService";
    public static final String ATTRIBUTES_NAME = "injected-location-setting";
    public static final String ACTION_INJECTED_SETTING_CHANGED =
            "android.location.InjectedSettingChanged";

    private static final String MESSENGER_KEY = "messenger";
    private static final String ENABLED_KEY = "enabled";
    private static final String SUMMARY_KEY = "summary";

    private final String mName;

    public SettingInjectorService(String name) {
        mName = name;
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public final void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
    }

    @Override
    public final int onStartCommand(Intent intent, int flags, int startId) {
        onHandleIntent(intent);
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    private void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        boolean enabled = false;
        String summary = null;
        try {
            enabled = onGetEnabled();
            summary = onGetSummary();
        } finally {
            Messenger messenger = intent.getParcelableExtra(MESSENGER_KEY);
            if (messenger != null) {
                Message message = Message.obtain();
                Bundle bundle = new Bundle();
                bundle.putBoolean(ENABLED_KEY, enabled);
                bundle.putString(SUMMARY_KEY, summary);
                message.setData(bundle);
                try {
                    messenger.send(message);
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    @Deprecated
    protected abstract String onGetSummary();

    protected abstract boolean onGetEnabled();

    public static final void refreshSettings(Context context) {
        Objects.requireNonNull(context, "context must not be null");
        Intent intent = new Intent(ACTION_INJECTED_SETTING_CHANGED);
        context.sendBroadcast(intent);
    }
}
