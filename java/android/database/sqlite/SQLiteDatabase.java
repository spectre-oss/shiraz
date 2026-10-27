// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;
import android.content.ContentValues;
import android.database.*;
import dalvik.system.BlockGuard;
import dalvik.system.CloseGuard;
import android.util.Pair;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import shiraz.runtime.NativeSqlite;

/** One serialized SQLite connection with real transactions and detached, bounded cursors. */
public final class SQLiteDatabase extends SQLiteClosable {
    public static final int OPEN_READWRITE = 0, OPEN_READONLY = 1, NO_LOCALIZED_COLLATORS = 16,
        CREATE_IF_NECESSARY = 0x10000000, ENABLE_WRITE_AHEAD_LOGGING = 0x20000000;
    public static final int CONFLICT_NONE = 0, CONFLICT_ROLLBACK = 1, CONFLICT_ABORT = 2, CONFLICT_FAIL = 3, CONFLICT_IGNORE = 4, CONFLICT_REPLACE = 5;
    public interface CursorFactory { Cursor newCursor(SQLiteDatabase database, SQLiteCursorDriver driver, String editTable, SQLiteQuery query); }
    private static final Set<SQLiteDatabase> ACTIVE = Collections.newSetFromMap(new WeakHashMap<SQLiteDatabase,Boolean>());
    private final AtomicLong handle = new AtomicLong();
    private final ReentrantLock lock = new ReentrantLock(true);
    private final String path;
    private final boolean readOnly;
    private final CursorFactory factory;
    private final DatabaseErrorHandler errorHandler;
    private final CloseGuard guard = CloseGuard.get();
    private static final class Tx { boolean successful, childFailed; final SQLiteTransactionListener listener; Tx(SQLiteTransactionListener listener) { this.listener = listener; } }
    private final ThreadLocal<ArrayDeque<Tx>> transactions = new ThreadLocal<ArrayDeque<Tx>>() { protected ArrayDeque<Tx> initialValue() { return new ArrayDeque<Tx>(); } };
    private final ThreadLocal<Boolean> manual = new ThreadLocal<Boolean>() { protected Boolean initialValue() { return false; } };
    private interface Action<T> { T run(); }
    private SQLiteDatabase(String path, CursorFactory factory, int flags, DatabaseErrorHandler errorHandler) {
        if (path == null) throw new IllegalArgumentException("Null database path");
        if ((flags & ~0x30000011) != 0 || ((flags & OPEN_READONLY) != 0 && (flags & CREATE_IF_NECESSARY) != 0)) throw new IllegalArgumentException("Unsupported database flags");
        this.path = path; this.factory = factory; this.errorHandler = errorHandler; readOnly = (flags & OPEN_READONLY) != 0;
        if (!path.equals(":memory:")) { BlockGuard.getThreadPolicy().onReadFromDisk(); if (!readOnly) BlockGuard.getThreadPolicy().onWriteToDisk(); }
        handle.set(NativeSqlite.open(path.getBytes(StandardCharsets.UTF_8), flags & 0x10000001)); guard.open("close");
        try { if ((flags & ENABLE_WRITE_AHEAD_LOGGING) != 0) enableWriteAheadLogging(); synchronized (ACTIVE) { ACTIVE.add(this); } }
        catch (Throwable error) { dispose(); throw error; }
    }
    public static SQLiteDatabase openDatabase(String path, CursorFactory factory, int flags) { return openDatabase(path, factory, flags, null); }
    public static SQLiteDatabase openDatabase(String path, CursorFactory factory, int flags, DatabaseErrorHandler handler) { return new SQLiteDatabase(path, factory, flags, handler); }
    public static SQLiteDatabase openOrCreateDatabase(String path, CursorFactory factory) { return openDatabase(path, factory, CREATE_IF_NECESSARY); }
    public static SQLiteDatabase openOrCreateDatabase(File path, CursorFactory factory) { return openOrCreateDatabase(path.getPath(), factory); }
    public static SQLiteDatabase openOrCreateDatabase(String path, CursorFactory factory, DatabaseErrorHandler handler) { return openDatabase(path, factory, CREATE_IF_NECESSARY, handler); }
    public static SQLiteDatabase create(CursorFactory factory) { return openOrCreateDatabase(":memory:", factory); }
    private long live() { long value = handle.get(); if (value == 0) throw new IllegalStateException("Database is closed"); return value; }
    private <T> T locked(Action<T> action) {
        lock.lock();
        try { live(); return action.run(); }
        catch (SQLiteDatabaseCorruptException corrupt) { if (errorHandler != null) errorHandler.onCorruption(this); throw corrupt; }
        finally { lock.unlock(); }
    }
    private void trackManualTransaction() {
        if (handle.get() == 0 || !transactions.get().isEmpty()) return;
        boolean automatic = NativeSqlite.autocommit(live());
        if (!automatic && !manual.get()) { acquireReference(); manual.set(true); lock.lock(); }
        else if (automatic && manual.get()) { manual.set(false); lock.unlock(); releaseReference(); }
    }
    long prepare(final String sql) { return locked(new Action<Long>() { public Long run() { if (!path.equals(":memory:")) BlockGuard.getThreadPolicy().onReadFromDisk(); return NativeSqlite.prepare(live(), sql); } }); }
    private void io(SQLiteProgram statement) {
        boolean statementReadOnly = NativeSqlite.isReadOnly(statement.handle());
        if (readOnly && !statementReadOnly) throw new SQLiteReadOnlyDatabaseException("Database is read-only");
        if (path.equals(":memory:")) return;
        if (statementReadOnly) BlockGuard.getThreadPolicy().onReadFromDisk(); else BlockGuard.getThreadPolicy().onWriteToDisk();
    }
    long execute(final SQLiteProgram statement, final int mode) {
        statement.acquireReference();
        try { return locked(new Action<Long>() { public Long run() { io(statement); try { return NativeSqlite.execute(live(), statement.handle(), statement.arguments(), mode); } finally { trackManualTransaction(); } } }); }
        finally { statement.releaseReference(); }
    }
    String scalarString(final SQLiteProgram statement) {
        statement.acquireReference();
        try { return locked(new Action<String>() { public String run() { io(statement); try { return NativeSqlite.scalarString(live(), statement.handle(), statement.arguments()); } finally { trackManualTransaction(); } } }); }
        finally { statement.releaseReference(); }
    }
    NativeSqlite.Result snapshot(final SQLiteProgram statement) {
        statement.acquireReference();
        try { return locked(new Action<NativeSqlite.Result>() { public NativeSqlite.Result run() { io(statement); try { return NativeSqlite.query(live(), statement.handle(), statement.arguments()); } finally { trackManualTransaction(); } } }); }
        finally { statement.releaseReference(); }
    }
    public SQLiteStatement compileStatement(String sql) { return new SQLiteStatement(this, sql, null); }
    public void execSQL(String sql) { execSQL(sql, new Object[0]); }
    public void execSQL(String sql, Object[] arguments) { if (arguments == null) throw new IllegalArgumentException("Null arguments"); try (SQLiteStatement statement = new SQLiteStatement(this, sql, arguments)) { statement.execute(); } }
    public Cursor rawQuery(String sql, String[] args) { return rawQueryWithFactory(null, sql, args, null); }
    public Cursor rawQueryWithFactory(CursorFactory supplied, String sql, String[] args, String editTable) {
        final SQLiteQuery query = new SQLiteQuery(this, sql, args);
        final SQLiteCursorDriver driver = new SQLiteCursorDriver() {
            public Cursor query(CursorFactory next, String[] values) { throw new UnsupportedOperationException("Re-entering a cursor driver is not supported"); }
            public void cursorDeactivated() { } public void cursorRequeried(Cursor cursor) { } public void cursorClosed() { }
            public void setBindArguments(String[] values) { query.clearBindings(); query.bindAllArgsAsStrings(values); }
        };
        try { CursorFactory chosen = supplied == null ? factory : supplied; Cursor cursor = chosen == null ? new SQLiteCursor(driver, editTable, query) : chosen.newCursor(this, driver, editTable, query); if (cursor == null) throw new SQLiteException("Cursor factory returned null"); return cursor; }
        catch (Throwable error) { query.close(); throw error; }
    }
    private static String table(String value) { if (value == null || value.isEmpty() || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid table expression"); return value; }
    private static String identifier(String value) { if (value == null || value.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid identifier"); return "\"" + value.replace("\"", "\"\"") + "\""; }
    private static String conflict(int value) { String[] clauses = {"", " OR ROLLBACK", " OR ABORT", " OR FAIL", " OR IGNORE", " OR REPLACE"}; if (value < 0 || value >= clauses.length) throw new IllegalArgumentException("Invalid conflict policy"); return clauses[value]; }
    public long insert(String table, String nullColumnHack, ContentValues values) { try { return insertOrThrow(table, nullColumnHack, values); } catch (SQLiteException failure) { return -1; } }
    public long insertOrThrow(String table, String nullColumnHack, ContentValues values) { return insertWithOnConflict(table, nullColumnHack, values, CONFLICT_NONE); }
    public long replace(String table, String nullColumnHack, ContentValues values) { try { return replaceOrThrow(table, nullColumnHack, values); } catch (SQLiteException failure) { return -1; } }
    public long replaceOrThrow(String table, String nullColumnHack, ContentValues values) { return insertWithOnConflict(table, nullColumnHack, values, CONFLICT_REPLACE); }
    public long insertWithOnConflict(String table, String nullColumnHack, ContentValues values, int policy) {
        StringBuilder sql = new StringBuilder("INSERT" + conflict(policy) + " INTO " + table(table)); List<Object> args = new ArrayList<Object>();
        if (values == null || values.size() == 0) {
            if (nullColumnHack == null) sql.append(" DEFAULT VALUES"); else sql.append(" (").append(identifier(nullColumnHack)).append(") VALUES(NULL)");
        } else {
            StringBuilder slots = new StringBuilder(); sql.append(" (");
            for (Map.Entry<String,Object> value : values.valueSet()) { if (!args.isEmpty()) { sql.append(','); slots.append(','); } sql.append(identifier(value.getKey())); slots.append('?'); args.add(value.getValue()); }
            sql.append(") VALUES(").append(slots).append(')');
        }
        try (SQLiteStatement statement = new SQLiteStatement(this, sql.toString(), args.toArray())) { return statement.executeInsert(); }
    }
    public int update(String table, ContentValues values, String where, String[] args) { return updateWithOnConflict(table, values, where, args, CONFLICT_NONE); }
    public int updateWithOnConflict(String table, ContentValues values, String where, String[] selectionArgs, int policy) {
        if (values == null || values.size() == 0) throw new IllegalArgumentException("Empty update values");
        StringBuilder sql = new StringBuilder("UPDATE" + conflict(policy) + " " + table(table) + " SET "); List<Object> args = new ArrayList<Object>();
        for (Map.Entry<String,Object> value : values.valueSet()) { if (!args.isEmpty()) sql.append(','); sql.append(identifier(value.getKey())).append("=?"); args.add(value.getValue()); }
        if (where != null && !where.isEmpty()) sql.append(" WHERE ").append(where);
        if (selectionArgs != null) Collections.addAll(args, selectionArgs);
        try (SQLiteStatement statement = new SQLiteStatement(this, sql.toString(), args.toArray())) { return statement.executeUpdateDelete(); }
    }
    public int delete(String table, String where, String[] args) { String sql = "DELETE FROM " + table(table) + (where == null || where.isEmpty() ? "" : " WHERE " + where); try (SQLiteStatement statement = new SQLiteStatement(this, sql, args)) { return statement.executeUpdateDelete(); } }
    public Cursor query(String table, String[] columns, String where, String[] args, String groupBy, String having, String orderBy) { return query(table, columns, where, args, groupBy, having, orderBy, null); }
    public Cursor query(String table, String[] columns, String where, String[] args, String groupBy, String having, String orderBy, String limit) { return query(false, table, columns, where, args, groupBy, having, orderBy, limit); }
    public Cursor query(boolean distinct, String table, String[] columns, String where, String[] args, String groupBy, String having, String orderBy, String limit) {
        if (having != null && !having.isEmpty() && (groupBy == null || groupBy.isEmpty())) throw new IllegalArgumentException("HAVING requires GROUP BY");
        StringBuilder sql = new StringBuilder(distinct ? "SELECT DISTINCT " : "SELECT ");
        if (columns == null || columns.length == 0) sql.append('*'); else for (int i = 0; i < columns.length; ++i) { if (i != 0) sql.append(','); sql.append(columns[i]); }
        sql.append(" FROM ").append(table(table)); String[] prefixes = {" WHERE ", " GROUP BY ", " HAVING ", " ORDER BY ", " LIMIT "}; String[] clauses = {where, groupBy, having, orderBy, limit};
        for (int i = 0; i < clauses.length; ++i) if (clauses[i] != null && !clauses[i].isEmpty()) sql.append(prefixes[i]).append(clauses[i]);
        return rawQuery(sql.toString(), args);
    }
    private long scalar(String sql) { try (SQLiteStatement statement = compileStatement(sql)) { return statement.simpleQueryForLong(); } }
    public int getVersion() { return (int)scalar("PRAGMA user_version"); }
    public void setVersion(int version) { execSQL("PRAGMA user_version=" + version); }
    public long getPageSize() { return scalar("PRAGMA page_size"); }
    public void setPageSize(long bytes) { if (bytes < 512 || bytes > 65536 || (bytes & (bytes - 1)) != 0) throw new IllegalArgumentException("Invalid page size"); execSQL("PRAGMA page_size=" + bytes); }
    public long getMaximumSize() { return scalar("PRAGMA max_page_count") * getPageSize(); }
    public long setMaximumSize(long bytes) { if (bytes < 0) throw new IllegalArgumentException("Negative size"); long page = getPageSize(); return scalar("PRAGMA max_page_count=" + (bytes / page + (bytes % page == 0 ? 0 : 1))) * page; }
    public boolean needUpgrade(int version) { return getVersion() < version; }
    public boolean isOpen() { return handle.get() != 0; }
    public boolean isReadOnly() { live(); return readOnly; }
    public String getPath() { return path; }
    public List<Pair<String, String>> getAttachedDbs() {
        if (!isOpen()) return null;
        List<Pair<String, String>> result = new ArrayList<Pair<String, String>>();
        try (Cursor cursor = rawQuery("PRAGMA database_list", null)) {
            while (cursor.moveToNext()) result.add(Pair.create(cursor.getString(1), cursor.getString(2)));
        }
        return result;
    }
    public void setForeignKeyConstraintsEnabled(boolean value) { if (inTransaction()) throw new IllegalStateException("Foreign keys cannot change in a transaction"); execSQL("PRAGMA foreign_keys=" + (value ? "ON" : "OFF")); }
    public boolean enableWriteAheadLogging() { if (isReadOnly() || path.equals(":memory:")) return false; if (inTransaction()) throw new IllegalStateException("WAL cannot change in a transaction"); try (SQLiteStatement statement = compileStatement("PRAGMA journal_mode=WAL")) { return "wal".equalsIgnoreCase(statement.simpleQueryForString()); } }
    public void disableWriteAheadLogging() { if (inTransaction()) throw new IllegalStateException("WAL cannot change in a transaction"); try (SQLiteStatement statement = compileStatement("PRAGMA journal_mode=DELETE")) { if (!"delete".equalsIgnoreCase(statement.simpleQueryForString()) && !path.equals(":memory:")) throw new SQLiteException("Cannot disable WAL"); } }
    public boolean isWriteAheadLoggingEnabled() { try (SQLiteStatement statement = compileStatement("PRAGMA journal_mode")) { return "wal".equalsIgnoreCase(statement.simpleQueryForString()); } }
    public void setLocale(Locale locale) { throw new UnsupportedOperationException("Localized SQLite collations are not implemented"); }
    public void setMaxSqlCacheSize(int size) { live(); if (size > 100 || size < 0) throw new IllegalStateException("expected value between 0 and 100"); }
    public boolean inTransaction() { live(); return !transactions.get().isEmpty() || manual.get(); }
    public boolean isDbLockedByCurrentThread() { return lock.isHeldByCurrentThread(); }
    public boolean isDbLockedByOtherThreads() { return lock.isLocked() && !lock.isHeldByCurrentThread(); }
    public void beginTransaction() { begin("BEGIN EXCLUSIVE", null); }
    public void beginTransactionNonExclusive() { begin("BEGIN IMMEDIATE", null); }
    public void beginTransactionWithListener(SQLiteTransactionListener listener) { begin("BEGIN EXCLUSIVE", listener); }
    public void beginTransactionWithListenerNonExclusive(SQLiteTransactionListener listener) { begin("BEGIN IMMEDIATE", listener); }
    private void transactionSql(String sql) { try (SQLiteStatement statement = compileStatement(sql)) { NativeSqlite.execute(live(), statement.handle(), statement.arguments(), 0); } }
    private void begin(String sql, SQLiteTransactionListener listener) {
        acquireReference(); lock.lock(); ArrayDeque<Tx> stack = transactions.get(); boolean started = false; Tx tx = new Tx(listener);
        try {
            live(); if (manual.get()) throw new IllegalStateException("Cannot nest Java transaction inside manual SQL transaction");
            if (!stack.isEmpty() && stack.peek().successful) throw new IllegalStateException("Transaction already marked successful");
            if (stack.isEmpty()) { if (!path.equals(":memory:")) BlockGuard.getThreadPolicy().onWriteToDisk(); transactionSql(sql); started = true; }
            stack.push(tx); if (listener != null) listener.onBegin();
        } catch (Throwable error) {
            if (!stack.isEmpty() && stack.peek() == tx) stack.pop();
            if (started) try { transactionSql("ROLLBACK"); } catch (Throwable rollback) { error.addSuppressed(rollback); }
            else if (!stack.isEmpty()) stack.peek().childFailed = true;
            lock.unlock(); releaseReference(); throw error;
        }
    }
    public void setTransactionSuccessful() { if (!lock.isHeldByCurrentThread() || transactions.get().isEmpty()) throw new IllegalStateException("No current transaction"); Tx tx = transactions.get().peek(); if (tx.successful) throw new IllegalStateException("Transaction already marked successful"); tx.successful = true; }
    public void endTransaction() {
        ArrayDeque<Tx> stack = transactions.get(); if (!lock.isHeldByCurrentThread() || stack.isEmpty()) throw new IllegalStateException("No current transaction");
        Tx tx = stack.peek(); boolean successful = tx.successful && !tx.childFailed; Throwable failure = null;
        try {
            try { if (tx.listener != null) { if (successful) tx.listener.onCommit(); else tx.listener.onRollback(); } }
            catch (Throwable error) { successful = false; failure = error; }
            stack.pop();
            if (stack.isEmpty()) {
                try { transactionSql(successful ? "COMMIT" : "ROLLBACK"); }
                catch (Throwable error) { if (failure == null) failure = error; else failure.addSuppressed(error); if (successful) try { transactionSql("ROLLBACK"); } catch (Throwable rollback) { failure.addSuppressed(rollback); } }
            } else if (!successful) stack.peek().childFailed = true;
        } finally { lock.unlock(); releaseReference(); }
        if (failure instanceof RuntimeException) throw (RuntimeException)failure; if (failure instanceof Error) throw (Error)failure; if (failure != null) throw new SQLiteException(failure.toString());
    }
    private void dispose() { long owned = handle.getAndSet(0); try { if (owned != 0) NativeSqlite.close(owned); } finally { guard.close(); synchronized (ACTIVE) { ACTIVE.remove(this); } } }
    @Override protected void onAllReferencesReleased() { dispose(); }
    @Override protected void finalize() throws Throwable { try { if (guard != null) { guard.warnIfOpen(); dispose(); } } finally { super.finalize(); } }
    public static void shirazShutdown() { SQLiteDatabase[] active; synchronized (ACTIVE) { active = ACTIVE.toArray(new SQLiteDatabase[0]); } for (SQLiteDatabase database : active) database.dispose(); }
    public static boolean deleteDatabase(File file) { boolean existed = false, success = true; for (String suffix : new String[]{"", "-journal", "-wal", "-shm"}) { File part = new File(file.getPath() + suffix); if (part.exists()) { existed = true; success &= part.delete(); } } return existed && success; }
}
