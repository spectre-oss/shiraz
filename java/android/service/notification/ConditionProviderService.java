// SPDX-License-Identifier: GPL-3.0-only
package android.service.notification;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;

public abstract class ConditionProviderService extends Service {
    public static final String SERVICE_INTERFACE =
            "android.service.notification.ConditionProviderService";
    public static final String META_DATA_RULE_TYPE =
            "android.service.zen.automatic.ruleType";
    public static final String META_DATA_CONFIGURATION_ACTIVITY =
            "android.service.zen.automatic.configurationActivity";
    public static final String META_DATA_RULE_INSTANCE_LIMIT =
            "android.service.zen.automatic.ruleInstanceLimit";
    public static final String EXTRA_RULE_ID =
            "android.service.notification.extra.RULE_ID";

    private final IBinder mBinder = new Binder();

    public ConditionProviderService() {
    }

    public abstract void onConnected();

    public void onRequestConditions(int relevance) {
    }

    public abstract void onSubscribe(Uri conditionId);

    public abstract void onUnsubscribe(Uri conditionId);

    public static final void requestRebind(ComponentName componentName) {
    }

    public final void requestUnbind() {
    }

    public final void notifyCondition(Condition condition) {
    }

    public final void notifyConditions(Condition... conditions) {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
