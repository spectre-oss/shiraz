// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ISyncAdapter;
import android.content.ISyncContext;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/** One host-authorized operation inside the adapter process; no guest admission API. */
final class LocalSyncExecution {
    interface Listener {
        void finished(SyncResult result, boolean canceled);
    }
    enum State { NEW, RUNNING, CANCEL_REQUESTED, DELIVERY_FAILED, FINISHED }
    private final ISyncAdapter adapter;
    private final Account account;
    private final String authority;
    private final Bundle extras;
    private final Listener listener;
    private final Object commands = new Object();
    private State state = State.NEW;
    private boolean cancelRequested;
    private long heartbeats;
    LocalSyncExecution(IBinder binder, Account account, String authority, Bundle extras, Listener listener) {
        if (binder == null || account == null || authority == null || authority.isEmpty() || extras == null || listener == null)
            throw new IllegalArgumentException("Sync adapter, account, authority, extras and listener required");
        IInterface local = binder.queryLocalInterface("android.content.ISyncAdapter");
        if (!(local instanceof ISyncAdapter)) throw new SecurityException("Sync execution requires an in-process adapter");
        this.adapter = (ISyncAdapter)local;
        this.account = new Account(account.name, account.type);
        this.authority = authority;
        this.extras = extras.shirazBroadcastCopy();
        ContentResolver.validateSyncExtrasBundle(this.extras);
        this.listener = listener;
    }
    private final ISyncContext.Stub callback = new ISyncContext.Stub() {
        public void sendHeartbeat() {
            synchronized (LocalSyncExecution.this) {
                if (state == State.RUNNING || state == State.CANCEL_REQUESTED) heartbeats++;
            }
        }
        public void onFinished(SyncResult result) {
            if (result == null) throw new IllegalArgumentException("Sync result required");
            SyncResult snapshot;
            Parcel parcel = Parcel.obtain();
            try {
                result.writeToParcel(parcel, 0); parcel.setDataPosition(0);
                snapshot = SyncResult.CREATOR.createFromParcel(parcel);
            } finally { parcel.recycle(); }
            boolean canceled;
            synchronized (LocalSyncExecution.this) {
                if (state == State.NEW || state == State.FINISHED) return;
                canceled = cancelRequested;
                state = State.FINISHED;
            }
            listener.finished(snapshot, canceled);
        }
    };
    void start() throws RemoteException {
        synchronized (commands) {
            synchronized (this) {
                if (state != State.NEW) throw new IllegalStateException("Sync operation already started");
                state = State.RUNNING;
            }
            try { adapter.startSync(callback, authority, account, extras); }
            catch (RemoteException | RuntimeException | Error failure) {
                synchronized (this) { if (state != State.FINISHED) state = State.DELIVERY_FAILED; }
                // Delivery may have started work before failing. The host must confirm worker
                // termination before releasing its claim; this is not a completion callback.
                throw failure;
            }
        }
    }
    void cancel() throws RemoteException {
        synchronized (commands) {
            synchronized (this) {
                if (state == State.NEW) throw new IllegalStateException("Sync operation not started");
                if (state == State.FINISHED || cancelRequested) return;
                cancelRequested = true; state = State.CANCEL_REQUESTED;
            }
            adapter.cancelSync(callback);
            // Android adapters may suppress onFinished when canceled. Keep the claim live
            // until host-owned process shutdown is confirmed; cancelSync returning is not proof.
        }
    }
    synchronized State state() { return state; }
    synchronized long heartbeats() { return heartbeats; }
}
