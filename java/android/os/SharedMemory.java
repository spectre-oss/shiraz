// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import android.system.ErrnoException;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;

/** Anonymous shared memory region backed by a host file descriptor and mmap. */
public final class SharedMemory implements Parcelable, Closeable {
    private static final int PROT_READ = resolveConst("PROT_READ", 1);
    private static final int PROT_WRITE = resolveConst("PROT_WRITE", 2);
    private static final int PROT_EXEC = resolveConst("PROT_EXEC", 4);
    private static final int EINVAL = resolveConst("EINVAL", 22);
    private static final int EBADF = resolveConst("EBADF", 9);

    private static int resolveConst(String name, int fallback) {
        try {
            return android.system.OsConstants.class.getField(name).getInt(null);
        } catch (Throwable unavailable) {
            return fallback;
        }
    }

    public static final Parcelable.Creator<SharedMemory> CREATOR =
            new Parcelable.Creator<SharedMemory>() {
                @Override
                public SharedMemory createFromParcel(Parcel source) {
                    ParcelFileDescriptor pfd = source.readFileDescriptor();
                    return new SharedMemory(pfd);
                }

                @Override
                public SharedMemory[] newArray(int size) {
                    return new SharedMemory[size];
                }
            };

    private ParcelFileDescriptor pfd;
    private int size;
    private int allowedProt = PROT_READ | PROT_WRITE;

    private SharedMemory(ParcelFileDescriptor pfd) {
        this.pfd = Objects.requireNonNull(pfd, "Unable to create SharedMemory from a null FileDescriptor");
        try {
            long statSize = pfd.getStatSize();
            if (statSize <= 0 || statSize > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("SharedMemory size out of bounds: " + statSize);
            }
            this.size = (int) statSize;
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to inspect SharedMemory descriptor", e);
        }
    }

    public static SharedMemory create(String name, int size) throws ErrnoException {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than zero");
        }
        File temp = null;
        try {
            String prefix = (name == null || name.length() < 3) ? "ashmem" : "ashmem_" + name.replaceAll("[^A-Za-z0-9_]", "_");
            if (prefix.length() > 32) {
                prefix = prefix.substring(0, 32);
            }
            temp = File.createTempFile(prefix, ".shm");
            RandomAccessFile raf = new RandomAccessFile(temp, "rw");
            try {
                raf.setLength(size);
            } finally {
                raf.close();
            }
            ParcelFileDescriptor fd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_WRITE);
            // Unlink immediately so POSIX open-file semantics clean up backing storage on close.
            temp.delete();
            return new SharedMemory(fd);
        } catch (IOException e) {
            if (temp != null) {
                temp.delete();
            }
            throw new ErrnoException("SharedMemory.create", EINVAL, e);
        }
    }

    public static SharedMemory fromFileDescriptor(ParcelFileDescriptor fd) {
        Objects.requireNonNull(fd, "Unable to create SharedMemory from a null ParcelFileDescriptor");
        try {
            ParcelFileDescriptor copy = fd.dup();
            fd.close();
            return new SharedMemory(copy);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid ParcelFileDescriptor", e);
        }
    }

    private void checkOpen() {
        if (pfd == null) {
            throw new IllegalStateException("SharedMemory is closed");
        }
    }

    public boolean setProtect(int prot) {
        checkOpen();
        if ((prot & ~(PROT_READ | PROT_WRITE | PROT_EXEC)) != 0) {
            throw new IllegalArgumentException("Invalid protection flags: " + prot);
        }
        if ((prot & ~allowedProt) != 0) {
            return false;
        }
        allowedProt = prot;
        return true;
    }

    public int getSize() {
        checkOpen();
        return size;
    }

    public ByteBuffer mapReadWrite() throws ErrnoException {
        return map(PROT_READ | PROT_WRITE, 0, size);
    }

    public ByteBuffer mapReadOnly() throws ErrnoException {
        return map(PROT_READ, 0, size);
    }

    public ByteBuffer map(int prot, int offset, int length) throws ErrnoException {
        checkOpen();
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be >= 0");
        }
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be > 0");
        }
        if (offset + length > size || offset + length < 0) {
            throw new IllegalArgumentException("offset + length must not exceed getSize()");
        }
        if ((prot & ~(PROT_READ | PROT_WRITE | PROT_EXEC)) != 0) {
            throw new IllegalArgumentException("Invalid prot value");
        }
        if ((prot & ~allowedProt) != 0) {
            throw new ErrnoException("mmap", EBADF);
        }
        boolean writable = (prot & PROT_WRITE) != 0;
        try {
            ParcelFileDescriptor dup = pfd.dup();
            try {
                RandomAccessFileOrChannel holder = new RandomAccessFileOrChannel(dup, writable);
                try {
                    MappedByteBuffer mapped = holder.channel.map(
                            writable ? FileChannel.MapMode.READ_WRITE : FileChannel.MapMode.READ_ONLY,
                            offset,
                            length);
                    return writable ? mapped : mapped.asReadOnlyBuffer();
                } finally {
                    holder.close();
                }
            } finally {
                dup.close();
            }
        } catch (IOException e) {
            throw new ErrnoException("mmap", EINVAL, e);
        }
    }

    private static final class RandomAccessFileOrChannel implements Closeable {
        final java.io.FileInputStream fis;
        final RandomAccessFile raf;
        final FileChannel channel;

        RandomAccessFileOrChannel(ParcelFileDescriptor dup, boolean writable) throws IOException {
            if (writable) {
                fis = null;
                int rawFd = dup.getFd();
                File devFd = new File("/dev/fd/" + rawFd);
                if (!devFd.exists()) {
                    devFd = new File("/proc/self/fd/" + rawFd);
                }
                raf = new RandomAccessFile(devFd, "rw");
                channel = raf.getChannel();
            } else {
                raf = null;
                fis = new java.io.FileInputStream(dup.getFileDescriptor());
                channel = fis.getChannel();
            }
        }

        @Override
        public void close() throws IOException {
            if (raf != null) {
                raf.close();
            }
            if (fis != null) {
                fis.close();
            }
        }
    }

    public static void unmap(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer");
    }

    @Override
    public void close() {
        if (pfd != null) {
            FileUtils.closeQuietly(pfd);
            pfd = null;
        }
    }

    @Override
    public int describeContents() {
        return CONTENTS_FILE_DESCRIPTOR;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        checkOpen();
        dest.writeFileDescriptor(pfd.getFileDescriptor());
    }
}
