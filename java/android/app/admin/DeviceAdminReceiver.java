// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.UserHandle;

public class DeviceAdminReceiver extends BroadcastReceiver {
    public static final String ACTION_DEVICE_ADMIN_ENABLED =
            "android.app.action.DEVICE_ADMIN_ENABLED";
    public static final String ACTION_DEVICE_ADMIN_DISABLE_REQUESTED =
            "android.app.action.DEVICE_ADMIN_DISABLE_REQUESTED";
    public static final String EXTRA_DISABLE_WARNING =
            "android.app.extra.DISABLE_WARNING";
    public static final String ACTION_DEVICE_ADMIN_DISABLED =
            "android.app.action.DEVICE_ADMIN_DISABLED";
    public static final String ACTION_PASSWORD_CHANGED =
            "android.app.action.ACTION_PASSWORD_CHANGED";
    public static final String ACTION_PASSWORD_FAILED =
            "android.app.action.ACTION_PASSWORD_FAILED";
    public static final String ACTION_PASSWORD_SUCCEEDED =
            "android.app.action.ACTION_PASSWORD_SUCCEEDED";
    public static final String ACTION_PASSWORD_EXPIRING =
            "android.app.action.ACTION_PASSWORD_EXPIRING";
    public static final String ACTION_PROFILE_PROVISIONING_COMPLETE =
            "android.app.action.PROFILE_PROVISIONING_COMPLETE";
    public static final String ACTION_LOCK_TASK_ENTERING =
            "android.app.action.LOCK_TASK_ENTERING";
    public static final String EXTRA_LOCK_TASK_PACKAGE =
            "android.app.extra.LOCK_TASK_PACKAGE";
    public static final String ACTION_LOCK_TASK_EXITING =
            "android.app.action.LOCK_TASK_EXITING";
    public static final String ACTION_CHOOSE_PRIVATE_KEY_ALIAS =
            "android.app.action.CHOOSE_PRIVATE_KEY_ALIAS";
    public static final String ACTION_SECURITY_LOGS_AVAILABLE =
            "android.app.action.SECURITY_LOGS_AVAILABLE";
    public static final String ACTION_NETWORK_LOGS_AVAILABLE =
            "android.app.action.NETWORK_LOGS_AVAILABLE";
    public static final String EXTRA_TRANSFER_OWNERSHIP_ADMIN_EXTRAS_BUNDLE =
            "android.app.extra.TRANSFER_OWNERSHIP_ADMIN_EXTRAS_BUNDLE";

    public static final int BUGREPORT_FAILURE_FAILED_COMPLETING = 0;
    public static final int BUGREPORT_FAILURE_FILE_NO_LONGER_AVAILABLE = 1;

    public static final String DEVICE_ADMIN_META_DATA = "android.app.device_admin";

    private DevicePolicyManager mManager;
    private ComponentName mWho;

    public DeviceAdminReceiver() {
    }

    public DevicePolicyManager getManager(Context context) {
        if (mManager == null && context != null) {
            mManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        }
        return mManager;
    }

    public ComponentName getWho(Context context) {
        if (mWho == null && context != null) {
            mWho = new ComponentName(context, getClass());
        }
        return mWho;
    }

    public void onEnabled(Context context, Intent intent) {
    }

    public CharSequence onDisableRequested(Context context, Intent intent) {
        return null;
    }

    public void onDisabled(Context context, Intent intent) {
    }

    @Deprecated
    public void onPasswordChanged(Context context, Intent intent) {
    }

    public void onPasswordChanged(Context context, Intent intent, UserHandle user) {
        onPasswordChanged(context, intent);
    }

    @Deprecated
    public void onPasswordFailed(Context context, Intent intent) {
    }

    public void onPasswordFailed(Context context, Intent intent, UserHandle user) {
        onPasswordFailed(context, intent);
    }

    @Deprecated
    public void onPasswordSucceeded(Context context, Intent intent) {
    }

    public void onPasswordSucceeded(Context context, Intent intent, UserHandle user) {
        onPasswordSucceeded(context, intent);
    }

