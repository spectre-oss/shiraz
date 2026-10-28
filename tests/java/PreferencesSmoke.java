// SPDX-License-Identifier: GPL-3.0-only
import android.content.SharedPreferences;
import android.os.Looper;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import shiraz.runtime.HostPreferences;

public final class PreferencesSmoke {
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Path root = Files.createTempDirectory(Paths.get("build"), "preferences-check-");
        try {
            Path directory = root.resolve("store");
            try (HostPreferences manager = new HostPreferences(directory.toFile())) {
                final SharedPreferences p = manager.get("state", 0);
                require(p.edit().putInt("value", 42).commit(), "Initial durable write");
                require(Files.getPosixFilePermissions(directory).equals(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")), "Private preference directory");
                try (Stream<Path> paths = Files.walk(directory)) {
                    Path saved = paths.filter(Files::isRegularFile).findFirst().get();
                    require(Files.getPosixFilePermissions(saved).equals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")), "Private preference file");
                    java.nio.file.attribute.FileTime marker=java.nio.file.attribute.FileTime.fromMillis(1000);
                    Files.setLastModifiedTime(saved,marker);
                    require(p.edit().putInt("value",42).commit(),"Unchanged commit succeeds");
                    require(Files.getLastModifiedTime(saved).equals(marker),"Unchanged commit avoids file replacement");
                    p.edit().putInt("value",41).apply();
                    require(p.edit().putInt("value",42).commit(),"Commit waits for earlier apply and restores durable value");
                }
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(directory);
                try {
                    Files.setPosixFilePermissions(directory, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
                    require(!p.edit().putInt("value", 43).commit(), "Write failure must return false");
                    require(p.getInt("value", 0) == 43, "Memory commit is visible despite failed persistence");
                } finally { Files.setPosixFilePermissions(directory, permissions); }
                require(p.edit().putInt("value",43).commit(),"Unchanged memory retries failed disk write");
                try(HostPreferences reader=new HostPreferences(directory.toFile())) {
                    require(reader.get("state",0).getInt("value",0)==43,"Retry really persisted the changed value");
                }
                require(p.edit().putInt("value",42).commit(),"Restore durable value after retry");
            }
            try (HostPreferences manager = new HostPreferences(directory.toFile())) {
                final SharedPreferences p = manager.get("state", 0);
                require(p.getInt("value", 0) == 42, "Failed replacement preserved the old file");
                final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
                Thread[] workers = new Thread[4];
                for (int i = 0; i < workers.length; ++i) {
                    final int id = i;
                    workers[i] = new Thread(new Runnable() { public void run() {
                        try {
                            for (int value = 0; value < 16; ++value)
                                require(p.edit().putInt("thread-" + id, value).commit(), "Concurrent commit");
                        } catch (Throwable failure) { error.compareAndSet(null, failure); }
                    }}); workers[i].start();
                }
                for (Thread worker : workers) { worker.join(5000); require(!worker.isAlive(), "Preference deadlock"); }
                if (error.get() != null) throw new AssertionError("Concurrent preference write", error.get());
                char[] big = new char[2 * 1024 * 1024]; Arrays.fill(big, 'x');
                try { p.edit().putString("too-big", new String(big)).commit(); throw new AssertionError("Oversize store accepted"); }
                catch (IllegalArgumentException expected) { }
                require(!p.contains("too-big"), "Rejected transaction partially changed memory");
                require(p.edit().putBoolean("healthy", true).commit(), "Writer did not recover from validation error");
            }
            HostPreferences closed = new HostPreferences(directory.toFile());
            SharedPreferences p = closed.get("state", 0);
            for (int i = 0; i < 4; ++i) require(p.getInt("thread-" + i, -1) == 15, "Concurrent writes lost a key");
            SharedPreferences.Editor late = p.edit().putInt("late", 1);
            p.edit().putBoolean("shutdown-applied", true).apply(); closed.close();
            try { late.apply(); throw new AssertionError("Edit after close accepted"); } catch (IllegalStateException expected) { }
            try (HostPreferences manager = new HostPreferences(directory.toFile())) {
                require(manager.get("state", 0).getBoolean("shutdown-applied", false), "Close did not flush apply");
            }
            Path moved = root.resolve("moved");
            try (HostPreferences source = new HostPreferences(directory.toFile());
                 HostPreferences destination = new HostPreferences(moved.toFile())) {
                SharedPreferences from = source.get("move", 0), to = destination.get("move", 0);
                from.edit().putString("value", "pending").apply();
                to.edit().putString("obsolete", "old").apply();
                require(HostPreferences.move(from, from), "Same-store move succeeds");
                require(from.getString("value", "").equals("pending"), "Same-store move preserves data");
                require(HostPreferences.move(from, to), "Move flushes pending writes");
                require(to.getString("value", "").equals("pending") && !to.contains("obsolete"), "Destination replaced");
                require(from.getAll().isEmpty(), "Source cache cleared");
                require(HostPreferences.move(from, to), "Missing source move succeeds");
                require(to.getString("value", "").equals("pending"), "Missing source preserves destination");
            }
            try (HostPreferences destination = new HostPreferences(moved.toFile())) {
                require(destination.get("move", 0).getString("value", "").equals("pending"), "Move persists across restart");
            }
            Path file;
            try (Stream<Path> paths = Files.walk(directory)) { file = paths.filter(path -> path.toString().endsWith(".spr")).findFirst().get(); }
            byte[] malformed = {0x53, 0x50, 0x52, 0x31, 0, 0, 0, 1, 0x7f, -1, -1, -1};
            Files.write(file, malformed);
            try (HostPreferences manager = new HostPreferences(directory.toFile())) {
                try { manager.get("state", 0); throw new AssertionError("Corrupt data silently discarded"); }
                catch (IllegalStateException expected) { require(expected.getCause() instanceof java.io.IOException, "Decode failure cause"); }
            }
            require(Arrays.equals(Files.readAllBytes(file), malformed), "Corrupt original was overwritten");
            System.out.println("PREFERENCES_FAILURE_CONCURRENCY_OK");
        } finally {
            Looper.shirazShutdownMain();
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : (Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
    }
}
