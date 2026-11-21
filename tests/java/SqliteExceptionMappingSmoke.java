// SPDX-License-Identifier: GPL-3.0-only
import android.database.sqlite.SQLiteAccessPermException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteTableLockedException;
import java.io.File;

public class SqliteExceptionMappingSmoke {
    private static native int provoke(int kind);
    public static void main(String[] args) {
        System.load(new File("build/macos/m5/libsqlite-exception-fixture.so").getAbsolutePath());
        Class<?>[] expected = {SQLiteTableLockedException.class, SQLiteAccessPermException.class,
                               SQLiteDiskIOException.class, SQLiteDatabaseLockedException.class};
        for (int i = 0; i < expected.length; i++) {
            try {
                provoke(i);
                throw new AssertionError("SQLite error was not thrown: " + i);
            } catch (RuntimeException failure) {
                if (failure.getClass() != expected[i]) throw new AssertionError("wrong SQLite error for " + i, failure);
            }
        }
        System.out.println("SQLITE_EXCEPTION_MAPPING_PASS");
    }
}
