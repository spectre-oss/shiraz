// SPDX-License-Identifier: GPL-3.0-only
package android.database;

/** Concatenates an array of {@link Cursor} instances into a single sequential {@link Cursor}. */
public class MergeCursor extends AbstractCursor {
    private Cursor mCursor;
    private final Cursor[] mCursors;
    private final DataSetObserver mObserver =
            new DataSetObserver() {
                @Override
                public void onChanged() {
                    mPos = -1;
                }

                @Override
                public void onInvalidated() {
                    mPos = -1;
                }
            };

    public MergeCursor(Cursor[] cursors) {
        if (cursors == null) {
            throw new NullPointerException("cursors == null");
        }
        mCursors = cursors;
        mCursor = cursors.length > 0 ? cursors[0] : null;
        for (Cursor cursor : mCursors) {
            if (cursor != null) {
                cursor.registerDataSetObserver(mObserver);
            }
        }
    }

    @Override
    public int getCount() {
        int count = 0;
        for (Cursor cursor : mCursors) {
            if (cursor != null) {
                count += cursor.getCount();
            }
        }
        return count;
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        mCursor = null;
        int cursorStartPos = 0;
        for (Cursor cursor : mCursors) {
            if (cursor == null) {
                continue;
            }
            int size = cursor.getCount();
            if (newPosition < (cursorStartPos + size)) {
                mCursor = cursor;
                break;
            }
            cursorStartPos += size;
        }
        if (mCursor != null) {
            return mCursor.moveToPosition(newPosition - cursorStartPos);
        }
        return false;
    }

    @Override public String getString(int column) { return mCursor.getString(column); }
    @Override public short getShort(int column) { return mCursor.getShort(column); }
    @Override public int getInt(int column) { return mCursor.getInt(column); }
    @Override public long getLong(int column) { return mCursor.getLong(column); }
    @Override public float getFloat(int column) { return mCursor.getFloat(column); }
    @Override public double getDouble(int column) { return mCursor.getDouble(column); }
    @Override public int getType(int column) { return mCursor.getType(column); }
    @Override public boolean isNull(int column) { return mCursor.isNull(column); }
    @Override public byte[] getBlob(int column) { return mCursor.getBlob(column); }

    @Override
    public String[] getColumnNames() {
        if (mCursor != null) {
            return mCursor.getColumnNames();
        }
        for (Cursor cursor : mCursors) {
            if (cursor != null) {
                return cursor.getColumnNames();
            }
        }
        return new String[0];
    }

    @Override
    public void deactivate() {
        for (Cursor cursor : mCursors) {
            if (cursor != null) {
                cursor.deactivate();
            }
        }
        super.deactivate();
    }

    @Override
    public void close() {
        for (Cursor cursor : mCursors) {
            if (cursor != null) {
                cursor.close();
            }
        }
        super.close();
    }

    @Override
    public void registerContentObserver(ContentObserver observer) {
        for (Cursor cursor : mCursors) {
            if (cursor != null) {
                cursor.registerContentObserver(observer);
            }
        }
    }

    @Override
    public void unregisterContentObserver(ContentObserver observer) {
        for (Cursor cursor : mCursors) {
            if (cursor != null) {
                cursor.unregisterContentObserver(observer);
            }
        }
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        for (Cursor cursor : mCursors) {
            if (cursor != null) {
                cursor.registerDataSetObserver(observer);
            }
        }
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        for (Cursor cursor : mCursors) {
            if (cursor != null) {
                cursor.unregisterDataSetObserver(observer);
            }
        }
    }

    @Override
    public boolean requery() {
        if (isClosed()) {
            return false;
        }
        for (Cursor cursor : mCursors) {
            if (cursor != null && !cursor.requery()) {
                return false;
            }
        }
        return true;
    }
}
