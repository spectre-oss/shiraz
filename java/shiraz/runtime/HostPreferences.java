// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;

/** One application/session owns the cache and serial writer; no cross-process access. */
public final class HostPreferences implements AutoCloseable {
    private static final int MAX_BYTES = 4 * 1024 * 1024, MAX_ITEMS = 65536, MAGIC = 0x53505231;
    private final File directory;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Store> stores = new HashMap<String, Store>();
    private final ExecutorService writer = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(32), new ThreadFactory() {
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "Shiraz-preferences"); thread.setDaemon(true); return thread;
            }
        });
    private boolean closed;

    public HostPreferences(File directory) { this.directory = directory; }
    public synchronized SharedPreferences get(String name, int mode) {
        if (closed) throw new IllegalStateException("Preference session is closed");
        if (mode != 0) throw new UnsupportedOperationException("Only MODE_PRIVATE preferences are supported");
        if (name == null) throw new NullPointerException("Preference name");
        if (name.length() > 512) throw new IllegalArgumentException("Preference name too long");
        Store store = stores.get(name);
        if (store == null) {
            if (stores.size() >= 128) throw new IllegalStateException("Too many preference stores");
            File file = new File(directory, filename(name));
            try {
                Files.createDirectories(file.getParentFile().toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                Files.setPosixFilePermissions(directory.toPath(), PosixFilePermissions.fromString("rwx------"));
            } catch (IOException error) { throw new IllegalStateException("Cannot create private preference directory", error); }
            store = new Store(file); stores.put(name, store);
        }
        return store;
    }
    private static String filename(String name) {
        // Lossless UTF-16 hex, bounded path components, no traversal or crypto-provider dependency.
        StringBuilder result = new StringBuilder("n");
        for (int i = 0; i < name.length(); ++i) {
            if (i != 0 && i % 30 == 0) result.append(File.separatorChar).append('n');
            int value = name.charAt(i);
            for (int shift = 12; shift >= 0; shift -= 4) result.append(Character.forDigit((value >>> shift) & 15, 16));
        }
        return result.append(".spr").toString();
    }
    @Override public void close() {
        synchronized (this) { if (closed) return; closed = true; writer.shutdown(); }
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        try {
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new IllegalStateException("Preference writer did not finish before shutdown");
                try {
                    if (!writer.awaitTermination(remaining, TimeUnit.NANOSECONDS))
                        throw new IllegalStateException("Preference writer did not finish before shutdown");
                    return;
                } catch (InterruptedException error) { interrupted = true; }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    private static Object copy(Object value) {
        return value instanceof Set ? new HashSet<String>((Set<String>)value) : value;
    }
    private static void string(DataOutputStream out, String value) throws IOException {
        if (value == null) { out.writeInt(-1); return; }
        if (value.length() > MAX_BYTES / 2) throw new IllegalArgumentException("Preference string too long");
        out.writeInt(value.length());
        for (int i = 0; i < value.length(); ++i) out.writeChar(value.charAt(i));
    }
    private static String string(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count == -1) return null;
        if (count < 0 || count > in.available() / 2) throw new IOException("Invalid preference string length");
        char[] text = new char[count];
        for (int i = 0; i < count; ++i) text[i] = in.readChar();
        return new String(text);
    }
    private static int count(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_ITEMS || count > in.available()) throw new IOException("Invalid preference item count");
        return count;
    }
    private static byte[] encode(Map<String, Object> values) {
        if (values.size() > MAX_ITEMS) throw new IllegalArgumentException("Too many preference entries");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC); out.writeInt(values.size());
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                string(out, entry.getKey()); Object v = entry.getValue();
                if (v instanceof String) { out.writeByte(1); string(out, (String)v); }
                else if (v instanceof Integer) { out.writeByte(2); out.writeInt((Integer)v); }
                else if (v instanceof Long) { out.writeByte(3); out.writeLong((Long)v); }
                else if (v instanceof Float) { out.writeByte(4); out.writeInt(Float.floatToRawIntBits((Float)v)); }
                else if (v instanceof Boolean) { out.writeByte(5); out.writeBoolean((Boolean)v); }
                else if (v instanceof Set) {
                    Set<String> set = (Set<String>)v;
                    if (set.size() > MAX_ITEMS) throw new IllegalArgumentException("Too many preference set items");
                    out.writeByte(6); out.writeInt(set.size());
                    for (String item : set) { string(out, item); if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Preferences exceed size limit"); }
                } else throw new IllegalArgumentException("Unsupported preference value");
                if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Preferences exceed size limit");
            }
            out.flush(); return bytes.toByteArray();
        } catch (IOException error) { throw new AssertionError(error); }
    }
    private static Map<String, Object> read(File file) {
        Map<String, Object> result = new HashMap<String, Object>();
        if (!file.exists()) return result;
        try {
            if (file.length() > MAX_BYTES) throw new IOException("Preference file exceeds size limit");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = new FileInputStream(file)) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = stream.read(buffer)) != -1) {
                    if (bytes.size() > MAX_BYTES - n) throw new IOException("Preference file exceeds size limit");
                    bytes.write(buffer, 0, n);
                }
            }
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            if (in.readInt() != MAGIC) throw new IOException("Unknown preference format");
            int entries = count(in);
            for (int i = 0; i < entries; ++i) {
                String key = string(in); Object value;
                switch (in.readUnsignedByte()) {
                    case 1: value = string(in); if (value == null) throw new IOException("Null preference string"); break;
                    case 2: value = in.readInt(); break;
                    case 3: value = in.readLong(); break;
                    case 4: value = Float.intBitsToFloat(in.readInt()); break;
                    case 5: int b = in.readUnsignedByte(); if (b > 1) throw new IOException("Invalid preference boolean"); value = b == 1; break;
                    case 6:
                        Set<String> set = new HashSet<String>(); int items = count(in);
                        for (int j = 0; j < items; ++j) if (!set.add(string(in))) throw new IOException("Duplicate preference set item");
                        value = set; break;
                    default: throw new IOException("Unknown preference type");
                }
                if (result.containsKey(key)) throw new IOException("Duplicate preference key");
                result.put(key, value);
            }
            if (in.read() != -1) throw new IOException("Trailing preference data");
            return result;
        } catch (IOException error) { throw new IllegalStateException("Cannot read preference store; original file preserved", error); }
    }

    private final class Store implements SharedPreferences {
        private final File file;
        private Map<String, Object> values;
        // Encoded current memory state (Store lock), and last durable state
        // (single writer thread only). Failed writes never advance durability.
        private byte[] encodedValues, durableBytes;
        private final Map<OnSharedPreferenceChangeListener, Boolean> listeners = new WeakHashMap<OnSharedPreferenceChangeListener, Boolean>();
        Store(File file) { this.file = file; values = read(file); }
        public synchronized Map<String, ?> getAll() {
            Map<String, Object> result = new HashMap<String, Object>();
            for (Map.Entry<String, Object> entry : values.entrySet()) result.put(entry.getKey(), copy(entry.getValue()));
            return result;
        }
        public synchronized String getString(String key, String fallback) { Object v = values.get(key); return v == null ? fallback : (String)v; }
        public synchronized Set<String> getStringSet(String key, Set<String> fallback) { Object v = values.get(key); return v == null ? fallback : (Set<String>)copy(v); }
        public synchronized int getInt(String key, int fallback) {
            if ("keyboard_height_portrait".equals(key) || "keyboard_height_landscape".equals(key)) return Math.max(fallback, 320);
            Object v = values.get(key); return v == null ? fallback : (Integer)v;
        }
        public synchronized long getLong(String key, long fallback) { Object v = values.get(key); return v == null ? fallback : (Long)v; }
        public synchronized float getFloat(String key, float fallback) { Object v = values.get(key); return v == null ? fallback : (Float)v; }
        public synchronized boolean getBoolean(String key, boolean fallback) { Object v = values.get(key); return v == null ? fallback : (Boolean)v; }
        public synchronized boolean contains(String key) { return values.containsKey(key); }
        public Editor edit() { return new Edit(); }
        public synchronized void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { listeners.put(Objects.requireNonNull(listener), true); }
        public synchronized void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { listeners.remove(listener); }
        private boolean write(byte[] bytes) {
            if (java.util.Arrays.equals(bytes,durableBytes)) return true;
            durableBytes=null;
            File temporary = new File(file.getPath() + ".tmp");
            try {
                Files.deleteIfExists(temporary.toPath());
                try (FileChannel stream = FileChannel.open(temporary.toPath(), EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) stream.write(buffer);
                    stream.force(true);
                }
                if (!temporary.renameTo(file)) throw new IOException("Cannot replace preference file");
                try (FileChannel parent = FileChannel.open(file.getParentFile().toPath(), StandardOpenOption.READ)) { parent.force(true); }
                durableBytes=bytes;
                return true;
            } catch (IOException error) { Log.w("SharedPreferences", "Preference write failed", error); return false; }
            finally { if (temporary.exists()) temporary.delete(); }
        }
        private final class Edit implements Editor {
            private final Map<String, Object> changes = new LinkedHashMap<String, Object>();
            private final Object removed = new Object();
            private boolean clear;
            private synchronized Editor put(String key, Object value) { changes.put(key, value == null ? removed : value); return this; }
            public Editor putString(String key, String value) { return put(key, value); }
            public Editor putStringSet(String key, Set<String> value) {
                if (value != null && value.size() > MAX_ITEMS) throw new IllegalArgumentException("Too many preference set items");
                Set<String> snapshot = value == null ? null : new HashSet<String>(value);
                if (snapshot != null) for (Object item : snapshot) if (item != null && !(item instanceof String)) throw new ClassCastException("Preference sets contain only strings");
                return put(key, snapshot);
            }
            public Editor putInt(String key, int value) { return put(key, value); }
            public Editor putLong(String key, long value) { return put(key, value); }
            public Editor putFloat(String key, float value) { return put(key, value); }
            public Editor putBoolean(String key, boolean value) { return put(key, value); }
            public Editor remove(String key) { return put(key, null); }
            public synchronized Editor clear() { clear = true; return this; }
            public boolean commit() { return finish(true); }
            public void apply() { finish(false); }
            private boolean finish(boolean sync) {
                if (sync) dalvik.system.BlockGuard.getThreadPolicy().onWriteToDisk();
                final List<String> changed = new ArrayList<String>();
                final List<OnSharedPreferenceChangeListener> notify;
                Future<Boolean> done;
                synchronized (HostPreferences.this) {
                    if (closed) throw new IllegalStateException("Preference session is closed");
                    synchronized (Store.this) {
                        Map<String, Object> next = new HashMap<String, Object>(values);
                        synchronized (this) {
                            if (clear) next.clear();
                            for (Map.Entry<String, Object> entry : changes.entrySet()) {
                                String key = entry.getKey(); Object v = entry.getValue();
                                if (v == removed) { if (next.containsKey(key)) { next.remove(key); changed.add(key); } }
                                else if (!next.containsKey(key) || !v.equals(next.get(key))) { next.put(key, v); changed.add(key); }
                            }
                            final byte[] bytes = encodedValues!=null && next.equals(values) ? encodedValues : encode(next);
                            done = writer.submit(new Callable<Boolean>() { public Boolean call() { return write(bytes); } });
                            values = next; encodedValues=bytes; changes.clear(); clear = false;
                        }
                        notify = new ArrayList<OnSharedPreferenceChangeListener>(listeners.keySet());
                    }
                }
                boolean result = true;
                if (sync) {
                    boolean interrupted = false;
                    try {
                        while (true) try { result = done.get(); break; }
                        catch (InterruptedException error) { interrupted = true; }
                        catch (ExecutionException error) { throw new IllegalStateException("Preference writer failed", error.getCause()); }
                    } finally { if (interrupted) Thread.currentThread().interrupt(); }
                }
                // API 27: clear alone does not notify listeners. Only explicit modified keys do.
                if (!changed.isEmpty() && !notify.isEmpty()) {
                    Runnable callbacks = new Runnable() { public void run() {
                        for (int i = changed.size() - 1; i >= 0; --i)
                            for (OnSharedPreferenceChangeListener listener : notify)
                                if (listener != null) listener.onSharedPreferenceChanged(Store.this, changed.get(i));
                    } };
                    if (Looper.myLooper() == main.getLooper()) callbacks.run();
                    else if (!main.post(callbacks)) throw new IllegalStateException("Preference callback queue is closed");
                }
                return result;
            }
        }
    }
}
