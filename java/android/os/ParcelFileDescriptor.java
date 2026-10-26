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
    @Override public void close() throws IOException {
        synchronized (fd) { try { NativeFd.close(fd); } finally { if (guard != null) guard.close(); } }
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
