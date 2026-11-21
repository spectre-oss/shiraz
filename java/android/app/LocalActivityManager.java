// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.Window;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Embedded activity container used by legacy ActivityGroup and TabHost. */
@Deprecated
public class LocalActivityManager {
    private static final int RESTORED = 1;
    private static final int CREATED = 2;
    private static final int STARTED = 3;
    private static final int RESUMED = 4;
    private static final int DESTROYED = 5;

    private static final class LocalActivityRecord {
        final String id;
        Intent intent;
        Bundle instanceState;
        Activity activity;
        Window window;
        int curState = RESTORED;

        LocalActivityRecord(String id, Intent intent) {
            this.id = id;
            this.intent = intent;
        }
    }

    private final Activity mParent;
    private final boolean mSingleMode;
    private final LinkedHashMap<String, LocalActivityRecord> mActivities =
            new LinkedHashMap<String, LocalActivityRecord>();
    private LocalActivityRecord mResumed;
    private int mCurState = CREATED;
    private boolean mFinishing;

    public LocalActivityManager(Activity parent, boolean singleMode) {
        this.mParent = parent;
        this.mSingleMode = singleMode;
    }

    private static void attachContext(Activity activity, Context base) {
        if (base == null || activity.getBaseContext() != null) {
            return;
        }
        try {
            Method m = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            m.setAccessible(true);
            m.invoke(activity, base);
        } catch (Exception ignored) {
        }
    }

    private Activity instantiateActivity(Intent intent) {
        ComponentName cn = intent != null ? intent.getComponent() : null;
        if (cn == null && intent != null && mParent != null) {
            PackageManager pm = mParent.getPackageManager();
            if (pm != null) {
                ResolveInfo ri = pm.resolveActivity(intent, 0);
                if (ri != null && ri.activityInfo != null && ri.activityInfo.name != null) {
                    String pkg = ri.activityInfo.packageName != null
                            ? ri.activityInfo.packageName
                            : mParent.getPackageName();
                    cn = new ComponentName(pkg != null ? pkg : "", ri.activityInfo.name);
                }
            }
        }
        Activity child = null;
        if (cn != null && cn.getClassName() != null && !cn.getClassName().isEmpty()) {
            try {
                ClassLoader cl = mParent != null ? mParent.getClassLoader() : null;
                Class<?> cls = cl != null
                        ? Class.forName(cn.getClassName(), true, cl)
                        : Class.forName(cn.getClassName());
                if (Activity.class.isAssignableFrom(cls)) {
                    child = (Activity) cls.getDeclaredConstructor().newInstance();
                    child.shirazBindComponent(cn, cls.getName());
                }
            } catch (Exception ignored) {
            }
        }
        if (child == null) {
            child = new Activity();
        }
        attachContext(child, mParent);
        child.setIntent(intent);
        return child;
    }

    private void moveToState(LocalActivityRecord r, int desiredState) {
        if (r.curState == DESTROYED) {
            return;
        }
        if (r.activity == null) {
            r.activity = instantiateActivity(r.intent);
            r.window = r.activity.getWindow();
            r.activity.onCreate(r.instanceState);
            r.curState = CREATED;
        }
        if (r.curState < desiredState) {
            if (r.curState == CREATED && desiredState >= STARTED) {
                r.activity.onStart();
                r.curState = STARTED;
            }
            if (r.curState == STARTED && desiredState >= RESUMED) {
                r.activity.onResume();
                r.activity.onPostResume();
                r.curState = RESUMED;
            }
        } else if (r.curState > desiredState) {
            if (r.curState == RESUMED && desiredState < RESUMED) {
                r.activity.onPause();
                r.curState = STARTED;
            }
            if (r.curState == STARTED && desiredState < STARTED) {
                r.activity.onStop();
                r.curState = CREATED;
            }
        }
    }

