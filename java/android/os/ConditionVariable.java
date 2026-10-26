// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** A gate that can be opened or closed across threads. */
public final class ConditionVariable {
    private boolean open;

    public ConditionVariable() { this(false); }
    public ConditionVariable(boolean state) { open = state; }

    public synchronized void open() {
        open = true;
        notifyAll();
    }

    public synchronized void close() { open = false; }

    public void block() { block(0); }

    public synchronized boolean block(long timeout) {
        if (open) return true;
        if (timeout < 0) return false;
        long deadline = timeout == 0 ? 0 : SystemClock.uptimeMillis() + timeout;
        while (!open) {
            long remaining = timeout == 0 ? 0 : deadline - SystemClock.uptimeMillis();
            if (timeout != 0 && remaining <= 0) break;
            try { wait(remaining); }
            catch (InterruptedException ignored) { }
        }
        return open;
    }
}
