// SPDX-License-Identifier: GPL-3.0-only
package java.lang.ref;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a set of object references and corresponding cleaning actions.
 */
public final class Cleaner {
    public interface Cleanable {
        void clean();
    }

    private static final AtomicInteger CLEANER_IDS = new AtomicInteger(1);

    private final ReferenceQueue<Object> mQueue = new ReferenceQueue<Object>();
    private final PhantomCleanable mHead = new PhantomCleanable();

    private Cleaner(ThreadFactory threadFactory) {
        Runnable loop = new Runnable() {
            @Override
            public void run() {
                while (!mHead.isListEmpty()) {
                    try {
                        Cleanable ref = (Cleanable) mQueue.remove(60000L);
                        if (ref != null) {
                            try {
                                ref.clean();
                            } catch (Throwable ignored) {
                                // Exceptions thrown by the cleaning action are ignored per spec.
                            }
                        }
                    } catch (InterruptedException ignored) {
                        // Continue draining active cleanables.
                    }
                }
            }
        };
        // Register a sentinel so the cleaner thread stays alive while the Cleaner itself is reachable.
        new PhantomCleanable(this, this, new Runnable() {
            @Override
            public void run() {
            }
        });
        Thread thread = threadFactory.newThread(loop);
        thread.setDaemon(true);
        thread.start();
    }

    public static Cleaner create() {
        final int id = CLEANER_IDS.getAndIncrement();
        return create(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Cleaner-" + id);
                t.setDaemon(true);
                t.setPriority(Thread.MAX_PRIORITY - 2);
                return t;
            }
        });
    }

    public static Cleaner create(ThreadFactory threadFactory) {
        Objects.requireNonNull(threadFactory, "threadFactory");
        Thread probe = threadFactory.newThread(new Runnable() {
            @Override
            public void run() {
            }
        });
        if (probe == null) {
            throw new NullPointerException("ThreadFactory returned null Thread");
        }
        return new Cleaner(threadFactory);
    }

    public Cleanable register(Object obj, Runnable action) {
        Objects.requireNonNull(obj, "obj");
        Objects.requireNonNull(action, "action");
        return new PhantomCleanable(this, obj, action);
    }

    private static final class PhantomCleanable extends PhantomReference<Object> implements Cleanable {
        private final PhantomCleanable mRoot;
        private final Runnable mAction;
        private final AtomicBoolean mCleaned = new AtomicBoolean(false);
        private PhantomCleanable mPrev;
        private PhantomCleanable mNext;

        PhantomCleanable() {
            super(null, null);
            mRoot = this;
            mAction = null;
            mPrev = this;
            mNext = this;
        }

        PhantomCleanable(Cleaner cleaner, Object referent, Runnable action) {
            super(referent, cleaner.mQueue);
            mRoot = cleaner.mHead;
            mAction = action;
            synchronized (mRoot) {
                mPrev = mRoot;
                mNext = mRoot.mNext;
                mRoot.mNext.mPrev = this;
                mRoot.mNext = this;
            }
        }

        boolean isListEmpty() {
            synchronized (mRoot) {
                return mRoot.mNext == mRoot;
            }
        }

        private boolean remove() {
            synchronized (mRoot) {
                if (mNext != this) {
                    mNext.mPrev = mPrev;
                    mPrev.mNext = mNext;
                    mPrev = this;
                    mNext = this;
                    return true;
                }
                return false;
            }
        }

        @Override
        public void clean() {
            if (mCleaned.compareAndSet(false, true)) {
                remove();
                clear();
                if (mAction != null) {
                    mAction.run();
                }
            }
        }
    }
}
