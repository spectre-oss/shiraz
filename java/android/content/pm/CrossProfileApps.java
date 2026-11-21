// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** System service inspecting and launching activities across personal/work user profiles. */
public class CrossProfileApps {
    public static final String ACTION_CAN_INTERACT_ACROSS_PROFILES_CHANGED =
            "android.content.pm.action.CAN_INTERACT_ACROSS_PROFILES_CHANGED";

    private final Context mContext;

    public CrossProfileApps() {
        this(null);
    }

    public CrossProfileApps(Context context) {
        mContext = context;
    }

    public void startMainActivity(ComponentName component, UserHandle targetUser) {
        startMainActivity(component, targetUser, null, null);
    }

    public void startMainActivity(
            ComponentName component, UserHandle targetUser, Activity callingActivity, Bundle options) {
        Objects.requireNonNull(component);
        Objects.requireNonNull(targetUser);
        throw new SecurityException("Target user profile is not accessible: " + targetUser);
    }

    public void startActivity(Intent intent, UserHandle targetUser, Activity callingActivity) {
        startActivity(intent, targetUser, callingActivity, null);
    }

    public void startActivity(
            Intent intent, UserHandle targetUser, Activity callingActivity, Bundle options) {
        Objects.requireNonNull(intent);
        Objects.requireNonNull(targetUser);
        throw new SecurityException("Target user profile is not accessible: " + targetUser);
    }

    public List<UserHandle> getTargetUserProfiles() {
        return Collections.emptyList();
    }

    public boolean isProfile(UserHandle userHandle) {
        Objects.requireNonNull(userHandle);
        return false;
    }

    public boolean isManagedProfile(UserHandle userHandle) {
        Objects.requireNonNull(userHandle);
        return false;
    }

    public CharSequence getProfileSwitchingLabel(UserHandle userHandle) {
        Objects.requireNonNull(userHandle);
        throw new SecurityException("Invalid target user profile: " + userHandle);
    }

    public Drawable getProfileSwitchingIconDrawable(UserHandle userHandle) {
        Objects.requireNonNull(userHandle);
        if (!getTargetUserProfiles().contains(userHandle)) {
            throw new SecurityException("Invalid target user profile: " + userHandle);
        }
        return new ColorDrawable(0);
    }

    public boolean canRequestInteractAcrossProfiles() {
        return false;
    }

    public boolean canInteractAcrossProfiles() {
        return false;
    }

    public Intent createRequestInteractAcrossProfilesIntent() {
        if (!canRequestInteractAcrossProfiles()) {
            throw new SecurityException("Caller cannot request interact across profiles");
        }
        return new Intent("android.settings.MANAGE_CROSS_PROFILE_ACCESS");
    }
}
