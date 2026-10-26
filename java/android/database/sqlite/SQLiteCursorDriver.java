// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;
import android.database.Cursor;
public interface SQLiteCursorDriver {
    Cursor query(SQLiteDatabase.CursorFactory factory, String[] selectionArgs);
    void cursorDeactivated(); void cursorRequeried(Cursor cursor); void cursorClosed();
    void setBindArguments(String[] bindArgs);
}
