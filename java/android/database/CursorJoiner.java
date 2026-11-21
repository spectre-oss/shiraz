// SPDX-License-Identifier: GPL-3.0-only
package android.database;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Performs a streaming sort-merge join across two {@link Cursor} instances pre-sorted by the
 * supplied key columns.
 */
public final class CursorJoiner implements Iterator<CursorJoiner.Result>, Iterable<CursorJoiner.Result> {
    /** Join match outcome for the current cursor positions. */
    public enum Result {
        RIGHT,
        LEFT,
        BOTH
    }

    private final Cursor mCursorLeft;
    private final Cursor mCursorRight;
    private boolean mCompareResultIsValid;
    private Result mCompareResult;
    private final int[] mColumnsLeft;
    private final int[] mColumnsRight;
    private final String[] mValues;

    public CursorJoiner(
            Cursor cursorLeft,
            String[] columnNamesLeft,
            Cursor cursorRight,
            String[] columnNamesRight) {
        if (columnNamesLeft.length != columnNamesRight.length) {
            throw new IllegalArgumentException(
                    "you must have the same number of columns on the left and right, "
                            + columnNamesLeft.length + " != " + columnNamesRight.length);
        }
        mCursorLeft = cursorLeft;
        mCursorRight = cursorRight;
        mColumnsLeft = buildColumnIndexArray(cursorLeft, columnNamesLeft);
        mColumnsRight = buildColumnIndexArray(cursorRight, columnNamesRight);
        mCursorLeft.moveToFirst();
        mCursorRight.moveToFirst();
        mCompareResultIsValid = false;
        mValues = new String[mColumnsLeft.length * 2];
    }

    @Override
    public Iterator<Result> iterator() {
        return this;
    }

    private static int[] buildColumnIndexArray(Cursor cursor, String[] columnNames) {
        int[] columns = new int[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            columns[i] = cursor.getColumnIndexOrThrow(columnNames[i]);
        }
        return columns;
    }

    private static void populateValues(String[] values, Cursor cursor, int[] columnIndicies, int startingIndex) {
        for (int i = 0; i < columnIndicies.length; i++) {
            values[startingIndex + i * 2] = cursor.getString(columnIndicies[i]);
        }
    }

    private void incrementCursors() {
        if (mCompareResultIsValid) {
            switch (mCompareResult) {
                case LEFT:
                    mCursorLeft.moveToNext();
                    break;
                case RIGHT:
                    mCursorRight.moveToNext();
                    break;
                case BOTH:
                    mCursorLeft.moveToNext();
                    mCursorRight.moveToNext();
                    break;
            }
            mCompareResultIsValid = false;
        }
    }

    @Override
    public boolean hasNext() {
        if (mCompareResultIsValid) {
            switch (mCompareResult) {
                case BOTH:
                    return !mCursorLeft.isLast() || !mCursorRight.isLast();
                case LEFT:
                    return !mCursorLeft.isLast() || !mCursorRight.isAfterLast();
                case RIGHT:
                    return !mCursorLeft.isAfterLast() || !mCursorRight.isLast();
            }
        }
        return !mCursorLeft.isAfterLast() || !mCursorRight.isAfterLast();
    }

    @Override
    public Result next() {
        if (!hasNext()) {
            throw new NoSuchElementException("you must only call next() when hasNext() is true");
        }
        incrementCursors();
        boolean hasLeft = !mCursorLeft.isAfterLast();
        boolean hasRight = !mCursorRight.isAfterLast();
        if (hasLeft && hasRight) {
            populateValues(mValues, mCursorLeft, mColumnsLeft, 0);
            populateValues(mValues, mCursorRight, mColumnsRight, 1);
            switch (compareStrings(mValues)) {
                case -1:
                    mCompareResult = Result.LEFT;
                    break;
                case 0:
                    mCompareResult = Result.BOTH;
                    break;
                case 1:
                    mCompareResult = Result.RIGHT;
                    break;
            }
        } else if (hasLeft) {
            mCompareResult = Result.LEFT;
        } else {
            mCompareResult = Result.RIGHT;
        }
        mCompareResultIsValid = true;
        return mCompareResult;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("not implemented");
    }

    private static int compareStrings(String... values) {
        if ((values.length % 2) != 0) {
            throw new IllegalArgumentException("you must specify an even number of values");
        }
        for (int index = 0; index < values.length; index += 2) {
            if (values[index] == null) {
                if (values[index + 1] == null) {
                    continue;
                }
                return -1;
            }
            if (values[index + 1] == null) {
                return 1;
            }
            int comp = values[index].compareTo(values[index + 1]);
            if (comp != 0) {
                return comp < 0 ? -1 : 1;
            }
        }
        return 0;
    }
}
