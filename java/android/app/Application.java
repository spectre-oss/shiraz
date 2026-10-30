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
        default void onActivityPreCreated(Activity activity, android.os.Bundle state) {}
        default void onActivityPreStarted(Activity activity) {}
        default void onActivityPreResumed(Activity activity) {}
        default void onActivityPrePaused(Activity activity) {}
        default void onActivityPreStopped(Activity activity) {}
        default void onActivityPreSaveInstanceState(Activity activity, android.os.Bundle state) {}
        default void onActivityPreDestroyed(Activity activity) {}
        default void onActivityPostCreated(Activity activity, android.os.Bundle state) {}
        default void onActivityPostStarted(Activity activity) {}
        default void onActivityPostResumed(Activity activity) {}
        default void onActivityPostPaused(Activity activity) {}
        default void onActivityPostStopped(Activity activity) {}
        default void onActivityPostSaveInstanceState(Activity activity, android.os.Bundle state) {}
        default void onActivityPostDestroyed(Activity activity) {}

        void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState);
        void onActivityStarted(Activity activity);
        void onActivityResumed(Activity activity);
        void onActivityPaused(Activity activity);
        void onActivityStopped(Activity activity);
        void onActivitySaveInstanceState(Activity activity, android.os.Bundle outState);
        void onActivityDestroyed(Activity activity);
    }
    /** Snapshot each phase so callbacks may register or unregister during delivery.
     * Phases: 0 before lifecycle entry, 1 in the base Activity callback, 2 after return. */
    static final class LifecycleCallbacks {
        private final java.util.ArrayList<ActivityLifecycleCallbacks> values=new java.util.ArrayList<ActivityLifecycleCallbacks>();
        synchronized void add(ActivityLifecycleCallbacks callback){if(callback==null)throw new NullPointerException("callback");if(values.size()>=1024)throw new IllegalStateException("Lifecycle callback limit");values.add(callback);}
        synchronized void remove(ActivityLifecycleCallbacks callback){values.remove(callback);}
        void dispatch(int kind,int phase,Activity activity,android.os.Bundle state,boolean reverse){
            ActivityLifecycleCallbacks[] snapshot;
            synchronized(this){snapshot=values.toArray(new ActivityLifecycleCallbacks[values.size()]);}
            for(int i=0;i<snapshot.length;i++){
                ActivityLifecycleCallbacks callback=snapshot[reverse?snapshot.length-1-i:i];
                switch(phase*7+kind){
                    case 0: callback.onActivityPreCreated(activity,state);break;
                    case 1: callback.onActivityPreStarted(activity);break;
                    case 2: callback.onActivityPreResumed(activity);break;
                    case 3: callback.onActivityPrePaused(activity);break;
                    case 4: callback.onActivityPreStopped(activity);break;
                    case 5: callback.onActivityPreSaveInstanceState(activity,state);break;
                    case 6: callback.onActivityPreDestroyed(activity);break;
                    case 7: callback.onActivityCreated(activity,state);break;
                    case 8: callback.onActivityStarted(activity);break;
                    case 9: callback.onActivityResumed(activity);break;
                    case 10: callback.onActivityPaused(activity);break;
                    case 11: callback.onActivityStopped(activity);break;
                    case 12: callback.onActivitySaveInstanceState(activity,state);break;
                    case 13: callback.onActivityDestroyed(activity);break;
                    case 14: callback.onActivityPostCreated(activity,state);break;
                    case 15: callback.onActivityPostStarted(activity);break;
                    case 16: callback.onActivityPostResumed(activity);break;
                    case 17: callback.onActivityPostPaused(activity);break;
                    case 18: callback.onActivityPostStopped(activity);break;
                    case 19: callback.onActivityPostSaveInstanceState(activity,state);break;
                    case 20: callback.onActivityPostDestroyed(activity);break;
                    default:throw new IllegalArgumentException("Lifecycle event");
                }
            }
        }
    }
    private final LifecycleCallbacks lifecycleCallbacks=new LifecycleCallbacks();
    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback){lifecycleCallbacks.add(callback);}
    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback){lifecycleCallbacks.remove(callback);}
    public void shirazDispatchLifecycle(int kind,Activity activity,android.os.Bundle state){shirazDispatchLifecycle(kind,1,activity,state);}
    public void shirazDispatchLifecycle(int kind,int phase,Activity activity,android.os.Bundle state){lifecycleCallbacks.dispatch(kind,phase,activity,state,false);}
    private static volatile String processName;
    public static String getProcessName(){return processName;}
    public static void shirazSetProcessName(String name){if(name==null||name.isEmpty())throw new IllegalArgumentException("Process name");processName=name;}
    public Application() { super(null); }
    public void onCreate() { }
    @Override public Context getApplicationContext() { return this; }
}
