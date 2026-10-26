// SPDX-License-Identifier: GPL-3.0-only
public final class BionicLoadSmoke {
    private static native int value();
    public static void main(String[] args) {
        System.loadLibrary("shiraz-bionic-probe");
        if (value() != 73) throw new AssertionError("Native constructor did not run");
        System.loadLibrary("shiraz-bionic-probe");
        if (value() != 73) throw new AssertionError("Repeated native load failed");
        System.out.println("BIONIC_ART_LOAD_AND_CONSTRUCTOR_OK");
    }
}
