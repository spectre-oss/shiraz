// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;
import android.content.Context;
import android.database.DatabaseErrorHandler;
import java.io.Closeable;
/** Lazy open and atomic schema migrations using the real database connection. */
public abstract class SQLiteOpenHelper implements Closeable {
    private final Context context;
    private final String name;
    private final SQLiteDatabase.CursorFactory factory;
    private final int version;
    private final DatabaseErrorHandler handler;
    private SQLiteDatabase database;
    private boolean initializing, wal;
    public SQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) { this(context, name, factory, version, null); }
    public SQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version, DatabaseErrorHandler handler) {
        if (context == null) throw new NullPointerException("context"); if (version < 1) throw new IllegalArgumentException("Version must be positive");
        this.context = context; this.name = name; this.factory = factory; this.version = version; this.handler = handler;
    }
    public String getDatabaseName() { return name; }
    public synchronized void setWriteAheadLoggingEnabled(boolean enabled) {
        if (wal == enabled) return;
        if (database != null && database.isOpen() && !database.isReadOnly()) { if (enabled) database.enableWriteAheadLogging(); else database.disableWriteAheadLogging(); }
        wal = enabled;
    }
    public synchronized SQLiteDatabase getWritableDatabase() { return open(true); }
    public synchronized SQLiteDatabase getReadableDatabase() { return open(false); }
    private SQLiteDatabase open(boolean writable) {
        if (database != null && !database.isOpen()) database = null;
        if (database != null && (!writable || !database.isReadOnly())) return database;
        if (initializing) throw new IllegalStateException("Recursive database initialization");
        SQLiteDatabase opened = null; initializing = true;
        try {
            if (name == null) opened = SQLiteDatabase.create(factory);
            else {
                String path = context.getDatabasePath(name).getPath();
                try { opened = SQLiteDatabase.openDatabase(path, factory, SQLiteDatabase.CREATE_IF_NECESSARY | (wal ? SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING : 0), handler); }
                catch (SQLiteException failure) { if (writable) throw failure; opened = SQLiteDatabase.openDatabase(path, factory, SQLiteDatabase.OPEN_READONLY, handler); }
            }
            onConfigure(opened);
            int old = opened.getVersion();
            if (old != version) {
                if (opened.isReadOnly()) throw new SQLiteException("Cannot migrate a read-only database");
                opened.beginTransaction();
                try {
                    if (old == 0) onCreate(opened); else if (old > version) onDowngrade(opened, old, version); else onUpgrade(opened, old, version);
                    opened.setVersion(version); opened.setTransactionSuccessful();
                } finally { opened.endTransaction(); }
            }
            onOpen(opened);
            if (database != null) database.close(); database = opened; return opened;
        } finally { initializing = false; if (opened != null && opened != database) opened.close(); }
    }
    @Override public synchronized void close() { if (initializing) throw new IllegalStateException("Cannot close during initialization"); if (database != null) { database.close(); database = null; } }
    public void onConfigure(SQLiteDatabase database) { }
    public abstract void onCreate(SQLiteDatabase database);
    public abstract void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion);
    public void onDowngrade(SQLiteDatabase database, int oldVersion, int newVersion) { throw new SQLiteException("Cannot downgrade database from " + oldVersion + " to " + newVersion); }
    public void onOpen(SQLiteDatabase database) { }
}
