// SPDX-License-Identifier: GPL-3.0-only
package android.app.blob;

import android.os.ParcelFileDescriptor;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Local file-backed BlobStoreManager with SHA-256 verification on commit. */
public class BlobStoreManager {
    private static final long DEFAULT_QUOTA_BYTES = 256L * 1024L * 1024L;
    private static final AtomicLong NEXT_SESSION_ID = new AtomicLong(1L);

    private final File mRootDir;
    private final Map<Long, Session> mSessions = new LinkedHashMap<Long, Session>();
    private final Map<BlobHandle, File> mCommittedBlobs = new LinkedHashMap<BlobHandle, File>();
    private final Set<BlobHandle> mLeasedBlobs = new LinkedHashSet<BlobHandle>();

    public BlobStoreManager() {
        this(new File(System.getProperty("java.io.tmpdir", "/tmp"), "shiraz-blobstore"));
    }

    public BlobStoreManager(File rootDir) {
        mRootDir = rootDir;
    }

    public synchronized long createSession(BlobHandle blobHandle) throws IOException {
        if (blobHandle == null) throw new NullPointerException("blobHandle");
        if (!mRootDir.exists() && !mRootDir.mkdirs() && !mRootDir.isDirectory()) {
            throw new IOException("Unable to create blob store directory");
        }
        long id = NEXT_SESSION_ID.getAndIncrement();
        File sessionFile = new File(mRootDir, "session-" + id + ".blob");
        if (!sessionFile.exists() && !sessionFile.createNewFile()) {
            throw new IOException("Unable to create session file");
        }
        Session session = new Session(this, id, blobHandle, sessionFile);
        mSessions.put(Long.valueOf(id), session);
        return id;
    }

    public synchronized Session openSession(long sessionId) throws IOException {
        if (sessionId <= 0) throw new IllegalArgumentException("sessionId must be positive");
        Session s = mSessions.get(Long.valueOf(sessionId));
        if (s == null || s.mAbandoned) {
            throw new SecurityException("Session not found: " + sessionId);
        }
        s.mClosed = false;
        return s;
    }

    public synchronized void abandonSession(long sessionId) throws IOException {
        if (sessionId <= 0) throw new IllegalArgumentException("sessionId must be positive");
        Session s = mSessions.remove(Long.valueOf(sessionId));
        if (s == null) {
            throw new SecurityException("Session not found: " + sessionId);
        }
        s.abandonInternal();
    }

