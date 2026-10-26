// SPDX-License-Identifier: GPL-3.0-only
package android.os;
public class Handler {
    public interface Callback { boolean handleMessage(Message message); }
    private final Looper looper;
    private final Callback callback;
    private final boolean asynchronous;
    public Handler() { this(Looper.myLooper(), null, false); }
    public Handler(Callback callback) { this(Looper.myLooper(), callback, false); }
    public Handler(Looper looper) { this(looper, null, false); }
    public Handler(Looper looper, Callback callback) { this(looper, callback, false); }
    public Handler(Looper looper, Callback callback, boolean asynchronous) {
        if (looper == null) throw new RuntimeException("Cannot create Handler without a prepared Looper");
        this.looper = looper; this.callback = callback; this.asynchronous = asynchronous;
    }
    public static Handler createAsync(Looper looper) { return new Handler(looper, null, true); }
    public static Handler createAsync(Looper looper, Callback callback) { return new Handler(looper, callback, true); }
    public final Looper getLooper() { return looper; }
    public void handleMessage(Message message) { }
    public void dispatchMessage(Message message) {
        if (message.callback != null) message.callback.run();
        else if (callback == null || !callback.handleMessage(message)) handleMessage(message);
    }
    public final Message obtainMessage() { return Message.obtain(this); }
    public final Message obtainMessage(int what) { return Message.obtain(this, what); }
    public final Message obtainMessage(int what, Object obj) { return Message.obtain(this, what, obj); }
    public final Message obtainMessage(int what, int a, int b) { return Message.obtain(this, what, a, b); }
    public final Message obtainMessage(int what, int a, int b, Object obj) { return Message.obtain(this, what, a, b, obj); }
    public final boolean sendMessage(Message message) { return sendMessageDelayed(message, 0); }
    public final boolean sendEmptyMessage(int what) { return sendMessage(obtainMessage(what)); }
    public final boolean sendEmptyMessageDelayed(int what, long delay) { return sendMessageDelayed(obtainMessage(what), delay); }
    public final boolean sendEmptyMessageAtTime(int what, long when) { return sendMessageAtTime(obtainMessage(what), when); }
    public final boolean sendMessageDelayed(Message message, long delay) {
        long now = SystemClock.uptimeMillis();
        return sendMessageAtTime(message, delay > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + Math.max(0, delay));
    }
    public boolean sendMessageAtTime(Message message, long when) {
        if (message == null) throw new NullPointerException("message");
        if (asynchronous) message.setAsynchronous(true);
        return looper.getQueue().enqueue(message, this, Math.max(0, when));
    }
    public final boolean sendMessageAtFrontOfQueue(Message message) { return sendMessageAtTime(message, 0); }
    private Message task(Runnable runnable, Object token) {
        if (runnable == null) throw new NullPointerException("runnable");
        Message message = Message.obtain(this, runnable); message.obj = token; return message;
    }
    public final boolean post(Runnable runnable) { return sendMessage(task(runnable, null)); }
    public final boolean postDelayed(Runnable runnable, long delay) { return sendMessageDelayed(task(runnable, null), delay); }
    public final boolean postDelayed(Runnable runnable, Object token, long delay) { return sendMessageDelayed(task(runnable, token), delay); }
    public final boolean postAtTime(Runnable runnable, long when) { return postAtTime(runnable, null, when); }
    public final boolean postAtTime(Runnable runnable, Object token, long when) { return sendMessageAtTime(task(runnable, token), when); }
    public final boolean postAtFrontOfQueue(Runnable runnable) { return sendMessageAtFrontOfQueue(task(runnable, null)); }
    public final void removeCallbacks(Runnable runnable) { removeCallbacks(runnable, null); }
    public final void removeCallbacks(Runnable runnable, Object token) { if (runnable == null && token == null) return; looper.getQueue().remove(this, runnable, 0, token, false); }
    public final void removeMessages(int what) { removeMessages(what, null); }
    public final void removeMessages(int what, Object token) { looper.getQueue().remove(this, null, what, token, false); }
    public final void removeCallbacksAndMessages(Object token) { looper.getQueue().remove(this, null, 0, token, true); }
    public final boolean hasMessages(int what) { return hasMessages(what, null); }
    public final boolean hasMessages(int what, Object token) { return looper.getQueue().has(this, null, what, token); }
    public final boolean hasCallbacks(Runnable runnable) { if (runnable == null) throw new NullPointerException("runnable"); return looper.getQueue().has(this, runnable, 0, null); }
}
