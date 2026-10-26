// SPDX-License-Identifier: GPL-3.0-only
package shiraz;

/** Executed as DEX by host ART, never by the host OpenJDK JVM. */
public final class ArtSmoke {
    private static int sum(int n) {
        int result = 0;
        for (int i = 0; i <= n; ++i) result += i;
        return result;
    }

    public static void main(String[] args) {
        if (args.length != 1 || !"host-runtime".equals(args[0]) || sum(100) != 5050)
            throw new AssertionError("Host ART argument/arithmetic smoke failed");
        System.out.println("SHIRAZ_ART_OK:5050");
    }
}
