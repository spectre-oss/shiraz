// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;
public class SQLiteDatabaseCorruptException extends SQLiteException {
    public SQLiteDatabaseCorruptException() { }
    public SQLiteDatabaseCorruptException(String message) { super(message); }
}
