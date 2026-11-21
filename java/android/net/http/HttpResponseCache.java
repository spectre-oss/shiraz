// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.HttpURLConnection;
import java.net.ResponseCache;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class HttpResponseCache extends ResponseCache implements Closeable {
    private final File mDirectory;
    private final long mMaxSize;
    private final LinkedHashMap<String, CacheEntry> mEntries =
            new LinkedHashMap<String, CacheEntry>(16, 0.75f, true);
    private long mSize;
    private int mNetworkCount;
    private int mHitCount;
    private int mRequestCount;
    private boolean mClosed;

    private HttpResponseCache(File directory, long maxSize) throws IOException {
        this.mDirectory = Objects.requireNonNull(directory, "directory");
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0: " + maxSize);
        }
        this.mMaxSize = maxSize;
        if (!directory.exists() && !directory.mkdirs() && !directory.exists()) {
            throw new IOException("Unable to create cache directory: " + directory);
        }
        loadFromDisk();
    }

    public static HttpResponseCache getInstalled() {
        ResponseCache installed = ResponseCache.getDefault();
        return installed instanceof HttpResponseCache ? (HttpResponseCache) installed : null;
    }

    public static synchronized HttpResponseCache install(File directory, long maxSize)
            throws IOException {
        ResponseCache installed = ResponseCache.getDefault();
        if (installed instanceof HttpResponseCache) {
            HttpResponseCache existing = (HttpResponseCache) installed;
            if (!existing.mClosed
                    && existing.mDirectory.equals(directory)
                    && existing.mMaxSize == maxSize) {
                return existing;
            }
            existing.close();
        }
        HttpResponseCache newCache = new HttpResponseCache(directory, maxSize);
        ResponseCache.setDefault(newCache);
        return newCache;
    }

    @Override
    public synchronized CacheResponse get(
            URI uri, String requestMethod, Map<String, List<String>> requestHeaders)
            throws IOException {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(requestMethod, "requestMethod");
        if (mClosed) {
            return null;
        }
        mRequestCount++;
        if (!"GET".equalsIgnoreCase(requestMethod)) {
            mNetworkCount++;
            return null;
        }
        if (hasNoCacheDirective(requestHeaders)) {
            mNetworkCount++;
            return null;
        }
        final CacheEntry entry = mEntries.get(keyFor(uri));
        if (entry == null) {
            mNetworkCount++;
            return null;
        }
        mHitCount++;
        final Map<String, List<String>> headersSnapshot = entry.headers;
        final byte[] bodySnapshot = entry.body;
        return new CacheResponse() {
            @Override
            public Map<String, List<String>> getHeaders() throws IOException {
                return headersSnapshot;
            }

            @Override
            public InputStream getBody() throws IOException {
                return new ByteArrayInputStream(bodySnapshot);
            }
        };
    }

    @Override
    public synchronized CacheRequest put(URI uri, URLConnection urlConnection) throws IOException {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(urlConnection, "urlConnection");
        if (mClosed || !(urlConnection instanceof HttpURLConnection)) {
            return null;
        }
        HttpURLConnection http = (HttpURLConnection) urlConnection;
        String method = http.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) {
            invalidateUri(uri);
            return null;
        }
        int code = http.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK
                && code != HttpURLConnection.HTTP_NOT_AUTHORITATIVE
                && code != HttpURLConnection.HTTP_MOVED_PERM
                && code != HttpURLConnection.HTTP_GONE) {
            return null;
        }
        Map<String, List<String>> responseHeaders = http.getHeaderFields();
        if (hasNoStoreDirective(responseHeaders)) {
            return null;
        }
        final String key = keyFor(uri);
        final Map<String, List<String>> frozenHeaders = copyHeaders(responseHeaders);
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        return new CacheRequest() {
            private boolean mAborted;
            private boolean mCommitted;

            @Override
            public OutputStream getBody() throws IOException {
                return new FilterOutputStream(buffer) {
                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        out.write(b, off, len);
                    }

                    @Override
                    public void close() throws IOException {
                        super.close();
                        synchronized (HttpResponseCache.this) {
                            if (mAborted || mCommitted || mClosed) {
                                return;
                            }
                            mCommitted = true;
                            commitEntryLocked(key, frozenHeaders, buffer.toByteArray());
                        }
                    }
                };
            }

            @Override
            public void abort() {
                synchronized (HttpResponseCache.this) {
                    mAborted = true;
                }
            }
        };
    }

    public synchronized long size() {
        return mSize;
    }

    public long maxSize() {
        return mMaxSize;
    }

    public synchronized void flush() {
        if (mClosed) {
            return;
        }
        for (Map.Entry<String, CacheEntry> e : mEntries.entrySet()) {
            try {
                writeEntryToDisk(e.getKey(), e.getValue());
            } catch (IOException ignored) {
            }
        }
    }

    public synchronized int getNetworkCount() {
        return mNetworkCount;
    }

    public synchronized int getHitCount() {
        return mHitCount;
    }

    public synchronized int getRequestCount() {
        return mRequestCount;
    }

    @Override
    public synchronized void close() throws IOException {
        if (mClosed) {
            return;
        }
        flush();
        mClosed = true;
        if (ResponseCache.getDefault() == this) {
            ResponseCache.setDefault(null);
        }
    }

    public synchronized void delete() throws IOException {
        close();
        mEntries.clear();
        mSize = 0L;
        deleteRecursively(mDirectory);
    }

    private void commitEntryLocked(
            String key, Map<String, List<String>> headers, byte[] body) throws IOException {
        if (body.length > mMaxSize) {
            return;
        }
        CacheEntry prev = mEntries.remove(key);
        if (prev != null) {
            mSize -= prev.body.length;
        }
        CacheEntry entry = new CacheEntry(headers, body);
        mEntries.put(key, entry);
        mSize += body.length;
        writeEntryToDisk(key, entry);
        evictIfNeededLocked();
    }

    private void invalidateUri(URI uri) {
        String key = keyFor(uri);
        CacheEntry removed = mEntries.remove(key);
        if (removed != null) {
            mSize -= removed.body.length;
            File f = new File(mDirectory, key + ".cache");
            f.delete();
        }
    }

    private void evictIfNeededLocked() {
        Iterator<Map.Entry<String, CacheEntry>> it = mEntries.entrySet().iterator();
        while (mSize > mMaxSize && it.hasNext()) {
            Map.Entry<String, CacheEntry> eldest = it.next();
            mSize -= eldest.getValue().body.length;
            File f = new File(mDirectory, eldest.getKey() + ".cache");
            f.delete();
            it.remove();
        }
    }

    private void loadFromDisk() {
        File[] files = mDirectory.listFiles();
        if (files == null) {
            return;
        }
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            String name = f.getName();
            if (!f.isFile() || !name.endsWith(".cache")) {
                continue;
            }
            String key = name.substring(0, name.length() - 6);
            try {
                CacheEntry entry = readEntryFromDisk(f);
                if (entry != null && mSize + entry.body.length <= mMaxSize) {
                    mEntries.put(key, entry);
                    mSize += entry.body.length;
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void writeEntryToDisk(String key, CacheEntry entry) throws IOException {
        if (!mDirectory.exists() && !mDirectory.mkdirs() && !mDirectory.exists()) {
            return;
        }
        File target = new File(mDirectory, key + ".cache");
        DataOutputStream out = new DataOutputStream(new FileOutputStream(target));
        try {
            out.writeInt(entry.headers.size());
            for (Map.Entry<String, List<String>> he : entry.headers.entrySet()) {
                String k = he.getKey();
                out.writeBoolean(k != null);
                if (k != null) {
                    out.writeUTF(k);
                }
                List<String> vals = he.getValue();
                out.writeInt(vals.size());
                for (int i = 0; i < vals.size(); i++) {
                    String v = vals.get(i);
                    out.writeUTF(v == null ? "" : v);
                }
            }
            out.writeInt(entry.body.length);
            out.write(entry.body);
            out.flush();
        } finally {
            out.close();
        }
    }

    private static CacheEntry readEntryFromDisk(File file) throws IOException {
        DataInputStream in = new DataInputStream(new FileInputStream(file));
        try {
            int headerCount = in.readInt();
            Map<String, List<String>> map = new LinkedHashMap<String, List<String>>();
            for (int i = 0; i < headerCount; i++) {
                boolean hasKey = in.readBoolean();
                String k = hasKey ? in.readUTF() : null;
                int vCount = in.readInt();
                List<String> vals = new ArrayList<String>(vCount);
                for (int j = 0; j < vCount; j++) {
                    vals.add(in.readUTF());
                }
                map.put(k, Collections.unmodifiableList(vals));
            }
            int bodyLen = in.readInt();
            if (bodyLen < 0) {
                return null;
            }
            byte[] body = new byte[bodyLen];
            in.readFully(body);
            return new CacheEntry(Collections.unmodifiableMap(map), body);
        } finally {
            in.close();
        }
    }

    private static Map<String, List<String>> copyHeaders(Map<String, List<String>> src) {
        if (src == null) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> copy = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> e : src.entrySet()) {
            List<String> vals = e.getValue();
            copy.put(
                    e.getKey(),
                    vals == null
                            ? Collections.<String>emptyList()
                            : Collections.unmodifiableList(new ArrayList<String>(vals)));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static boolean hasNoCacheDirective(Map<String, List<String>> headers) {
        if (headers == null) {
            return false;
        }
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if ("Cache-Control".equalsIgnoreCase(e.getKey()) && e.getValue() != null) {
                for (int i = 0; i < e.getValue().size(); i++) {
                    String v = e.getValue().get(i);
                    if (v != null) {
                        String lower = v.toLowerCase(Locale.US);
                        if (lower.contains("no-cache") || lower.contains("no-store")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasNoStoreDirective(Map<String, List<String>> headers) {
        if (headers == null) {
            return false;
        }
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if ("Cache-Control".equalsIgnoreCase(e.getKey()) && e.getValue() != null) {
                for (int i = 0; i < e.getValue().size(); i++) {
                    String v = e.getValue().get(i);
                    if (v != null && v.toLowerCase(Locale.US).contains("no-store")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String keyFor(URI uri) {
        byte[] raw = uri.toASCIIString().getBytes(StandardCharsets.UTF_8);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (int i = 0; i < digest.length; i++) {
                int b = digest[i] & 0xff;
                if (b < 16) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(uri.hashCode());
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteRecursively(children[i]);
                }
            }
        }
        file.delete();
    }

    private static final class CacheEntry {
        final Map<String, List<String>> headers;
        final byte[] body;

        CacheEntry(Map<String, List<String>> headers, byte[] body) {
            this.headers = headers;
            this.body = body;
        }
    }
}
