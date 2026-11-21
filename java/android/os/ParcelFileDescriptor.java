// SPDX-License-Identifier: GPL-3.0-only
package android.os;
import java.io.*;
import java.nio.charset.StandardCharsets;
import dalvik.system.CloseGuard;
import shiraz.runtime.NativeFd;

/** Owned Linux descriptor. Parcel copies retain independent descriptor ownership. */
public final class ParcelFileDescriptor implements Closeable, Parcelable {
    public static final int MODE_READ_ONLY = 0x10000000, MODE_WRITE_ONLY = 0x20000000, MODE_READ_WRITE = 0x30000000,
        MODE_CREATE = 0x08000000, MODE_TRUNCATE = 0x04000000, MODE_APPEND = 0x02000000;
    private final FileDescriptor fd = new FileDescriptor();
    private final CloseGuard guard = CloseGuard.get();
    private ParcelFileDescriptor() { guard.open("close"); }
    public static ParcelFileDescriptor adoptFd(int raw) {
        ParcelFileDescriptor result = new ParcelFileDescriptor();
        try { NativeFd.adopt(result.fd, raw); return result; }
        catch (Throwable error) { result.guard.close(); throw error; }
    }
    /** Duplicates a borrowed descriptor; the caller retains ownership of raw. */
    public static ParcelFileDescriptor fromFd(int raw) throws IOException {
        ParcelFileDescriptor result = new ParcelFileDescriptor();
        try { NativeFd.duplicateNumber(result.fd, raw); return result; }
        catch (Throwable error) { result.guard.close(); throw error; }
    }
    public static ParcelFileDescriptor dup(FileDescriptor source) throws IOException {
        if (source == null) throw new NullPointerException("descriptor");
        ParcelFileDescriptor result = new ParcelFileDescriptor();
        try { synchronized (source) { NativeFd.duplicate(result.fd, source); } return result; }
        catch (Throwable error) { result.guard.close(); throw error; }
    }
    public ParcelFileDescriptor dup() throws IOException { return dup(fd); }
    public static ParcelFileDescriptor open(File file, int mode) throws FileNotFoundException {
        if (file == null) throw new NullPointerException("file");
        if ((mode & ~0x3e000000) != 0 || (mode & MODE_READ_WRITE) == 0) throw new IllegalArgumentException("Unsupported descriptor mode");
        if ((mode & MODE_READ_ONLY) != 0) dalvik.system.BlockGuard.getThreadPolicy().onReadFromDisk();
        if ((mode & (MODE_WRITE_ONLY | MODE_CREATE | MODE_TRUNCATE | MODE_APPEND)) != 0) dalvik.system.BlockGuard.getThreadPolicy().onWriteToDisk();
        ParcelFileDescriptor result = new ParcelFileDescriptor();
        try { NativeFd.open(result.fd, file.getPath().getBytes(StandardCharsets.UTF_8), mode); return result; }
        catch (Throwable error) { result.guard.close(); throw error; }
    }
    public FileDescriptor getFileDescriptor() { return fd; }
    public int getFd() {
        synchronized (fd) { int raw = NativeFd.number(fd); if (raw < 0) throw new IllegalStateException("Descriptor is closed"); return raw; }
    }
    public int detachFd() {
        synchronized (fd) { getFd(); int raw = NativeFd.detach(fd); guard.close(); return raw; }
    }
    public long getStatSize() throws IOException { synchronized (fd) { return NativeFd.size(fd); } }
    /** Closing the stream also releases the descriptor's ownership guard. */
    public static class AutoCloseInputStream extends FileInputStream {
        private final ParcelFileDescriptor owner;
        private boolean closed;
        public AutoCloseInputStream(ParcelFileDescriptor owner) {
            super(owner.getFileDescriptor()); this.owner=owner;
        }
        @Override public void close() throws IOException {
            synchronized(owner.fd) {
                if(closed)return;
                closed=true;
                try { super.close(); } finally { owner.close(); }
            }
        }
    }
    public static class AutoCloseOutputStream extends FileOutputStream {
        private final ParcelFileDescriptor owner;
        private boolean closed;
        public AutoCloseOutputStream(ParcelFileDescriptor owner) {
            super(owner.getFileDescriptor()); this.owner=owner;
        }
        @Override public void close() throws IOException {
            synchronized(owner.fd) {
                if(closed)return;
                closed=true;
                try { super.close(); } finally { owner.close(); }
            }
        }
    }
    private Handler closeHandler;
    private OnCloseListener closeListener;
    private boolean closeNotified;

