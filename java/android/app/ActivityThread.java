// SPDX-License-Identifier: GPL-3.0-only
package android.app;

/** Process application identity exposed by the standalone activity runner. */
public final class ActivityThread {
    private static volatile ActivityThread sCurrentActivityThread;
    private volatile Application mInitialApplication;
    private final String packageName;
    private ContextImpl systemContext;
    private ActivityThread(Application app,String name){mInitialApplication=app;packageName=name;}
    /** Publish process identity before application class initialization runs. */
    public static void shirazPrepare(String packageName,java.util.Properties metadata){
        if(packageName==null)throw new NullPointerException();
        ActivityThread thread=new ActivityThread(null,packageName);
        thread.systemContext=new ContextImpl(metadata);
        sCurrentActivityThread=thread;
    }
    public static void shirazBind(Application app,String packageName){
        if(app==null||packageName==null)throw new NullPointerException();
        ActivityThread thread=sCurrentActivityThread;
        if(thread==null||!packageName.equals(thread.packageName))
            sCurrentActivityThread=new ActivityThread(app,packageName);
        else thread.mInitialApplication=app;
    }
    public static void shirazClear(){sCurrentActivityThread=null;}
    public static ActivityThread currentActivityThread(){return sCurrentActivityThread;}
    public static Application currentApplication(){ActivityThread thread=sCurrentActivityThread;return thread==null?null:thread.mInitialApplication;}
    public static String currentPackageName(){ActivityThread thread=sCurrentActivityThread;return thread==null?null:thread.packageName;}
    public static String currentOpPackageName(){return currentPackageName();}
    public static String currentProcessName(){return Application.getProcessName();}
    public static boolean isSystem(){return false;}
    public Application getApplication(){return mInitialApplication;}
    public ContextImpl getSystemContext(){
        if(systemContext==null)throw new IllegalStateException("Process context has not been prepared");
        return systemContext;
    }
    public String getProcessName(){return currentProcessName();}
    public android.os.Looper getLooper(){return android.os.Looper.getMainLooper();}
}
