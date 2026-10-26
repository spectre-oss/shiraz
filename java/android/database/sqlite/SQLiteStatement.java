// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;
public final class SQLiteStatement extends SQLiteProgram {
    SQLiteStatement(SQLiteDatabase database, String sql, Object[] args) { super(database, sql, args); }
    public void execute() { execute(0); }
    public long executeInsert() { return execute(1); }
    public int executeUpdateDelete() { return (int)execute(2); }
    public long simpleQueryForLong() { return execute(3); }
    public String simpleQueryForString() { return database.scalarString(this); }
}
