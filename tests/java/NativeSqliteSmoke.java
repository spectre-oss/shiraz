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
    static final class PagedCursor extends android.database.AbstractWindowedCursor {
        public int getCount(){return 5;}public String[] getColumnNames(){return new String[]{"id","label"};}
        public boolean onMove(int oldPosition,int newPosition){
            int start=newPosition/2*2;clearOrCreateWindow("paged");mWindow.setStartPosition(start);mWindow.setNumColumns(2);
            for(int row=start;row<Math.min(start+2,getCount());row++){if(!mWindow.allocRow()||!mWindow.putLong(row,row,0)||!mWindow.putString("row-"+row,row,1))throw new AssertionError("Page fill");}return true;
        }
    }
    static void cursorWindows() throws Exception {
        android.database.CursorWindow window=new android.database.CursorWindow("typed",256);
        try{
            if(!window.setNumColumns(3)||!window.allocRow())throw new AssertionError("Window allocation");window.setStartPosition(10);
            byte[] blob={1,2,3};if(!window.putBlob(blob,10,0)||!window.putLong(Long.MAX_VALUE,10,1)||!window.putString("café\u0000\ud83d\ude80",10,2))throw new AssertionError("Typed window fill");blob[0]=9;byte[] read=window.getBlob(10,0);read[0]=8;
            if(window.getBlob(10,0)[0]!=1||window.getType(10,0)!=4||window.getLong(10,1)!=Long.MAX_VALUE||window.getType(10,2)!=3)throw new AssertionError("Window types or blob ownership");
            android.database.CharArrayBuffer buffer=new android.database.CharArrayBuffer(1);window.copyStringToBuffer(10,2,buffer);if(!"café\u0000\ud83d\ude80".equals(new String(buffer.data,0,buffer.sizeCopied)))throw new AssertionError("Window text buffer");
            if(window.putBlob(new byte[512],10,0)||window.getBlob(10,0)[0]!=1||window.putNull(9,0)||window.putNull(10,3)||window.setNumColumns(4))throw new AssertionError("Rejected window write changed state");
            window.putString("  -12tail",10,2);if(window.getLong(10,2)!=-12)throw new AssertionError("Numeric text prefix");window.putString("1.25e2tail",10,2);if(window.getDouble(10,2)!=125)throw new AssertionError("Real text prefix");
            window.putString("9223372036854775808",10,2);if(window.getLong(10,2)!=Long.MAX_VALUE)throw new AssertionError("Positive integer saturation");window.putString("-9223372036854775809",10,2);if(window.getLong(10,2)!=Long.MIN_VALUE)throw new AssertionError("Negative integer saturation");
            window.putNull(10,0);if(!window.isNull(10,0)||window.getLong(10,0)!=0)throw new AssertionError("Null conversion");
            window.freeLastRow();if(window.getNumRows()!=0||!window.allocRow())throw new AssertionError("Released row capacity");window.clear();if(window.getStartPosition()!=0||window.getNumRows()!=0||!window.setNumColumns(1))throw new AssertionError("Window clear reset");
            window.acquireReference();window.close();if(!window.allocRow())throw new AssertionError("Owned reference lost on close");window.releaseReference();fails(IllegalStateException.class,new Task(){public void run(){window.getNumRows();}});
        }finally{window.close();}
        PagedCursor cursor=new PagedCursor();final int[] notices={0,0};cursor.registerDataSetObserver(new android.database.DataSetObserver(){public void onChanged(){notices[0]++;}public void onInvalidated(){notices[1]++;}});
        try{
            if(!cursor.isBeforeFirst()||!cursor.moveToPosition(3)||cursor.getInt(0)!=3||!"row-3".equals(cursor.getString(1))||cursor.getColumnIndex("t.LABEL")!=1)throw new AssertionError("Paged cursor positioning");
            android.database.CursorWindow copy=new android.database.CursorWindow("copy",512);try{cursor.fillWindow(1,copy);if(cursor.getPosition()!=3||copy.getStartPosition()!=1||copy.getNumRows()!=4||copy.getLong(4,0)!=4)throw new AssertionError("Window fill snapshot and source restoration");}finally{copy.close();}
            android.database.CursorWindow small=new android.database.CursorWindow("small",128);try{cursor.fillWindow(0,small);if(small.getNumRows()!=1||cursor.getPosition()!=3)throw new AssertionError("Bounded fill leaves complete rows and restores source");}finally{small.close();}
            android.database.CursorWindow old=cursor.getWindow();cursor.setWindow(new android.database.CursorWindow("replacement"));fails(IllegalStateException.class,new Task(){public void run(){old.getNumRows();}});
            cursor.deactivate();if(cursor.hasWindow()||notices[1]!=1||!cursor.requery()||notices[0]!=1||cursor.getPosition()!=-1)throw new AssertionError("Cursor deactivate/requery lifecycle");
            if(!cursor.moveToLast()||cursor.getInt(0)!=4||cursor.moveToNext()||!cursor.isAfterLast())throw new AssertionError("After-last cursor boundary");fails(android.database.CursorIndexOutOfBoundsException.class,new Task(){public void run(){cursor.getString(1);}});
        }finally{cursor.close();cursor.close();}
        if(notices[1]!=2||!cursor.isClosed()||cursor.requery()||cursor.hasWindow())throw new AssertionError("Cursor close ownership and notification");
        android.database.MatrixCursor broken=new android.database.MatrixCursor(new String[]{"a","b"}){public String getString(int column){if(column==1)throw new IllegalStateException("fixture read failure");return super.getString(column);}};
        broken.addRow(new Object[]{"a","b"});android.database.CursorWindow partial=new android.database.CursorWindow("partial");
        try{fails(IllegalStateException.class,new Task(){public void run(){android.database.DatabaseUtils.cursorFillWindow(broken,0,partial);}});if(partial.getNumRows()!=0||broken.getPosition()!=-1)throw new AssertionError("Failed fill retained partial row or moved source");}finally{partial.close();broken.close();}
    }
    static void textHelpers(long db){
        String[] statements={" select 1","\ninsert into t values(1)","update t set x=1","replace into t values(1)","delete from t","attach 'x' as y","begin immediate","commit","end","rollback","pragma user_version","create table t(x)","drop table t","alter table t rename to x","analyze","detach y",""," x ","-- comment\nselect 1","with t as (select 1) select * from t"};
        int[] expected={1,2,2,2,2,3,4,5,5,6,7,8,8,8,9,9,99,99,99,99};
        java.util.Locale previous=java.util.Locale.getDefault();try{java.util.Locale.setDefault(new java.util.Locale("tr"));for(int i=0;i<statements.length;i++)if(android.database.DatabaseUtils.getSqlStatementType(statements[i])!=expected[i])throw new AssertionError("SQL prefix classification "+i);}finally{java.util.Locale.setDefault(previous);}
        for(String value:new String[]{"","'","a''b","café \\ \ud83d\ude80","'); DROP TABLE values_test; --"}){
            StringBuilder sql=new StringBuilder("SELECT ");android.database.DatabaseUtils.appendEscapedSQLString(sql,value);
            if(!sql.toString().equals("SELECT "+android.database.DatabaseUtils.sqlEscapeString(value))||!value.equals(query(db,sql.toString(),NONE).rows[0][0]))throw new AssertionError("Escaped SQL literal round trip");
        }
    }
    public static void main(String[] args) throws Exception {
        cursorWindows();
        Path root = Paths.get(args[0]); Files.createDirectories(root);
        Path file = root.resolve("values-café.db"); Files.deleteIfExists(file);
        long db = open(file.toString(), 0x10000000);
        try {
            textHelpers(db);
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