    public synchronized ParcelFileDescriptor openBlob(BlobHandle blobHandle) throws IOException {
        if (blobHandle == null) throw new NullPointerException("blobHandle");
        if (blobHandle.getExpiryTimeMillis() > 0
                && System.currentTimeMillis() > blobHandle.getExpiryTimeMillis()) {
            throw new SecurityException("Blob has expired");
        }
        File file = mCommittedBlobs.get(blobHandle);
        if (file == null || !file.isFile()) {
            throw new SecurityException("Caller is not allowed to access blob or blob does not exist");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    public synchronized void acquireLease(BlobHandle blobHandle, int descriptionResId, long leaseExpiryTimeMillis)
            throws IOException {
        acquireLeaseInternal(blobHandle, leaseExpiryTimeMillis);
    }

    public synchronized void acquireLease(BlobHandle blobHandle, CharSequence description, long leaseExpiryTimeMillis)
            throws IOException {
        acquireLeaseInternal(blobHandle, leaseExpiryTimeMillis);
    }

    public synchronized void acquireLease(BlobHandle blobHandle, int descriptionResId) throws IOException {
        acquireLeaseInternal(blobHandle, 0L);
    }

    public synchronized void acquireLease(BlobHandle blobHandle, CharSequence description) throws IOException {
        acquireLeaseInternal(blobHandle, 0L);
    }

    private void acquireLeaseInternal(BlobHandle blobHandle, long leaseExpiryTimeMillis) {
        if (blobHandle == null) throw new NullPointerException("blobHandle");
        if (leaseExpiryTimeMillis < 0) {
            throw new IllegalArgumentException("leaseExpiryTimeMillis must not be negative");
        }
        if (blobHandle.getExpiryTimeMillis() > 0 && leaseExpiryTimeMillis > blobHandle.getExpiryTimeMillis()) {
            throw new IllegalArgumentException("Lease expiry cannot exceed blob expiry");
        }
        mLeasedBlobs.add(blobHandle);
    }

    public synchronized void releaseLease(BlobHandle blobHandle) throws IOException {
        if (blobHandle == null) throw new NullPointerException("blobHandle");
        mLeasedBlobs.remove(blobHandle);
    }

    public synchronized long getRemainingLeaseQuotaBytes() {
        long used = 0L;
        for (BlobHandle handle : mLeasedBlobs) {
            File f = mCommittedBlobs.get(handle);
            if (f != null && f.isFile()) {
                used += f.length();
            }
        }
        return Math.max(0L, DEFAULT_QUOTA_BYTES - used);
    }

    public synchronized List<BlobHandle> getLeasedBlobs() throws IOException {
        return new ArrayList<BlobHandle>(mLeasedBlobs);
    }

    synchronized int commitSession(Session session) throws IOException {
        mSessions.remove(Long.valueOf(session.mSessionId));
        byte[] actual = computeSha256(session.mFile);
        if (!Arrays.equals(actual, session.mBlobHandle.getSha256Digest())) {
            session.mFile.delete();
            return 1;
        }
        File committed = new File(mRootDir, "committed-" + session.mSessionId + ".blob");
        if (!session.mFile.renameTo(committed)) {
            committed = session.mFile;
        }
        mCommittedBlobs.put(session.mBlobHandle, committed);
        return 0;
    }

    private static byte[] computeSha256(File file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            FileInputStream in = new FileInputStream(file);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            } finally {
                in.close();
            }
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    public static class Session implements Closeable {
        private final BlobStoreManager mOwner;
        final long mSessionId;
        final BlobHandle mBlobHandle;
        final File mFile;
        private final Map<String, byte[]> mAllowedPackages = new LinkedHashMap<String, byte[]>();
        private boolean mSameSignatureAccess;
        private boolean mPublicAccess;
        boolean mClosed;
        boolean mAbandoned;
        private boolean mCommitted;

        Session(BlobStoreManager owner, long sessionId, BlobHandle blobHandle, File file) {
            mOwner = owner;
            mSessionId = sessionId;
            mBlobHandle = blobHandle;
            mFile = file;
        }

        private void ensureOpen() throws IOException {
            if (mAbandoned || mCommitted) {
                throw new IllegalStateException("Session is finalized");
            }
            if (mClosed) {
                throw new IllegalStateException("Session is closed");
            }
        }

        public ParcelFileDescriptor openWrite(long offsetBytes, long lengthBytes) throws IOException {
            ensureOpen();
            if (offsetBytes < 0) throw new IllegalArgumentException("offsetBytes < 0");
            return ParcelFileDescriptor.open(
                    mFile,
                    ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE);
        }

        public ParcelFileDescriptor openRead() throws IOException {
            ensureOpen();
            return ParcelFileDescriptor.open(mFile, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        public long getSize() throws IOException {
            ensureOpen();
            return mFile.exists() ? mFile.length() : 0L;
        }

        @Override
        public void close() throws IOException {
            mClosed = true;
        }

        public void abandon() throws IOException {
            ensureOpen();
            mOwner.abandonSession(mSessionId);
        }

        void abandonInternal() {
            mAbandoned = true;
            mClosed = true;
            if (mFile.exists()) {
                mFile.delete();
            }
        }

        public synchronized void allowPackageAccess(String packageName, byte[] certificate) throws IOException {
            ensureOpen();
            if (packageName == null) throw new NullPointerException("packageName");
            if (certificate == null) throw new NullPointerException("certificate");
            mAllowedPackages.put(packageName, certificate.clone());
        }

        public synchronized boolean isPackageAccessAllowed(String packageName, byte[] certificate) throws IOException {
            ensureOpen();
            if (packageName == null) throw new NullPointerException("packageName");
            if (certificate == null) throw new NullPointerException("certificate");
            byte[] stored = mAllowedPackages.get(packageName);
            return stored != null && Arrays.equals(stored, certificate);
        }

        public synchronized void allowSameSignatureAccess() throws IOException {
            ensureOpen();
            mSameSignatureAccess = true;
        }

        public synchronized boolean isSameSignatureAccessAllowed() throws IOException {
            ensureOpen();
            return mSameSignatureAccess;
        }

        public synchronized void allowPublicAccess() throws IOException {
            ensureOpen();
            mPublicAccess = true;
        }

        public synchronized boolean isPublicAccessAllowed() throws IOException {
            ensureOpen();
            return mPublicAccess;
        }

        public void commit(Executor executor, final Consumer<Integer> resultCallback) throws IOException {
            ensureOpen();
            if (executor == null) throw new NullPointerException("executor");
            if (resultCallback == null) throw new NullPointerException("resultCallback");
            mCommitted = true;
            mClosed = true;
            final int code = mOwner.commitSession(this);
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    resultCallback.accept(Integer.valueOf(code));
                }
            });
        }
    }
}
