// SPDX-License-Identifier: GPL-3.0-only
/** Calls actual APK SWIG classes and unchanged native Core code. No UI claim. */
public final class OsmAndCoreNativeRoundTrip {
    public static void main(String[] args) throws Exception {
        boolean qtLoaded=false;
        try {
        String directory = System.getProperty("guest.osmand.directory");
        String[] libraries = {"libc++_shared.so", "libQt5Core.so", "libQt5Network.so",
                "libQt5Sql.so", "libOsmAndCoreWithJNI.so"};
        for (String library : libraries) {
            System.load(new java.io.File(directory, library).getAbsolutePath());
            if(library.equals("libQt5Core.so"))qtLoaded=true;
            System.out.println("M5_OSMAND_LIBRARY_LOADED " + library);
        }
        Class<?> point = Class.forName("net.osmand.core.jni.PointI");
        Class<?> core = Class.forName("net.osmand.core.jni.OsmAndCore");
        java.lang.reflect.Constructor<?> construct = point.getConstructor(int.class, int.class);
        Object a = construct.newInstance(0, 0), b = construct.newInstance(10, 10);
        Object c = construct.newInstance(0, 10), d = construct.newInstance(10, 0);
        Object result = construct.newInstance(-1, -1);
        try {
            java.lang.reflect.Method intersect = core.getMethod("computeLineSegmentIntersection",
                    point, point, point, point, point);
            if (!Boolean.TRUE.equals(intersect.invoke(null, a, b, c, d, result)))
                throw new AssertionError("crossing segments rejected");
            if (!Integer.valueOf(5).equals(point.getMethod("getX").invoke(result))
                    || !Integer.valueOf(5).equals(point.getMethod("getY").invoke(result)))
                throw new AssertionError("native intersection coordinates wrong");
            java.lang.reflect.Method cross = core.getMethod("crossProductSign", point, point, point);
            int forward = ((Integer) cross.invoke(null, a, b, c)).intValue();
            int reverse = ((Integer) cross.invoke(null, a, c, b)).intValue();
            int collinear = ((Integer) cross.invoke(null, a, b, b)).intValue();
            if (forward == 0 || reverse != -forward || collinear != 0)
                throw new AssertionError("native orientation calculations wrong");
        } finally {
            java.lang.reflect.Method delete = point.getMethod("delete");
            for (Object value : new Object[]{a, b, c, d, result}) delete.invoke(value);
        }
        System.out.println("M5_OSMAND_CORE_JNI_PASS native_objects=5 intersection=5,5 orientation=3 deleted=5 app_execution=0");
        System.load(new java.io.File(directory, "libandroidx.graphics.path.so").getAbsolutePath());
        AndroidxPathRoundTrip.run();
        System.out.println("M5_OSMAND_ALL_LIBRARIES_PASS required=7 images=7 jni_onload=executed app_execution=0");
        } finally {
            if(qtLoaded) {
                Class<?> nativeQt=Class.forName("org.qtproject.qt5.android.QtNative");
                java.lang.reflect.Field field=nativeQt.getDeclaredField("m_qtThread");field.setAccessible(true);
                Object thread=field.get(null);
                if(thread==null)throw new AssertionError("Qt helper thread missing");
                java.lang.reflect.Method exit=thread.getClass().getDeclaredMethod("exit");exit.setAccessible(true);exit.invoke(thread);
                System.out.println("M5_QT_THREAD_SHUTDOWN_PASS actual_apk_exit=1");
            }
        }
    }
}