    @Deprecated
    public void onPasswordExpiring(Context context, Intent intent) {
    }

    public void onPasswordExpiring(Context context, Intent intent, UserHandle user) {
        onPasswordExpiring(context, intent);
    }

    public void onProfileProvisioningComplete(Context context, Intent intent) {
    }

    @Deprecated
    public void onReadyForUserInitialization(Context context, Intent intent) {
    }

    public void onLockTaskModeEntering(Context context, Intent intent, String pkg) {
    }

    public void onLockTaskModeExiting(Context context, Intent intent) {
    }

    public String onChoosePrivateKeyAlias(
            Context context, Intent intent, int uid, Uri uri, String alias) {
        return null;
    }

    public void onSystemUpdatePending(Context context, Intent intent, long receivedTime) {
    }

    public void onBugreportSharingDeclined(Context context, Intent intent) {
    }

    public void onBugreportShared(Context context, Intent intent, String bugreportHash) {
    }

    public void onBugreportFailed(Context context, Intent intent, int failureCode) {
    }

    public void onSecurityLogsAvailable(Context context, Intent intent) {
    }

    public void onNetworkLogsAvailable(
            Context context, Intent intent, long batchToken, int networkLogsCount) {
    }

    public void onUserAdded(Context context, Intent intent, UserHandle addedUser) {
    }

    public void onUserRemoved(Context context, Intent intent, UserHandle removedUser) {
    }

    public void onUserStarted(Context context, Intent intent, UserHandle startedUser) {
    }

    public void onUserStopped(Context context, Intent intent, UserHandle stoppedUser) {
    }

    public void onUserSwitched(Context context, Intent intent, UserHandle switchedUser) {
    }

    public void onTransferOwnershipComplete(Context context, PersistableBundle bundle) {
    }

    public void onTransferAffiliatedProfileOwnershipComplete(Context context, UserHandle user) {
    }

    public void onOperationSafetyStateChanged(Context context, int reason, boolean isSafe) {
    }

    public void onComplianceAcknowledgementRequired(Context context, Intent intent) {
        DevicePolicyManager dpm = getManager(context);
        if (dpm != null) {
            dpm.acknowledgeDeviceCompliant();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_PASSWORD_CHANGED.equals(action)) {
            onPasswordChanged(context, intent, UserHandle.of(UserHandle.myUserId()));
        } else if (ACTION_PASSWORD_FAILED.equals(action)) {
            onPasswordFailed(context, intent, UserHandle.of(UserHandle.myUserId()));
        } else if (ACTION_PASSWORD_SUCCEEDED.equals(action)) {
            onPasswordSucceeded(context, intent, UserHandle.of(UserHandle.myUserId()));
        } else if (ACTION_DEVICE_ADMIN_ENABLED.equals(action)) {
            onEnabled(context, intent);
        } else if (ACTION_DEVICE_ADMIN_DISABLE_REQUESTED.equals(action)) {
            CharSequence res = onDisableRequested(context, intent);
            if (res != null) {
                getResultExtras(true).putCharSequence(EXTRA_DISABLE_WARNING, res);
            }
        } else if (ACTION_DEVICE_ADMIN_DISABLED.equals(action)) {
            onDisabled(context, intent);
        } else if (ACTION_PASSWORD_EXPIRING.equals(action)) {
            onPasswordExpiring(context, intent, UserHandle.of(UserHandle.myUserId()));
        } else if (ACTION_PROFILE_PROVISIONING_COMPLETE.equals(action)) {
            onProfileProvisioningComplete(context, intent);
        } else if (ACTION_LOCK_TASK_ENTERING.equals(action)) {
            String pkg = intent.getStringExtra(EXTRA_LOCK_TASK_PACKAGE);
            onLockTaskModeEntering(context, intent, pkg);
        } else if (ACTION_LOCK_TASK_EXITING.equals(action)) {
            onLockTaskModeExiting(context, intent);
        } else if (ACTION_SECURITY_LOGS_AVAILABLE.equals(action)) {
            onSecurityLogsAvailable(context, intent);
        }
    }
}
