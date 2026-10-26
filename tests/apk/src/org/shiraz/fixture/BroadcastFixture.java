// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Stock SDK compilation; tests real callbacks on the registered Handler's looper. */
final class BroadcastFixture {
    static final String ACTION = "org.shiraz.fixture.BROADCAST";
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void await(CountDownLatch latch) { try { if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("Broadcast callback timeout"); } catch (InterruptedException error) { throw new AssertionError(error); } }
    static void check(final Context context) throws Exception {
        final HandlerThread worker = new HandlerThread("broadcast-fixture"); worker.start();
        final Handler handler = new Handler(worker.getLooper());
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        worker.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() { public void uncaughtException(Thread thread, Throwable error) { failure.set(error); } });
        final CountDownLatch release = new CountDownLatch(1), blocked = new CountDownLatch(1), callbacks = new CountDownLatch(2), finished = new CountDownLatch(1);
        handler.post(new Runnable() { public void run() { blocked.countDown(); await(release); } }); await(blocked);
        final StringBuilder order = new StringBuilder();
        BroadcastReceiver low = new BroadcastReceiver() { public void onReceive(Context owner, Intent intent) {
            check(owner == context && Thread.currentThread() == worker, "receiver context/looper");
            check(!isOrderedBroadcast() && !isInitialStickyBroadcast(), "unordered delivery kind");
            check(intent.getByteArrayExtra("bytes")[0] == 1 && "original".equals(intent.getBundleExtra("nested").getString("key")), "independent receiver payload");
            check(intent.getStringArrayListExtra("list").equals(Arrays.asList("a")), "independent list payload");
            check("before".contentEquals(intent.getCharSequenceArrayExtra("chars")[0]), "mutable CharSequence array transport");
            order.append('L'); callbacks.countDown();
        } };
        BroadcastReceiver high = new BroadcastReceiver() { public void onReceive(Context owner, Intent intent) {
            check(ACTION.equals(intent.getAction()) && intent.getByteArrayExtra("bytes")[0] == 1, "sender snapshot");
            check("original".equals(intent.getBundleExtra("nested").getString("key")), "nested sender snapshot");
            intent.getByteArrayExtra("bytes")[0] = 7; intent.getBundleExtra("nested").putString("key", "receiver change"); intent.getStringArrayListExtra("list").add("changed");
            intent.getCharSequenceArrayExtra("chars")[0] = "receiver change";
            order.append('H'); final PendingResult pending = goAsync(); check(pending != null && goAsync() == null, "asynchronous result ownership");
            new Thread(new Runnable() { public void run() { try { pending.finish(); } catch (Throwable error) { failure.set(error); } finally { finished.countDown(); } } }, "broadcast-complete").start();
            callbacks.countDown();
        } };
        IntentFilter lowFilter = new IntentFilter(ACTION), highFilter = new IntentFilter(ACTION); highFilter.setPriority(20);
        context.registerReceiver(low, lowFilter, null, handler);
        context.registerReceiver(high, highFilter, null, handler);
        context.registerReceiver(high, new IntentFilter(highFilter), null, handler);
        try {
            byte[] bytes = {1,2}; Bundle nested = new Bundle(); nested.putString("key", "original"); ArrayList<String> list = new ArrayList<String>(Arrays.asList("a"));
            StringBuilder[] chars = {new StringBuilder("before")};
            Intent source = new Intent(ACTION).putExtra("bytes", bytes).putExtra("nested", nested).putStringArrayListExtra("list", list).putExtra("chars", (CharSequence[])chars);
            context.sendBroadcast(source); chars[0].append(" changed by sender"); source.setAction("changed"); bytes[0] = 9; nested.putString("key", "sender change"); list.add("sender change");
            release.countDown(); await(callbacks); await(finished);
            check("HL".equals(order.toString()) && failure.get() == null, "priority, deduplication and callback completion");
        } finally { release.countDown(); context.unregisterReceiver(high); context.unregisterReceiver(low); worker.quit(); worker.join(3000); }
        check(!worker.isAlive(), "receiver looper teardown");
        BroadcastReceiver missing = new BroadcastReceiver() { public void onReceive(Context owner, Intent intent) { throw new AssertionError("unregistered callback"); } };
        try { context.unregisterReceiver(missing); throw new AssertionError("unknown unregister accepted"); } catch (IllegalArgumentException expected) { }
        check(context.registerReceiver(null, new IntentFilter("fixture.no.sticky")) == null, "no fabricated sticky value");
        IntentFilter filter = new IntentFilter(ACTION, "image/*"); filter.addCategory("fixture.category");
        Uri uri = Uri.parse("content://provider/path/a%20b?key=one+two#part");
        check("content".equals(uri.getScheme()) && "/path/a b".equals(uri.getPath()) && "a b".equals(uri.getLastPathSegment()) && "one two".equals(uri.getQueryParameter("key")), "URI routing components");
        check(filter.match(ACTION, "image/png", "content", uri, null, "fixture") >= 0, "MIME wildcard and content scheme");
        check(filter.match(ACTION, "image/png", "https", Uri.parse("https://host/path"), null, "fixture") == IntentFilter.NO_MATCH_DATA, "implicit MIME scheme restrictions");
        check(filter.match(ACTION, "text/plain", "content", uri, null, "fixture") == IntentFilter.NO_MATCH_TYPE, "MIME mismatch");
        check(filter.match("other", "image/png", "content", uri, null, "fixture") == IntentFilter.NO_MATCH_ACTION, "action mismatch");
        check(filter.match(ACTION, "image/png", "content", uri, Collections.singleton("missing"), "fixture") == IntentFilter.NO_MATCH_CATEGORY, "category subset");
        Intent clearing = new Intent(ACTION, uri).setType("text/plain"); check(clearing.getData() == null, "setType clears data"); clearing.setDataAndType(uri, "text/plain").setData(uri); check(clearing.getType() == null, "setData clears type");
        ComponentName name = new ComponentName(context, BroadcastFixture.class); check(name.equals(ComponentName.unflattenFromString(name.flattenToShortString())), "component flattening");
        check("a%20b%F0%9F%98%80".equals(Uri.encode("a b😀")) && "a+b".equals(Uri.decode("a+b")), "URI Unicode encoding and literal plus");
        check("\ufffd".equals(Uri.decode("%2")) && "\ufffd".equals(Uri.decode("%FF")), "Android URI decoder error handling");
        System.out.println("SHIRAZ_FIXTURE_BROADCASTS_OK");
    }
}
