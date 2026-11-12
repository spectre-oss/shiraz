// SPDX-License-Identifier: GPL-3.0-only
// Deterministic interpreter workloads shaped like app frame code: small virtual and
// interface calls, static helpers, field arithmetic, array loops and short-lived objects.
// Prints one "INTERPRETER_BENCH <name> ms=<n> check=<n>" line per workload; the checks
// make every runtime compute the same result, so a faster run cannot be a wrong one.
public final class InterpreterBench {
    interface Shape {
        int area();
    }

    static class Rect implements Shape {
        private int width;
        private int height;

        Rect(int width, int height) {
            this.width = width;
            this.height = height;
        }

        int getWidth() {
            return width;
        }

        void setWidth(int value) {
            width = value;
        }

        @Override
        public int area() {
            return width * height;
        }
    }

    static final class Square extends Rect {
        Square(int side) {
            super(side, side);
        }

        @Override
        public int area() {
            return getWidth() * getWidth();
        }
    }

    static final class Particle {
        float x;
        float y;
        float dx;
        float dy;
    }

    private InterpreterBench() {}

    static int mix(int a, int b) {
        return (a * 31) ^ b;
    }

    static long virtualCalls(int rounds) {
        Rect rect = new Rect(3, 4);
        long sum = 0;
        for (int i = 0; i < rounds; i++) {
            rect.setWidth(i & 15);
            sum += rect.getWidth() + rect.area();
        }
        return sum;
    }

    static long interfaceCalls(int rounds) {
        Shape[] shapes = {new Rect(2, 3), new Square(5), new Rect(7, 1), new Square(2)};
        long sum = 0;
        for (int i = 0; i < rounds; i++) {
            sum += shapes[i & 3].area();
        }
        return sum;
    }

    static long staticCalls(int rounds) {
        int value = 1;
        for (int i = 0; i < rounds; i++) {
            value = mix(value, i);
        }
        return value;
    }

    static long particles(int rounds) {
        Particle[] particles = new Particle[64];
        for (int i = 0; i < particles.length; i++) {
            Particle p = new Particle();
            p.dx = (i % 7) - 3;
            p.dy = (i % 5) - 2;
            particles[i] = p;
        }
        for (int step = 0; step < rounds; step++) {
            for (Particle p : particles) {
                p.x += p.dx;
                p.y += p.dy;
                if (p.x < 0 || p.x > 360) {
                    p.dx = -p.dx;
                }
                if (p.y < 0 || p.y > 640) {
                    p.dy = -p.dy;
                }
            }
        }
        long sum = 0;
        for (Particle p : particles) {
            sum += (long) p.x * 7 + (long) p.y;
        }
        return sum;
    }

    static long arrays(int rounds) {
        int[] values = new int[256];
        long sum = 0;
        for (int round = 0; round < rounds; round++) {
            for (int i = 0; i < values.length; i++) {
                values[i] = values[(i + 1) & 255] + i + round;
                sum += values[i] & 1023;
            }
        }
        return sum;
    }

    static long allocation(int rounds) {
        long sum = 0;
        for (int i = 0; i < rounds; i++) {
            Rect rect = new Rect(i & 7, 3);
            StringBuilder text = new StringBuilder(8).append(rect.area()).append('x');
            sum += text.length();
        }
        return sum;
    }

    static void run(String name, long check, long started) {
        long ms = (System.nanoTime() - started) / 1000000;
        System.out.println("INTERPRETER_BENCH " + name + " ms=" + ms + " check=" + check);
    }

    public static void main(String[] args) {
        int scale = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        long total = System.nanoTime();
        long started = System.nanoTime();
        run("virtual", virtualCalls(2000000 * scale), started);
        started = System.nanoTime();
        run("interface", interfaceCalls(2000000 * scale), started);
        started = System.nanoTime();
        run("static", staticCalls(2000000 * scale), started);
        started = System.nanoTime();
        run("particles", particles(20000 * scale), started);
        started = System.nanoTime();
        run("arrays", arrays(4000 * scale), started);
        started = System.nanoTime();
        run("allocation", allocation(200000 * scale), started);
        run("total", 0, total);
    }
}
