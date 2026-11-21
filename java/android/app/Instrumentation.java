// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.TestLooperManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Instrumentation {
    public static final String REPORT_KEY_IDENTIFIER = "id";
    public static final String REPORT_KEY_STREAMRESULT = "stream";

    private Context mInstrContext;
    private Context mAppContext;
    private ComponentName mComponent;
    private boolean mInTouchMode = true;
    private final Object mSync = new Object();
    private final List<ActivityMonitor> mActivityMonitors = new ArrayList<ActivityMonitor>();
    private UiAutomation mUiAutomation;

    public Instrumentation() {
    }

    public void onCreate(Bundle arguments) {
    }

    public void start() {
        onStart();
    }

    public void onStart() {
    }

    public boolean onException(Object obj, Throwable e) {
        return false;
    }

    public void sendStatus(int resultCode, Bundle results) {
    }

    public void addResults(Bundle results) {
    }

    public void finish(int resultCode, Bundle results) {
        onDestroy();
    }

    public void setAutomaticPerformanceSnapshots() {
    }

    public void startPerformanceSnapshot() {
    }

    public void endPerformanceSnapshot() {
    }

    public void onDestroy() {
    }

    public Context getContext() {
        return mInstrContext;
    }

    public ComponentName getComponentName() {
        return mComponent;
    }

    public Context getTargetContext() {
        return mAppContext;
    }

    public String getProcessName() {
        return Application.getProcessName();
    }

    public boolean isProfiling() {
        return false;
    }

    public void startProfiling() {
    }

    public void stopProfiling() {
    }

    public void setInTouchMode(boolean inTouch) {
        mInTouchMode = inTouch;
    }

    public void resetInTouchMode() {
        mInTouchMode = true;
    }

    public void waitForIdle(Runnable recipient) {
        if (recipient == null) {
            return;
        }
        Looper main = Looper.getMainLooper();
        if (main != null) {
            new Handler(main).post(recipient);
        } else {
            recipient.run();
        }
    }

    public void waitForIdleSync() {
        Looper main = Looper.getMainLooper();
        if (main == null || Thread.currentThread() == main.getThread()) {
            return;
        }
        final boolean[] done = new boolean[1];
        Handler h = new Handler(main);
        synchronized (done) {
            if (h.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (done) {
                        done[0] = true;
                        done.notifyAll();
                    }
                }
            })) {
                while (!done[0]) {
                    try {
                        done.wait(1000L);
                        break;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    public void runOnMainSync(final Runnable runner) {
        if (runner == null) {
            return;
        }
        Looper main = Looper.getMainLooper();
        if (main == null || Thread.currentThread() == main.getThread()) {
            runner.run();
            return;
        }
        final Throwable[] thrown = new Throwable[1];
        final boolean[] done = new boolean[1];
        Handler h = new Handler(main);
        synchronized (done) {
            if (!h.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        runner.run();
                    } catch (Throwable t) {
                        thrown[0] = t;
                    } finally {
                        synchronized (done) {
                            done[0] = true;
                            done.notifyAll();
                        }
                    }
                }
            })) {
                throw new IllegalStateException("Main Looper is closed");
            }
            while (!done[0]) {
                try {
                    done.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (thrown[0] instanceof RuntimeException) {
            throw (RuntimeException) thrown[0];
        }
        if (thrown[0] instanceof Error) {
            throw (Error) thrown[0];
        }
        if (thrown[0] != null) {
            throw new RuntimeException(thrown[0]);
        }
    }

    public Activity startActivitySync(Intent intent) {
        return startActivitySync(intent, null);
    }

    public Activity startActivitySync(Intent intent, Bundle options) {
        Context ctx = mAppContext != null ? mAppContext : mInstrContext;
        if (ctx != null) {
            ctx.startActivity(intent, options);
        }
        return null;
    }

    public void addMonitor(ActivityMonitor monitor) {
        synchronized (mSync) {
            mActivityMonitors.add(monitor);
        }
    }

    public ActivityMonitor addMonitor(IntentFilter filter, ActivityResult result, boolean block) {
        ActivityMonitor am = new ActivityMonitor(filter, result, block);
        addMonitor(am);
        return am;
    }

    public ActivityMonitor addMonitor(String cls, ActivityResult result, boolean block) {
        ActivityMonitor am = new ActivityMonitor(cls, result, block);
        addMonitor(am);
        return am;
    }

    public boolean checkMonitorHit(ActivityMonitor monitor, int minHits) {
        waitForIdleSync();
        synchronized (mSync) {
            if (monitor.getHits() < minHits) {
                return false;
            }
            mActivityMonitors.remove(monitor);
        }
        return true;
    }

    public Activity waitForMonitor(ActivityMonitor monitor) {
        Activity activity = monitor.waitForActivity();
        synchronized (mSync) {
            mActivityMonitors.remove(monitor);
        }
        return activity;
    }

    public Activity waitForMonitorWithTimeout(ActivityMonitor monitor, long timeOut) {
        Activity activity = monitor.waitForActivityWithTimeout(timeOut);
        synchronized (mSync) {
            mActivityMonitors.remove(monitor);
        }
        return activity;
    }

    public void removeMonitor(ActivityMonitor monitor) {
        synchronized (mSync) {
            mActivityMonitors.remove(monitor);
        }
    }

    public boolean invokeMenuActionSync(final Activity targetActivity, final int id, final int flag) {
        if (targetActivity == null) {
            return false;
        }
        final boolean[] ok = new boolean[1];
        runOnMainSync(new Runnable() {
            @Override
            public void run() {
                android.view.Menu menu = targetActivity.getWindow().shirazPrepareOptionsPanel();
                ok[0] = menu != null && menu.performIdentifierAction(id, flag);
            }
        });
        return ok[0];
    }

    public boolean invokeContextMenuAction(Activity targetActivity, int id, int flag) {
        return false;
    }

    public void sendStringSync(String text) {
        if (text == null) {
            return;
        }
        KeyEvent event = new KeyEvent(System.currentTimeMillis(), text, 0, 0);
        sendKeySync(event);
    }

    public void sendKeySync(KeyEvent event) {
    }

    public void sendKeyDownUpSync(int keyCode) {
        sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        sendKeySync(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    public void sendCharacterSync(int keyCode) {
        sendKeyDownUpSync(keyCode);
    }

    public void sendPointerSync(MotionEvent event) {
    }

    public void sendTrackballEventSync(MotionEvent event) {
    }

    public Application newApplication(ClassLoader cl, String className, Context context)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return newApplication(cl.loadClass(className), context);
    }

    public static Application newApplication(Class<?> clazz, Context context)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Application app = (Application) clazz.newInstance();
        attachBase(app, context);
        return app;
    }

    private static void attachBase(ContextWrapper wrapper, Context base) {
        if (wrapper == null || base == null || wrapper.getBaseContext() != null) {
            return;
        }
        try {
            Method m = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            m.setAccessible(true);
            m.invoke(wrapper, base);
        } catch (Exception ignored) {
        }
    }

    public void callApplicationOnCreate(Application app) {
        app.onCreate();
    }

    public Activity newActivity(Class<?> clazz, Context context, IBinder token,
            Application application, Intent intent, ActivityInfo info, CharSequence title,
            Activity parent, String id, Object lastNonConfigurationInstance)
            throws IllegalAccessException, InstantiationException {
        Activity activity = (Activity) clazz.newInstance();
        attachBase(activity, context);
        activity.setIntent(intent);
        if (title != null) {
            activity.setTitle(title);
        }
        return activity;
    }

    public Activity newActivity(ClassLoader cl, String className, Intent intent)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Activity activity = (Activity) cl.loadClass(className).newInstance();
        activity.setIntent(intent);
        return activity;
    }

    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        activity.onCreate(icicle);
    }

    public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
        activity.onCreate(icicle, persistentState);
    }

    public void callActivityOnDestroy(Activity activity) {
        activity.onDestroy();
    }

    public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState) {
        activity.onRestoreInstanceState(savedInstanceState);
    }

    public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState,
            PersistableBundle persistentState) {
        activity.onRestoreInstanceState(savedInstanceState, persistentState);
    }

    public void callActivityOnPostCreate(Activity activity, Bundle icicle) {
        activity.onPostCreate(icicle);
    }

    public void callActivityOnPostCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
        activity.onPostCreate(icicle, persistentState);
    }

    public void callActivityOnNewIntent(Activity activity, Intent intent) {
        activity.onNewIntent(intent);
    }

    public void callActivityOnNewIntent(Activity activity, Intent intent, ComponentCaller caller) {
        activity.onNewIntent(intent, caller);
    }

    public void callActivityOnStart(Activity activity) {
        activity.onStart();
    }

    public void callActivityOnRestart(Activity activity) {
        activity.onRestart();
    }

    public void callActivityOnResume(Activity activity) {
        activity.onResume();
    }

    public void callActivityOnStop(Activity activity) {
        activity.onStop();
    }

    public void callActivityOnSaveInstanceState(Activity activity, Bundle outState) {
        activity.onSaveInstanceState(outState);
    }

    public void callActivityOnSaveInstanceState(Activity activity, Bundle outState,
            PersistableBundle outPersistentState) {
        activity.onSaveInstanceState(outState, outPersistentState);
    }

    public void callActivityOnPause(Activity activity) {
        activity.onPause();
    }

    public void callActivityOnUserLeaving(Activity activity) {
        activity.onUserLeaveHint();
    }

    public void callActivityOnPictureInPictureRequested(Activity activity) {
        activity.onPictureInPictureRequested();
    }

    @Deprecated
    public void startAllocCounting() {
    }

    @Deprecated
    public void stopAllocCounting() {
    }

    public Bundle getAllocCounts() {
        return new Bundle();
    }

    public Bundle getBinderCounts() {
        return new Bundle();
    }

    public UiAutomation getUiAutomation() {
        return getUiAutomation(0);
    }

    public UiAutomation getUiAutomation(int flags) {
        if (mUiAutomation == null) {
            mUiAutomation = new UiAutomation();
        }
        return mUiAutomation;
    }

    public TestLooperManager acquireLooperManager(Looper looper) {
        try {
            Constructor<TestLooperManager> ctor = TestLooperManager.class.getDeclaredConstructor(Looper.class);
            ctor.setAccessible(true);
            return ctor.newInstance(looper);
        } catch (Exception e) {
            return null;
        }
    }

    public static class ActivityMonitor {
        private final IntentFilter mWhich;
        private final String mClass;
        private final ActivityResult mResult;
        private final boolean mBlock;
        private final boolean mIgnoreMatchingSpecificIntents;
        private int mHits;
        private Activity mLastActivity;

        public ActivityMonitor(IntentFilter which, ActivityResult result, boolean block) {
            mWhich = which;
            mClass = null;
            mResult = result;
            mBlock = block;
            mIgnoreMatchingSpecificIntents = false;
        }

        public ActivityMonitor(String cls, ActivityResult result, boolean block) {
            mWhich = null;
            mClass = cls;
            mResult = result;
            mBlock = block;
            mIgnoreMatchingSpecificIntents = false;
        }

        public ActivityMonitor() {
            mWhich = null;
            mClass = null;
            mResult = null;
            mBlock = false;
            mIgnoreMatchingSpecificIntents = true;
        }

        public final IntentFilter getFilter() {
            return mWhich;
        }

        public final ActivityResult getResult() {
            return mResult;
        }

        public final boolean isBlocking() {
            return mBlock;
        }

        public final int getHits() {
            return mHits;
        }

        public final Activity getLastActivity() {
            return mLastActivity;
        }

        public final Activity waitForActivity() {
            synchronized (this) {
                while (mLastActivity == null) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                Activity res = mLastActivity;
                mLastActivity = null;
                return res;
            }
        }

        public final Activity waitForActivityWithTimeout(long timeOut) {
            synchronized (this) {
                if (mLastActivity == null) {
                    try {
                        wait(Math.max(1L, timeOut));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if (mLastActivity == null) {
                    return null;
                }
                Activity res = mLastActivity;
                mLastActivity = null;
                return res;
            }
        }

        public ActivityResult onStartActivity(Intent intent) {
            return null;
        }
    }

    public static final class ActivityResult {
        private final int mResultCode;
        private final Intent mResultData;

        public ActivityResult(int resultCode, Intent resultData) {
            mResultCode = resultCode;
            mResultData = resultData;
        }

        public int getResultCode() {
            return mResultCode;
        }

        public Intent getResultData() {
            return mResultData;
        }
    }
}