    public Window startActivity(String id, Intent intent) {
        if (id == null) {
            throw new NullPointerException("id");
        }
        if (mCurState == RESTORED) {
            throw new IllegalStateException(
                    "Activities can't be added until the containing group has been created.");
        }
        LocalActivityRecord r = mActivities.get(id);
        boolean sameIntent = false;
        if (r != null && r.intent != null && intent != null) {
            sameIntent = r.intent.filterEquals(intent);
        }
        if (mSingleMode && mResumed != null && mResumed != r) {
            moveToState(mResumed, STARTED);
            mResumed = null;
        }
        if (r == null) {
            r = new LocalActivityRecord(id, intent);
            mActivities.put(id, r);
        } else if (!sameIntent) {
            performDestroy(r, true);
            r = new LocalActivityRecord(id, intent);
            mActivities.put(id, r);
        } else {
            r.intent = intent;
            if (r.activity != null) {
                r.activity.setIntent(intent);
                if ((intent.getFlags() & Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0) {
                    r.activity.onNewIntent(intent);
                }
            }
        }
        if (mResumed != null && mResumed != r && mCurState == RESUMED) {
            moveToState(mResumed, STARTED);
        }
        moveToState(r, mCurState);
        if (mCurState == RESUMED) {
            mResumed = r;
        }
        return r.window;
    }

    private Window performDestroy(LocalActivityRecord r, boolean finish) {
        Window win = r.window;
        if (r.curState == RESUMED && !finish) {
            r.activity.onPause();
            r.curState = STARTED;
        }
        if (r.activity != null) {
            if (r.curState == RESUMED) {
                r.activity.onPause();
                r.curState = STARTED;
            }
            if (r.curState >= STARTED) {
                r.activity.onStop();
                r.curState = CREATED;
            }
            if (!finish) {
                Bundle out = new Bundle();
                r.activity.onSaveInstanceState(out);
                r.instanceState = out;
            }
            if (finish) {
                r.activity.finish();
            }
            r.activity.onDestroy();
            r.activity.shirazSetDestroyed();
            r.activity.shirazReleaseServiceBindings();
            r.activity.shirazReleaseWindow();
        }
        r.activity = null;
        r.window = null;
        r.curState = finish ? DESTROYED : RESTORED;
        if (mResumed == r) {
            mResumed = null;
        }
        return win;
    }

    public Window destroyActivity(String id, boolean finish) {
        LocalActivityRecord r = mActivities.get(id);
        Window win = null;
        if (r != null) {
            win = performDestroy(r, finish);
            if (finish) {
                mActivities.remove(id);
            }
        }
        return win;
    }

    public Activity getCurrentActivity() {
        return mResumed != null ? mResumed.activity : null;
    }

    public String getCurrentId() {
        return mResumed != null ? mResumed.id : null;
    }

    public Activity getActivity(String id) {
        LocalActivityRecord r = mActivities.get(id);
        return r != null ? r.activity : null;
    }

    public void dispatchCreate(Bundle state) {
        if (state != null) {
            for (String id : state.keySet()) {
                Bundle astate = state.getBundle(id);
                LocalActivityRecord r = mActivities.get(id);
                if (r != null) {
                    r.instanceState = astate;
                } else {
                    r = new LocalActivityRecord(id, null);
                    r.instanceState = astate;
                    mActivities.put(id, r);
                }
            }
        }
        mCurState = CREATED;
    }

    public Bundle saveInstanceState() {
        Bundle state = null;
        for (Map.Entry<String, LocalActivityRecord> entry : mActivities.entrySet()) {
            LocalActivityRecord r = entry.getValue();
            if (state == null) {
                state = new Bundle();
            }
            if ((r.instanceState != null || r.curState == CREATED) && r.activity == null) {
                if (r.instanceState != null) {
                    state.putBundle(entry.getKey(), r.instanceState);
                }
            } else if (r.activity != null) {
                Bundle childState = new Bundle();
                r.activity.onSaveInstanceState(childState);
                r.instanceState = childState;
                state.putBundle(entry.getKey(), childState);
            }
        }
        return state;
    }

    public void dispatchResume() {
        mCurState = RESUMED;
        if (mSingleMode) {
            if (mResumed != null) {
                moveToState(mResumed, RESUMED);
            }
        } else {
            for (LocalActivityRecord r : new ArrayList<LocalActivityRecord>(mActivities.values())) {
                if (r.activity != null) {
                    moveToState(r, RESUMED);
                }
            }
        }
    }

    public void dispatchPause(boolean finishing) {
        if (finishing) {
            mFinishing = true;
        }
        mCurState = STARTED;
        if (mSingleMode) {
            if (mResumed != null) {
                moveToState(mResumed, STARTED);
            }
        } else {
            for (LocalActivityRecord r : new ArrayList<LocalActivityRecord>(mActivities.values())) {
                if (r.curState == RESUMED) {
                    moveToState(r, STARTED);
                }
            }
        }
    }

    public void dispatchStop() {
        mCurState = CREATED;
        for (LocalActivityRecord r : new ArrayList<LocalActivityRecord>(mActivities.values())) {
            if (r.activity != null) {
                moveToState(r, CREATED);
            }
        }
    }

    public void removeAllActivities() {
        dispatchDestroy(true);
    }

    public void dispatchDestroy(boolean finishing) {
        ArrayList<LocalActivityRecord> list =
                new ArrayList<LocalActivityRecord>(mActivities.values());
        for (LocalActivityRecord r : list) {
            performDestroy(r, finishing || mFinishing);
        }
        mActivities.clear();
        mResumed = null;
    }
}
