// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.Objects;

public class TaskStackBuilder {
    private final ArrayList<Intent> mIntents = new ArrayList<Intent>();
    private final Context mSourceContext;

    private TaskStackBuilder(Context a) {
        mSourceContext = Objects.requireNonNull(a, "context");
    }

    public static TaskStackBuilder create(Context context) {
        return new TaskStackBuilder(context);
    }

    public TaskStackBuilder addNextIntent(Intent nextIntent) {
        mIntents.add(Objects.requireNonNull(nextIntent, "nextIntent"));
        return this;
    }

    public TaskStackBuilder addNextIntentWithParentStack(Intent nextIntent) {
        Objects.requireNonNull(nextIntent, "nextIntent");
        ComponentName target = nextIntent.getComponent();
        if (target == null && mSourceContext.getPackageManager() != null) {
            target = nextIntent.resolveActivity(mSourceContext.getPackageManager());
        }
        if (target != null) {
            addParentStack(target);
        }
        addNextIntent(nextIntent);
        return this;
    }

    public TaskStackBuilder addParentStack(Activity sourceActivity) {
        Intent parent = sourceActivity != null ? sourceActivity.getParentActivityIntent() : null;
        if (parent != null) {
            ComponentName target = parent.getComponent();
            if (target == null && mSourceContext.getPackageManager() != null) {
                target = parent.resolveActivity(mSourceContext.getPackageManager());
            }
            if (target != null) {
                addParentStack(target);
            }
            addNextIntent(parent);
        } else if (sourceActivity != null && sourceActivity.getComponentName() != null) {
            addParentStack(sourceActivity.getComponentName());
        }
        return this;
    }

    public TaskStackBuilder addParentStack(Class<?> sourceActivityClass) {
        return addParentStack(new ComponentName(mSourceContext, sourceActivityClass));
    }

    public TaskStackBuilder addParentStack(ComponentName sourceActivityName) {
        Objects.requireNonNull(sourceActivityName, "sourceActivityName");
        int insertAt = mIntents.size();
        PackageManager pm = mSourceContext.getPackageManager();
        if (pm == null) {
            return this;
        }
        try {
            ActivityInfo info = pm.getActivityInfo(sourceActivityName, 0);
            String parentActivity = info != null ? info.parentActivityName : null;
            while (parentActivity != null) {
                final ComponentName target;
                if (parentActivity.charAt(0) == '.') {
                    target = new ComponentName(sourceActivityName.getPackageName(),
                            sourceActivityName.getPackageName() + parentActivity);
                } else {
                    target = new ComponentName(sourceActivityName.getPackageName(), parentActivity);
                }
                info = pm.getActivityInfo(target, 0);
                parentActivity = info != null ? info.parentActivityName : null;
                Intent parent = parentActivity == null && insertAt == 0
                        ? Intent.makeMainActivity(target)
                        : new Intent().setComponent(target);
                mIntents.add(insertAt, parent);
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
        return this;
    }

    public int getIntentCount() {
        return mIntents.size();
    }

    public Intent editIntentAt(int index) {
        return mIntents.get(index);
    }

    public void startActivities() {
        startActivities(null);
    }

    public void startActivities(Bundle options) {
        if (mIntents.isEmpty()) {
            throw new IllegalStateException("No intents added to TaskStackBuilder; cannot startActivities");
        }
        Intent[] intents = getIntents();
        for (int i = 0; i < intents.length; i++) {
            mSourceContext.startActivity(intents[i], i == intents.length - 1 ? options : null);
        }
    }

    public PendingIntent getPendingIntent(int requestCode, int flags) {
        return getPendingIntent(requestCode, flags, null);
    }

    public PendingIntent getPendingIntent(int requestCode, int flags, Bundle options) {
        if (mIntents.isEmpty()) {
            throw new IllegalStateException("No intents added to TaskStackBuilder; cannot getPendingIntent");
        }
        Intent[] intents = getIntents();
        return PendingIntent.getActivity(mSourceContext, requestCode, intents[intents.length - 1], flags);
    }

    public Intent[] getIntents() {
        Intent[] intents = new Intent[mIntents.size()];
        if (intents.length == 0) {
            return intents;
        }
        intents[0] = new Intent(mIntents.get(0)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK | 0x00004000);
        for (int i = 1; i < intents.length; i++) {
            intents[i] = new Intent(mIntents.get(i));
        }
        return intents;
    }
}
