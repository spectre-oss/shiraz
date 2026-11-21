// SPDX-License-Identifier: GPL-3.0-only
package android.print;

import java.util.Objects;

public final class PrintJob {
    private PrintJobInfo mCachedInfo;
    private final PrintManager mPrintManager;

    PrintJob(PrintJobInfo info, PrintManager printManager) {
        this.mCachedInfo = Objects.requireNonNull(info, "info");
        this.mPrintManager = printManager;
    }

    public PrintJobId getId() {
        return mCachedInfo.getId();
    }

    public PrintJobInfo getInfo() {
        if (mPrintManager != null && !isInImmutableState()) {
            PrintJobInfo updated = mPrintManager.getPrintJobInfo(mCachedInfo.getId());
            if (updated != null) {
                mCachedInfo = updated;
            }
        }
        return mCachedInfo;
    }

    public void cancel() {
        int state = getInfo().getState();
        if (state == PrintJobInfo.STATE_CREATED
                || state == PrintJobInfo.STATE_QUEUED
                || state == PrintJobInfo.STATE_STARTED
                || state == PrintJobInfo.STATE_BLOCKED) {
            if (mPrintManager != null) {
                mPrintManager.cancelPrintJob(mCachedInfo.getId());
                PrintJobInfo updated = mPrintManager.getPrintJobInfo(mCachedInfo.getId());
                if (updated != null) {
                    mCachedInfo = updated;
                }
            } else {
                mCachedInfo.setState(PrintJobInfo.STATE_CANCELED);
            }
        }
    }

    public void restart() {
        if (isFailed() || isBlocked()) {
            if (mPrintManager != null) {
                mPrintManager.restartPrintJob(mCachedInfo.getId());
                PrintJobInfo updated = mPrintManager.getPrintJobInfo(mCachedInfo.getId());
                if (updated != null) {
                    mCachedInfo = updated;
                }
            } else {
                mCachedInfo.setState(PrintJobInfo.STATE_QUEUED);
            }
        }
    }

    public boolean isQueued() {
        return getInfo().getState() == PrintJobInfo.STATE_QUEUED;
    }

    public boolean isStarted() {
        return getInfo().getState() == PrintJobInfo.STATE_STARTED;
    }

    public boolean isBlocked() {
        return getInfo().getState() == PrintJobInfo.STATE_BLOCKED;
    }

    public boolean isCompleted() {
        return getInfo().getState() == PrintJobInfo.STATE_COMPLETED;
    }

    public boolean isFailed() {
        return getInfo().getState() == PrintJobInfo.STATE_FAILED;
    }

    public boolean isCancelled() {
        return getInfo().getState() == PrintJobInfo.STATE_CANCELED;
    }

    private boolean isInImmutableState() {
        int state = mCachedInfo.getState();
        return state == PrintJobInfo.STATE_COMPLETED || state == PrintJobInfo.STATE_CANCELED;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        PrintJob other = (PrintJob) obj;
        return Objects.equals(mCachedInfo.getId(), other.mCachedInfo.getId());
    }

    @Override
    public int hashCode() {
        PrintJobId id = mCachedInfo.getId();
        return id == null ? 0 : id.hashCode();
    }
}
