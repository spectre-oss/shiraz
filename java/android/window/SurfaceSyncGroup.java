// SPDX-License-Identifier: GPL-3.0-only
package android.window;

import android.view.AttachedSurfaceControl;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;

public final class SurfaceSyncGroup {
    private final String mName;
    private final SurfaceControl.Transaction mTransaction = new SurfaceControl.Transaction();
    private boolean mSyncReady;
    private boolean mFinished;

    public SurfaceSyncGroup(String name) {
        mName = name != null ? name : "SurfaceSyncGroup";
    }

    public void markSyncReady() {
        boolean shouldApply = false;
        synchronized (this) {
            if (!mSyncReady) {
                mSyncReady = true;
                mFinished = true;
                shouldApply = true;
            }
        }
        if (shouldApply) {
            mTransaction.apply();
        }
    }

    public boolean add(AttachedSurfaceControl attachedSurfaceControl, Runnable runnable) {
        synchronized (this) {
            if (mSyncReady) {
                return false;
            }
        }
        if (runnable != null) {
            runnable.run();
        }
        if (attachedSurfaceControl != null) {
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            attachedSurfaceControl.applyTransactionOnDraw(t);
            addTransaction(t);
        }
        return true;
    }

    public boolean add(
            SurfaceControlViewHost.SurfacePackage surfacePackage, Runnable runnable) {
        synchronized (this) {
            if (mSyncReady) {
                return false;
            }
        }
        if (runnable != null) {
            runnable.run();
        }
        return true;
    }

    public void addTransaction(SurfaceControl.Transaction transaction) {
        if (transaction == null) {
            return;
        }
        boolean applyImmediately;
        synchronized (this) {
            applyImmediately = mFinished;
            if (!applyImmediately) {
                mTransaction.merge(transaction);
            }
        }
        if (applyImmediately) {
            transaction.apply();
        }
    }

    @Override
    public String toString() {
        return "SurfaceSyncGroup{" + mName + "}";
    }
}
