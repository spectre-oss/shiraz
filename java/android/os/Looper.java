// SPDX-License-Identifier: GPL-3.0-only
package android.os;
public final class Looper {
    private static final ThreadLocal<Looper> LOCAL = new ThreadLocal<Looper>();
    private static volatile Looper main;
    private final Thread thread = Thread.currentThread();
    private final MessageQueue queue;
    private Looper(boolean quitAllowed) { queue = new MessageQueue(quitAllowed); }
    public static void prepare() { prepare(true); }
    private static void prepare(boolean quitAllowed) {
        if (LOCAL.get() != null) throw new RuntimeException("Only one Looper may be created per thread");
        LOCAL.set(new Looper(quitAllowed));
    }
    public static synchronized void prepareMainLooper() {
        if (main != null) throw new IllegalStateException("The main Looper already exists");
        prepare(false); main = myLooper();
    }
    public static Looper myLooper() { return LOCAL.get(); }
    public static Looper getMainLooper() { return main; }
    public static MessageQueue myQueue() { Looper looper = myLooper(); if (looper == null) throw new IllegalStateException("No Looper"); return looper.queue; }
    public MessageQueue getQueue() { return queue; }
    public Thread getThread() { return thread; }
    public boolean isCurrentThread() { return Thread.currentThread() == thread; }
    private static final boolean TRACE="1".equals(System.getenv("SHIRAZ_TRACE_FRAMES"));
    private int tracedMessages;
    private java.util.HashMap<String,Integer> tracedClasses;
    private void dispatch(Message message) {
        long start=TRACE?SystemClock.uptimeMillis():0;
        try { message.target.dispatchMessage(message); }
        finally {
            if(TRACE&&tracedMessages<1024){
                long duration=SystemClock.uptimeMillis()-start,late=message.when==0?0:Math.max(0,start-message.when);
                if(duration>=16||late>=100){Object callback=message.callback;String name=callback==null?message.target.getClass().getName():callback.getClass().getName();
                    if(tracedClasses==null)tracedClasses=new java.util.HashMap<String,Integer>();Integer count=tracedClasses.get(name);
                    if((count!=null||tracedClasses.size()<64)&&(count==null||count<32)){tracedMessages++;tracedClasses.put(name,count==null?1:count+1);
                        System.err.println("SHIRAZ_MESSAGE_TIMING main="+(this==main)+" duration_ms="+duration+" late_ms="+late+" handler="+message.target.getClass().getName()+" callback="+(callback==null?"none":name));
                    }
                }
            }
            message.recycleUnchecked();
        }
    }
    private volatile boolean blockingLoop;
    public boolean shirazUsesBlockingLoop(){return blockingLoop;}
    public static void loop() {
        Looper looper = myLooper();
        if (looper == null) throw new RuntimeException("No Looper; call prepare first");
        looper.blockingLoop=true;android.view.Choreographer.shirazStartLoop();
        try{for (;;) { Message message = looper.queue.next(true); if (message == null) return; looper.dispatch(message); }}finally{looper.blockingLoop=false;android.view.Choreographer.shirazReleaseCurrent();}
    }
    /** Host SDL loop budget: pending callbacks cannot monopolize an entire frame. */
    public void shirazDispatchDue(int limit) {
        if (!isCurrentThread()) throw new IllegalStateException("Dispatch must run on the owning thread");
        if (limit < 1 || limit > 10000) throw new IllegalArgumentException("Dispatch limit");
        long start = System.nanoTime();
        for (int count = 0; count < limit && System.nanoTime() - start < 16000000; ++count) {
            Message message = queue.next(false); if (message == null) return; dispatch(message);
        }
    }
    public void quit() { queue.quit(false, false); }
    public void quitSafely() { queue.quit(true, false); }
    public static void shirazRequestMainExit(){Looper value=main;if(value!=null)value.queue.quit(false,true);}
    public static synchronized void shirazShutdownMain() {
        if (main == null) return;
        if (!main.isCurrentThread()) throw new IllegalStateException("Main Looper teardown thread");
        android.view.Choreographer.shirazReleaseCurrent();
        main.queue.quit(false, true); LOCAL.remove(); main = null;
    }
}
