// SPDX-License-Identifier: GPL-3.0-only
package android.database;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import android.util.Pair;
import java.io.File;
import java.util.List;

/** Default {@link DatabaseErrorHandler} closing and deleting corrupted SQLite database files. */
public final class DefaultDatabaseErrorHandler implements DatabaseErrorHandler {
    private static final String TAG = "DefaultDatabaseErrorHandler";

    public DefaultDatabaseErrorHandler() {}

    @Override
    public void onCorruption(SQLiteDatabase dbObj) {
        if (dbObj == null) {
            return;
        }
        Log.e(TAG, "Corruption reported by sqlite on database: " + dbObj.getPath());
        if (!dbObj.isOpen()) {
            deleteDatabaseFile(dbObj.getPath());
            return;
        }
        List<Pair<String, String>> attachedDbs = null;
        try {
            try {
                attachedDbs = dbObj.getAttachedDbs();
            } catch (SQLiteException ignored) {
                // Ignore failure while reading attached databases from a corrupt handle.
            }
            try {
                dbObj.close();
            } catch (SQLiteException ignored) {
                // Ignore close failure.
            }
        } finally {
            if (attachedDbs != null) {
                for (Pair<String, String> p : attachedDbs) {
                    deleteDatabaseFile(p.second);
                }
            } else {
                deleteDatabaseFile(dbObj.getPath());
            }
        }
    }

    private static void deleteDatabaseFile(String fileName) {
        if (fileName == null || fileName.equalsIgnoreCase(":memory:") || fileName.trim().isEmpty()) {
            return;
        }
        Log.e(TAG, "deleting the database file: " + fileName);
        try {
            SQLiteDatabase.deleteDatabase(new File(fileName));
        } catch (Exception e) {
            Log.w(TAG, "delete failed: " + e.getMessage());
        }
    }
}
