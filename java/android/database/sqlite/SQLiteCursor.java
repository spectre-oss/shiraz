// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;
import android.database.MatrixCursor;
import shiraz.runtime.NativeSqlite;
public class SQLiteCursor extends MatrixCursor {
    private final SQLiteQuery query;
    private final SQLiteCursorDriver driver;
    public SQLiteCursor(SQLiteCursorDriver driver, String editTable, SQLiteQuery query) { this(driver, query, query.query()); }
    private SQLiteCursor(SQLiteCursorDriver driver, SQLiteQuery query, NativeSqlite.Result data) {
        super(data.columns, data.rows.length); this.query = query; this.driver = driver; for (Object[] row : data.rows) addRow(row);
    }
    public SQLiteDatabase getDatabase() { return query.database; }
    public void setSelectionArguments(String[] args) { query.clearBindings(); query.bindAllArgsAsStrings(args); }
    @Override public boolean requery() { live(); NativeSqlite.Result data = query.query(); if (!java.util.Arrays.equals(getColumnNames(), data.columns)) throw new SQLiteException("Requery changed cursor columns"); rows.clear(); for (Object[] row : data.rows) addRow(row); position = -1; if (driver != null) driver.cursorRequeried(this); changed(); return true; }
    @Override public void deactivate() { super.deactivate(); if (driver != null) driver.cursorDeactivated(); }
    @Override public void close() { if (isClosed()) return; try { super.close(); } finally { try { query.close(); } finally { if (driver != null) driver.cursorClosed(); } } }
}
