// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class DelegatedAdminReceiver extends BroadcastReceiver {
    private static final String EXTRA_CHOOSE_PRIVATE_KEY_SENDER_UID =
            "android.app.extra.CHOOSE_PRIVATE_KEY_SENDER_UID";
    private static final String EXTRA_CHOOSE_PRIVATE_KEY_URI =
            "android.app.extra.CHOOSE_PRIVATE_KEY_URI";
    private static final String EXTRA_CHOOSE_PRIVATE_KEY_ALIAS =
            "android.app.extra.CHOOSE_PRIVATE_KEY_ALIAS";
    private static final String EXTRA_NETWORK_LOGS_TOKEN =
            "android.app.extra.EXTRA_NETWORK_LOGS_TOKEN";
    private static final String EXTRA_NETWORK_LOGS_COUNT =
            "android.app.extra.EXTRA_NETWORK_LOGS_COUNT";

    public DelegatedAdminReceiver() {
    }

    public String onChoosePrivateKeyAlias(
            Context context, Intent intent, int uid, Uri uri, String alias) {
        throw new UnsupportedOperationException("onChoosePrivateKeyAlias should be implemented");
    }

    public void onNetworkLogsAvailable(
            Context context, Intent intent, long batchToken, int networkLogsCount) {
        throw new UnsupportedOperationException("onNetworkLogsAvailable should be implemented");
    }

    public void onSecurityLogsAvailable(Context context, Intent intent) {
        throw new UnsupportedOperationException("onSecurityLogsAvailable should be implemented");
    }

    @Override
    public final void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (DeviceAdminReceiver.ACTION_CHOOSE_PRIVATE_KEY_ALIAS.equals(action)) {
            int uid = intent.getIntExtra(EXTRA_CHOOSE_PRIVATE_KEY_SENDER_UID, -1);
            Uri uri = intent.getParcelableExtra(EXTRA_CHOOSE_PRIVATE_KEY_URI);
            String alias = intent.getStringExtra(EXTRA_CHOOSE_PRIVATE_KEY_ALIAS);
            String chosen = onChoosePrivateKeyAlias(context, intent, uid, uri, alias);
            setResultData(chosen);
        } else if (DeviceAdminReceiver.ACTION_NETWORK_LOGS_AVAILABLE.equals(action)) {
            long token = intent.getLongExtra(EXTRA_NETWORK_LOGS_TOKEN, -1L);
            int count = intent.getIntExtra(EXTRA_NETWORK_LOGS_COUNT, 0);
            onNetworkLogsAvailable(context, intent, token, count);
        } else if (DeviceAdminReceiver.ACTION_SECURITY_LOGS_AVAILABLE.equals(action)) {
            onSecurityLogsAvailable(context, intent);
        }
    }
}
