// SPDX-License-Identifier: GPL-3.0-only
package android.database;

/** {@link CursorWrapper} implementing {@link CrossProcessCursor} window delegation. */
public class CrossProcessCursorWrapper extends CursorWrapper implements CrossProcessCursor {
    public CrossProcessCursorWrapper(Cursor cursor) {
        super(cursor);
    }

    @Override
    public void fillWindow(int position, CursorWindow window) {
        if (mCursor instanceof CrossProcessCursor) {
            ((CrossProcessCursor) mCursor).fillWindow(position, window);
            return;
        }
        DatabaseUtils.cursorFillWindow(mCursor, position, window);
    }

    @Override
    public CursorWindow getWindow() {
        if (mCursor instanceof CrossProcessCursor) {
            return ((CrossProcessCursor) mCursor).getWindow();
        }
        return null;
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        if (mCursor instanceof CrossProcessCursor) {
            return ((CrossProcessCursor) mCursor).onMove(oldPosition, newPosition);
        }
        return true;
    }
}
