// SPDX-License-Identifier: GPL-3.0-only
package android.os;
public class HandlerThread extends Thread {
    private static final Object workersLock=new Object();
    private static final java.util.HashSet<HandlerThread> workers=new java.util.HashSet<HandlerThread>();
    private static boolean shuttingDown;
    private volatile Looper looper;
    private final int priority;
    private boolean finished;
    private volatile int tid = -1;
    public HandlerThread(String name) { this(name, Process.THREAD_PRIORITY_DEFAULT); }
    public HandlerThread(String name, int priority) {
        super(name);
        this.priority = priority;
    }
    @Override public void start() {
        synchronized(workersLock){
            if(shuttingDown)throw new IllegalStateException("Guest HandlerThreads are shutting down");
            workers.add(this);
            try{super.start();}catch(RuntimeException|Error failure){if(!isAlive())workers.remove(this);throw failure;}
        }
    }
    /** Dedicated guest process boundary, never an Activity recreation hook. */
    public static void shirazShutdown(long timeoutMillis) {
        if(timeoutMillis<1||timeoutMillis>30000)throw new IllegalArgumentException("HandlerThread shutdown deadline");
        HandlerThread[] active;
        synchronized(workersLock){
            if(workers.contains(Thread.currentThread()))throw new IllegalStateException("HandlerThread cannot shut down its own process");
            shuttingDown=true;active=workers.toArray(new HandlerThread[workers.size()]);
        }
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        for(HandlerThread worker:active){Looper current=worker.looper;if(current!=null)current.quit();}
        boolean interrupted=false;
        try{
            for(HandlerThread worker:active){
                while(worker.isAlive()){
                    long left=deadline-System.nanoTime();
                    if(left<=0)throw new IllegalStateException("Guest HandlerThread shutdown timed out");
                    // Avoid acquiring the guest Thread monitor: app code may hold it.
                    try{Thread.sleep(Math.max(1,Math.min(10,java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(left))));}
                    catch(InterruptedException failure){interrupted=true;}
                }
            }
        }finally{if(interrupted)Thread.currentThread().interrupt();}
    }
    @Override public void run() {
        try {
            tid = Process.myTid(); Looper.prepare();
            synchronized (this) { looper = Looper.myLooper(); notifyAll(); }
            synchronized(workersLock){if(shuttingDown){looper.quit();return;}}
            Process.setThreadPriority(priority);
            onLooperPrepared(); Looper.loop();
        } catch (RuntimeException | Error failure) {
            android.util.Log.e("HandlerThread", "Uncaught failure in " + getName(), failure);
            throw failure;
        } finally {
            Looper current = Looper.myLooper();
            if (current != null) current.getQueue().quit(false, true);
            synchronized (this) { tid = -1; finished = true; notifyAll(); }
            synchronized(workersLock){workers.remove(this);}
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
