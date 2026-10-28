// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** Serial countdown callbacks on the constructing thread's Looper. */
public abstract class CountDownTimer {
    private final long duration, interval;
    private final Handler handler = new Handler();
    private long deadline, generation;
    private boolean cancelled;
    public CountDownTimer(long millisInFuture, long countDownInterval) {
        if (countDownInterval <= 0) throw new IllegalArgumentException("interval must be positive");
        duration = millisInFuture; interval = countDownInterval;
    }
    public synchronized final void cancel() {
        cancelled = true; generation++; handler.removeCallbacksAndMessages(null);
    }
    public synchronized final CountDownTimer start() {
        cancelled = false; final long run = ++generation;
        handler.removeCallbacksAndMessages(null);
        if (duration <= 0) { onFinish(); return this; }
        long now = SystemClock.elapsedRealtime();
        deadline = duration > Long.MAX_VALUE-now ? Long.MAX_VALUE : now+duration;
        handler.post(new Runnable() {
            public void run() {
                synchronized (CountDownTimer.this) {
                    if (cancelled || run != generation) return;
                    long left = deadline-SystemClock.elapsedRealtime();
                    if (left <= 0) { onFinish(); return; }
                    long started = SystemClock.elapsedRealtime();
                    onTick(left);
                    if (cancelled || run != generation) return;
                    long elapsed = SystemClock.elapsedRealtime()-started;
                    long delay = left < interval ? Math.max(0,left-elapsed) : interval-(elapsed%interval);
                    handler.postDelayed(this,delay);
                }
            }
        });
        return this;
    }
    public abstract void onTick(long millisUntilFinished);
    public abstract void onFinish();
}
