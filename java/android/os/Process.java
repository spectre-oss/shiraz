// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** Identity of this host process/thread. Android UID isolation is not emulated. */
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
    /** Termination is restricted to this runtime process; host process IDs are not an Android namespace. */
    public static void killProcess(int pid){if(pid!=myPid())throw new SecurityException("Cannot terminate an unrelated host process");killSelf();}
    private static native void killSelf();
    public static final native int myPid();
    public static final native int myTid();
    public static final native int myUid();
    public static final native boolean is64Bit();
    public static final native long getElapsedCpuTime();
    public static final native long getStartUptimeMillis();
    public static long getStartElapsedRealtime() {
        return getStartUptimeMillis() + SystemClock.elapsedRealtime() - SystemClock.uptimeMillis();
    }
}
