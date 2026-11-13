// SPDX-License-Identifier: GPL-3.0-only
// DatabaseUtils scalar helpers (queryNumEntries, longForQuery, stringForQuery) on an in-memory
// database, and Cursor content-observer registration, as OsmAnd's analytics and history code use them.
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteStatement;

public final class DatabaseUtilsSmoke {
    private DatabaseUtilsSmoke() {}

    static void check(boolean ok, String why) {
        if (!ok) {
            throw new AssertionError(why);
        }
    }

    interface Task {
        void run();
    }

    static void fails(Class<? extends Throwable> type, Task task, String why) {
        try {
            task.run();
        } catch (Throwable issue) {
            if (type.isInstance(issue)) {
                return;
            }
            throw new AssertionError(why + ": wrong exception", issue);
        }
        throw new AssertionError(why + ": no exception");
    }

    static void observers(final Cursor cursor, String kind) {
        final ContentObserver observer = new ContentObserver(null) {};
        cursor.registerContentObserver(observer);
        fails(IllegalStateException.class, new Task() { public void run() { cursor.registerContentObserver(observer); } }, kind + " duplicate observer");
        cursor.unregisterContentObserver(observer);
        fails(IllegalStateException.class, new Task() { public void run() { cursor.unregisterContentObserver(observer); } }, kind + " unknown observer");
        cursor.registerContentObserver(observer);
        cursor.close();
        cursor.unregisterContentObserver(observer); // Released by close; AOSP tolerates this after close.
    }

    public static void main(String[] args) {
        final SQLiteDatabase db = SQLiteDatabase.create(null);
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY, kind TEXT, at INTEGER)");
        for (int i = 0; i < 7; i++) {
            db.execSQL("INSERT INTO events (kind, at) VALUES (?, ?)", new Object[] {i % 2 == 0 ? "zoom" : "pan", 100 + i});
        }
        check(DatabaseUtils.queryNumEntries(db, "events") == 7, "queryNumEntries(table)");
        check(DatabaseUtils.queryNumEntries(db, "events", "kind = 'zoom'") == 4, "queryNumEntries(selection)");
        check(DatabaseUtils.queryNumEntries(db, "events", "") == 7, "Empty selection counts every row");
        check(DatabaseUtils.queryNumEntries(db, "events", null) == 7, "Null selection counts every row");
        check(DatabaseUtils.queryNumEntries(db, "events", "kind = ? AND at > ?", new String[] {"pan", "102"}) == 2,
                "queryNumEntries(selection, args)");
        check(DatabaseUtils.longForQuery(db, "SELECT max(at) FROM events WHERE kind = ?", new String[] {"zoom"}) == 106,
                "longForQuery with string arguments");
        check(DatabaseUtils.longForQuery(db, "SELECT count(*) FROM events", null) == 7, "longForQuery without arguments");
        check("pan".equals(DatabaseUtils.stringForQuery(db, "SELECT kind FROM events WHERE at = ?", new String[] {"101"})),
                "stringForQuery");
        SQLiteStatement statement = db.compileStatement("SELECT count(*) FROM events WHERE kind = ?");
        check(DatabaseUtils.longForQuery(statement, new String[] {"zoom"}) == 4, "longForQuery(statement) first binding");
        check(DatabaseUtils.longForQuery(statement, new String[] {"pan"}) == 3, "longForQuery(statement) rebinding");
        statement.close();
        fails(SQLiteDoneException.class, new Task() { public void run() { DatabaseUtils.longForQuery(db, "SELECT at FROM events WHERE id < 0", null); } },
                "Empty scalar query");
        fails(RuntimeException.class, new Task() { public void run() { DatabaseUtils.queryNumEntries(db, "missing_table"); } }, "Missing table");

        observers(db.rawQuery("SELECT * FROM events", null), "Query cursor");
        observers(new MatrixCursor(new String[] {"a"}), "MatrixCursor");
        final Cursor open = db.rawQuery("SELECT 1", null);
        fails(IllegalArgumentException.class, new Task() { public void run() { open.registerContentObserver(null); } }, "Null observer");
        open.close();
        db.close();
        System.out.println("DATABASE_UTILS_OK");
    }
}
