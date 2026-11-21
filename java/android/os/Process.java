// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** Host process identity and guest-visible thread identity. Registered Android UIDs are logical identities, not host OS isolation. */
public class Process {
    static { System.loadLibrary("shiraz-host"); }
    public static final int THREAD_PRIORITY_DEFAULT = 0, THREAD_PRIORITY_BACKGROUND = 10;
    public static native void setThreadPriority(int priority);
    public static native int getThreadPriority(int tid);
    public static native void setArgV0(String name);
    public static native void shirazSetDumpable(boolean enabled);
    public static native int getUidForName(String name);
    public static native int getGidForName(String name);
    public Process() { }
    /** Shiraz runs as the host user, never as an Android isolated UID. */
    public static boolean isIsolated() { return false; }
    public static UserHandle myUserHandle() { return UserHandle.of(UserHandle.myUserId()); }
    /** Cross-process termination requires host-verified package ownership. */
    public static void killProcess(int pid){if(pid==myPid()){killSelf();return;}shiraz.runtime.HostProcesses.terminate(pid);}
    private static native void killSelf();
    public static final native int myPid();
    public static final native int myTid();
    private static native int hostUid();
    private static int resolveHostUid(){
        try{return hostUid();}catch(UnsatisfiedLinkError fallback){return android.system.Os.getuid();}
    }
    private static final int PACKAGE_UID=packageUid();
    private static int packageUid(){
        String value=System.getenv("SHIRAZ_PACKAGE_UID");if(value==null)return resolveHostUid();
        if(!value.matches("1[0-9]{4}"))throw new IllegalStateException("Invalid registered package UID");
        return Integer.parseInt(value);
    }
    public static final int myUid(){return PACKAGE_UID;}
    public static final native boolean is64Bit();
    public static final native long getElapsedCpuTime();
    public static final native long getStartUptimeMillis();
    public static long getStartElapsedRealtime() {
        return getStartUptimeMillis() + SystemClock.elapsedRealtime() - SystemClock.uptimeMillis();
    }
}