    public interface OnCloseListener {
        void onClose(IOException e);
    }

    public static class FileDescriptorDetachedException extends IOException {
        public FileDescriptorDetachedException() {
            super("Remote side is detached");
        }
    }

    public static int parseMode(String mode) {
        if ("r".equals(mode)) {
            return MODE_READ_ONLY;
        } else if ("w".equals(mode) || "wt".equals(mode)) {
            return MODE_WRITE_ONLY | MODE_CREATE | MODE_TRUNCATE;
        } else if ("wa".equals(mode)) {
            return MODE_WRITE_ONLY | MODE_CREATE | MODE_APPEND;
        } else if ("rw".equals(mode)) {
            return MODE_READ_WRITE | MODE_CREATE;
        } else if ("rwt".equals(mode)) {
            return MODE_READ_WRITE | MODE_CREATE | MODE_TRUNCATE;
        }
        throw new IllegalArgumentException("Bad mode '" + mode + "'");
    }

    public static ParcelFileDescriptor open(
            File file, int mode, Handler handler, final OnCloseListener listener) throws IOException {
        if (handler == null) {
            throw new IllegalArgumentException("Handler must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        ParcelFileDescriptor pfd = open(file, mode);
        pfd.closeHandler = handler;
        pfd.closeListener = listener;
        return pfd;
    }

    public static ParcelFileDescriptor wrap(
            ParcelFileDescriptor pfd, Handler handler, OnCloseListener listener) throws IOException {
        if (pfd == null) {
            throw new NullPointerException("pfd");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        ParcelFileDescriptor wrapped = pfd.dup();
        pfd.close();
        wrapped.closeHandler = handler;
        wrapped.closeListener = listener;
        return wrapped;
    }

    public static ParcelFileDescriptor[] createPipe() throws IOException {
        try {
            FileDescriptor[] raw = android.system.Os.pipe();
            ParcelFileDescriptor readEnd = null;
            ParcelFileDescriptor writeEnd = null;
            try {
                readEnd = dup(raw[0]);
                writeEnd = dup(raw[1]);
                return new ParcelFileDescriptor[] { readEnd, writeEnd };
            } catch (IOException e) {
                if (readEnd != null) {
                    try { readEnd.close(); } catch (IOException ignored) {}
                }
                if (writeEnd != null) {
                    try { writeEnd.close(); } catch (IOException ignored) {}
                }
                throw e;
            } finally {
                try { android.system.Os.close(raw[0]); } catch (Throwable ignored) {}
                try { android.system.Os.close(raw[1]); } catch (Throwable ignored) {}
            }
        } catch (android.system.ErrnoException e) {
            throw new IOException("Failed to create pipe", e);
        } catch (LinkageError | RuntimeException e) {
            throw new IOException("Pipe creation requires ART OS bridge", e);
        }
    }

    public static ParcelFileDescriptor[] createReliablePipe() throws IOException {
        return createPipe();
    }

    @Override public void close() throws IOException {
        final Handler h;
        final OnCloseListener l;
        synchronized (fd) {
            try { NativeFd.close(fd); } finally { if (guard != null) guard.close(); }
            if (!closeNotified && closeListener != null) {
                closeNotified = true;
                h = closeHandler;
                l = closeListener;
            } else {
                h = null;
                l = null;
            }
        }
        if (h != null && l != null) {
            h.post(new Runnable() {
                @Override public void run() { l.onClose(null); }
            });
        }
    }
    @Override protected void finalize() throws Throwable {
        try { if (guard != null && fd != null && fd.valid()) guard.warnIfOpen(); }
        finally { try { if (fd != null) close(); } finally { super.finalize(); } }
    }
    public int describeContents() { return CONTENTS_FILE_DESCRIPTOR; }
    public void writeToParcel(Parcel out, int flags) {
        synchronized(fd) { out.writeInt(0); out.writeFileDescriptor(fd); }
        if ((flags & PARCELABLE_WRITE_RETURN_VALUE) != 0) try { close(); }
        catch (IOException error) { throw new IllegalStateException("Cannot close returned descriptor", error); }
    }
    public static final Parcelable.Creator<ParcelFileDescriptor> CREATOR = new Parcelable.Creator<ParcelFileDescriptor>() {
        public ParcelFileDescriptor createFromParcel(Parcel source) {
            if (source.readInt() != 0) throw new UnsupportedOperationException("Reliable descriptor communication is unavailable");
            return source.readFileDescriptor();
        }
        public ParcelFileDescriptor[] newArray(int size) { return new ParcelFileDescriptor[size]; }
    };
}
