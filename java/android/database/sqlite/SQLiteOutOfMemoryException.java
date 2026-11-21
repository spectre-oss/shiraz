// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;

/** Thrown when SQLite fails to allocate memory (`SQLITE_NOMEM`). */
public class SQLiteOutOfMemoryException extends SQLiteException {
    public SQLiteOutOfMemoryException() {}

    public SQLiteOutOfMemoryException(String error) {
        super(error);
    }
}
