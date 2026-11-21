// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

/** Host CPU idle-sleep assertion. Callers must close ownership explicitly. */
public final class NativePower implements AutoCloseable {
    static { System.loadLibrary("shiraz-power"); }
    private long handle;
    private NativePower() {}
    public static NativePower acquireIdleSleep() { NativePower owner=new NativePower();owner.handle=acquire();return owner; }
    public synchronized boolean isActive() { return handle!=0 && active(handle); }
    @Override public synchronized void close() { if(handle!=0){release(handle);handle=0;} }
    private static native long acquire();
    private static native boolean active(long handle);
    private static native void release(long handle);
}
