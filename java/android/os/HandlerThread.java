// SPDX-License-Identifier: GPL-3.0-only
package android.os;
public class HandlerThread extends Thread {
    private Looper looper;
    private final int priority;
    private boolean finished;
    private volatile int tid = -1;
    public HandlerThread(String name) { this(name, Process.THREAD_PRIORITY_DEFAULT); }
    public HandlerThread(String name, int priority) {
        super(name);
        this.priority = priority;
    }
    @Override public void run() {
        try {
            tid = Process.myTid(); Looper.prepare();
            synchronized (this) { looper = Looper.myLooper(); notifyAll(); }
            Process.setThreadPriority(priority);
            onLooperPrepared(); Looper.loop();
        } catch (RuntimeException | Error failure) {
            android.util.Log.e("HandlerThread", "Uncaught failure in " + getName(), failure);
            throw failure;
        } finally {
            Looper current = Looper.myLooper();
            if (current != null) current.getQueue().quit(false, true);
            synchronized (this) { tid = -1; finished = true; notifyAll(); }
        }
    }
    protected void onLooperPrepared() { }
    public synchronized Looper getLooper() {
        boolean interrupted = false;
        while (isAlive() && !finished && looper == null) {
            try { wait(); } catch (InterruptedException error) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
        return isAlive() && !finished ? looper : null;
    }
    public boolean quit() { Looper current = getLooper(); if (current == null) return false; current.quit(); return true; }
    public boolean quitSafely() { Looper current = getLooper(); if (current == null) return false; current.quitSafely(); return true; }
    public int getThreadId() { return tid; }
}
