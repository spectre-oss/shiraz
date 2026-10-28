// SPDX-License-Identifier: GPL-3.0-only
package shiraz;
public final class ThreadSmoke {
    private static native int run();
    public static void main(String[] args) {
        System.load(args[0]);
        int result=run();
        if(result!=42)throw new AssertionError("Guest thread lifecycle: "+result);
        System.out.println("DIGITALIS_THREADS_TLS_JOIN_DESTRUCTORS_OK");
    }
}
