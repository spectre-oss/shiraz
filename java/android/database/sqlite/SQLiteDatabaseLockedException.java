// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;
public class SQLiteDatabaseLockedException extends SQLiteException {
    public SQLiteDatabaseLockedException() { }
    public SQLiteDatabaseLockedException(String message) { super(message); }
}
