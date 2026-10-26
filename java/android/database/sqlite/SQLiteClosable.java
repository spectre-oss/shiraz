// SPDX-License-Identifier: GPL-3.0-only
package android.database.sqlite;
import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
public abstract class SQLiteClosable implements Closeable {
    private final AtomicInteger references = new AtomicInteger(1);
    private final AtomicBoolean initialReleased = new AtomicBoolean();
    public void acquireReference() {
        for (;;) { int count = references.get(); if (count <= 0) throw new IllegalStateException("SQLite object is closed"); if (count == Integer.MAX_VALUE) throw new IllegalStateException("Reference overflow"); if (references.compareAndSet(count, count + 1)) return; }
    }
    public void releaseReference() {
        for (;;) { int count = references.get(); if (count <= 0) throw new IllegalStateException("SQLite reference already released"); if (references.compareAndSet(count, count - 1)) { if (count == 1) onAllReferencesReleased(); return; } }
    }
    protected abstract void onAllReferencesReleased();
    @Override public void close() { if (initialReleased.compareAndSet(false, true)) releaseReference(); }
}
