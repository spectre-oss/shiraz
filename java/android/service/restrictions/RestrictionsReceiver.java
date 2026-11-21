// SPDX-License-Identifier: GPL-3.0-only
package android.service.restrictions;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.RestrictionsManager;
import android.os.PersistableBundle;

public abstract class RestrictionsReceiver extends BroadcastReceiver {
    public RestrictionsReceiver() {
    }

    public abstract void onRequestPermission(Context context, String packageName,
            String requestType, String requestId, PersistableBundle request);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (RestrictionsManager.ACTION_REQUEST_PERMISSION.equals(intent.getAction())) {
            String packageName = intent.getStringExtra(RestrictionsManager.EXTRA_PACKAGE_NAME);
            String requestType = intent.getStringExtra(RestrictionsManager.EXTRA_REQUEST_TYPE);
            String requestId = intent.getStringExtra(RestrictionsManager.EXTRA_REQUEST_ID);
            PersistableBundle request = intent.getParcelableExtra(
                    RestrictionsManager.EXTRA_REQUEST_BUNDLE, PersistableBundle.class);
            onRequestPermission(context, packageName, requestType, requestId, request);
        }
    }
}
