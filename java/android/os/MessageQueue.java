// SPDX-License-Identifier: GPL-3.0-only
package android.os;
/** Ordered in-process queue. Binder and synchronization barriers are not implemented. */
public final class MessageQueue {
    public interface IdleHandler { boolean queueIdle(); }
    private final java.util.ArrayList<IdleHandler> idleHandlers = new java.util.ArrayList<IdleHandler>();
    private boolean idleDispatched;
    public synchronized void addIdleHandler(IdleHandler handler) {
        if (handler == null) throw new NullPointerException("handler");
        idleHandlers.add(handler);
    }
    public synchronized void removeIdleHandler(IdleHandler handler) { idleHandlers.remove(handler); }
    private Message head;
    private int size;
    private boolean quitting;
    private final boolean quitAllowed;
    MessageQueue(boolean quitAllowed) { this.quitAllowed = quitAllowed; }
    synchronized boolean enqueue(Message message, Handler target, long when) {
        if (message == null) throw new NullPointerException("message");
        if (size >= 100000) throw new java.util.concurrent.RejectedExecutionException("Message queue capacity exceeded");
        message.markInUse();
        if (quitting) { message.recycleUnchecked(); return false; }
        message.target = target; message.when = when;
        if (head == null || when == 0 || when < head.when) { message.next = head; head = message; }
        else {
            Message previous = head;
            while (previous.next != null && previous.next.when <= when) previous = previous.next;
            message.next = previous.next; previous.next = message;
        }
        ++size; notifyAll(); return true;
    }
    // Android JNI callers (including Gecko) use the no-argument blocking ABI.
    Message next() { return next(true); }
    Message next(boolean block) {
        for (;;) {
            IdleHandler[] pending = null;
            synchronized (this) {
                long now = SystemClock.uptimeMillis();
                if (head != null && head.when <= now) {
                    Message result = head; head = head.next; result.next = null; --size;
                    idleDispatched = false;
                    return result;
                }
                if (quitting) return null;
                if (!idleDispatched) {
                    idleDispatched = true;
                    if (!idleHandlers.isEmpty()) pending = idleHandlers.toArray(new IdleHandler[idleHandlers.size()]);
                }
                if (pending == null) {
                    if (!block) return null;
                    try { if (head == null) wait(); else wait(Math.max(1, head.when - now)); }
                    catch (InterruptedException ignored) { /* Shutdown uses quit(). */ }
                    continue;
                }
            }
            // Call app code without holding the queue lock, so other threads can post.
            for (IdleHandler handler : pending) {
                boolean keep = false;
                try { keep = handler.queueIdle(); }
                catch (Throwable error) { android.util.Log.w("MessageQueue", "IdleHandler failed", error); }
                if (!keep) removeIdleHandler(handler);
            }
        }
    }
    public synchronized boolean isIdle() { return head == null || head.when > SystemClock.uptimeMillis(); }
    synchronized boolean has(Handler handler, Runnable callback, int what, Object token) {
        for (Message message = head; message != null; message = message.next)
            if (matches(message, handler, callback, what, token, false)) return true;
        return false;
    }
    private boolean matches(Message message, Handler handler, Runnable callback, int what, Object token, boolean all) {
        return message.target == handler && (token == null || message.obj == token) &&
            (all || (callback != null ? message.callback == callback : message.what == what));
    }
    synchronized void remove(Handler handler, Runnable callback, int what, Object token, boolean all) {
        Message previous = null, message = head;
        while (message != null) {
            Message next = message.next;
            if (matches(message, handler, callback, what, token, all)) {
                if (previous == null) head = next; else previous.next = next;
                --size; message.recycleUnchecked();
            } else previous = message;
            message = next;
        }
        notifyAll();
    }
    synchronized void quit(boolean safely, boolean internal) {
        if (!quitAllowed && !internal) throw new IllegalStateException("The main Looper cannot quit");
        if (quitting) return;
        quitting = true;
        long cutoff = SystemClock.uptimeMillis();
        Message previous = null, message = head;
        while (message != null) {
            Message next = message.next;
            if (!safely || message.when > cutoff) {
                if (previous == null) head = next; else previous.next = next;
                --size; message.recycleUnchecked();
            } else previous = message;
            message = next;
        }
        notifyAll();
    }
}
