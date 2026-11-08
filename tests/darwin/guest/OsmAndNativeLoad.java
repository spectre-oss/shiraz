// SPDX-License-Identifier: GPL-3.0-only
/** Runs the unchanged APK class and ELF; this is not a UI acceptance test. */
public final class OsmAndNativeLoad {
    private static native int nativeOpenCount(String path);
    public static void main(String[] args) throws Exception {
        Class<?> actual = Class.forName("net.osmand.NativeLibrary", false,
                OsmAndNativeLoad.class.getClassLoader());
        System.load(System.getProperty("guest.osmand.library"));
        java.lang.reflect.Method open = actual.getDeclaredMethod("initBinaryMapFile",
                String.class, boolean.class, boolean.class);
        java.lang.reflect.Method close = actual.getDeclaredMethod("closeBinaryMapFile", String.class);
        open.setAccessible(true);
        close.setAccessible(true);
        Object receiver = null;
        if (!java.lang.reflect.Modifier.isStatic(open.getModifiers())) {
            java.lang.reflect.Constructor<?> constructor = actual.getDeclaredConstructor();
            constructor.setAccessible(true);
            receiver = constructor.newInstance();
        }
        System.load(System.getProperty("guest.osmand.observer"));
        String map = System.getProperty("guest.osmand.map");
        if (nativeOpenCount(map) != 0) throw new AssertionError("basemap already open");
        if (!Boolean.TRUE.equals(open.invoke(receiver, map, true, false)))
            throw new AssertionError("real APK basemap did not open");
        if (nativeOpenCount(map) <= 0) throw new AssertionError("basemap descriptor not observed");
        // Upstream native close returns void despite this APK's boolean Java
        // declaration. Verify its descriptor side effect instead of garbage x0.
        close.invoke(receiver, map);
        if (nativeOpenCount(map) != 0) throw new AssertionError("basemap descriptor not closed");
        if (!Boolean.FALSE.equals(open.invoke(receiver, map + ".does-not-exist", true, false)))
            throw new AssertionError("missing basemap incorrectly accepted");
        System.out.println("M5_OSMAND_NATIVE_MAP_PASS unchanged_basemap=1 open=1 close=1 app_execution=0");
        if (Boolean.getBoolean("guest.osmand.core")) OsmAndCoreNativeRoundTrip.main(args);
        System.out.println("M5_OSMAND_NATIVE_LOAD_PASS apk_class=" + actual.getName()
                + " library=osmand constructors_and_jni_onload=1 app_execution=0");
        NativeClosureInventory.emitIfRequested();
    }
}
