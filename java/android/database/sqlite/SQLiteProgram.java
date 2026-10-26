// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;
import dalvik.system.CloseGuard;
import shiraz.runtime.NativeSqlite;
public abstract class SQLiteProgram extends SQLiteClosable {
    final SQLiteDatabase database;
    private final Object[] arguments;
    private long statement;
    private boolean databaseHeld;
    private final CloseGuard guard = CloseGuard.get();
    SQLiteProgram(SQLiteDatabase database, String sql, Object[] initial) {
        this.database = database; database.acquireReference(); databaseHeld = true; long owned = 0;
        try {
            owned = database.prepare(sql); arguments = new Object[NativeSqlite.parameterCount(owned)];
            if (initial != null && initial.length > arguments.length) throw new SQLiteBindOrColumnIndexOutOfRangeException("Too many bind arguments");
            if (initial != null) for (int i = 0; i < initial.length; ++i) arguments[i] = normalize(initial[i]);
            statement = owned; guard.open("close");
        } catch (Throwable error) { if (owned != 0) NativeSqlite.finalizeStatement(owned); databaseHeld = false; database.releaseReference(); throw error; }
    }
    private static Object normalize(Object value) {
        if (value == null || value instanceof String || value instanceof byte[]) return value;
        if (value instanceof Boolean) return (Boolean)value ? 1L : 0L;
        if (value instanceof Float || value instanceof Double) return ((Number)value).doubleValue();
        if (value instanceof Number) return ((Number)value).longValue();
        throw new IllegalArgumentException("Unsupported SQL value type");
    }
    private synchronized void bind(int index, Object value) { if (statement == 0) throw new IllegalStateException("Statement is closed"); if (index < 1 || index > arguments.length) throw new SQLiteBindOrColumnIndexOutOfRangeException("Invalid binding index"); arguments[index - 1] = value; }
    public void bindNull(int index) { bind(index, null); }
    public void bindLong(int index, long value) { bind(index, value); }
    public void bindDouble(int index, double value) { bind(index, value); }
    public void bindString(int index, String value) { if (value == null) throw new IllegalArgumentException("Null string binding"); bind(index, value); }
    public void bindBlob(int index, byte[] value) { if (value == null) throw new IllegalArgumentException("Null blob binding"); bind(index, value); }
    public synchronized void clearBindings() { if (statement == 0) throw new IllegalStateException("Statement is closed"); java.util.Arrays.fill(arguments, null); }
    public synchronized void bindAllArgsAsStrings(String[] values) { if (values == null) return; for (int i = values.length; i > 0; --i) bindString(i, values[i - 1]); }
    final synchronized long handle() { if (statement == 0) throw new IllegalStateException("Statement is closed"); return statement; }
    final synchronized Object[] arguments() { return arguments.clone(); }
    final long execute(int mode) { return database.execute(this, mode); }
    final NativeSqlite.Result query() { return database.snapshot(this); }
    @Override protected void onAllReferencesReleased() {
        long owned; synchronized (this) { owned = statement; statement = 0; guard.close(); if (!databaseHeld) return; databaseHeld = false; }
        try { if (owned != 0) NativeSqlite.finalizeStatement(owned); } finally { database.releaseReference(); }
    }
    @Override protected void finalize() throws Throwable { try { if (guard != null) { guard.warnIfOpen(); if (databaseHeld) onAllReferencesReleased(); } } finally { super.finalize(); } }
}
