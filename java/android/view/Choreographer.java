// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;

/** API 27 callback phases driven by the host presentation loop, not Android display vsync. */
public final class Choreographer {
    public interface FrameCallback {void doFrame(long frameTimeNanos);}
    public static final int CALLBACK_INPUT=0,CALLBACK_ANIMATION=1,CALLBACK_TRAVERSAL=2,CALLBACK_COMMIT=3;
    private static final Object FRAME_TOKEN=new Object();
    private static final ThreadLocal<Choreographer> LOCAL=new ThreadLocal<Choreographer>();
    private final Looper looper;
    private final ArrayList<Entry> queue=new ArrayList<Entry>();
    private final android.os.Handler timer;
    private boolean timerPending;private long timerDue;
    private final Runnable pulse=new Runnable(){public void run(){synchronized(Choreographer.this){timerPending=false;if(closed)return;}shirazDoFrame(System.nanoTime(),null);}};
    private boolean running,closed;private long mLastFrameTimeNanos=Long.MIN_VALUE;
    private static final class Entry {
        final int phase;final Object action,token;final long due;
        Entry(int phase,Object action,Object token,long due){this.phase=phase;this.action=action;this.token=token;this.due=due;}
    }
    private Choreographer(Looper looper){this.looper=looper;timer=new android.os.Handler(looper);}
    public static Choreographer getInstance(){
        Looper looper=Looper.myLooper();if(looper==null)throw new IllegalStateException("Choreographer requires a prepared Looper");
        Choreographer value=LOCAL.get();if(value==null||value.looper!=looper){value=new Choreographer(looper);LOCAL.set(value);}return value;
    }
    private static void phase(int value){if(value<0||value>3)throw new IllegalArgumentException("Callback phase");}
    private synchronized void add(int phase,Object action,Object token,long delay){
        phase(phase);if(action==null)throw new IllegalArgumentException("Callback must not be null");if(closed)throw new IllegalStateException("Choreographer is closed");
        if(queue.size()>=4096)throw new IllegalStateException("Frame callback limit");long now=SystemClock.uptimeMillis();long due=delay>Long.MAX_VALUE-now?Long.MAX_VALUE:now+Math.max(0,delay);
        Entry entry=new Entry(phase,action,token,due);int at=queue.size();while(at>0&&queue.get(at-1).due>due)at--;queue.add(at,entry);scheduleTimer();
    }
    private void scheduleTimer(){
        if(closed||queue.isEmpty()||!looper.shirazUsesBlockingLoop())return;
        long now=SystemClock.uptimeMillis(),due=Math.max(now+16,queue.get(0).due);
        if(timerPending&&timerDue<=due)return;
        if(timerPending)timer.removeCallbacks(pulse);
        timerDue=due;timerPending=timer.postAtTime(pulse,due);
    }
    public void postFrameCallback(FrameCallback callback){postFrameCallbackDelayed(callback,0);}
    public void postFrameCallbackDelayed(FrameCallback callback,long delayMillis){add(CALLBACK_ANIMATION,callback,FRAME_TOKEN,delayMillis);}
    public void removeFrameCallback(FrameCallback callback){if(callback==null)throw new IllegalArgumentException("callback");removeCallbacks(CALLBACK_ANIMATION,callback,FRAME_TOKEN);}
    public void postCallback(int type,Runnable action,Object token){postCallbackDelayed(type,action,token,0);}
    public void postCallbackDelayed(int type,Runnable action,Object token,long delayMillis){add(type,action,token,delayMillis);}
    public void removeCallbacks(int type,Runnable action,Object token){removeCallbacks(type,(Object)action,token);}
    private synchronized void removeCallbacks(int type,Object action,Object token){phase(type);for(int i=queue.size()-1;i>=0;i--){Entry e=queue.get(i);if(e.phase==type&&(action==null||e.action==action)&&(token==null||e.token==token))queue.remove(i);}}
    public synchronized long getFrameTimeNanos(){if(!running||!looper.isCurrentThread())throw new IllegalStateException("Frame time is only available inside frame callbacks");return mLastFrameTimeNanos;}
    public static long shirazAnimationTimeMillis(){Choreographer current=LOCAL.get();return current!=null&&current.running?current.mLastFrameTimeNanos/1000000:SystemClock.uptimeMillis();}
    public long getFrameTime(){return getFrameTimeNanos()/1000000;}
    private void dispatch(int type){
        ArrayList<Entry> batch=null;synchronized(this){long now=SystemClock.uptimeMillis();for(int i=0;i<queue.size();){Entry e=queue.get(i);if(e.due>now)break;if(e.phase==type){if(batch==null)batch=new ArrayList<Entry>();batch.add(e);queue.remove(i);}else i++;}}
        if(batch==null)return;
        for(Entry e:batch){synchronized(this){if(closed)return;}if(e.token==FRAME_TOKEN)((FrameCallback)e.action).doFrame(mLastFrameTimeNanos);else ((Runnable)e.action).run();}
    }
    public void shirazDoFrame(long timeNanos,Runnable traversal){
        if(!looper.isCurrentThread())throw new IllegalStateException("Frame must run on its owning thread");
        synchronized(this){if(closed||running)throw new IllegalStateException("Closed or reentrant frame dispatch");if(timeNanos<0)throw new IllegalArgumentException("Frame time");running=true;mLastFrameTimeNanos=timeNanos;}
        try{dispatch(CALLBACK_INPUT);dispatch(CALLBACK_ANIMATION);dispatch(CALLBACK_TRAVERSAL);if(traversal!=null)traversal.run();dispatch(CALLBACK_COMMIT);}finally{synchronized(this){running=false;scheduleTimer();}}
    }
    public static void shirazStartLoop(){Choreographer value=LOCAL.get();if(value!=null)synchronized(value){value.scheduleTimer();}}
    public static void shirazReleaseCurrent(){Choreographer value=LOCAL.get();if(value!=null){synchronized(value){value.closed=true;value.queue.clear();value.timer.removeCallbacks(value.pulse);}LOCAL.remove();}}
}
