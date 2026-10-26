// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
final class SQLiteFixture {
    static final String NAME = "sqlite-api-fixture.db";
    static final class Helper extends SQLiteOpenHelper {
        final StringBuilder calls = new StringBuilder(); final boolean fail;
        Helper(Context context, int version, boolean fail) { super(context, NAME, null, version); this.fail = fail; }
        public void onConfigure(SQLiteDatabase db) { calls.append('C'); db.setForeignKeyConstraintsEnabled(true); }
        public void onCreate(SQLiteDatabase db) { calls.append('N'); db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY,label TEXT UNIQUE,payload BLOB,amount REAL,flag INTEGER)"); }
        public void onUpgrade(SQLiteDatabase db, int old, int next) { calls.append('U'); db.execSQL("ALTER TABLE entries ADD COLUMN " + (fail ? "failed" : "note") + " TEXT"); if (fail) throw new IllegalStateException("fixture migration failure"); }
        public void onOpen(SQLiteDatabase db) { calls.append('O'); }
    }
    static long count(SQLiteDatabase db, String where) { try (SQLiteStatement statement = db.compileStatement("SELECT count(*) FROM entries" + (where == null ? "" : " WHERE " + where))) { return statement.simpleQueryForLong(); } }
    static void check(Context context) throws Exception {
        ContentValues conversions = new ContentValues(); conversions.put("large", Double.MAX_VALUE); conversions.put("integer-overflow", "2147483648"); conversions.put("short-overflow", "32768"); conversions.put("byte-overflow", "128"); conversions.put("blob", new byte[0]);
        if (conversions.getAsInteger("large") != Integer.MAX_VALUE || conversions.getAsInteger("integer-overflow") != null
                || conversions.getAsShort("short-overflow") != null || conversions.getAsByte("byte-overflow") != null || conversions.getAsBoolean("blob") != null || conversions.getAsByteArray("integer-overflow") != null)
            throw new AssertionError("ContentValues numeric/unsupported conversion");
        context.deleteDatabase(NAME); File path = context.getDatabasePath(NAME); Helper helper = new Helper(context, 1, false);
        if (path.exists() || helper.calls.length() != 0 || !NAME.equals(helper.getDatabaseName())) throw new AssertionError("Helper constructor was not lazy");
        SQLiteDatabase db = helper.getWritableDatabase();
        if (!"CNO".equals(helper.calls.toString()) || helper.getReadableDatabase() != db || db.getVersion() != 1 || !db.isOpen() || db.isReadOnly()
                || !db.getPath().equals(path.getPath())) throw new AssertionError("Helper lifecycle/version");
        if (!Files.getPosixFilePermissions(path.toPath()).equals(PosixFilePermissions.fromString("rw-------"))
                || !Files.getPosixFilePermissions(path.getParentFile().toPath()).equals(PosixFilePermissions.fromString("rwx------"))) throw new AssertionError("Database privacy");
        ContentValues values = new ContentValues(); values.put("label", "café\ud83d\ude80"); values.put("payload", new byte[]{0,-1}); values.put("amount", 2.5); values.put("flag", true);
        long id = db.insertOrThrow("entries", null, values); if (id <= 0 || db.insert("entries", null, values) != -1) throw new AssertionError("Insert conflict result");
        if (db.insertWithOnConflict("entries", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1) throw new AssertionError("Ignored conflict returned an inserted ID");
        final int[] observed = new int[2]; Cursor cursor = db.rawQuery("SELECT id,label,payload,amount,flag,NULL AS missing FROM entries", null);
        cursor.registerDataSetObserver(new DataSetObserver() { public void onChanged() { observed[0]++; } public void onInvalidated() { observed[1]++; } });
        if (cursor.getPosition() != -1 || !cursor.isBeforeFirst() || cursor.getCount() != 1 || !cursor.moveToFirst() || !cursor.isFirst() || !cursor.isLast()
                || cursor.getLong(0) != id || !"café\ud83d\ude80".equals(cursor.getString(cursor.getColumnIndexOrThrow("LABEL")))
                || !Arrays.equals(cursor.getBlob(2), new byte[]{0,-1}) || cursor.getDouble(3) != 2.5 || cursor.getInt(4) != 1 || !cursor.isNull(5)
                || cursor.getType(2) != Cursor.FIELD_TYPE_BLOB || cursor.getType(3) != Cursor.FIELD_TYPE_FLOAT) throw new AssertionError("Typed cursor fields/navigation");
        String[] names = cursor.getColumnNames(); names[0] = "changed"; if (!"id".equals(cursor.getColumnName(0))) throw new AssertionError("Column name alias");
        CharArrayBuffer buffer = new CharArrayBuffer(1); cursor.copyStringToBuffer(1, buffer); if (!"café\ud83d\ude80".equals(new String(buffer.data, 0, buffer.sizeCopied))) throw new AssertionError("Cursor string buffer");
        if (cursor.moveToNext() || !cursor.isAfterLast() || !cursor.moveToPrevious()) throw new AssertionError("Cursor boundary state");
        ContentValues changed = new ContentValues(); changed.put("amount", 7.5);
        if (db.update("entries", changed, "id=?", new String[]{Long.toString(id)}) != 1 || !cursor.requery() || observed[0] != 1 || !cursor.moveToFirst() || cursor.getDouble(3) != 7.5)
            throw new AssertionError("Bound update/requery");
        cursor.close(); cursor.close(); if (observed[1] != 1 || !cursor.isClosed()) throw new AssertionError("Cursor close notification");
        try (Cursor empty = db.query("entries", new String[]{"id"}, "id<0", null, null, null, null)) {
            if (empty.getCount() != 0 || empty.moveToFirst() || empty.isBeforeFirst() || empty.isAfterLast()) throw new AssertionError("Empty cursor states");
        }
        try (SQLiteStatement insert = db.compileStatement("INSERT INTO entries(label,amount) VALUES(?,?)")) {
            insert.bindString(1, "bound"); insert.bindDouble(2, 3.5); if (insert.executeInsert() <= 0) throw new AssertionError("Bound statement insert");
            insert.clearBindings(); insert.bindString(1, "null-value"); insert.executeInsert();
        }
        try (SQLiteStatement scalar = db.compileStatement("SELECT label FROM entries WHERE id=?")) { scalar.bindLong(1, id); if (!"café\ud83d\ude80".equals(scalar.simpleQueryForString())) throw new AssertionError("Scalar string"); }
        long before = count(db, null);
        db.beginTransaction(); try { db.execSQL("INSERT INTO entries(label) VALUES('outer')"); db.beginTransaction(); try { db.execSQL("INSERT INTO entries(label) VALUES('inner')"); } finally { db.endTransaction(); } db.setTransactionSuccessful(); } finally { db.endTransaction(); }
        if (count(db, null) != before) throw new AssertionError("Nested failure did not roll back outer transaction");
        final int[] events = new int[3]; SQLiteTransactionListener listener = new SQLiteTransactionListener() {
            public void onBegin() { events[0]++; } public void onCommit() { events[1]++; } public void onRollback() { events[2]++; }
        };
        db.beginTransactionWithListener(listener); try { db.execSQL("INSERT INTO entries(label) VALUES('committed')"); db.setTransactionSuccessful(); } finally { db.endTransaction(); }
        if (!Arrays.equals(events, new int[]{1,1,0}) || count(db, null) != before + 1 || db.inTransaction()) throw new AssertionError("Transaction commit/listener");
        db.beginTransactionWithListener(new SQLiteTransactionListener() {
            public void onBegin() { } public void onCommit() { throw new IllegalStateException("fixture commit listener failure"); } public void onRollback() { }
        });
        db.execSQL("INSERT INTO entries(label) VALUES('listener-rollback')"); db.setTransactionSuccessful();
        try { db.endTransaction(); throw new AssertionError("Commit listener failure hidden"); } catch (IllegalStateException expected) { }
        if (count(db, "label='listener-rollback'") != 0 || db.inTransaction()) throw new AssertionError("Commit listener failure did not roll back");
        ownership(db, false); ownership(db, true);
        db.execSQL("SAVEPOINT fixture_savepoint"); db.execSQL("INSERT INTO entries(label) VALUES('savepoint')"); db.execSQL("RELEASE fixture_savepoint");
        if (db.inTransaction() || count(db, "label='savepoint'") != 1) throw new AssertionError("Manual savepoint ownership/commit");
        try { db.rawQuery("SELECT ?", new String[]{"a", "b"}); throw new AssertionError("Extra bind args accepted"); } catch (SQLiteBindOrColumnIndexOutOfRangeException expected) { }
        System.gc(); count(db, null); // Failed program construction must not release the database twice.
        if (!db.enableWriteAheadLogging() || !db.isWriteAheadLoggingEnabled()) throw new AssertionError("Actual WAL mode");
        db.disableWriteAheadLogging(); if (db.isWriteAheadLoggingEnabled()) throw new AssertionError("WAL disable");
        helper.close(); helper.close(); if (db.isOpen()) throw new AssertionError("Helper did not close database");
        Helper upgraded = new Helper(context, 2, false); SQLiteDatabase second = upgraded.getWritableDatabase();
        if (!"CUO".equals(upgraded.calls.toString()) || second.getVersion() != 2 || count(second, null) != before + 4) throw new AssertionError("Schema upgrade/persistence");
        upgraded.close();
        Helper broken = new Helper(context, 3, true);
        try { broken.getWritableDatabase(); throw new AssertionError("Migration failure hidden"); } catch (IllegalStateException expected) { }
        broken.close();
        Helper reopened = new Helper(context, 2, false); SQLiteDatabase third = reopened.getWritableDatabase();
        try (Cursor schema = third.rawQuery("PRAGMA table_info(entries)", null)) { while (schema.moveToNext()) if ("failed".equals(schema.getString(1))) throw new AssertionError("Failed migration DDL was committed"); }
        if (third.getVersion() != 2 || !"CO".equals(reopened.calls.toString())) throw new AssertionError("Failed migration changed schema version");
        reopened.close();
        Helper downgrade = new Helper(context, 1, false);
        try { downgrade.getWritableDatabase(); throw new AssertionError("Default downgrade silently succeeded"); } catch (SQLiteException expected) { } finally { downgrade.close(); }
        Files.setPosixFilePermissions(path.toPath(), PosixFilePermissions.fromString("r--------"));
        try {
            if (!Files.isWritable(path.toPath())) {
                Helper readOnlyHelper = new Helper(context, 2, false);
                try {
                    SQLiteDatabase readOnly = readOnlyHelper.getReadableDatabase();
                    if (!readOnly.isReadOnly() || readOnly.getVersion() != 2) throw new AssertionError("Readable helper did not fall back to read-only");
                    try { readOnlyHelper.getWritableDatabase(); throw new AssertionError("Read-only file accepted writable helper"); } catch (SQLiteException expected) { }
                } finally { readOnlyHelper.close(); }
            }
            try (SQLiteDatabase readOnly = SQLiteDatabase.openDatabase(path.getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
                try { readOnly.execSQL("DELETE FROM entries"); throw new AssertionError("Read-only connection changed data"); } catch (SQLiteReadOnlyDatabaseException expected) { }
            }
        } finally { Files.setPosixFilePermissions(path.toPath(), PosixFilePermissions.fromString("rw-------")); }
        memoryAndFactory();
        if (!context.deleteDatabase(NAME)) throw new AssertionError("Database cleanup");
        try { context.getDatabasePath("../escape.db"); throw new AssertionError("Traversing context database name accepted"); } catch (IllegalArgumentException expected) { }
        persistence(context);
        System.out.println("SHIRAZ_FIXTURE_SQLITE_API_OK");
    }
    static void ownership(final SQLiteDatabase db, boolean manual) throws Exception {
        final String label = manual ? "outside-manual" : "outside-java";
        if (manual) db.execSQL("BEGIN IMMEDIATE"); else db.beginTransactionNonExclusive();
        db.execSQL("INSERT INTO entries(label) VALUES('uncommitted-" + label + "')");
        final CountDownLatch ready = new CountDownLatch(1), done = new CountDownLatch(1); final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread writer = new Thread(new Runnable() { public void run() {
            try { if (db.inTransaction()) throw new AssertionError("Other thread inherited transaction state"); ready.countDown(); db.execSQL("INSERT INTO entries(label) VALUES(?)", new Object[]{label}); }
            catch (Throwable issue) { error.set(issue); } finally { ready.countDown(); done.countDown(); }
        }});
        writer.start(); if (!ready.await(2, TimeUnit.SECONDS)) throw new AssertionError("Database worker did not start");
        Thread.sleep(20); boolean blocked = done.getCount() != 0;
        if (manual) db.execSQL("ROLLBACK"); else db.endTransaction();
        writer.join(3000); if (!blocked || writer.isAlive() || error.get() != null) throw new AssertionError("Transaction thread ownership", error.get());
        if (count(db, "label='" + label + "'") != 1 || count(db, "label='uncommitted-" + label + "'") != 0) throw new AssertionError("Other thread's write joined rolled-back transaction");
    }
    static void memoryAndFactory() {
        final int[] calls = new int[1];
        android.os.StrictMode.ThreadPolicy original = android.os.StrictMode.getThreadPolicy();
        android.os.StrictMode.setThreadPolicy(new android.os.StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().penaltyDeath().build());
        try (SQLiteDatabase memory = SQLiteDatabase.create(new SQLiteDatabase.CursorFactory() {
            public Cursor newCursor(SQLiteDatabase database, SQLiteCursorDriver driver, String editTable, SQLiteQuery query) { calls[0]++; return new SQLiteCursor(driver, editTable, query); }
        })) {
            memory.beginTransaction(); try { memory.execSQL("CREATE TABLE test(value)"); memory.execSQL("INSERT INTO test VALUES(1)"); memory.setTransactionSuccessful(); } finally { memory.endTransaction(); }
            try (Cursor cursor = memory.rawQuery("SELECT value FROM test", null)) { if (!cursor.moveToFirst() || cursor.getInt(0) != 1 || calls[0] != 1) throw new AssertionError("Custom cursor factory/in-memory policy"); }
        } finally { android.os.StrictMode.setThreadPolicy(original); }
    }
    static void persistence(Context context) {
        String mode = System.getenv("SHIRAZ_FIXTURE_PREFERENCES"); if (!"write".equals(mode) && !"read".equals(mode)) return;
        String name = "sqlite-persist-fixture.db"; if ("write".equals(mode)) context.deleteDatabase(name);
        try (SQLiteDatabase db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)) {
            if ("write".equals(mode)) { db.execSQL("CREATE TABLE saved(value)"); db.execSQL("INSERT INTO saved VALUES(?)", new Object[]{"café persisted"}); }
            else try (Cursor cursor = db.rawQuery("SELECT value FROM saved", null)) { if (!cursor.moveToFirst() || !"café persisted".equals(cursor.getString(0))) throw new AssertionError("Database did not survive process restart"); System.out.println("SHIRAZ_FIXTURE_SQLITE_PERSISTED"); }
        }
    }
}
