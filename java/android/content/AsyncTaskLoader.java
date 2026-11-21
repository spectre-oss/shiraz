// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.OperationCanceledException;
import android.os.SystemClock;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Abstract Loader that runs loadInBackground on AsyncTask.THREAD_POOL_EXECUTOR with throttling and cancellation. */
@Deprecated
public abstract class AsyncTaskLoader<D> extends Loader<D> {
    final class LoadTask implements Runnable {
        final AtomicBoolean mCancelled = new AtomicBoolean(false);
        boolean waiting;

        void cancelTask() {
            mCancelled.set(true);
            cancelLoadInBackground();
        }

        boolean isCancelled() {
            return mCancelled.get();
        }

        @Override
        public void run() {
            waiting = false;
            executePendingTask();
        }

        void executeOnPool(Executor executor) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    D data = null;
                    boolean opCancelled = false;
                    try {
                        data = onLoadInBackground();
                    } catch (OperationCanceledException ex) {
                        if (!isCancelled()) {
                            throw ex;
                        }
                        opCancelled = true;
                    }
                    final D result = data;
                    final boolean wasCancelled = isCancelled() || opCancelled;
                    Handler main = new Handler(Looper.getMainLooper());
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (wasCancelled) {
                                dispatchOnCancelled(LoadTask.this, result);
                            } else {
                                dispatchOnLoadComplete(LoadTask.this, result);
                            }
                        }
                    });
                }
            });
        }
    }

    private final Executor mExecutor;
    volatile LoadTask mTask;
    volatile LoadTask mCancellingTask;
    long mUpdateThrottle;
    long mLastLoadCompleteTime = -10000L;
    Handler mHandler;

    public AsyncTaskLoader(Context context) {
        super(context);
        mExecutor = AsyncTask.THREAD_POOL_EXECUTOR;
    }

    public void setUpdateThrottle(long delayMS) {
        mUpdateThrottle = delayMS;
        if (delayMS != 0) {
            mHandler = new Handler(Looper.getMainLooper());
        }
    }

    @Override
    protected void onForceLoad() {
        super.onForceLoad();
        cancelLoad();
        mTask = new LoadTask();
        executePendingTask();
    }

    @Override
    protected boolean onCancelLoad() {
        if (mTask != null) {
            if (!mStarted) {
                mContentChanged = true;
            }
            if (mCancellingTask != null) {
                if (mTask.waiting) {
                    mTask.waiting = false;
                    mHandler.removeCallbacks(mTask);
                }
                mTask = null;
                return false;
            } else if (mTask.waiting) {
                mTask.waiting = false;
                mHandler.removeCallbacks(mTask);
                mTask = null;
                return false;
            } else {
                mTask.cancelTask();
                mCancellingTask = mTask;
                mTask = null;
                return true;
            }
        }
        return false;
    }

    public void onCanceled(D data) {}

    void executePendingTask() {
        if (mCancellingTask == null && mTask != null) {
            if (mTask.waiting) {
                mTask.waiting = false;
                mHandler.removeCallbacks(mTask);
            }
            if (mUpdateThrottle > 0) {
                long now = SystemClock.uptimeMillis();
                if (now < (mLastLoadCompleteTime + mUpdateThrottle)) {
                    mTask.waiting = true;
                    mHandler.postAtTime(mTask, mLastLoadCompleteTime + mUpdateThrottle);
                    return;
                }
            }
            mTask.executeOnPool(mExecutor);
        }
    }

    void dispatchOnCancelled(LoadTask task, D data) {
        onCanceled(data);
        if (mCancellingTask == task) {
            rollbackContentChanged();
            mLastLoadCompleteTime = SystemClock.uptimeMillis();
            mCancellingTask = null;
            deliverCancellation();
            executePendingTask();
        }
    }

    void dispatchOnLoadComplete(LoadTask task, D data) {
        if (mTask != task) {
            dispatchOnCancelled(task, data);
        } else {
            if (isAbandoned()) {
                onCanceled(data);
            } else {
                commitContentChanged();
                mLastLoadCompleteTime = SystemClock.uptimeMillis();
                mTask = null;
                deliverResult(data);
            }
        }
    }

    public abstract D loadInBackground();

    protected D onLoadInBackground() {
        return loadInBackground();
    }

    public void cancelLoadInBackground() {}

    public boolean isLoadInBackgroundCanceled() {
        return mCancellingTask != null;
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        if (mTask != null) {
            writer.print(prefix); writer.print("mTask="); writer.print(mTask);
            writer.print(" waiting="); writer.println(mTask.waiting);
        }
        if (mCancellingTask != null) {
            writer.print(prefix); writer.print("mCancellingTask="); writer.print(mCancellingTask);
            writer.print(" waiting="); writer.println(mCancellingTask.waiting);
        }
        if (mUpdateThrottle != 0) {
            writer.print(prefix); writer.print("mUpdateThrottle="); writer.println(mUpdateThrottle);
        }
    }
}
