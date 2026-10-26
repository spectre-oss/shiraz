// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.os.*;
import shiraz.runtime.HostBroadcasts;
import java.util.*;
import java.util.concurrent.atomic.*;

public final class BroadcastSmoke {
    private static final class TestContext extends ContextWrapper {
        final HostBroadcasts broadcasts;
        TestContext(HostBroadcasts broadcasts) { super(null); this.broadcasts = broadcasts; }
        @Override protected HostBroadcasts shirazBroadcasts() { return broadcasts; }
        @Override public int checkPermission(String permission, int pid, int uid) {
            return "org.shiraz.fixture.PRIVATE".equals(permission) ? 0 : -1;
        }
        @Override public String getPackageName() { return "org.shiraz.fixture"; }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void pump() { Looper.myLooper().shirazDispatchDue(256); }
    private static void rejected(Runnable action) { try { action.run(); throw new AssertionError("accepted rejected operation"); } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException expected) { } }
    public static void main(String[] args) {
        Looper.prepareMainLooper();
        try {
            final HostBroadcasts registry = new HostBroadcasts("org.shiraz.fixture", 30);
            final TestContext context = new TestContext(registry), other = new TestContext(registry);
            final AtomicInteger calls = new AtomicInteger();
            final BroadcastReceiver receiver = new BroadcastReceiver() { public void onReceive(Context owner, Intent value) { check(Thread.currentThread() == Looper.getMainLooper().getThread(), "default main receiver thread"); calls.incrementAndGet(); } };
            try {
                context.registerReceiver(receiver, new IntentFilter("queued"), Context.RECEIVER_NOT_EXPORTED);
                context.sendBroadcast(new Intent("queued")); check(calls.get() == 0, "delivery must be asynchronous");
                context.unregisterReceiver(receiver); pump(); check(calls.get() == 0, "unregister canceled queued delivery");
                context.registerReceiver(receiver, new IntentFilter("queued")); other.registerReceiver(receiver, new IntentFilter("queued"));
                context.sendBroadcast(new Intent("queued")); pump(); check(calls.get() == 2, "receiver registrations owned by distinct contexts");
                other.unregisterReceiver(receiver); context.sendBroadcast(new Intent("queued")); pump(); check(calls.get() == 3, "unregister only one owner");
                rejected(new Runnable() { public void run() { other.unregisterReceiver(receiver); } });
                context.sendBroadcast(new Intent("queued").setPackage("another.package"));
                context.sendBroadcast(new Intent("queued").setComponent(new ComponentName("another.package","Receiver")));
                context.sendBroadcast(new Intent("queued").setPackage("org.shiraz.fixture").setComponent(new ComponentName("another.package","Receiver"))); pump(); check(calls.get() == 3, "package routing");
                context.unregisterReceiver(receiver);
                IntentFilter original = new IntentFilter("snapshot"); context.registerReceiver(receiver, original); original.addAction("later mutation");
                context.sendBroadcast(new Intent("later mutation")); pump(); check(calls.get() == 3, "registered filter snapshot"); context.unregisterReceiver(receiver);
                final AtomicReference<BroadcastReceiver.PendingResult> async = new AtomicReference<BroadcastReceiver.PendingResult>();
                BroadcastReceiver asynchronous = new BroadcastReceiver() { public void onReceive(Context owner, Intent value) { async.set(goAsync()); } };
                context.registerReceiver(asynchronous, new IntentFilter("async")); context.sendBroadcast(new Intent("async")); pump();
                check(async.get() != null, "goAsync result"); async.get().finish(); rejected(new Runnable() { public void run() { async.get().finish(); } });
                SystemClock.sleep(40); pump(); // Successful finish must remove the deadline.
                context.sendBroadcast(new Intent("async")); pump(); SystemClock.sleep(40);
                try { pump(); throw new AssertionError("unfinished broadcast did not time out"); }
                catch (IllegalStateException expected) { check(expected.getMessage().contains("deadline"), "timeout cause"); }
                rejected(new Runnable() { public void run() { async.get().finish(); } }); context.unregisterReceiver(asynchronous);
                final AssertionError marker = new AssertionError("broadcast callback failure");
                BroadcastReceiver broken = new BroadcastReceiver() { public void onReceive(Context owner, Intent value) { throw marker; } };
                context.registerReceiver(broken, new IntentFilter("broken")); context.sendBroadcast(new Intent("broken"));
                try { pump(); throw new AssertionError("callback failure swallowed"); } catch (AssertionError expected) { check(expected == marker, "original callback failure"); }
                context.unregisterReceiver(broken);
                context.registerReceiver(receiver, new IntentFilter("budget"));
                final Intent large = new Intent("budget").putExtra("payload", new byte[2 * 1024 * 1024]);
                int accepted = 0;
                for (; accepted < 16; ++accepted) { try { context.sendBroadcast(large); } catch (IllegalStateException expected) { break; } }
                check(accepted > 0 && accepted < 16, "queued broadcast byte bound"); context.unregisterReceiver(receiver); pump(); check(calls.get() == 3, "canceled large delivery");
                context.registerReceiver(receiver, new IntentFilter("budget")); context.sendBroadcast(new Intent("budget")); pump(); check(calls.get() == 4, "unregister reclaimed queue budget"); context.unregisterReceiver(receiver);
                String longAction = new String(new char[65000]).replace('\0', 'x'); int registered = 0;
                for (; registered < 80; ++registered) {
                    try { context.registerReceiver(receiver, new IntentFilter(longAction + registered)); }
                    catch (IllegalStateException expected) { break; }
                }
                check(registered > 0 && registered < 80, "registered filter byte bound"); context.unregisterReceiver(receiver);
                context.registerReceiver(receiver, new IntentFilter("recovered")); context.unregisterReceiver(receiver);
                final AtomicInteger privateCalls = new AtomicInteger();
                BroadcastReceiver privateReceiver = new BroadcastReceiver() { public void onReceive(Context owner, Intent value) { privateCalls.incrementAndGet(); } };
                context.registerReceiver(privateReceiver, new IntentFilter("private"), "org.shiraz.fixture.PRIVATE", null);
                context.sendBroadcast(new Intent("private")); pump();
                check(privateCalls.get() == 1, "granted app-private receiver permission");
                context.unregisterReceiver(privateReceiver);
                rejected(new Runnable() { public void run() { context.registerReceiver(receiver, new IntentFilter("x"), "org.shiraz.fixture.DENIED", null); } });
                rejected(new Runnable() { public void run() { context.registerReceiver(receiver, new IntentFilter("x"), "other.PRIVATE", null); } });
                final Bundle cycle = new Bundle(); cycle.putBundle("self", cycle);
                rejected(new Runnable() { public void run() { context.sendBroadcast(new Intent("x").putExtras(cycle)); } });
                context.sendBroadcast(new Intent("x").putExtra("serializable", new java.util.Date())); // In-process Serializable extras are supported.
                rejected(new Runnable() { public void run() { context.sendBroadcast(new Intent("x").putExtra("oversized", new byte[4 * 1024 * 1024])); } });
                rejected(new Runnable() { public void run() { context.sendBroadcast(new Intent("x").setComponent(new ComponentName("org.shiraz.fixture", "Receiver"))); } });
                rejected(new Runnable() { public void run() { context.sendBroadcast(new Intent("x"), "permission"); } });
                rejected(new Runnable() { public void run() { context.sendOrderedBroadcast(new Intent("x"), null); } });
                rejected(new Runnable() { public void run() { context.registerReceiver(receiver, new IntentFilter("x"), 6); } });
                HandlerThread stopped = new HandlerThread("stopped-broadcast"); stopped.start(); Handler dead = new Handler(stopped.getLooper()); stopped.quit();
                try { stopped.join(3000); } catch (InterruptedException error) { throw new AssertionError(error); }
                context.registerReceiver(receiver, new IntentFilter("stopped"), null, dead);
                rejected(new Runnable() { public void run() { context.sendBroadcast(new Intent("stopped")); } }); context.unregisterReceiver(receiver);
                context.registerReceiver(asynchronous, new IntentFilter("async")); context.sendBroadcast(new Intent("async")); pump();
                context.registerReceiver(receiver, new IntentFilter("close")); context.sendBroadcast(new Intent("close")); registry.close(); pump();
                rejected(new Runnable(){public void run(){other.unregisterReceiver(receiver);}});
                context.unregisterReceiver(receiver);
                rejected(new Runnable(){public void run(){context.unregisterReceiver(receiver);}});
                check(calls.get() == 4, "session close canceled queued callback"); rejected(new Runnable() { public void run() { async.get().finish(); } });
                rejected(new Runnable() { public void run() { context.sendBroadcast(new Intent("after close")); } });
            } finally { registry.close(); }
            System.out.println("BROADCAST_OWNERSHIP_LIMITS_FAILURES_AND_TEARDOWN_OK");
        } finally { Looper.shirazShutdownMain(); }
    }
}
