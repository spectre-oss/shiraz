// SPDX-License-Identifier: GPL-3.0-only
package android.app.role;

import android.content.Intent;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class RoleManager {
    public static final String ROLE_ASSISTANT = "android.app.role.ASSISTANT";
    public static final String ROLE_BROWSER = "android.app.role.BROWSER";
    public static final String ROLE_DIALER = "android.app.role.DIALER";
    public static final String ROLE_SMS = "android.app.role.SMS";
    public static final String ROLE_EMERGENCY = "android.app.role.EMERGENCY";
    public static final String ROLE_HOME = "android.app.role.HOME";
    public static final String ROLE_CALL_REDIRECTION = "android.app.role.CALL_REDIRECTION";
    public static final String ROLE_CALL_SCREENING = "android.app.role.CALL_SCREENING";
    public static final String ROLE_NOTES = "android.app.role.NOTES";
    public static final String ROLE_WALLET = "android.app.role.WALLET";

    private static final String ACTION_REQUEST_ROLE = "android.app.role.action.REQUEST_ROLE";
    private static final Set<String> KNOWN_ROLES;

    static {
        Set<String> set = new HashSet<String>();
        set.add(ROLE_ASSISTANT);
        set.add(ROLE_BROWSER);
        set.add(ROLE_DIALER);
        set.add(ROLE_SMS);
        set.add(ROLE_EMERGENCY);
        set.add(ROLE_HOME);
        set.add(ROLE_CALL_REDIRECTION);
        set.add(ROLE_CALL_SCREENING);
        set.add(ROLE_NOTES);
        set.add(ROLE_WALLET);
        KNOWN_ROLES = Collections.unmodifiableSet(set);
    }

    private final Set<String> mHeldRoles = new HashSet<String>();

    public RoleManager() {
    }

    public Intent createRequestRoleIntent(String roleName) {
        if (roleName == null || roleName.isEmpty()) {
            throw new IllegalArgumentException("roleName cannot be null or empty");
        }
        Intent intent = new Intent(ACTION_REQUEST_ROLE);
        intent.putExtra("android.intent.extra.ROLE_NAME", roleName);
        return intent;
    }

    public boolean isRoleAvailable(String roleName) {
        if (roleName == null || roleName.isEmpty()) {
            throw new IllegalArgumentException("roleName cannot be null or empty");
        }
        return KNOWN_ROLES.contains(roleName);
    }

    public synchronized boolean isRoleHeld(String roleName) {
        if (roleName == null || roleName.isEmpty()) {
            throw new IllegalArgumentException("roleName cannot be null or empty");
        }
        return mHeldRoles.contains(roleName);
    }
}
