// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.NativeSqlite;
import android.database.sqlite.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
public final class NativeSqliteSmoke {
    static final Object[] NONE = new Object[0];
    interface Task { void run() throws Exception; }
    static void fails(Class<? extends Throwable> type, Task task) throws Exception {
        try { task.run(); } catch (Throwable issue) { if (type.isInstance(issue)) return; throw new AssertionError("Wrong database exception", issue); }
        throw new AssertionError("Expected " + type.getName());
    }
    static long open(String path, int flags) { return NativeSqlite.open(path.getBytes(StandardCharsets.UTF_8), flags); }
    static long execute(long db, String sql, Object[] args, int mode) {
        long statement = NativeSqlite.prepare(db, sql);
        try { return NativeSqlite.execute(db, statement, args, mode); } finally { NativeSqlite.finalizeStatement(statement); }
    }
    static void sql(long db, String value) { execute(db, value, NONE, 0); }
    static NativeSqlite.Result query(long db, String sql, Object[] args) {
        long statement = NativeSqlite.prepare(db, sql);
        try { return NativeSqlite.query(db, statement, args); } finally { NativeSqlite.finalizeStatement(statement); }
    }
    static long count(long db, String table) { return execute(db, "SELECT count(*) FROM " + table, NONE, 3); }
    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args[0]); Files.createDirectories(root);
        Path file = root.resolve("values-café.db"); Files.deleteIfExists(file);
        long db = open(file.toString(), 0x10000000);
        try {
            if (!Files.getPosixFilePermissions(file).equals(PosixFilePermissions.fromString("rw-------"))) throw new AssertionError("Database file privacy");
            sql(db, "CREATE TABLE values_test(id INTEGER PRIMARY KEY, value TEXT UNIQUE, wide INTEGER, real_value REAL, blob_value BLOB, absent)");
            long insert = NativeSqlite.prepare(db, "INSERT INTO values_test(value,wide,real_value,blob_value,absent) VALUES(?,?,?,?,?)");
            String text = "café\u0000\ud83d\ude80";
            try {
                if (NativeSqlite.parameterCount(insert) != 5) throw new AssertionError("Parameter count");
                if (NativeSqlite.execute(db, insert, new Object[]{text, Long.MAX_VALUE, 1.25d, new byte[]{0, -1, 4}, null}, 1) != 1)
                    throw new AssertionError("Insert row ID");
                final long owner = db, prepared = insert;
                fails(SQLiteConstraintException.class, new Task() { public void run() { NativeSqlite.execute(owner, prepared, new Object[]{text, 0L, 0d, new byte[0], null}, 1); } });
                if (NativeSqlite.execute(db, insert, new Object[]{"", Long.MIN_VALUE, -2.5d, new byte[0], null}, 1) != 2)
                    throw new AssertionError("Statement not reusable after constraint");
                fails(IllegalArgumentException.class, new Task() { public void run() { NativeSqlite.execute(owner, prepared, NONE, 0); } });
            } finally { NativeSqlite.finalizeStatement(insert); NativeSqlite.finalizeStatement(insert); }
            NativeSqlite.Result result = query(db, "SELECT value,wide,real_value,blob_value,absent FROM values_test ORDER BY id", NONE);
            if (result.columns.length != 5 || !"value".equals(result.columns[0]) || result.rows.length != 2 || !text.equals(result.rows[0][0])
                    || !Long.valueOf(Long.MAX_VALUE).equals(result.rows[0][1]) || !Double.valueOf(1.25).equals(result.rows[0][2])
                    || !Arrays.equals((byte[])result.rows[0][3], new byte[]{0, -1, 4}) || result.rows[0][4] != null
                    || !"".equals(result.rows[1][0]) || !Long.valueOf(Long.MIN_VALUE).equals(result.rows[1][1])
                    || !(result.rows[1][3] instanceof byte[]) || ((byte[])result.rows[1][3]).length != 0)
                throw new AssertionError("Typed/Unicode/NUL/empty blob result");
            NativeSqlite.Result empty = query(db, "SELECT value FROM values_test WHERE id<0", NONE);
            if (empty.rows.length != 0 || !"value".equals(empty.columns[0])) throw new AssertionError("Empty cursor columns");
            if (execute(db, "UPDATE values_test SET real_value=? WHERE id=?", new Object[]{7.5d, 1L}, 2) != 1) throw new AssertionError("Changed rows");
            sql(db, "BEGIN IMMEDIATE"); sql(db, "INSERT INTO values_test(value) VALUES('rolled back')"); sql(db, "ROLLBACK");
            if (count(db, "values_test") != 2) throw new AssertionError("Rollback lost atomicity");
            sql(db, "BEGIN EXCLUSIVE"); sql(db, "INSERT INTO values_test(value) VALUES('committed')"); sql(db, "COMMIT");
            if (count(db, "values_test") != 3) throw new AssertionError("Commit lost data");
            sql(db, "CREATE TABLE schema_change(a)"); sql(db, "INSERT INTO schema_change VALUES(1)");
            long select = NativeSqlite.prepare(db, "SELECT * FROM schema_change");
            try {
                sql(db, "ALTER TABLE schema_change ADD COLUMN b DEFAULT 7");
                NativeSqlite.Result changed = NativeSqlite.query(db, select, NONE);
                if (changed.columns.length != 2 || !"b".equals(changed.columns[1]) || !Long.valueOf(7).equals(changed.rows[0][1]))
                    throw new AssertionError("Recompiled query metadata");
            } finally { NativeSqlite.finalizeStatement(select); }
            final long owner = db;
            if (execute(db, "SELECT 1; SELECT 2", NONE, 3) != 1) throw new AssertionError("Only first statement should execute");
            sql(db, "PRAGMA foreign_keys=OFF");
            if (execute(db, "SELECT 1; PRAGMA foreign_keys=ON", NONE, 3) != 1) throw new AssertionError("SQL tail changed first statement");
            if (execute(db, "PRAGMA foreign_keys", NONE, 3) != 0) throw new AssertionError("Ignored SQL tail changed connection state");
            if (execute(db, "SELECT 9; ; /* comment */ -- tail", NONE, 3) != 9) throw new AssertionError("SQL trivia tail");
            fails(IllegalArgumentException.class, new Task() { public void run() { NativeSqlite.prepare(owner, "SELECT 1\u0000; DROP TABLE values_test"); } });
            try { NativeSqlite.prepare(db, "SELECT * FROM \"missing_\ud83d\ude80\""); throw new AssertionError("Missing Unicode table accepted"); }
            catch (SQLiteException expected) { if (!expected.getMessage().contains("missing_\ud83d\ude80")) throw new AssertionError("UTF-8 SQLite error lost supplementary Unicode", expected); }
            if (execute(db, "SELECT 9; -- trailing comment", NONE, 3) != 9) throw new AssertionError("SQL tail handling");
            fails(SQLiteDoneException.class, new Task() { public void run() { execute(owner, "SELECT id FROM values_test WHERE id<0", NONE, 3); } });
            fails(SQLiteBlobTooBigException.class, new Task() { public void run() { query(owner, "SELECT zeroblob(20000000)", NONE); } });
            fails(SQLiteBlobTooBigException.class, new Task() { public void run() { query(owner, "WITH RECURSIVE x(v) AS (VALUES(1) UNION ALL SELECT v+1 FROM x WHERE v<100001) SELECT v FROM x", NONE); } });
            fails(SQLiteBlobTooBigException.class, new Task() { public void run() { query(owner, "WITH RECURSIVE x(v) AS (VALUES(1) UNION ALL SELECT v+1 FROM x WHERE v<5) SELECT CAST(zeroblob(4194304) AS TEXT) FROM x", NONE); } });
            fails(SQLiteException.class, new Task() { public void run() { query(owner, "WITH RECURSIVE x(v) AS (VALUES(1) UNION ALL SELECT v+1 FROM x) SELECT count(*) FROM x", NONE); } });
            if (count(db, "values_test") != 3) throw new AssertionError("Database poisoned after bounded query failure");
            NativeSqlite.Result updated=query(db,"UPDATE values_test SET wide=42 WHERE id=1 RETURNING wide",NONE);
            if(updated.rows.length!=1||!Long.valueOf(42L).equals(updated.rows[0][0]))throw new AssertionError("Writable cursor RETURNING");
            if(query(db,"UPDATE values_test SET wide=9223372036854775807 WHERE id=1",NONE).rows.length!=0)throw new AssertionError("Writable empty cursor");
            // Closing the connection must invalidate/finalize every live statement and roll back work.
            long pending = NativeSqlite.prepare(db, "SELECT value FROM values_test");
            sql(db, "BEGIN"); sql(db, "INSERT INTO values_test(value) VALUES('close rollback')");
            NativeSqlite.close(db); NativeSqlite.close(db);
            final long stale = pending;
            fails(IllegalStateException.class, new Task() { public void run() { NativeSqlite.parameterCount(stale); } });
            NativeSqlite.finalizeStatement(pending);
        } finally { NativeSqlite.close(db); }
        long reopened = open(file.toString(), 0);
        try { if (count(reopened, "values_test") != 3) throw new AssertionError("Persistent reopen/close rollback"); }
        finally { NativeSqlite.close(reopened); }
        final long readOnly = open(file.toString(), 1);
        try {
            if (count(readOnly, "values_test") != 3) throw new AssertionError("Read-only query");
            fails(SQLiteReadOnlyDatabaseException.class, new Task() { public void run() { sql(readOnly, "DELETE FROM values_test"); } });
        } finally { NativeSqlite.close(readOnly); }
        concurrency(); closeRace();
        int descriptors = new File("/proc/self/fd").list().length;
        for (int i = 0; i < 64; ++i) {
            long connection = open(file.toString(), 0); NativeSqlite.prepare(connection, "SELECT * FROM values_test"); NativeSqlite.close(connection);
            fails(SQLiteCantOpenDatabaseException.class, new Task() { public void run() { open(root.resolve("missing/parent.db").toString(), 0); } });
        }
        if (new File("/proc/self/fd").list().length != descriptors) throw new AssertionError("SQLite file descriptors leaked");
        System.out.println("NATIVE_SQLITE_TYPED_PERSISTENCE_AND_OWNERSHIP_OK");
    }
    static void concurrency() throws Exception {
        final long db = open(":memory:", 0x10000000); final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        try {
            sql(db, "CREATE TABLE concurrent(value)"); Thread[] threads = new Thread[4];
            for (int i = 0; i < threads.length; ++i) {
                threads[i] = new Thread(new Runnable() { public void run() {
                    try { for (int count = 0; count < 64; ++count) execute(db, "INSERT INTO concurrent VALUES(?)", new Object[]{(long)count}, 1); }
                    catch (Throwable issue) { failure.compareAndSet(null, issue); }
                }}); threads[i].start();
            }
            for (Thread thread : threads) thread.join(); if (failure.get() != null) throw new AssertionError(failure.get());
            if (count(db, "concurrent") != 256) throw new AssertionError("Concurrent writes lost data");
        } finally { NativeSqlite.close(db); }
    }
    static void closeRace() throws Exception {
        for (int round = 0; round < 16; ++round) {
            final long db = open(":memory:", 0x10000000);
            final long select = NativeSqlite.prepare(db, "SELECT 1");
            final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
            Thread reader = new Thread(new Runnable() { public void run() {
                try { for (int i = 0; i < 64; ++i) NativeSqlite.query(db, select, NONE); }
                catch (IllegalStateException expected) { }
                catch (Throwable problem) { error.set(problem); }
            }});
            reader.start(); NativeSqlite.close(db); NativeSqlite.finalizeStatement(select); reader.join();
            if (error.get() != null) throw new AssertionError("Concurrent close/query failed", error.get());
        }
    }

}
