// SPDX-License-Identifier: GPL-3.0-only
/** A disposable ART JNI probe, not an app execution test. */
public final class IsolatedNativeLoad {
    private static native int probe();
    public static void main(String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("library path and fixture flag required");
        System.load(args[0]);
        if ("fixture".equals(args[1]) && probe() != 73) throw new AssertionError("native fixture result");
        System.out.println("M5_ISOLATED_ART_LOAD_PASS fixture=" + "fixture".equals(args[1]) + " app=0");
    }
}
