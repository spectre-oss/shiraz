// SPDX-License-Identifier: GPL-3.0-only
// Interpreter-to-interpreter invokes: argument and return shapes, range invokes, exceptions
// across frames, deep recursion, long-running call loops on small stacks, and the callees
// that must keep taking the generic bridge (class initialization, synchronized, native,
// proxy, reflective, String constructors, null receivers). Prints a digest that OpenJDK and
// ART must agree on.
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public final class InterpreterInvokeSmoke {
    interface Op {
        long apply(long value);
    }

    static class Base implements Op {
        int count;

        @Override
        public long apply(long value) {
            count++;
            return value + 1;
        }

        synchronized int locked(int value) {
            return value * 3;
        }
    }

    static final class Derived extends Base {
        @Override
        public long apply(long value) {
            return super.apply(value) * 2;
        }
    }

    static final class Lazy {
        static final List<String> EVENTS = new ArrayList<>();
        static int value;

        static {
            EVENTS.add("clinit");
            value = 41;
        }

        static int next() {
            return ++value;
        }
    }

    static final class Broken {
        static final int VALUE = fail();

        static int fail() {
            throw new IllegalStateException("broken clinit");
        }

        static int touch() {
            return VALUE;
        }
    }

    static final class Wide {
        static double mixed(int a, long b, float c, double d, boolean e, byte f, char g, short h) {
            return a + b + c + d + (e ? 1 : 0) + f + g + h;
        }

        static long range(long a, long b, long c, long d, long e, long f, long g) {
            return a - b + c - d + e - f + g;
        }

        static Object pick(Object a, Object b, int which) {
            return which == 0 ? a : b;
        }

        static boolean flag(int v) {
            return v > 0;
        }

        static byte narrowByte(int v) {
            return (byte) v;
        }

        static char narrowChar(int v) {
            return (char) v;
        }

        static short narrowShort(int v) {
            return (short) v;
        }

        static float half(float v) {
            return v / 2;
        }

        static int[] array(int n) {
            int[] values = new int[n];
            for (int i = 0; i < n; i++) {
                values[i] = i * i;
            }
            return values;
        }

        static void nothing() {}
    }

    private InterpreterInvokeSmoke() {}

    static long digest = 17;

    static void mix(Object value) {
        digest = digest * 31 + String.valueOf(value).hashCode();
        System.out.println(value);
    }

    static int depth(int n) {
        return n == 0 ? 0 : 1 + depth(n - 1);
    }

    static int unbounded(int n) {
        return unbounded(n + 1) + 1;
    }

    static int thrower(int n) {
        if (n == 0) {
            throw new IllegalArgumentException("bottom");
        }
        return thrower(n - 1) + 1;
    }

    static long garbage(int n) {
        Object[] keep = new Object[4];
        long sum = 0;
        for (int i = 0; i < n; i++) {
            keep[i & 3] = new long[] {i, i + 1};
            if ((i & 1023) == 0) {
                System.gc();
            }
            sum += ((long[]) keep[i & 3])[1];
        }
        return sum;
    }

    static long loop(Op op, int rounds) {
        long value = 0;
        for (int i = 0; i < rounds; i++) {
            value = op.apply(value) & 0xffff;
        }
        return value;
    }

    static String overflowOn(int stack) throws InterruptedException {
        final String[] outcome = new String[1];
        Thread thread =
                new Thread(
                        null,
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    unbounded(0);
                                    outcome[0] = "no overflow";
                                } catch (StackOverflowError expected) {
                                    outcome[0] = "overflow caught, after=" + depth(100);
                                }
                            }
                        },
                        "small",
                        stack);
        thread.start();
        thread.join();
        return outcome[0];
    }

    public static void main(String[] args) throws Exception {
        mix(Wide.mixed(1, 2L, 3.5f, 4.25, true, (byte) -5, 'A', (short) -7));
        mix(Wide.range(1, 2, 3, 4, 5, 6, 7));
        mix(Wide.pick("a", "b", 1));
        mix(Wide.flag(3) + " " + Wide.flag(-3));
        mix(Wide.narrowByte(0x1ff) + " " + (int) Wide.narrowChar(-1) + " " + Wide.narrowShort(0x18000));
        mix(Wide.half(3f));
        mix(Wide.array(5)[4]);
        Wide.nothing();

        Op[] ops = {new Base(), new Derived()};
        mix(ops[0].apply(5) + " " + ops[1].apply(5));
        mix(loop(new Base(), 3000000) + " " + loop(new Derived(), 1000000));
        mix(((Base) ops[1]).locked(7) + " count=" + ((Base) ops[1]).count);

        mix(Lazy.EVENTS.size() + " " + Lazy.next() + " " + Lazy.next() + " " + Lazy.EVENTS);
        for (int i = 0; i < 2; i++) {
            try {
                mix(Broken.touch());
            } catch (Throwable expected) {
                mix(expected.getClass().getName());
            }
        }

        try {
            thrower(50);
        } catch (IllegalArgumentException expected) {
            StackTraceElement[] trace = expected.getStackTrace();
            int throwerFrames = 0;
            for (StackTraceElement element : trace) {
                if (element.getMethodName().equals("thrower")) {
                    throwerFrames++;
                }
            }
            mix(expected.getMessage() + " frames=" + throwerFrames + " top=" + trace[0].getMethodName());
        }
        Base none = null;
        for (int i = 0; i < 3; i++) {
            try {
                none.apply(1);
            } catch (NullPointerException expected) {
                mix("npe " + i);
            }
        }

        mix(new String(new char[] {'o', 'k'}) + new String(new byte[] {'!'}, "UTF-8"));
        mix(Math.abs(-3) + " " + Integer.toHexString(255) + " " + System.identityHashCode(null));
        Method method = Wide.class.getDeclaredMethod("range", long.class, long.class, long.class,
                long.class, long.class, long.class, long.class);
        mix(method.invoke(null, 7L, 6L, 5L, 4L, 3L, 2L, 1L));
        Op proxy =
                (Op)
                        Proxy.newProxyInstance(
                                Op.class.getClassLoader(),
                                new Class<?>[] {Op.class},
                                new InvocationHandler() {
                                    @Override
                                    public Object invoke(Object self, Method m, Object[] a) {
                                        return (Long) a[0] * 10;
                                    }
                                });
        mix(proxy.apply(4) + " " + loop(proxy, 1000));

        mix(depth(2000));
        try {
            unbounded(0);
            mix("no overflow");
        } catch (StackOverflowError expected) {
            mix("main overflow caught, after=" + depth(100));
        }
        mix(overflowOn(256 * 1024));
        mix(overflowOn(256 * 1024));

        // Long call loops on a small stack: per-call frames must be released on return.
        final long[] small = new long[1];
        Runnable body =
                new Runnable() {
                    @Override
                    public void run() {
                        small[0] = loop(new Derived(), 20000000);
                    }
                };
        Thread loops = new Thread(null, body, "loops", 128 * 1024);
        loops.start();
        loops.join();
        mix("small stack loop " + small[0]);
        mix(garbage(200000));
        System.out.println("INTERPRETER_INVOKE_OK digest=" + digest);
    }
}
