// SPDX-License-Identifier: GPL-3.0-only
package android.app;

/** Process application identity exposed by the standalone activity runner. */
public final class ActivityThread {
    private static volatile ActivityThread sCurrentActivityThread;
    private volatile Application mInitialApplication;
    private final String packageName;
    private ActivityThread(Application app,String name){mInitialApplication=app;packageName=name;}
    public static void shirazBind(Application app,String packageName){
        if(app==null||packageName==null)throw new NullPointerException();
        sCurrentActivityThread=new ActivityThread(app,packageName);
    }
    public static void shirazClear(){sCurrentActivityThread=null;}
    public static ActivityThread currentActivityThread(){return sCurrentActivityThread;}
    public static Application currentApplication(){ActivityThread thread=sCurrentActivityThread;return thread==null?null:thread.mInitialApplication;}
    public static String currentPackageName(){ActivityThread thread=sCurrentActivityThread;return thread==null?null:thread.packageName;}
    public static String currentOpPackageName(){return currentPackageName();}
    public static String currentProcessName(){return Application.getProcessName();}
    public static boolean isSystem(){return false;}
    public Application getApplication(){return mInitialApplication;}
    public String getProcessName(){return currentProcessName();}
    public android.os.Looper getLooper(){return android.os.Looper.getMainLooper();}
}
