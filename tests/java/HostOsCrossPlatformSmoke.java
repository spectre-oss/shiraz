// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.util.Arrays;
import java.util.List;

/** Cross-platform verification for HostOs and ExternalLauncher across Linux, macOS, and Windows. */
public final class HostOsCrossPlatformSmoke {
    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        check(HostOs.DARWIN.equals(HostOs.classify("Darwin", "Linux")), "uname Darwin must win over ART os.name=Linux");
        check(HostOs.LINUX.equals(HostOs.classify("Linux", "Linux")), "uname Linux");
        check(HostOs.WINDOWS.equals(HostOs.classify("MINGW64_NT-10.0", "Windows 11")), "uname MINGW");
        check(HostOs.WINDOWS.equals(HostOs.classify(null, "Windows 11")), "os.name Windows 11");
        check(HostOs.DARWIN.equals(HostOs.classify(null, "Mac OS X")), "os.name Mac OS X");
        check(HostOs.LINUX.equals(HostOs.classify(null, "Linux")), "os.name Linux");

        check("libshiraz-video.so".equals(HostOs.sharedLibraryName("shiraz-video", HostOs.LINUX)), "linux lib name");
        check("libshiraz-video.so".equals(HostOs.sharedLibraryName("shiraz-video", HostOs.DARWIN)), "darwin lib name");
        check("shiraz-video.dll".equals(HostOs.sharedLibraryName("shiraz-video", HostOs.WINDOWS)), "windows lib name");

        check("/dev/null".equals(HostOs.nullDevicePath(HostOs.LINUX)), "linux null device");
        check("/dev/null".equals(HostOs.nullDevicePath(HostOs.DARWIN)), "darwin null device");
        check("NUL".equals(HostOs.nullDevicePath(HostOs.WINDOWS)), "windows null device");

        List<String> linCmd = ExternalLauncher.commandFor("https://example.org", HostOs.LINUX);
        check(Arrays.asList("xdg-open", "https://example.org").equals(linCmd), "linux opener: " + linCmd);

        List<String> macCmd = ExternalLauncher.commandFor("https://example.org", HostOs.DARWIN);
        check(Arrays.asList("/usr/bin/open", "https://example.org").equals(macCmd), "darwin opener: " + macCmd);

        List<String> winCmd = ExternalLauncher.commandFor("https://example.org", HostOs.WINDOWS);
        check(Arrays.asList("rundll32.exe", "url.dll,FileProtocolHandler", "https://example.org").equals(winCmd),
                "windows opener: " + winCmd);

        String active = HostOs.name();
        check(HostOs.LINUX.equals(active) || HostOs.DARWIN.equals(active) || HostOs.WINDOWS.equals(active),
                "valid active OS: " + active);
        System.out.println("HOST_OS_CROSS_PLATFORM_SMOKE_PASS active=" + active);
    }
}
