// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.DesktopNotifications;
import java.io.File;
import java.util.Arrays;

/** Uses an isolated real D-Bus daemon and protocol fixture, never the user's desktop. */
public final class DesktopNotificationsSmoke {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void illegal(Runnable work) {
        try { work.run(); throw new AssertionError("accepted invalid arguments"); }
        catch (IllegalArgumentException expected) { }
    }
    private static void unavailable() {
        try (DesktopNotifications ignored = new DesktopNotifications("unavailable fixture")) {
            throw new AssertionError("accepted unavailable service");
        } catch (IllegalStateException expected) { }
    }
    private static int descriptors() { return new File("/proc/self/fd").list().length; }
    public static void main(String[] args) throws Exception {
        if (args.length != 0 && "missing".equals(args[0])) {
            unavailable(); int before = descriptors();
            for (int repeat = 0; repeat < 32; ++repeat) unavailable();
            check(descriptors() == before, "failed connection socket lifetime");
            System.out.println("NOTIFICATION_UNAVAILABLE_AND_LIFETIME_OK"); return;
        }
        if (args.length != 0 && "no-body".equals(args[0])) {
            try (DesktopNotifications current = new DesktopNotifications("capability fixture")) {
                try { current.post(0, "title", "body", 1, -1, true); throw new AssertionError("dropped body"); }
                catch (IllegalStateException expected) { }
            }
            System.out.println("NOTIFICATION_BODY_CAPABILITY_REJECTED"); return;
        }
        if (args.length != 0 && "owner-loss".equals(args[0])) {
            try (DesktopNotifications current = new DesktopNotifications("owner fixture")) {
                current.post(0, "drop owner", "", 1, -1, true);
                Thread.sleep(100);
                try { current.capabilities(); throw new AssertionError("stale service owner"); }
                catch (IllegalStateException expected) { }
            }
            System.out.println("NOTIFICATION_OWNER_LOSS_REJECTED"); return;
        }
        final DesktopNotifications first = new DesktopNotifications("Shiraz fixture");
        check(Arrays.asList(first.capabilities()).contains("body-markup"), "real capabilities");
        long id = first.post(0, "سلام 😀", "<b>plain</b> & text 😀", 1, -1, true);
        check(id > Integer.MAX_VALUE && id <= 0xffffffffL, "unsigned desktop identifier");
        check(first.post(id, "updated", "second", 2, 0, false) == id, "atomic replacement identifier");
        check(Arrays.equals(first.activeIds(), new long[]{id}), "owned active set");
        final long owned = id;
        try (final DesktopNotifications second = new DesktopNotifications("second fixture")) {
            illegal(new Runnable() { public void run() { second.post(owned, "not mine", "body", 1, -1, true); } });
            second.cancel(owned); // An unrelated connection cannot recall another app's notification.
            check(first.activeIds().length == 1, "cross connection ownership");
        }
        illegal(new Runnable() { public void run() { first.post(0, "nul\0", "", 1, -1, true); } });
        illegal(new Runnable() { public void run() { first.post(0, "\ud800", "", 1, -1, true); } });
        illegal(new Runnable() { public void run() { first.post(0, "", "", 3, -1, true); } });
        illegal(new Runnable() { public void run() { first.post(0x100000000L, "", "", 1, -1, true); } });
        final String large = new String(new char[8193]).replace('\0', 'x');
        illegal(new Runnable() { public void run() { first.post(0, large, "", 1, -1, true); } });
        first.cancel(id); first.cancel(id);
        long raced = first.post(0, "expire before cancel", "", 1, -1, true);
        first.cancel(raced);
        check(first.activeIds().length == 0, "actual cancellation");
        first.post(0, "expire", "timer", 0, 20, true);
        long deadline = System.nanoTime() + 2000000000L;
        while (first.activeIds().length != 0 && System.nanoTime() < deadline) Thread.sleep(5);
        check(first.activeIds().length == 0, "NotificationClosed signal");
        long expired = first.post(0, "expire again", "timer", 0, 20, true);
        deadline = System.nanoTime() + 2000000000L;
        while (first.activeIds().length != 0 && System.nanoTime() < deadline) Thread.sleep(5);
        long renewed = first.post(expired, "after expiry", "progress", 1, -1, true);
        check(renewed != 0 && Arrays.equals(first.activeIds(), new long[]{renewed}), "replacement after server closure");
        first.cancel(renewed);
        for (String special : new String[]{"malformed", "zero", "error"}) {
            try { first.post(0, special, "", 1, -1, true); throw new AssertionError("accepted " + special); }
            catch (IllegalStateException expected) { }
        }
        long started = System.nanoTime();
        try { first.post(0, "timeout", "", 1, -1, true); throw new AssertionError("accepted timeout"); }
        catch (IllegalStateException expected) { }
        check((System.nanoTime() - started) < 4000000000L, "bounded daemon wait");
        first.close(); first.close();
        try { first.capabilities(); throw new AssertionError("closed connection accepted"); }
        catch (IllegalStateException expected) { }
        int before = descriptors();
        for (int repeat = 0; repeat < 32; ++repeat) {
            try (DesktopNotifications current = new DesktopNotifications("lifetime fixture")) {
                long next = current.post(0, "lifetime", "body", 1, -1, true);
                current.cancel(next);
            }
        }
        check(descriptors() == before, "session bus socket lifetime");
        System.out.println("DESKTOP_NOTIFICATION_PROTOCOL_AND_LIFETIME_OK");
    }
}
