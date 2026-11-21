// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;

/** Caches rows from a Cursor in a Map of ContentValues keyed by a designated column. */
public class ContentQueryMap extends Observable {
    private volatile Cursor mCursor;
    private String[] mColumnNames;
    private int mKeyColumn;
    private Handler mHandlerForUpdateNotifications;
    private boolean mKeepUpdated = false;
    private Map<String, ContentValues> mValues = null;
    private ContentObserver mContentObserver;
    private boolean mDirty = false;

    public ContentQueryMap(Cursor cursor, String columnNameOfKey, boolean keepUpdated,
            Handler handlerForUpdateNotifications) {
        mCursor = cursor;
        mColumnNames = mCursor.getColumnNames();
        mKeyColumn = mCursor.getColumnIndexOrThrow(columnNameOfKey);
        mHandlerForUpdateNotifications = handlerForUpdateNotifications;
        setKeepUpdated(keepUpdated);
        if (!keepUpdated) {
            readCursorIntoCache(cursor);
        }
    }

    public void setKeepUpdated(boolean keepUpdated) {
        if (keepUpdated == mKeepUpdated) return;
        mKeepUpdated = keepUpdated;

        if (!mKeepUpdated) {
            if (mContentObserver != null && mCursor != null) {
                mCursor.unregisterContentObserver(mContentObserver);
                mContentObserver = null;
            }
        } else {
            if (mHandlerForUpdateNotifications == null) {
                mHandlerForUpdateNotifications = new Handler();
            }
            if (mContentObserver == null) {
                mContentObserver = new ContentObserver(mHandlerForUpdateNotifications) {
                    @Override
                    public void onChange(boolean selfChange) {
                        if (countObservers() != 0) {
                            requery();
                        } else {
                            mDirty = true;
                        }
                    }
                };
            }
            mCursor.registerContentObserver(mContentObserver);
            mDirty = true;
        }
    }

    public synchronized ContentValues getValues(String rowName) {
        if (mDirty) requery();
        return mValues != null ? mValues.get(rowName) : null;
    }

    public void requery() {
        final Cursor cursor = mCursor;
        if (cursor == null) {
            return;
        }
        mDirty = false;
        if (!cursor.requery()) {
            return;
        }
        readCursorIntoCache(cursor);
        setChanged();
        notifyObservers();
    }

    private synchronized void readCursorIntoCache(Cursor cursor) {
        int capacity = mValues != null ? mValues.size() : 0;
        mValues = new HashMap<String, ContentValues>(capacity);
        if (cursor.moveToFirst()) {
            do {
                ContentValues values = new ContentValues();
                for (int i = 0; i < mColumnNames.length; i++) {
                    if (i != mKeyColumn) {
                        values.put(mColumnNames[i], cursor.getString(i));
                    }
                }
                mValues.put(cursor.getString(mKeyColumn), values);
            } while (cursor.moveToNext());
        }
    }

    public synchronized Map<String, ContentValues> getRows() {
        if (mDirty) requery();
        return mValues;
    }

    public synchronized void close() {
        if (mContentObserver != null && mCursor != null) {
            mCursor.unregisterContentObserver(mContentObserver);
            mContentObserver = null;
        }
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
        mValues = null;
    }

    @Override
    protected void finalize() throws Throwable {
        if (mCursor != null) close();
        super.finalize();
    }
}
