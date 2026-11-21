// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;

/** Thrown when a value bound or stored in SQLite violates a column's datatype constraint. */
public class SQLiteDatatypeMismatchException extends SQLiteException {
    public SQLiteDatatypeMismatchException() {}

    public SQLiteDatatypeMismatchException(String error) {
        super(error);
    }
}
