// SPDX-License-Identifier: GPL-3.0-only
package android.content.res;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ParcelFileDescriptor;
import java.io.*;
import shiraz.runtime.NativeFd;

/** An owned descriptor plus a bounded byte region in the original package. */
public class AssetFileDescriptor implements Closeable, Parcelable {
    public static final long UNKNOWN_LENGTH = -1;
    private final ParcelFileDescriptor descriptor;
    private final long start, length;
    private final Bundle extras;
    public AssetFileDescriptor(ParcelFileDescriptor descriptor, long start, long length) { this(descriptor, start, length, null); }
    public AssetFileDescriptor(ParcelFileDescriptor descriptor, long start, long length, Bundle extras) {
        if (descriptor == null) throw new NullPointerException("descriptor");
        if (start < 0 || length < -1 || (length == UNKNOWN_LENGTH && start != 0) || (length >= 0 && start > Long.MAX_VALUE - length))
            throw new IllegalArgumentException("Invalid asset region");
        this.descriptor = descriptor; this.start = start; this.length = length; this.extras = extras;
    }
    public ParcelFileDescriptor getParcelFileDescriptor() { return descriptor; }
    public FileDescriptor getFileDescriptor() { return descriptor.getFileDescriptor(); }
    public long getStartOffset() { return start; }
    public long getDeclaredLength() { return length; }
    public long getLength() {
        if (length != UNKNOWN_LENGTH) return length;
        try { return descriptor.getStatSize(); } catch (IOException error) { return UNKNOWN_LENGTH; }
    }
    public Bundle getExtras() { return extras; }
    public FileInputStream createInputStream() throws IOException { return new AutoCloseInputStream(this); }
    public FileOutputStream createOutputStream() throws IOException { return new AutoCloseOutputStream(this); }
    public byte[] shirazReadRegion() throws IOException {
        FileDescriptor fd = descriptor.getFileDescriptor();
        synchronized (fd) {
            dalvik.system.BlockGuard.getThreadPolicy().onReadFromDisk();
            long size = getLength();
            if (size < 0) throw new IOException("Unknown asset region length");
            return NativeFd.readRegion(fd, start, size);
        }
    }
    @Override public void close() throws IOException { descriptor.close(); }
    public int describeContents() { return CONTENTS_FILE_DESCRIPTOR; }
    public void writeToParcel(Parcel out, int flags) { throw new UnsupportedOperationException("Asset descriptor parcel transport is not implemented"); }
    public static class AutoCloseOutputStream extends FileOutputStream {
        private final AssetFileDescriptor owner;
        private long remaining;
        private boolean closed;
        public AutoCloseOutputStream(AssetFileDescriptor owner) throws IOException {
            super(owner.getFileDescriptor()); this.owner = owner; remaining = owner.length;
            try { if (owner.length != UNKNOWN_LENGTH) getChannel().position(owner.start); }
            catch (IOException error) {
                try { close(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
                throw error;
            }
        }
        private void checkOpen() throws IOException {
            if (closed || !getFD().valid()) throw new IOException("Asset descriptor stream is closed");
        }
        @Override public void write(int value) throws IOException {
            // ART's FileOutputStream.write(int) calls the array overload virtually.
            // Route here explicitly so region accounting happens exactly once.
            write(new byte[]{(byte)value}, 0, 1);
        }
        @Override public void write(byte[] buffer) throws IOException { write(buffer, 0, buffer.length); }
        @Override public void write(byte[] buffer, int offset, int count) throws IOException {
            synchronized (owner.getFileDescriptor()) {
                checkOpen();
                if (offset < 0 || count < 0 || offset > buffer.length - count) throw new IndexOutOfBoundsException();
                int actual = remaining < 0 ? count : (int)Math.min(remaining, count);
                if (actual == 0) return;
                super.write(buffer, offset, actual); if (remaining > 0) remaining -= actual;
            }
        }
        @Override public void close() throws IOException {
            synchronized (owner.getFileDescriptor()) {
                if (closed) return; closed = true;
                IOException failure = null;
                try { super.close(); } catch (IOException error) { failure = error; }
                try { owner.close(); } catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
                if (failure != null) throw failure;
            }
        }
    }
    public static class AutoCloseInputStream extends FileInputStream {
        private final AssetFileDescriptor owner;
        private long remaining;
        private boolean closed;
        private void checkOpen() throws IOException { if (closed || !getFD().valid()) throw new IOException("Asset descriptor stream is closed"); }
        public AutoCloseInputStream(AssetFileDescriptor owner) throws IOException {
            super(owner.getFileDescriptor()); this.owner = owner; remaining = owner.length;
            try { if (owner.start != 0) getChannel().position(owner.start); }
            catch (IOException error) { close(); throw error; }
        }
        @Override public int read() throws IOException {
            synchronized (owner.getFileDescriptor()) {
                checkOpen(); if (remaining == 0) return -1;
                int value = super.read(); if (remaining > 0) remaining = value < 0 ? 0 : remaining - 1; return value;
            }
        }
        @Override public int read(byte[] buffer) throws IOException { return read(buffer, 0, buffer.length); }
        @Override public int read(byte[] buffer, int offset, int count) throws IOException {
            synchronized (owner.getFileDescriptor()) {
                checkOpen();
                if (offset < 0 || count < 0 || offset > buffer.length - count) throw new IndexOutOfBoundsException();
                if (count == 0) return 0;
                if (remaining == 0) return -1;
                int actual = super.read(buffer, offset, remaining < 0 ? count : (int)Math.min(remaining, count));
                if (remaining > 0) remaining = actual < 0 ? 0 : remaining - actual;
                return actual;
            }
        }
        @Override public long skip(long count) throws IOException {
            synchronized (owner.getFileDescriptor()) {
                checkOpen();
                if (count <= 0) return 0;
                long skipped = super.skip(remaining < 0 ? count : Math.min(remaining, count));
                if (remaining > 0) remaining -= skipped; return skipped;
            }
        }
        @Override public int available() throws IOException {
            synchronized (owner.getFileDescriptor()) {
                checkOpen(); int available = super.available(); return remaining < 0 ? available : (int)Math.min(remaining, available);
            }
        }
        @Override public void close() throws IOException {
            synchronized (owner.getFileDescriptor()) {
                if (closed) return; closed = true;
                IOException failure = null;
                try { super.close(); } catch (IOException error) { failure = error; }
                try { owner.close(); } catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
                if (failure != null) throw failure;
            }
        }
    }
}
