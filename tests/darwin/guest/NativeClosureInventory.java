// SPDX-License-Identifier: GPL-3.0-only
/** Read-only end-of-main dyld snapshot from the already loaded host helper. */
public final class NativeClosureInventory {
    private static native String nativeSnapshot();
    public static void emitIfRequested() {
        if (System.getProperty("guest.native.closure") == null) return;
        String report = nativeSnapshot();
        if (report == null) throw new AssertionError("native closure snapshot absent");
        System.out.print(report);
        System.out.flush();
    }
}
