// SPDX-License-Identifier: GPL-3.0-only
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;

/** Exercises retained native methods in the unchanged APK, not a substitute AndroidX class. */
public final class AndroidxPathRoundTrip {
    private static final int MOVE = 0, LINE = 1, QUADRATIC = 2, CUBIC = 4, CLOSE = 5, DONE = 6;
    private static final float SENTINEL = 123456.75f;
    // Native API27 iterators borrow the Path's view. Keep explicit GC roots
    // until after destroy; an unused local alone is not a lifetime guarantee.
    private static final ArrayList<Object> OWNERS = new ArrayList<Object>();
    private static Object apkReceiver;
    private static int created, destroyed;

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static Object call(Method method, Object receiver, Object... arguments) throws Exception {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw error;
        }
    }
    private static Method nativeMethod(Class<?> owner, String name, Class<?>... parameters) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameters);
        require(Modifier.isNative(method.getModifiers()), name + " must be the APK's actual native method");
        method.setAccessible(true);
        return method;
    }
    private static Object allocateApkReceiver(Class<?> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton;
        try { singleton = unsafeType.getDeclaredField("THE_ONE"); }
        catch (NoSuchFieldException expected) { singleton = unsafeType.getDeclaredField("theUnsafe"); }
        singleton.setAccessible(true);
        Method allocate = unsafeType.getMethod("allocateInstance", Class.class);
        return call(allocate, singleton.get(null), type);
    }
    private static Field backingPathField(Class<?> path) throws Exception {
        try {
            path.getDeclaredField("mNativePath");
            throw new AssertionError("ordinary JNI must not find a physical mNativePath/SkPath field");
        } catch (NoSuchFieldException expected) {
            // Only audited Darwin shadow JNI aliases this name to a real field.
        }
        return path.getDeclaredField("shirazNativePath");
    }
    private static final class Api {
        final Class<?> path = Class.forName("android.graphics.Path");
        final Class<?> matrix = Class.forName("android.graphics.Matrix");
        final Class<?> implementation = Class.forName("androidx.graphics.path.PathIteratorPreApi34Impl");
        final Constructor<?> newPath = path.getConstructor();
        final Constructor<?> copyPath = path.getConstructor(path);
        final Method move = path.getMethod("moveTo", float.class, float.class);
        final Method line = path.getMethod("lineTo", float.class, float.class);
        final Method quadratic = path.getMethod("quadTo", float.class, float.class, float.class, float.class);
        final Method cubic = path.getMethod("cubicTo", float.class, float.class, float.class, float.class, float.class, float.class);
        final Method closePath = path.getMethod("close");
        final Method reset = path.getMethod("reset");
        final Method set = path.getMethod("set", path);
        final Method offset = path.getMethod("offset", float.class, float.class);
        final Method offsetInto = path.getMethod("offset", float.class, float.class, path);
        final Method transform = path.getMethod("transform", matrix);
        final Method transformInto = path.getMethod("transform", matrix, path);
        final Method create = nativeMethod(implementation, "createInternalPathIterator", path, int.class, float.class);
        final Method destroy = nativeMethod(implementation, "destroyInternalPathIterator", long.class);
        final Method hasNext = nativeMethod(implementation, "internalPathIteratorHasNext", long.class);
        final Method peek = nativeMethod(implementation, "internalPathIteratorPeek", long.class);
        final Method next = nativeMethod(implementation, "internalPathIteratorNext", long.class, float[].class, int.class);
        final Method rawSize = nativeMethod(implementation, "internalPathIteratorRawSize", long.class);
        final Method size = nativeMethod(implementation, "internalPathIteratorSize", long.class);
        final Field receiverHandle = implementation.getDeclaredField("internalPathIterator");
        final Field nativePath = backingPathField(path);
        final Object receiver;
        Api() throws Exception {
            require(implementation.getDeclaredConstructors().length == 0,
                    "expected unchanged R8-stripped native-only AndroidX class");
            require(!Modifier.isAbstract(implementation.getModifiers()), "native receiver class must be concrete");
            int sdk = Class.forName("android.os.Build$VERSION").getField("SDK_INT").getInt(null);
            require(sdk == 27, "PathRef26 native layout requires actual API27 profile");
            receiverHandle.setAccessible(true);
            nativePath.setAccessible(true);
            receiver = allocateApkReceiver(implementation);
            apkReceiver = receiver;
            require(receiverHandle.getLong(receiver) == 0, "allocated APK receiver starts with no finalizer-owned handle");
        }
        Object path() throws Exception { return newPath.newInstance(); }
        Object shape(float dx, float dy) throws Exception {
            Object value = path();
            build(value, dx, dy);
            return value;
        }
        void build(Object value, float dx, float dy) throws Exception {
            call(move, value, 1 + dx, 2 + dy);
            call(line, value, 3 + dx, 4 + dy);
            call(quadratic, value, 5 + dx, 7 + dy, 8 + dx, 10 + dy);
            call(cubic, value, 11 + dx, 13 + dy, 14 + dx, 16 + dy, 17 + dx, 19 + dy);
            call(closePath, value);
        }
    }
    private static final class Cursor implements AutoCloseable {
        final Api api;
        final Object owner;
        long handle;
        Cursor(Api api, Object owner) throws Exception {
            this.api = api;
            this.owner = owner;
            OWNERS.add(this);
            try {
                // ConicEvaluation.AsConic has native ordinal0. No invented
                // enum/wrapper class is added to the APK's stripped package.
                handle = ((Long) call(api.create, api.receiver, owner, 0, 0.25f)).longValue();
                require(handle != 0, "actual native iterator created");
                ++created;
                require(api.nativePath.getLong(owner) != 0, "native GetLongField prepared the real Path mirror");
            } catch (Throwable error) {
                if (handle != 0) {
                    try { call(api.destroy, api.receiver, handle); handle = 0; ++destroyed; }
                    catch (Throwable release) { error.addSuppressed(release); }
                }
                if (handle == 0) OWNERS.remove(this);
                if (error instanceof Exception) throw (Exception) error;
                if (error instanceof Error) throw (Error) error;
                throw new AssertionError(error);
            }
        }
        boolean hasNext() throws Exception { return ((Boolean) call(api.hasNext, api.receiver, handle)).booleanValue(); }
        int peek() throws Exception { return ((Integer) call(api.peek, api.receiver, handle)).intValue(); }
        void counts(int expected) throws Exception {
            require(((Integer) call(api.rawSize, api.receiver, handle)).intValue() == expected, "native raw verb count " + expected);
            require(((Integer) call(api.size, api.receiver, handle)).intValue() == expected, "native evaluated verb count " + expected);
        }
        int next(float[] output, int offset) throws Exception {
            return ((Integer) call(api.next, api.receiver, handle, output, offset)).intValue();
        }
        @Override public void close() throws Exception {
            if (handle == 0) return;
            call(api.destroy, api.receiver, handle);
            handle = 0;
            ++destroyed;
            try {
                require(owner.getClass() == api.path && api.nativePath.getLong(owner) != 0,
                        "Path owner remains strongly live through iterator destruction");
            } finally { OWNERS.remove(this); }
            require(api.receiverHandle.getLong(api.receiver) == 0, "APK finalizer field remains zero; no double destruction");
        }
    }
    private static final class Segment {
        final int verb;
        final float[] points;
        Segment(int verb, float... points) { this.verb = verb; this.points = points; }
    }
    private static Segment[] shape(float sx, float sy, float dx, float dy) {
        return new Segment[] {
            new Segment(MOVE, 1*sx+dx, 2*sy+dy),
            new Segment(LINE, 1*sx+dx, 2*sy+dy, 3*sx+dx, 4*sy+dy),
            new Segment(QUADRATIC, 3*sx+dx, 4*sy+dy, 5*sx+dx, 7*sy+dy, 8*sx+dx, 10*sy+dy),
            new Segment(CUBIC, 8*sx+dx, 10*sy+dy, 11*sx+dx, 13*sy+dy, 14*sx+dx, 16*sy+dy, 17*sx+dx, 19*sy+dy),
            new Segment(CLOSE)
        };
    }
    private static void guards(float[] output, int offset) {
        for (int i = 0; i < output.length; ++i)
            if (i < offset || i >= offset + 8)
                require(Float.floatToIntBits(output[i]) == Float.floatToIntBits(SENTINEL), "native next array-offset guard " + i);
    }
    private static void sequence(Cursor cursor, Segment[] expected) throws Exception {
        cursor.counts(expected.length);
        for (int index = 0; index < expected.length; ++index) {
            Segment segment = expected[index];
            require(cursor.hasNext() && cursor.peek() == segment.verb && cursor.peek() == segment.verb,
                    "native peek/hasNext verb " + index + " expected=" + segment.verb);
            float[] output = new float[14]; Arrays.fill(output, SENTINEL);
            require(cursor.next(output, 3) == segment.verb, "native next verb " + index);
            guards(output, 3);
            for (int i = 0; i < segment.points.length; ++i)
                require(Math.abs(output[3+i] - segment.points[i]) <= 0.0001f,
                        "native point " + index + ":" + i + " expected=" + segment.points[i] + " actual=" + output[3+i]);
        }
        require(!cursor.hasNext() && cursor.peek() == DONE, "native iterator exhaustion");
        float[] output = new float[14]; Arrays.fill(output, SENTINEL);
        require(cursor.next(output, 3) == DONE && cursor.next(output, 3) == DONE, "native repeated Done");
        guards(output, 3);
        cursor.counts(expected.length);
    }
    private static void expect(Api api, Object path, Segment... segments) throws Exception {
        try (Cursor cursor = new Cursor(api, path)) { sequence(cursor, segments); }
    }
    /** Run only after loading the unchanged libandroidx.graphics.path.so. */
    public static void run() throws Exception {
        Api api = new Api();
        Object value = api.path();
        expect(api, value);
        call(api.move, value, 2f, 3f);
        expect(api, value, new Segment(MOVE, 2, 3));
        call(api.reset, value);
        call(api.line, value, 4f, 6f);
        expect(api, value, new Segment(MOVE, 0, 0), new Segment(LINE, 0, 0, 4, 6));
        call(api.reset, value); api.build(value, 0, 0);
        expect(api, value, shape(1, 1, 0, 0));
        call(api.closePath, value);
        expect(api, value, shape(1, 1, 0, 0));
        call(api.set, value, value);
        expect(api, value, shape(1, 1, 0, 0));

        Object copied = api.copyPath.newInstance(value);
        try (Cursor retained = new Cursor(api, copied)) {
            call(api.reset, value); call(api.move, value, -2f, -3f); call(api.line, value, -4f, -6f);
            expect(api, value, new Segment(MOVE, -2, -3), new Segment(LINE, -2, -3, -4, -6));
            System.gc();
            sequence(retained, shape(1, 1, 0, 0));
        }
        Object assigned = api.path(); call(api.set, assigned, copied);
        call(api.reset, copied); expect(api, copied);
        expect(api, assigned, shape(1, 1, 0, 0));
        Object translated = api.path(); call(api.offsetInto, assigned, 10f, -4f, translated);
        expect(api, assigned, shape(1, 1, 0, 0));
        expect(api, translated, shape(1, 1, 10, -4));
        call(api.offset, translated, -3f, 2f);
        expect(api, translated, shape(1, 1, 7, -2));

        Object matrix = api.matrix.getConstructor().newInstance();
        call(api.matrix.getMethod("setValues", float[].class), matrix,
                (Object) new float[] {2, 0, 5, 0, 3, -7, 0, 0, 1});
        Object transformed = api.path(); call(api.transformInto, assigned, matrix, transformed);
        expect(api, assigned, shape(1, 1, 0, 0));
        expect(api, transformed, shape(2, 3, 5, -7));
        call(api.transform, assigned, matrix);
        expect(api, assigned, shape(2, 3, 5, -7));

        Cursor first = new Cursor(api, transformed);
        try (Cursor second = new Cursor(api, transformed)) {
            first.close(); // Destroying one reader must not free the Path's view.
            System.gc();
            sequence(second, shape(2, 3, 5, -7));
        } finally { first.close(); }
        for (int i = 0; i < 64; ++i) {
            call(api.reset, value); api.build(value, i, -i);
            expect(api, value, shape(1, 1, i, -i));
            if (i % 8 == 0) System.gc();
        }
        call(api.reset, value); expect(api, value);
        require(OWNERS.isEmpty() && created == destroyed && apkReceiver == api.receiver,
                "all real native handles destroyed with owner roots retained");
        System.out.println("M5_ANDROIDX_PATH_JNI_PASS apk_native_only=1 api=27 verbs=Move,Line,Quadratic,Cubic,Close native_counts=1 points=1 array_offset=3 empty=1 path_offset=1 affine_transform=1 set_copy=1 dual_iterators=1 mutation_cycles=64 retained_owner=1 iterator_pairs="
                + destroyed + " app_execution=0");
    }
    public static void main(String[] args) throws Exception { run(); }
}
