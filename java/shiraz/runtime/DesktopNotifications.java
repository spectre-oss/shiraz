// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.io.Closeable;
/** Owned session-bus notification connection; no shell commands or GTK dependency. */
public final class DesktopNotifications implements Closeable {
    static { System.loadLibrary("shiraz-notifications"); }
    private long handle;
    private final java.util.LinkedHashSet<Long> posted = new java.util.LinkedHashSet<Long>();
    public DesktopNotifications(String application) { handle = open(application); }
    private long live() { if (handle == 0) throw new IllegalStateException("Notification connection closed"); return handle; }
    public synchronized String[] capabilities() { return capabilities(live()); }
    /** Replacing a notification of this connection that the server already closed posts a new notification. */
    public synchronized long post(long replaces, String summary, String body, int urgency, int timeout, boolean suppressSound) {
        long handle = live();
        if (replaces != 0 && posted.contains(replaces) && !active(handle, replaces)) replaces = 0;
        long id = post(handle, replaces, summary, body, urgency, timeout, suppressSound);
        posted.remove(id);
        posted.add(id);
        if (posted.size() > 1024) posted.remove(posted.iterator().next());
        return id;
    }
    private static boolean active(long handle, long id) {
        for (long value : activeIds(handle)) if (value == id) return true;
        return false;
    }
    public synchronized void cancel(long id) { cancel(live(), id); }
    public synchronized long[] activeIds() { return activeIds(live()); }
    /** Disconnecting does not recall delivered notifications; cancel them explicitly if needed. */
    @Override public synchronized void close() { if (handle != 0) { long old = handle; handle = 0; close(old); } }
    @Override protected void finalize() throws Throwable { try { close(); } finally { super.finalize(); } }
    private static native long open(String application);
    private static native void close(long handle);
    private static native String[] capabilities(long handle);
    private static native long post(long handle, long replaces, String summary, String body, int urgency, int timeout, boolean suppressSound);
    private static native void cancel(long handle, long id);
    private static native long[] activeIds(long handle);
}
