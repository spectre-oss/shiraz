// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import shiraz.runtime.NativeSqlite;

/**
 * Low-level prepared SQLite statement API (introduced in Android 15 / API 35) backed by
 * {@link SQLiteDatabase} and {@link NativeSqlite}.
 */
public final class SQLiteRawStatement implements Closeable {
    public static final int SQLITE_DATA_TYPE_INTEGER = 1;
    public static final int SQLITE_DATA_TYPE_FLOAT = 2;
    public static final int SQLITE_DATA_TYPE_TEXT = 3;
    public static final int SQLITE_DATA_TYPE_BLOB = 4;
    public static final int SQLITE_DATA_TYPE_NULL = 5;

    private final SQLiteDatabase mDatabase;
    private final String mSql;
    private final SQLiteQuery mQuery;
    private final Object[] mBindings;
    private final String[] mParamNames;
    private NativeSqlite.Result mCurrentResult;
    private int mRowIndex = -1;
    private boolean mClosed;

    SQLiteRawStatement() {
        mDatabase = null;
        mSql = "";
        mQuery = null;
        mBindings = new Object[0];
        mParamNames = new String[0];
        mClosed = true;
    }

    SQLiteRawStatement(SQLiteDatabase database, String sql) {
        mDatabase = Objects.requireNonNull(database, "database == null");
        mSql = Objects.requireNonNull(sql, "sql == null");
        mQuery = new SQLiteQuery(database, sql, null);
        int count = NativeSqlite.parameterCount(mQuery.handle());
        mBindings = new Object[count];
        mParamNames = parseParameterNames(sql, count);
    }

    @Override
    public void close() {
        if (!mClosed) {
            mClosed = true;
            mCurrentResult = null;
            mRowIndex = -1;
            if (mQuery != null) {
                mQuery.close();
            }
        }
    }

    public boolean isOpen() {
        return !mClosed;
    }

    private void throwIfClosed() {
        if (mClosed) {
            throw new IllegalStateException("SQLiteRawStatement is closed");
        }
    }

    private void syncBindingsToQuery() {
        mQuery.clearBindings();
        for (int i = 0; i < mBindings.length; i++) {
            Object v = mBindings[i];
            int idx = i + 1;
            if (v == null) {
                mQuery.bindNull(idx);
            } else if (v instanceof Long) {
                mQuery.bindLong(idx, ((Long) v).longValue());
            } else if (v instanceof Double) {
                mQuery.bindDouble(idx, ((Double) v).doubleValue());
            } else if (v instanceof String) {
                mQuery.bindString(idx, (String) v);
            } else if (v instanceof byte[]) {
                mQuery.bindBlob(idx, (byte[]) v);
            }
        }
    }

    public boolean step() {
        throwIfClosed();
        if (mCurrentResult == null) {
            syncBindingsToQuery();
            boolean readOrReturning =
                    NativeSqlite.isReadOnly(mQuery.handle())
                            || mSql.toUpperCase(java.util.Locale.ROOT).contains(" RETURNING ");
            if (readOrReturning) {
                mCurrentResult = mQuery.query();
                mRowIndex = 0;
                return mRowIndex < mCurrentResult.rows.length;
            }
            mQuery.execute(0);
            mCurrentResult = new NativeSqlite.Result(new String[0], new Object[0][]);
            mRowIndex = 0;
            return false;
        }
        if (mRowIndex < mCurrentResult.rows.length) {
            mRowIndex++;
        }
        return mRowIndex < mCurrentResult.rows.length;
    }

    public void reset() {
        throwIfClosed();
        mCurrentResult = null;
        mRowIndex = -1;
    }

    public void clearBindings() {
        throwIfClosed();
        Arrays.fill(mBindings, null);
        mQuery.clearBindings();
    }

    public int getParameterCount() {
        throwIfClosed();
        return mBindings.length;
    }

    public int getParameterIndex(String name) {
        throwIfClosed();
        Objects.requireNonNull(name, "name == null");
        for (int i = 0; i < mParamNames.length; i++) {
            if (name.equals(mParamNames[i])) {
                return i + 1;
            }
        }
        return 0;
    }

    public String getParameterName(int index) {
        throwIfClosed();
        if (index < 1 || index > mParamNames.length) {
            return null;
        }
        return mParamNames[index - 1];
    }

    private void checkBindIndex(int index) {
        throwIfClosed();
        if (index < 1 || index > mBindings.length) {
            throw new SQLiteBindOrColumnIndexOutOfRangeException("Invalid bind index: " + index);
        }
    }

    public void bindBlob(int index, byte[] value) {
        Objects.requireNonNull(value, "value == null");
        checkBindIndex(index);
        mBindings[index - 1] = value.clone();
    }

