// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Android-Zygote-style pre-warmed standby runner for standalone ART.
 *
 * Boots with boot.art + shiraz-framework.odex, pre-loads native host libraries and
 * core framework classes, then blocks on a private Unix SEQPACKET socket until
 * ShirazDaemon hands off an app launch (APK path, app_lib, environment, stdio &
 * broker descriptors) with zero VM cold-boot latency.
 */
public final class WarmPoolRunner {
    private static native boolean signalReadyNative(int sockFd);
    private static native String[] receiveAndPrepareNative(int sockFd) throws java.io.IOException;

    // Keep removed warm-pool dex Element strongly reachable so GC never finalizes
    // shiraz-warm-pool.jar's DexFile while WarmPoolRunner is still live on the stack.
    @SuppressWarnings("unused")
    private static final List<Object> RETAINED_WARM_ELEMENTS = new ArrayList<Object>();

    private WarmPoolRunner() {}

    private static void prewarm() {
        String[] libs = {
            "shiraz-warm-pool",
            "shiraz-host",
            "shiraz-jni",
            "shiraz-resources",
            "shiraz-bitmap",
            "shiraz-text",
            "shiraz-sqlite",
            "shiraz-path",
            "shiraz-image",
            "shiraz-audio",
            "shiraz-notifications",
        };
        for (String lib : libs) {
            try {
                System.loadLibrary(lib);
            } catch (UnsatisfiedLinkError ignored) {
                if ("shiraz-warm-pool".equals(lib)) {
                    throw ignored;
                }
            }
        }
        try {
            HostTlsProvider.install();
        } catch (Throwable ignored) {
            // ActivityRunner.main will install/report if TLS is required.
        }
        String[] classes = {
            "shiraz.runtime.ActivityRunner",
            "shiraz.runtime.NativeSurface",
            "shiraz.runtime.HostPackageManager",
            "shiraz.runtime.HostLayoutInflater",
            "shiraz.runtime.ServiceProcess",
            "android.content.res.Resources",
            "android.graphics.Bitmap",
            "android.graphics.Canvas",
            "android.graphics.Paint",
            "android.graphics.Typeface",
            "android.view.View",
            "android.view.ViewGroup",
            "android.widget.TextView",
            "android.database.sqlite.SQLiteDatabase",
        };
        for (String name : classes) {
            try {
                Class.forName(name);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Strip shiraz-warm-pool.jar from the system PathClassLoader's dexElements so the
     * runtime ClassLoaderContext stays exact PCL[shiraz-framework.jar] when ART verifies
     * the precompiled AOT .odex for the launched APK, then prepend appLibDir and add cpApk.
     */
    @SuppressWarnings("unchecked")
    private static void attachAppPaths(String cpApk, String appLibDir) throws Exception {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class<?> baseCls = Class.forName("dalvik.system.BaseDexClassLoader");
        if (!baseCls.isInstance(loader)) {
            throw new IllegalStateException("System class loader is not a BaseDexClassLoader");
        }
        Field pathListField = baseCls.getDeclaredField("pathList");
        pathListField.setAccessible(true);
        Object pathList = pathListField.get(loader);
        Class<?> pathListCls = pathList.getClass();

        // 1. Remove warm-pool helper jar from dexElements so CLC matches PCL[shiraz-framework.jar]
        try {
            Field dexElementsField = pathListCls.getDeclaredField("dexElements");
            dexElementsField.setAccessible(true);
            Object oldDex = dexElementsField.get(pathList);
            if (oldDex != null && oldDex.getClass().isArray()) {
                int len = Array.getLength(oldDex);
                List<Object> kept = new ArrayList<Object>(len);
                for (int i = 0; i < len; i++) {
                    Object elem = Array.get(oldDex, i);
                    String desc = elem == null ? "" : elem.toString();
                    if (desc.contains("shiraz-warm-pool.jar")) {
                        RETAINED_WARM_ELEMENTS.add(elem);
                    } else {
                        kept.add(elem);
                    }
                }
                if (kept.size() != len && !kept.isEmpty()) {
                    Object filtered = Array.newInstance(oldDex.getClass().getComponentType(), kept.size());
                    for (int i = 0; i < kept.size(); i++) {
                        Array.set(filtered, i, kept.get(i));
                    }
                    dexElementsField.set(pathList, filtered);
                }
            }
        } catch (Throwable ignored) {
            // Fall through: addDexPath still works (falls back to JIT if CLC differs).
        }

        // 2. Prepend appLibDir to nativeLibraryDirectories & nativeLibraryPathElements
        if (appLibDir != null && !appLibDir.isEmpty()) {
            File libDir = new File(appLibDir);
            try {
                Field dirsField = pathListCls.getDeclaredField("nativeLibraryDirectories");
                dirsField.setAccessible(true);
                List<File> oldDirs = (List<File>) dirsField.get(pathList);
                List<File> updatedDirs = new ArrayList<File>();
                updatedDirs.add(libDir);
                if (oldDirs != null) {
                    updatedDirs.addAll(oldDirs);
                }
                dirsField.set(pathList, updatedDirs);
            } catch (Throwable ignored) {
            }
            try {
                Method makeNative = pathListCls.getDeclaredMethod("makePathElements", List.class);
                makeNative.setAccessible(true);
                Object added = makeNative.invoke(null, Collections.singletonList(libDir));
                Field elemsField = pathListCls.getDeclaredField("nativeLibraryPathElements");
                elemsField.setAccessible(true);
                Object oldElems = elemsField.get(pathList);
                if (added != null && oldElems != null && added.getClass().isArray() && oldElems.getClass().isArray()) {
                    int addLen = Array.getLength(added);
                    int oldLen = Array.getLength(oldElems);
                    Object combined = Array.newInstance(oldElems.getClass().getComponentType(), addLen + oldLen);
                    for (int i = 0; i < addLen; i++) {
                        Array.set(combined, i, Array.get(added, i));
                    }
                    for (int i = 0; i < oldLen; i++) {
                        Array.set(combined, addLen + i, Array.get(oldElems, i));
                    }
                    elemsField.set(pathList, combined);
                }
            } catch (Throwable ignored) {
            }
        }

        // 3. Add the app APK (staged AOT path) to the system class loader
        if (cpApk != null && !cpApk.isEmpty()) {
            Method addDex = baseCls.getMethod("addDexPath", String.class);
            addDex.invoke(loader, cpApk);
        }
    }

    public static void main(String[] args) throws Throwable {
        // Handle secondary ServiceRunner child spawned via ServiceProcess.start()
        if (args.length >= 11 && "shiraz.runtime.ServiceRunner".equals(args[1])) {
            String[] svcArgs = Arrays.copyOfRange(args, 2, args.length);
            Properties meta = new Properties();
            try (InputStreamReader r = new InputStreamReader(new FileInputStream(svcArgs[8]), StandardCharsets.UTF_8)) {
                meta.load(r);
            }
            attachAppPaths(meta.getProperty("sourceDir", ""), meta.getProperty("nativeLibraryDir", ""));
            Class<?> svcRunner = Class.forName("shiraz.runtime.ServiceRunner");
            Method mainMethod = svcRunner.getMethod("main", String[].class);
            mainMethod.invoke(null, (Object) svcArgs);
            return;
        }

        if (args.length < 1) {
            throw new IllegalArgumentException("WarmPoolRunner requires control socket FD");
        }
        int controlFd = Integer.parseInt(args[0]);
        prewarm();
        if (!signalReadyNative(controlFd)) {
            System.exit(0);
            return;
        }
        String[] payload = receiveAndPrepareNative(controlFd);
        if (payload == null) {
            System.exit(0);
            return;
        }
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true));
        String cpApk = payload[0];
        String appLibDir = payload[1];
        int propCount = Integer.parseInt(payload[2]);
        int cursor = 3;
        for (int i = 0; i < propCount; i++) {
            String k = payload[cursor++];
            String v = payload[cursor++];
            if (k != null && !k.isEmpty()) {
                String existing = System.getProperty(k);
                if (existing == null || !existing.equals(v)) {
                    System.setProperty(k, v == null ? "" : v);
                }
            }
        }
        String[] activityArgs = Arrays.copyOfRange(payload, cursor, payload.length);
        attachAppPaths(cpApk, appLibDir);
        ActivityRunner.main(activityArgs);
    }
}
