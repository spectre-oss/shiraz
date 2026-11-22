// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.util.Locale;

/**
 * Host OS identity across Linux, macOS (Darwin), and Windows.
 * ART reports os.name=Linux on Unix hosts, so Unix detection reads uname(2) first
 * and falls back to os.name when running under a standard host JVM.
 */
public final class HostOs {
    public static final String LINUX = "linux";
    public static final String DARWIN = "darwin";
    public static final String WINDOWS = "windows";

    private HostOs() {}

    private static final class Kernel {
        static final String OS = detect();
    }

    static String classify(String sysname, String osName) {
        if (sysname != null && !sysname.isEmpty()) {
            String lower = sysname.toLowerCase(Locale.ROOT);
            if (lower.equals("darwin")) return DARWIN;
            if (lower.startsWith("mingw") || lower.startsWith("msys") || lower.startsWith("cygwin")
                    || lower.startsWith("win")) {
                return WINDOWS;
            }
            if (lower.equals("linux")) return LINUX;
        }
        String name = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (name.startsWith("mac os") || name.equals("darwin")) return DARWIN;
        if (name.startsWith("win")) return WINDOWS;
        return LINUX;
    }

    // libcore.io.Linux natives exist only under ART. On a host JVM (the OpenJDK
    // lanes of smokes) Os.uname() throws UnsatisfiedLinkError, and there os.name
    // is accurate ("Linux", "Mac OS X", "Windows 11"). Catch inside the
    // computation so a failed probe never poisons this class with NoClassDefFoundError.
    private static String detect() {
        String osName = System.getProperty("os.name", "");
        try {
            return classify(android.system.Os.uname().sysname, osName);
        } catch (LinkageError hostJvm) {
            return classify(null, osName);
        }
    }

    /** Normalized host OS identifier: "linux", "darwin", or "windows". */
    public static String name() { return Kernel.OS; }

    /** True on Linux hosts. */
    public static boolean linux() { return LINUX.equals(Kernel.OS); }

    /** True on macOS (XNU), where /proc, memfd and Linux-only paths do not exist. */
    public static boolean darwin() { return DARWIN.equals(Kernel.OS); }

    /** True on Windows hosts (Win32 / MSYS / MinGW). */
    public static boolean windows() { return WINDOWS.equals(Kernel.OS); }

    /** Runtime shared library filename for a Shiraz native module on the given OS. */
    public static String sharedLibraryName(String stem, String os) {
        if (stem == null || stem.isEmpty() || stem.indexOf('/') >= 0 || stem.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Invalid library stem: " + stem);
        }
        return WINDOWS.equals(os) ? stem + ".dll" : "lib" + stem + ".so";
    }

    public static String sharedLibraryName(String stem) {
        return sharedLibraryName(stem, Kernel.OS);
    }

    /** Platform null-device path ("NUL" on Windows, "/dev/null" on Unix). */
    public static String nullDevicePath(String os) {
        return WINDOWS.equals(os) ? "NUL" : "/dev/null";
    }

    public static String nullDevicePath() {
        return nullDevicePath(Kernel.OS);
    }
}
