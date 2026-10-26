// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.content.Context;
import android.content.ContextWrapper;

/** Application-owned snapshot callback registry. Host memory-pressure monitoring remains separate. */
public class Application extends ContextWrapper implements android.content.ComponentCallbacks2 {
    private final java.util.ArrayList<android.content.ComponentCallbacks> componentCallbacks = new java.util.ArrayList<android.content.ComponentCallbacks>();
    @Override public void registerComponentCallbacks(android.content.ComponentCallbacks callback) {
        if (callback == null) throw new NullPointerException("callback");
        synchronized(componentCallbacks) {
            if(componentCallbacks.size()>=1024) throw new IllegalStateException("Component callback limit");
            componentCallbacks.add(callback);
        }
    }
    @Override public void unregisterComponentCallbacks(android.content.ComponentCallbacks callback) {
        synchronized(componentCallbacks) { componentCallbacks.remove(callback); }
    }
    private android.content.ComponentCallbacks[] callbacks() {
        synchronized(componentCallbacks) { return componentCallbacks.toArray(new android.content.ComponentCallbacks[componentCallbacks.size()]); }
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) {
        if(configuration==null) throw new NullPointerException("configuration");
        for(android.content.ComponentCallbacks callback:callbacks()) callback.onConfigurationChanged(configuration);
    }
    @Override public void onLowMemory() {
        for(android.content.ComponentCallbacks callback:callbacks()) callback.onLowMemory();
    }
    @Override public void onTrimMemory(int level) {
        for(android.content.ComponentCallbacks callback:callbacks()) if(callback instanceof android.content.ComponentCallbacks2) ((android.content.ComponentCallbacks2)callback).onTrimMemory(level);
    }
    public interface ActivityLifecycleCallbacks {
        void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState);
        void onActivityStarted(Activity activity);
        void onActivityResumed(Activity activity);
        void onActivityPaused(Activity activity);
        void onActivityStopped(Activity activity);
        void onActivitySaveInstanceState(Activity activity, android.os.Bundle outState);
        void onActivityDestroyed(Activity activity);
    }
    private final java.util.ArrayList<ActivityLifecycleCallbacks> lifecycleCallbacks = new java.util.ArrayList<ActivityLifecycleCallbacks>();
    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        if (callback == null) throw new NullPointerException("callback");
        synchronized(lifecycleCallbacks) {
            if(lifecycleCallbacks.size()>=1024) throw new IllegalStateException("Lifecycle callback limit");
            lifecycleCallbacks.add(callback);
        }
    }
    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        synchronized(lifecycleCallbacks) { lifecycleCallbacks.remove(callback); }
    }
    public void shirazDispatchLifecycle(int kind, Activity activity, android.os.Bundle state) {
        ActivityLifecycleCallbacks[] snapshot;
        synchronized(lifecycleCallbacks) { snapshot = lifecycleCallbacks.toArray(new ActivityLifecycleCallbacks[lifecycleCallbacks.size()]); }
        for(ActivityLifecycleCallbacks callback:snapshot) {
            switch(kind) {
                case 0: callback.onActivityCreated(activity, state); break;
                case 1: callback.onActivityStarted(activity); break;
                case 2: callback.onActivityResumed(activity); break;
                case 3: callback.onActivityPaused(activity); break;
                case 4: callback.onActivityStopped(activity); break;
                case 5: callback.onActivitySaveInstanceState(activity, state); break;
                default: callback.onActivityDestroyed(activity); break;
            }
        }
    }
    private static volatile String processName;
    public static String getProcessName(){return processName;}
    public static void shirazSetProcessName(String name){if(name==null||name.isEmpty())throw new IllegalArgumentException("Process name");processName=name;}
    public Application() { super(null); }
    public void onCreate() { }
    @Override public Context getApplicationContext() { return this; }
}
