// SPDX-License-Identifier: GPL-3.0-only
package android.content;
import android.os.Bundle;

/** Dynamic in-process delivery with owned asynchronous completion. */
public abstract class BroadcastReceiver {
    private static final class Dispatch { final PendingResult result; boolean asynchronous; Dispatch(PendingResult result) { this.result = result; } }
    private final ThreadLocal<Dispatch> dispatch = new ThreadLocal<Dispatch>();
    public BroadcastReceiver() { }
    public abstract void onReceive(Context context, Intent intent);
    public final PendingResult goAsync() { Dispatch current = dispatch.get(); if (current == null || current.asynchronous) return null; current.asynchronous = true; return current.result; }
    public final boolean isOrderedBroadcast() { return false; }
    public final boolean isInitialStickyBroadcast() { return false; }
    public final void abortBroadcast() { throw results(); }
    public final void clearAbortBroadcast() { throw results(); }
    public final boolean getAbortBroadcast() { return false; }
    public final int getResultCode() { throw results(); }
    public final String getResultData() { throw results(); }
    public final Bundle getResultExtras(boolean makeMap) { throw results(); }
    public final void setResultCode(int code) { throw results(); }
    public final void setResultData(String data) { throw results(); }
    public final void setResultExtras(Bundle extras) { throw results(); }
    public final void setResult(int code, String data, Bundle extras) { throw results(); }
    private static UnsupportedOperationException results() { return new UnsupportedOperationException("Ordered broadcast results are not implemented"); }
    public final void shirazDispatch(Context context, Intent intent, PendingResult result) {
        if (dispatch.get() != null) throw new IllegalStateException("Receiver dispatch is already active on this thread");
        Dispatch current = new Dispatch(result); dispatch.set(current); boolean returned = false;
        try { onReceive(context, intent); returned = true; }
        finally { dispatch.remove(); if (!returned || !current.asynchronous) result.shirazCancel(); }
    }
    public static final class PendingResult {
        private Runnable completed;
        private boolean finished;
        private PendingResult(Runnable completed) { this.completed = completed; }
        public static PendingResult shirazCreate(Runnable completed) { return new PendingResult(java.util.Objects.requireNonNull(completed)); }
        public void finish() { if (!shirazCancel()) throw new IllegalStateException("Broadcast result already finished or canceled"); }
        public boolean shirazCancel() {
            Runnable completion;
            synchronized (this) { if (finished) return false; finished = true; completion = completed; completed = null; }
            completion.run(); return true;
        }
        public synchronized boolean shirazFinished() { return finished; }
        public int getResultCode() { throw results(); }
        public String getResultData() { throw results(); }
        public Bundle getResultExtras(boolean makeMap) { throw results(); }
        public void setResultCode(int code) { throw results(); }
        public void setResultData(String data) { throw results(); }
        public void setResultExtras(Bundle extras) { throw results(); }
        public void setResult(int code, String data, Bundle extras) { throw results(); }
        public void abortBroadcast() { throw results(); }
        public void clearAbortBroadcast() { throw results(); }
        public boolean getAbortBroadcast() { return false; }
    }
}
