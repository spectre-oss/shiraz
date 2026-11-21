// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;

/** Thrown when the SQLite library reports API misuse (`SQLITE_MISUSE`). */
public class SQLiteMisuseException extends SQLiteException {
    public SQLiteMisuseException() {}

    public SQLiteMisuseException(String error) {
        super(error);
    }
}
