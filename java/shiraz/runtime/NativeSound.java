// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
public final class NativeSound {
    private NativeSound() { }
    static { System.loadLibrary("shiraz-audio"); }
    public static native long create(int streams);
    public static native boolean reserve(long handle, int sound);
    public static native boolean load(long handle, int sound, byte[] encoded);
    public static native boolean unload(long handle, int sound);
    public static native int play(long handle, int sound, float left, float right, int priority, int loops, float rate);
    public static native void control(long handle, int stream, int operation, float a, float b, int value);
    public static native void release(long handle);
}
