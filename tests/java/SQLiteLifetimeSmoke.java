// SPDX-License-Identifier: GPL-3.0-only
import android.database.Cursor;
import android.database.sqlite.*;
import java.nio.file.*;
import java.util.stream.Stream;
public final class SQLiteLifetimeSmoke {
    static long descriptors() throws Exception { try (Stream<Path> entries = Files.list(Paths.get("/proc/self/fd"))) { return entries.count(); } }
    public static void main(String[] args) throws Exception {
        Path file = Paths.get(args[0]); Files.deleteIfExists(file);
        SQLiteDatabase first = SQLiteDatabase.openOrCreateDatabase(file.toFile(), null);
        first.execSQL("CREATE TABLE records(value)"); first.execSQL("INSERT INTO records VALUES(?)", new Object[]{"owned"}); first.close();
        long before = descriptors();
        for (int count = 0; count < 64; ++count) {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(file.toString(), null, 0);
            Cursor cursor = db.rawQuery("SELECT value FROM records", null); db.close();
            if (!db.isOpen() || !cursor.moveToFirst() || !"owned".equals(cursor.getString(0))) throw new AssertionError("Cursor did not retain its database/program reference");
            cursor.close(); if (db.isOpen()) throw new AssertionError("Last cursor reference did not close the database");
        }
        SQLiteDatabase db = SQLiteDatabase.openDatabase(file.toString(), null, 0);
        Cursor cursor = db.rawQuery("SELECT value FROM records", null);
        SQLiteDatabase.shirazShutdown(); if (db.isOpen()) throw new AssertionError("Session shutdown retained a database");
        if (!cursor.moveToFirst() || !"owned".equals(cursor.getString(0))) throw new AssertionError("Detached snapshot lost values");
        try { cursor.requery(); throw new AssertionError("Closed session requery succeeded"); } catch (IllegalStateException expected) { }
        cursor.close(); db.close();
        SQLiteDatabase memory = SQLiteDatabase.create(null);
        try { memory.rawQuery("SELECT ?", new String[]{"one", "two"}); throw new AssertionError("Extra arguments accepted"); }
        catch (SQLiteBindOrColumnIndexOutOfRangeException expected) { }
        System.gc(); System.runFinalization();
        memory.execSQL("CREATE TABLE healthy(value)"); memory.close();
        if (descriptors() != before) throw new AssertionError("Public SQLite lifetime leaked descriptors");
        Files.delete(file); System.out.println("SQLITE_PUBLIC_REFERENCE_LIFETIME_OK");
    }
}
