// SPDX-License-Identifier: GPL-3.0-only
/**
 * Field access semantics for the switch interpreter's cached fast path:
 * every primitive width (sign and zero extension), objects, volatile and
 * static fields, inherited fields, NullPointerException, class
 * initialization order and failure, and many call sites sharing the interpreter cache
 * while the collector runs.
 */
public final class InterpreterFieldSmoke {
    static class Base { int inherited = 7; }
    static final class Holder extends Base {
        boolean z; byte b; char c; short s; int i; long j; float f; double d; Object o;
        volatile int vi; volatile long vj; volatile Object vo;
    }
    static boolean zs; static byte bs; static char cs; static short ss; static int is; static long js; static Object os;
    static volatile int vis;
    static final StringBuilder order = new StringBuilder();
    static final class Lazy { static int value = init(); static int init() { order.append("init;"); return 41; } }
    static int zero;
    static final class Broken { static int value = 1 / zero; }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static long round(Holder h, int n) {
        h.z = (n & 1) == 0; h.b = (byte) (0x80 | n); h.c = (char) (0xff00 | n); h.s = (short) (0x8000 | n);
        h.i = n * 31; h.j = 0x1_0000_0000L * n + n; h.f = n / 2f; h.d = n / 4.0; h.o = h;
        h.vi = -n; h.vj = -0x1_0000_0000L * n; h.vo = order; h.inherited += n;
        check(h.z == ((n & 1) == 0), "boolean");
        check(h.b < 0 && (h.b & 0xff) == (0x80 | (n & 0x7f)), "byte sign extension");
        check(h.c >= 0xff00, "char zero extension");
        check(h.s < 0, "short sign extension");
        check(h.i == n * 31 && h.j == 0x1_0000_0000L * n + n, "int/long");
        check(h.f == n / 2f && h.d == n / 4.0, "float/double");
        check(h.o == h && h.vo == order && h.vi == -n && h.vj == -0x1_0000_0000L * n, "object/volatile");
        zs = !zs; bs = (byte) -n; cs = (char) (0xfffe - (n & 0xff)); ss = (short) -n; is += n; js -= n; os = h; vis = n;
        check(bs == (byte) -n && cs > 0xfe00 && ss == (short) -n && os == h && vis == n, "static fields");
        return h.j + h.i + h.inherited + is + js;
    }

    public static void main(String[] args) {
        check(order.length() == 0, "lazy class initialized early");
        check(Lazy.value == 41 && order.toString().equals("init;"), "static read initializes once");
        Lazy.value++;
        check(Lazy.value == 42 && order.toString().equals("init;"), "static write after init");
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                int x = Broken.value;
                throw new AssertionError("broken class initialized " + x);
            } catch (ExceptionInInitializerError first) {
                check(attempt == 0 && first.getCause() instanceof ArithmeticException, "first clinit failure");
            } catch (NoClassDefFoundError later) {
                check(attempt > 0, "later access to an erroneous class");
            }
        }
        Holder[] holders = new Holder[64];
        for (int k = 0; k < holders.length; k++) holders[k] = new Holder();
        long sum = 0;
        for (int n = 0; n < 200_000; n++) {
            sum += round(holders[n & 63], n & 0x7fff);
            if ((n & 0x3fff) == 0) { System.gc(); holders[n & 63] = new Holder(); }
        }
        Holder missing = null;
        try { int x = missing.i; throw new AssertionError("iget on null " + x); } catch (NullPointerException expected) { }
        try { missing.o = missing; throw new AssertionError("iput on null"); } catch (NullPointerException expected) { }
        try { long x = missing.vj; throw new AssertionError("volatile iget on null " + x); } catch (NullPointerException expected) { }
        // The same NPE site repeatedly: cached entries must not hide the null check.
        int npes = 0;
        for (int n = 0; n < 1000; n++) {
            Holder h = (n & 1) == 0 ? holders[0] : null;
            try { h.i = n; } catch (NullPointerException expected) { npes++; }
        }
        check(npes == 500, "repeated NPE site");
        System.out.println("INTERPRETER_FIELD_OK sum=" + sum);
    }
}
