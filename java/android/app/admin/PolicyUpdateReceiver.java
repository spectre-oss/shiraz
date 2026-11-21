// SPDX-License-Identifier: GPL-3.0-only
package android.app.admin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public abstract class PolicyUpdateReceiver extends BroadcastReceiver {
    public static final String ACTION_DEVICE_POLICY_SET_RESULT =
            "android.app.admin.action.DEVICE_POLICY_SET_RESULT";
    public static final String ACTION_DEVICE_POLICY_CHANGED =
            "android.app.admin.action.DEVICE_POLICY_CHANGED";

    public static final String EXTRA_PACKAGE_NAME = "android.app.admin.extra.PACKAGE_NAME";
    public static final String EXTRA_PERMISSION_NAME = "android.app.admin.extra.PERMISSION_NAME";
    public static final String EXTRA_INTENT_FILTER = "android.app.admin.extra.INTENT_FILTER";
    public static final String EXTRA_ACCOUNT_TYPE = "android.app.admin.extra.ACCOUNT_TYPE";

    private static final String EXTRA_POLICY_KEY = "android.app.admin.extra.POLICY_KEY";
    private static final String EXTRA_POLICY_BUNDLE_KEY =
            "android.app.admin.extra.POLICY_BUNDLE_KEY";
    private static final String EXTRA_POLICY_UPDATE_RESULT_KEY =
            "android.app.admin.extra.POLICY_UPDATE_RESULT_KEY";
    private static final String EXTRA_POLICY_TARGET_USER_ID =
            "android.app.admin.extra.POLICY_TARGET_USER_ID";

    public PolicyUpdateReceiver() {
    }

    @Override
    public final void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        String policyKey = intent.getStringExtra(EXTRA_POLICY_KEY);
        if (policyKey == null) return;
        Bundle additionalParams = intent.getBundleExtra(EXTRA_POLICY_BUNDLE_KEY);
        if (additionalParams == null) additionalParams = new Bundle();
        int resultCode = intent.getIntExtra(
                EXTRA_POLICY_UPDATE_RESULT_KEY, PolicyUpdateResult.RESULT_FAILURE_UNKNOWN);
        int targetUserId = intent.getIntExtra(EXTRA_POLICY_TARGET_USER_ID, TargetUser.UNKNOWN_USER_ID);
        TargetUser targetUser = TargetUser.fromId(targetUserId);
        PolicyUpdateResult result = new PolicyUpdateResult(resultCode);
        if (ACTION_DEVICE_POLICY_SET_RESULT.equals(action)) {
            onPolicySetResult(context, policyKey, additionalParams, targetUser, result);
        } else if (ACTION_DEVICE_POLICY_CHANGED.equals(action)) {
            onPolicyChanged(context, policyKey, additionalParams, targetUser, result);
        }
    }

    public void onPolicySetResult(
            Context context,
            String policyIdentifier,
            Bundle additionalPolicyParams,
            TargetUser targetUser,
            PolicyUpdateResult policyUpdateResult) {
    }

    public void onPolicyChanged(
            Context context,
            String policyIdentifier,
            Bundle additionalPolicyParams,
            TargetUser targetUser,
            PolicyUpdateResult policyUpdateResult) {
    }
}