    public void bindBlob(int index, byte[] value, int offset, int length) {
        Objects.requireNonNull(value, "value == null");
        if (offset < 0 || length < 0 || offset + length > value.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        checkBindIndex(index);
        mBindings[index - 1] = Arrays.copyOfRange(value, offset, offset + length);
    }

    public void bindDouble(int index, double value) {
        checkBindIndex(index);
        mBindings[index - 1] = Double.valueOf(value);
    }

    public void bindInt(int index, int value) {
        checkBindIndex(index);
        mBindings[index - 1] = Long.valueOf(value);
    }

    public void bindLong(int index, long value) {
        checkBindIndex(index);
        mBindings[index - 1] = Long.valueOf(value);
    }

    public void bindNull(int index) {
        checkBindIndex(index);
        mBindings[index - 1] = null;
    }

    public void bindText(int index, String value) {
        Objects.requireNonNull(value, "value == null");
        checkBindIndex(index);
        mBindings[index - 1] = value;
    }

    private Object currentCell(int columnIndex) {
        throwIfClosed();
        if (mCurrentResult == null || mRowIndex < 0 || mRowIndex >= mCurrentResult.rows.length) {
            throw new SQLiteMisuseException("No active row in SQLiteRawStatement");
        }
        if (columnIndex < 0 || columnIndex >= mCurrentResult.columns.length) {
            throw new SQLiteBindOrColumnIndexOutOfRangeException("Invalid column index: " + columnIndex);
        }
        return mCurrentResult.rows[mRowIndex][columnIndex];
    }

    public int getResultColumnCount() {
        throwIfClosed();
        return mCurrentResult != null ? mCurrentResult.columns.length : 0;
    }

    public int getColumnType(int columnIndex) {
        Object v = currentCell(columnIndex);
        if (v == null) return SQLITE_DATA_TYPE_NULL;
        if (v instanceof Long || v instanceof Integer) return SQLITE_DATA_TYPE_INTEGER;
        if (v instanceof Double || v instanceof Float) return SQLITE_DATA_TYPE_FLOAT;
        if (v instanceof byte[]) return SQLITE_DATA_TYPE_BLOB;
        return SQLITE_DATA_TYPE_TEXT;
    }

    public String getColumnName(int columnIndex) {
        throwIfClosed();
        if (mCurrentResult == null || columnIndex < 0 || columnIndex >= mCurrentResult.columns.length) {
            throw new SQLiteBindOrColumnIndexOutOfRangeException("Invalid column index: " + columnIndex);
        }
        return mCurrentResult.columns[columnIndex];
    }

    public int getColumnLength(int columnIndex) {
        Object v = currentCell(columnIndex);
        if (v == null) return 0;
        if (v instanceof byte[]) return ((byte[]) v).length;
        return v.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    public byte[] getColumnBlob(int columnIndex) {
        Object v = currentCell(columnIndex);
        if (v == null) return null;
        if (v instanceof byte[]) return ((byte[]) v).clone();
        return v.toString().getBytes(StandardCharsets.UTF_8);
    }

    public int readColumnBlob(
            int columnIndex, byte[] buffer, int offset, int length, int srcOffset) {
        Objects.requireNonNull(buffer, "buffer == null");
        if (offset < 0 || length < 0 || srcOffset < 0 || offset + length > buffer.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        byte[] src = getColumnBlob(columnIndex);
        if (src == null || srcOffset >= src.length) {
            return 0;
        }
        int count = Math.min(length, src.length - srcOffset);
        System.arraycopy(src, srcOffset, buffer, offset, count);
        return count;
    }

    public double getColumnDouble(int columnIndex) {
        Object v = currentCell(columnIndex);
        if (v == null) return 0.0d;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException ignored) {
            return 0.0d;
        }
    }

    public int getColumnInt(int columnIndex) {
        return (int) getColumnLong(columnIndex);
    }

    public long getColumnLong(int columnIndex) {
        Object v = currentCell(columnIndex);
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public String getColumnText(int columnIndex) {
        Object v = currentCell(columnIndex);
        if (v == null) return null;
        if (v instanceof byte[]) return new String((byte[]) v, StandardCharsets.UTF_8);
        return v.toString();
    }

    @Override
    public String toString() {
        return "SQLiteRawStatement: " + mSql;
    }

    private static String[] parseParameterNames(String sql, int count) {
        String[] names = new String[count];
        if (count == 0 || sql == null) {
            return names;
        }
        List<String> found = new ArrayList<String>();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (!inSingle && !inDouble) {
                if (c == '?') {
                    int end = i + 1;
                    while (end < sql.length() && Character.isDigit(sql.charAt(end))) {
                        end++;
                    }
                    found.add(end > i + 1 ? sql.substring(i, end) : null);
                    i = end - 1;
                } else if (c == ':' || c == '@' || c == '$') {
                    int end = i + 1;
                    while (end < sql.length()
                            && (Character.isLetterOrDigit(sql.charAt(end)) || sql.charAt(end) == '_')) {
                        end++;
                    }
                    if (end > i + 1) {
                        String token = sql.substring(i, end);
                        if (!found.contains(token)) {
                            found.add(token);
                        }
                        i = end - 1;
                    }
                }
            }
        }
        for (int i = 0; i < Math.min(count, found.size()); i++) {
            names[i] = found.get(i);
        }
        return names;
    }
}
