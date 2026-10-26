// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.*;
import android.os.*;
import java.util.*;

/** One application's dynamic receiver registry; no fabricated host/system events. */
public final class HostBroadcasts implements AutoCloseable {
    private final String pkg;
    private final Handler main;
    private final long timeout;
    private final ArrayList<Record> records = new ArrayList<Record>();
    private final LinkedHashSet<Delivery> pending = new LinkedHashSet<Delivery>();
    private int filters, filterBytes, queuedBytes;
    private boolean closed;
    private static final class Record {
        final Context context; final BroadcastReceiver receiver; final Handler handler;
        final ArrayList<IntentFilter> filters = new ArrayList<IntentFilter>(); boolean active = true;
        Record(Context context, BroadcastReceiver receiver, Handler handler) { this.context = context; this.receiver = receiver; this.handler = handler; }
    }
    public HostBroadcasts(String pkg) { this(pkg, 10000); }
    public HostBroadcasts(String pkg, long timeout) {
        this.pkg = Objects.requireNonNull(pkg); main = new Handler(Looper.getMainLooper());
        if (timeout < 1 || timeout > 10000) throw new IllegalArgumentException("Broadcast timeout"); this.timeout = timeout;
    }
    private void live() { if (closed) throw new IllegalStateException("Broadcast session is closed"); }
    public synchronized Intent register(Context context, BroadcastReceiver receiver, IntentFilter filter, String permission, Handler handler, int flags) {
        live(); Objects.requireNonNull(context); Objects.requireNonNull(filter);
        // This registry only accepts broadcasts from the loaded app's own process.
        // A granted app-private permission therefore protects every possible sender.
        if (permission != null && (!pkg.equals(context.getPackageName())
                || !permission.startsWith(pkg + ".")
                || context.checkPermission(permission, android.os.Process.myPid(), android.os.Process.myUid())
                    != android.content.pm.PackageManager.PERMISSION_GRANTED))
            throw new UnsupportedOperationException("Only granted app-private broadcast permissions are supported");
        if ((flags & ~7) != 0 || (flags & 6) == 6 || (flags & 5) == 5) throw new IllegalArgumentException("Invalid receiver export flags");
        // No sticky producer exists in this session. A query returns no existing sticky value.
        if (receiver == null) return null;
        Handler target = handler == null ? main : handler;
        Record record = null;
        for (Record candidate : records) if (candidate.context == context && candidate.receiver == receiver) { record = candidate; break; }
        if (record == null && records.size() >= 256 || filters >= 1024) throw new IllegalStateException("Receiver registration limit exceeded");
        if (record != null && record.handler.getLooper() != target.getLooper()) throw new IllegalArgumentException("Receiver already registered on another looper");
        IntentFilter snapshot = new IntentFilter(filter);
        if (snapshot.shirazByteSize() > 8 * 1024 * 1024 - filterBytes) throw new IllegalStateException("Registered filter byte limit exceeded");
        if (record == null) { record = new Record(context, receiver, target); records.add(record); }
        record.filters.add(snapshot); ++filters; filterBytes += snapshot.shirazByteSize(); return null;
    }
    public void unregister(Context context, BroadcastReceiver receiver) {
        Objects.requireNonNull(receiver); Record found = null; ArrayList<Delivery> canceled = new ArrayList<Delivery>();
        synchronized (this) {
            for (Record record : records) if (record.context == context && record.receiver == receiver) { found = record; break; }
            if (found == null) throw new IllegalArgumentException("Receiver is not registered by this context");
            found.active = false; records.remove(found);
            if(closed)return; // Service teardown may unregister asynchronously after host shutdown.
            filters -= found.filters.size();
            for (IntentFilter filter : found.filters) filterBytes -= filter.shirazByteSize();
            for (Delivery delivery : pending) if (delivery.record == found && !delivery.started) canceled.add(delivery);
        }
        for (Delivery delivery : canceled) delivery.cancel();
    }
    public void send(Intent source, String permission) {
        Objects.requireNonNull(source);
        if (permission != null) throw new UnsupportedOperationException("Broadcast receiver permission filtering is not implemented");
        if ((source.getFlags() & Intent.FLAG_RECEIVER_REPLACE_PENDING) != 0) throw new UnsupportedOperationException("Replacing queued broadcasts is not implemented");
        Intent snapshot = source.shirazBroadcastCopy(); ArrayList<Record> matched = new ArrayList<Record>();
        synchronized (this) {
            live(); if (snapshot.getPackage() != null && !snapshot.getPackage().equals(pkg)) return;
            // Only this APK is loaded. An explicit component in another package has no recipient.
            ComponentName component=snapshot.getComponent();
            if(component!=null){if(!component.getPackageName().equals(pkg))return;throw new UnsupportedOperationException("Manifest receiver dispatch is not implemented");}
            final IdentityHashMap<Record,Integer> rank = new IdentityHashMap<Record,Integer>();
            for (Record record : records) {
                boolean accepts = false; int priority = Integer.MIN_VALUE;
                for (IntentFilter filter : record.filters) if (filter.match(snapshot.getAction(), snapshot.getType(), snapshot.getScheme(), snapshot.getData(), snapshot.getCategories(), "Shiraz") >= 0) { accepts = true; priority = Math.max(priority, filter.getPriority()); }
                if (accepts) { matched.add(record); rank.put(record, priority); }
            }
            Collections.sort(matched, new Comparator<Record>() { public int compare(Record a, Record b) { return Integer.compare(rank.get(b), rank.get(a)); } });
            long cost = (long)snapshot.shirazBroadcastBytes() * matched.size();
            if (pending.size() + matched.size() > 4096 || cost > 16 * 1024 * 1024L - queuedBytes) throw new IllegalStateException("Broadcast delivery queue exceeds host limit");
            for (Record record : matched) {
                Delivery delivery = new Delivery(record, snapshot); pending.add(delivery); queuedBytes += snapshot.shirazBroadcastBytes();
                try {
                    if (!record.handler.postAtTime(delivery, delivery, SystemClock.uptimeMillis())) throw new IllegalStateException("Receiver looper has stopped");
                } catch (Throwable error) { complete(delivery); throw error; }
            }
        }
    }
    private synchronized void complete(Delivery delivery) {
        if (pending.remove(delivery)) queuedBytes -= delivery.intent.shirazBroadcastBytes();
        delivery.record.handler.removeCallbacksAndMessages(delivery); main.removeCallbacksAndMessages(delivery);
    }
    private final class Delivery implements Runnable {
        final Record record; final Intent intent; boolean started;
        final BroadcastReceiver.PendingResult result;
        Delivery(Record record, Intent intent) { this.record = record; this.intent = intent; result = BroadcastReceiver.PendingResult.shirazCreate(new Runnable() { public void run() { complete(Delivery.this); } }); }
        void cancel() { result.shirazCancel(); }
        public void run() {
            synchronized (HostBroadcasts.this) { if (!record.active || closed || result.shirazFinished()) { cancel(); return; } started = true; }
            Runnable deadline = new Runnable() { public void run() { if (result.shirazCancel()) throw new IllegalStateException("Asynchronous broadcast receiver did not finish before its deadline"); } };
            try {
                if (!main.postAtTime(deadline, this, SystemClock.uptimeMillis() + timeout)) throw new IllegalStateException("Broadcast completion looper has stopped");
                if (result.shirazFinished()) { main.removeCallbacksAndMessages(this); return; }
                record.receiver.shirazDispatch(record.context, intent.shirazBroadcastCopy(), result);
            } catch (Throwable error) { cancel(); throw error; }
        }
    }
    @Override public void close() {
        ArrayList<Delivery> canceled;
        synchronized (this) { if (closed) return; closed = true; for (Record record : records) { record.active = false; record.filters.clear(); } filters = 0; filterBytes = 0; canceled = new ArrayList<Delivery>(pending); }
        for (Delivery delivery : canceled) delivery.cancel();
    }
}
