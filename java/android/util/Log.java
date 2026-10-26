// SPDX-License-Identifier: GPL-3.0-only
package android.util;
import dalvik.system.BlockGuard;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/** Host logging backed by the application's captured stderr, with Android tag levels. */
public final class Log {
    public static final int VERBOSE = 2, DEBUG = 3, INFO = 4, WARN = 5, ERROR = 6, ASSERT = 7;
    private Log() { }
    private static int threshold(String tag) {
        String value = System.getProperty("log.tag." + String.valueOf(tag));
        if (value == null) value = System.getProperty("log.tag");
        if (value == null) value = System.getenv("SHIRAZ_LOG_LEVEL");
        if (value == null) value = "INFO";
        if (value.isEmpty()) return INFO;
        switch (Character.toUpperCase(value.charAt(0))) {
            case 'V': return VERBOSE;
            case 'D': return DEBUG;
            case 'I': return INFO;
            case 'W': return WARN;
            case 'E': return ERROR;
            case 'A': return ASSERT;
            case 'S': return ASSERT + 1;
            default: return INFO;
        }
    }
    public static boolean isLoggable(String tag, int level) { return level >= threshold(tag); }
    public static int println(int priority, String tag, String message) {
        if (priority < VERBOSE || priority > ASSERT) throw new IllegalArgumentException("log priority");
        if (!isLoggable(tag, priority)) return 0;
        String text = String.valueOf(message);
        char label = "VDIWEA".charAt(priority - VERBOSE);
        String prefix = label + "/" + String.valueOf(tag) + ": ";
        // Android liblog does not perform Java I/O; host stderr must likewise avoid
        // triggering BlockGuard recursively while a PrintStream buffer is active.
        BlockGuard.Policy previous = BlockGuard.getThreadPolicy();
        BlockGuard.setThreadPolicy(BlockGuard.LAX_POLICY);
        try {
            synchronized (System.err) {
                for (String line : text.split("\n", -1)) System.err.println(prefix + line);
            }
        } finally { BlockGuard.setThreadPolicy(previous); }
        return text.getBytes(StandardCharsets.UTF_8).length;
    }
    public static String getStackTraceString(Throwable error) {
        if (error == null) return "";
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof UnknownHostException) return "";
        StringWriter text = new StringWriter();
        PrintWriter writer = new PrintWriter(text); error.printStackTrace(writer); writer.flush();
        return text.toString();
    }
    private static String trace(String message, Throwable error) { return String.valueOf(message) + '\n' + getStackTraceString(error); }
    public static int v(String tag, String message) { return println(VERBOSE, tag, message); }
    public static int v(String tag, String message, Throwable error) { return v(tag, trace(message, error)); }
    public static int d(String tag, String message) { return println(DEBUG, tag, message); }
    public static int d(String tag, String message, Throwable error) { return d(tag, trace(message, error)); }
    public static int i(String tag, String message) { return println(INFO, tag, message); }
    public static int i(String tag, String message, Throwable error) { return i(tag, trace(message, error)); }
    public static int w(String tag, String message) { return println(WARN, tag, message); }
    public static int w(String tag, String message, Throwable error) { return w(tag, trace(message, error)); }
    public static int w(String tag, Throwable error) { return w(tag, getStackTraceString(error)); }
    public static int e(String tag, String message) { return println(ERROR, tag, message); }
    public static int e(String tag, String message, Throwable error) { return e(tag, trace(message, error)); }
    public static int wtf(String tag, String message) { return println(ASSERT, tag, message); }
    public static int wtf(String tag, String message, Throwable error) { return wtf(tag, trace(message, error)); }
    public static int wtf(String tag, Throwable error) { return wtf(tag, getStackTraceString(error)); }
}
