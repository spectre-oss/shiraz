// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

/** Lazy ownership of the Darwin guest PathRef mirror; software Path needs none. */
public final class NativeGuestPath {
    private NativeGuestPath() { }
    private static final class Library {
        static final boolean LOADED=load();
        private static boolean load() { System.loadLibrary("shiraz-guest");return true; }
    }
    private static void ensureLoaded() {
        if(!Library.LOADED)throw new IllegalStateException("Guest path library did not load");
    }
    public static long createPath() { ensureLoaded();return nCreate(); }
    public static void updatePath(long pointer,float[] semanticCommands,int generation) {
        if(pointer==0||semanticCommands==null)throw new IllegalArgumentException("Guest path update arguments");
        ensureLoaded();nUpdate(pointer,semanticCommands,generation);
    }
    public static void destroyPath(long pointer) { if(pointer!=0){ensureLoaded();nDestroy(pointer);} }
    private static native long nCreate();
    private static native void nUpdate(long pointer,float[] semanticCommands,int generation);
    private static native void nDestroy(long pointer);
}
