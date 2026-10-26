// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;
public class SQLiteBlobTooBigException extends SQLiteException {
    public SQLiteBlobTooBigException() { }
    public SQLiteBlobTooBigException(String message) { super(message); }
}
