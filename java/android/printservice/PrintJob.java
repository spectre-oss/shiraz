// SPDX-License-Identifier: GPL-3.0-only
package android.printservice;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintJobId;
import android.print.PrintJobInfo;
import android.print.PrinterId;
import java.util.Objects;

public final class PrintJob {
    private final Context mContext;
    private final PrintDocument mDocument;
    private PrintJobInfo mCachedInfo;
    private String mTag;
    private float mProgress;
    private CharSequence mStatus;

    PrintJob(Context context, PrintJobInfo jobInfo, PrintDocument document) {
        mContext = context;
        mCachedInfo = Objects.requireNonNull(jobInfo, "jobInfo cannot be null");
        mDocument = document;
        syncFieldsFromInfo(jobInfo);
    }

    private void syncFieldsFromInfo(PrintJobInfo info) {
        Parcel parcel = Parcel.obtain();
        try {
            info.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            parcel.readTypedObject(PrintJobId.CREATOR);
            parcel.readString();
            parcel.readTypedObject(PrinterId.CREATOR);
            parcel.readInt();
            parcel.readLong();
            parcel.readInt();
            parcel.createTypedArray(PageRange.CREATOR);
            parcel.readTypedObject(PrintAttributes.CREATOR);
            parcel.readBundle(null);
            mTag = parcel.readString();
            mProgress = parcel.readFloat();
            mStatus = parcel.readString();
        } finally {
            parcel.recycle();
        }
    }

    private void updateInfo(int newState, String newTag, float newProgress, CharSequence newStatus) {
        Parcel src = Parcel.obtain();
        Parcel dst = Parcel.obtain();
        try {
            mCachedInfo.writeToParcel(src, 0);
            src.setDataPosition(0);
            PrintJobId id = src.readTypedObject(PrintJobId.CREATOR);
            String label = src.readString();
            PrinterId printerId = src.readTypedObject(PrinterId.CREATOR);
            src.readInt(); // old state
            long creationTime = src.readLong();
            int copies = src.readInt();
            PageRange[] pages = src.createTypedArray(PageRange.CREATOR);
            PrintAttributes attributes = src.readTypedObject(PrintAttributes.CREATOR);
            Bundle advancedOptions = src.readBundle(null);

            dst.writeTypedObject(id, 0);
            dst.writeString(label);
            dst.writeTypedObject(printerId, 0);
            dst.writeInt(newState);
            dst.writeLong(creationTime);
            dst.writeInt(copies);
            dst.writeTypedArray(pages, 0);
            dst.writeTypedObject(attributes, 0);
            dst.writeBundle(advancedOptions);
            dst.writeString(newTag);
            dst.writeFloat(newProgress);
            dst.writeString(newStatus == null ? null : newStatus.toString());
            dst.setDataPosition(0);

            mCachedInfo = PrintJobInfo.CREATOR.createFromParcel(dst);
            mTag = newTag;
            mProgress = newProgress;
            mStatus = newStatus;
        } finally {
            src.recycle();
            dst.recycle();
        }
    }

    private boolean isInImmutableState() {
        int state = mCachedInfo.getState();
        return state == PrintJobInfo.STATE_COMPLETED
                || state == PrintJobInfo.STATE_CANCELED
                || state == PrintJobInfo.STATE_FAILED;
    }

    public PrintJobId getId() {
        return mCachedInfo.getId();
    }

    public PrintJobInfo getInfo() {
        return mCachedInfo;
    }

    public PrintDocument getDocument() {
        return mDocument;
    }

    public boolean isQueued() {
        return mCachedInfo.getState() == PrintJobInfo.STATE_QUEUED;
    }

    public boolean isStarted() {
        return mCachedInfo.getState() == PrintJobInfo.STATE_STARTED;
    }

    public boolean isBlocked() {
        return mCachedInfo.getState() == PrintJobInfo.STATE_BLOCKED;
    }

    public boolean isCompleted() {
        return mCachedInfo.getState() == PrintJobInfo.STATE_COMPLETED;
    }

    public boolean isFailed() {
        return mCachedInfo.getState() == PrintJobInfo.STATE_FAILED;
    }

    public boolean isCancelled() {
        return mCachedInfo.getState() == PrintJobInfo.STATE_CANCELED;
    }

    public boolean start() {
        int state = mCachedInfo.getState();
        if (state == PrintJobInfo.STATE_QUEUED || state == PrintJobInfo.STATE_BLOCKED) {
            updateInfo(PrintJobInfo.STATE_STARTED, mTag, mProgress, mStatus);
            return true;
        }
        return false;
    }

    public boolean block(String reason) {
        int state = mCachedInfo.getState();
        if (state == PrintJobInfo.STATE_STARTED || state == PrintJobInfo.STATE_BLOCKED) {
            updateInfo(PrintJobInfo.STATE_BLOCKED, mTag, mProgress, reason);
            return true;
        }
        return false;
    }

    public boolean complete() {
        if (isStarted()) {
            updateInfo(PrintJobInfo.STATE_COMPLETED, mTag, mProgress, mStatus);
            return true;
        }
        return false;
    }

    public boolean fail(String error) {
        if (!isInImmutableState()) {
            updateInfo(PrintJobInfo.STATE_FAILED, mTag, mProgress, error);
            return true;
        }
        return false;
    }

    public boolean cancel() {
        if (!isInImmutableState()) {
            updateInfo(PrintJobInfo.STATE_CANCELED, mTag, mProgress, mStatus);
            return true;
        }
        return false;
    }

    public void setProgress(float progress) {
        if (isInImmutableState()) {
            return;
        }
        updateInfo(mCachedInfo.getState(), mTag, progress, mStatus);
    }

    public void setStatus(CharSequence status) {
        if (isInImmutableState()) {
            return;
        }
        updateInfo(mCachedInfo.getState(), mTag, mProgress, status);
    }

    public void setStatus(int statusResId) {
        if (isInImmutableState()) {
            return;
        }
        CharSequence resolved = null;
        if (mContext != null) {
            try {
                resolved = mContext.getResources().getText(statusResId);
            } catch (RuntimeException ignored) {
                resolved = null;
            }
        }
        updateInfo(mCachedInfo.getState(), mTag, mProgress, resolved);
    }

    public boolean setTag(String tag) {
        if (isInImmutableState()) {
            return false;
        }
        updateInfo(mCachedInfo.getState(), tag, mProgress, mStatus);
        return true;
    }

    public String getTag() {
        return mTag;
    }

    public String getAdvancedStringOption(String key) {
        return mCachedInfo.getAdvancedStringOption(key);
    }

    public boolean hasAdvancedOption(String key) {
        return mCachedInfo.hasAdvancedOption(key);
    }

    public int getAdvancedIntOption(String key) {
        return mCachedInfo.getAdvancedIntOption(key);
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
