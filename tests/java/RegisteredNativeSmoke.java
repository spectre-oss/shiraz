// SPDX-License-Identifier: GPL-3.0-only
public class RegisteredNativeSmoke {
    private static native int answer();
    private static native Object[] values();
    public static void main(String[] args) {
        System.load(args[0]);
        int actual = answer();
        if (actual != 42) throw new AssertionError(actual);
        Object[] result=values();
        if(((int[])result[0])[0]!=42||!"fixture".equals(((String[])result[1])[0]))throw new AssertionError("JNI object result");
        System.out.println("REGISTERED_ARM64_JNI_OK 42");
    }
}
