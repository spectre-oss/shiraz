// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Deterministic message-by-message stepping controller for a {@link Looper}'s {@link MessageQueue}. */
public class TestLooperManager {
    private static final MessageExecution STOP_EXECUTION = new MessageExecution(null);

    private final Looper looper;
    private final MessageQueue queue;
    private final LinkedBlockingQueue<MessageExecution> executeQueue;
    private boolean looperBlocked;
    private boolean released;

    public TestLooperManager(Looper looper) {
        this.looper = Objects.requireNonNull(looper, "looper");
        this.queue = looper.getQueue();
        if (!looper.isCurrentThread() && looper.getThread().isAlive()) {
            this.executeQueue = new LinkedBlockingQueue<MessageExecution>();
            final CountDownLatch captured = new CountDownLatch(1);
            new Handler(looper).postAtFrontOfQueue(new Runnable() {
                @Override
                public void run() {
                    captured.countDown();
                    runLooperHolder();
                }
            });
            try {
                looperBlocked = captured.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            this.executeQueue = null;
        }
    }

    private void runLooperHolder() {
        while (!released) {
            final MessageExecution item;
            try {
                item = executeQueue.take();
            } catch (InterruptedException e) {
                continue;
            }
            if (item == STOP_EXECUTION || item.message == null) {
                return;
            }
            synchronized (item) {
                try {
                    if (item.message.target != null) {
                        item.message.target.dispatchMessage(item.message);
                    }
                } catch (Throwable t) {
                    item.response = t;
                } finally {
                    item.done = true;
                    item.notifyAll();
                }
            }
        }
    }

    private synchronized void checkReleased() {
        if (released) {
            throw new RuntimeException("TestLooperManager already released");
        }
    }

    public MessageQueue getMessageQueue() {
        checkReleased();
        return queue;
    }

    public Message next() {
        checkReleased();
        return queue.next(true);
    }

    public synchronized void release() {
        if (released) {
            return;
        }
        released = true;
        if (looperBlocked && executeQueue != null) {
            executeQueue.offer(STOP_EXECUTION);
        }
    }

    public void execute(Message message) {
        checkReleased();
        Objects.requireNonNull(message, "message");
        if (looper.isCurrentThread() || !looperBlocked || executeQueue == null) {
            if (message.target != null) {
                message.target.dispatchMessage(message);
            }
            return;
        }
        MessageExecution execution = new MessageExecution(message);
        synchronized (execution) {
            executeQueue.offer(execution);
            while (!execution.done) {
                try {
                    execution.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (execution.response != null) {
                if (execution.response instanceof RuntimeException) {
                    throw (RuntimeException) execution.response;
                }
                if (execution.response instanceof Error) {
                    throw (Error) execution.response;
                }
                throw new RuntimeException(execution.response);
            }
        }
    }

    public void recycle(Message msg) {
        checkReleased();
        Objects.requireNonNull(msg, "msg");
        msg.recycleUnchecked();
    }

    public boolean hasMessages(Handler h, Object object, int what) {
        checkReleased();
        Objects.requireNonNull(h, "h");
        return queue.has(h, null, what, object);
    }

    public boolean hasMessages(Handler h, Object object, Runnable r) {
        checkReleased();
        Objects.requireNonNull(h, "h");
        Objects.requireNonNull(r, "r");
        return queue.has(h, r, 0, object);
    }

    private static final class MessageExecution {
        final Message message;
        Throwable response;
        boolean done;

        MessageExecution(Message message) {
            this.message = message;
        }
    }
}
