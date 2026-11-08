// SPDX-License-Identifier: GPL-3.0-only
public class GuestJniFixture {
    static {
        System.load(System.getProperty("guest.fixture"));
        System.load(System.getProperty("guest.host.fixture"));
    }
    static native int hostCheck();
    static native byte narrowByte();
    static native short narrowShort();
    static native char narrowChar();
    static native String registeredObject(String text, int[] values);
    static native long twelve(int a, int b, int c, int d, int e, int f,
                              int g, int h, int i, int j, int k, int l);
    static native long registered(int a, int b, int c, int d, int e, int f,
                                  int g, int h, int i, int j, int k, int l);
    static native double mixed(int a, double b, byte c, float d, short e, double f,
                               int g, double h, int i, double j, int k, double l,
                               int m, double n, int o, double p, int q, double r,
                               int s, double t, int u, double v, int w, double x);
    static native double narrowStack(int a, int b, int c, int d, int e, int f, byte g, short h, char i, boolean z, int j, long k, float f0, float f1, float f2, float f3, float f4, float f5, float f6, float f7, float f8, double d0, double d1, double d2, double d3, double d4, double d5, double d6, double d7, double d8);
    static double javaNarrow(int a, int b, int c, int d, int e, int f, byte g, short h, char i, boolean z, int j, long k, float f0, float f1, float f2, float f3, float f4, float f5, float f6, float f7, float f8, double d0, double d1, double d2, double d3, double d4, double d5, double d6, double d7, double d8) { return 1 * (double)a + 2 * (double)b + 3 * (double)c + 4 * (double)d + 5 * (double)e + 6 * (double)f + 7 * (double)g + 8 * (double)h + 9 * (double)i + 10 * (double)(z ? 1 : 0) + 11 * (double)j + 12 * (double)k + 13 * (double)f0 + 14 * (double)f1 + 15 * (double)f2 + 16 * (double)f3 + 17 * (double)f4 + 18 * (double)f5 + 19 * (double)f6 + 20 * (double)f7 + 21 * (double)f8 + 22 * (double)d0 + 23 * (double)d1 + 24 * (double)d2 + 25 * (double)d3 + 26 * (double)d4 + 27 * (double)d5 + 28 * (double)d6 + 29 * (double)d7 + 30 * (double)d8; }
    static native int memory();
    static native int bitmap(Object value);
    static native int fileRoundtrip(String path);
    static native int mappedMemory();
    static native int createdThreads();
    static int threadValue(int value) { return value * 7 + 3; }
    static native int exercise();
    static native double reflected(java.lang.reflect.Method method);
    static native void reregister();
    static double javaTiny(int i, double d, long j) { return i + d + j; }
    public static class Registered { static native int value(); }
    static native void throwFromGuest();
    static double javaMixed(int a, double b, byte c, float d, short e, double f,
                            int g, double h, int i, double j, int k, double l,
                            int m, double n, int o, double p, int q, double r,
                            int s, double t, int u, double v, int w, double x) {
        return a + 2*b + 3*c + 4*d + 5*e + 6*f + 7*g + 8*h + 9*i + 10*j +
               11*k + 12*l + 13*m + 14*n + 15*o + 16*p + 17*q + 18*r +
               19*s + 20*t + 21*u + 22*v + 23*w + 24*x;
    }
    public static class Box {
        final long number;
        final double value;
        byte small;
        Box(int i, long j, float f, double d) { number = i + j; value = f + d; }
        double score(int i) { return number + value + i; }
    }
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    public static void main(String[] ignored) throws Exception {
        check(narrowByte() == -113 && narrowShort() == -32123 && narrowChar() == '\ufedc', "narrow native returns");
        String text = "shadow JNI";
        check(registeredObject(text, new int[]{2,-4,9}) == text, "object/array registered signature");
        check(fileRoundtrip(System.getProperty("guest.io.file")) == 73, "file adapters under real ART");
        check(mappedMemory() == 79, "owned memory mappings under real ART");
        check(createdThreads() == 83, "guest-created threads attach/detach and reattach");
        android.graphics.Bitmap pixels = new android.graphics.Bitmap();
        check(bitmap(pixels) == 89 && pixels.preparations == 1 &&
              pixels.pixels[0] == 0x80ff4000 && pixels.pixels[1] == 0x8000ff00,
              "libjnigraphics shadow environment, nested locks and pixel writeback");
        System.out.println("M5_ART_BITMAP_PASS shadow_env=1 rgba=1 nested=1 writeback=1 fixture_class=1");
        check(memory() == 73, "explicit libc dependency providers");
        check(hostCheck() == 19, "host Mach-O library routing");
        check(twelve(1,-2,3,-4,5,-6,7,-8,9,-10,11,-12) == -78, "integer stack args");
        check(registered(1,-2,3,-4,5,-6,7,-8,9,-10,11,-12) == -78, "RegisterNatives");
        double expected = javaMixed(-1,2.25,(byte)-3,4.25f,(short)-5,6.25,-7,8.25,-9,10.25,-11,12.25,
                                    -13,14.25,-15,16.25,-17,18.25,-19,20.25,-21,22.25,-23,24.25);
        check(mixed(-1,2.25,(byte)-3,4.25f,(short)-5,6.25,-7,8.25,-9,10.25,-11,12.25,
                    -13,14.25,-15,16.25,-17,18.25,-19,20.25,-21,22.25,-23,24.25) == expected,
              "mixed native args and Java upcall");
        check(narrowStack(1, 2, 3, 4, 5, 6, (byte)-7, (short)-300, '\ufedc', true, -90000, 1234567890123L, 1.25f, 2.25f, 3.25f, 4.25f, 5.25f, 6.25f, 7.25f, 8.25f, 9.25f, 10.5, 11.5, 12.5, 13.5, 14.5, 15.5, 16.5, 17.5, 18.5) == javaNarrow(1, 2, 3, 4, 5, 6, (byte)-7, (short)-300, '\ufedc', true, -90000, 1234567890123L, 1.25f, 2.25f, 3.25f, 4.25f, 5.25f, 6.25f, 7.25f, 8.25f, 9.25f, 10.5, 11.5, 12.5, 13.5, 14.5, 15.5, 16.5, 17.5, 18.5), "packed narrow and FP overflow stack args");
        check(exercise() == 73, "JNI tables, va_list, objects and fields");
        check(reflected(GuestJniFixture.class.getDeclaredMethod("javaTiny", int.class, double.class, long.class)) == 1008.5,
              "method ID created through reflection");
        check(Registered.value() == 311, "initial registration");
        reregister();
        check(Registered.value() == 411, "unregister and re-register");
        try { throwFromGuest(); throw new AssertionError("exception missing"); }
        catch (IllegalStateException expectedException) {
            check("guest exception".equals(expectedException.getMessage()), "exception text");
        }
        final Throwable[] failures = new Throwable[4];
        Thread[] threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(new Runnable() { public void run() {
                try {
                    for (int j = 0; j < 100; j++) {
                        check(twelve(1,-2,3,-4,5,-6,7,-8,9,-10,11,-12) == -78, "thread native");
                        check(exercise() == 73, "thread shadow env");
                    }
                } catch (Throwable error) { failures[index] = error; }
            }});
            threads[i].start();
        }
        for (Thread thread : threads) thread.join();
        for (Throwable failure : failures) if (failure != null) throw new AssertionError(failure);
        System.out.println("M5_ART_JNI_PASS integer12=1 mixed24=1 register=1 upcalls=ellipsis,V,A objects=1 exceptions=1 threads=4 guest_created=4 attach_cycles=8 tls_dtors=4");
        NativeClosureInventory.emitIfRequested();
    }
}
