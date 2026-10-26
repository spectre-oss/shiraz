// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;
public final class SQLiteQuery extends SQLiteProgram {
    SQLiteQuery(SQLiteDatabase database, String sql, Object[] args) { super(database, sql, args); }
}
