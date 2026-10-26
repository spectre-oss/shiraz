// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.FileNotFoundException;

/** Linux descriptor operations; callers synchronize on the FileDescriptor for ownership changes. */
public final class NativeFd {
    static { System.loadLibrary("shiraz-host"); }
    private NativeFd() { }
    public static native void adopt(FileDescriptor target, int source);
    public static native void open(FileDescriptor target, byte[] path, int mode) throws FileNotFoundException;
    public static native void duplicate(FileDescriptor target, FileDescriptor source) throws IOException;
    public static native void duplicateNumber(FileDescriptor target, int source) throws IOException;
    public static native int number(FileDescriptor descriptor);
    public static native int detach(FileDescriptor descriptor);
    public static native void close(FileDescriptor descriptor) throws IOException;
    public static native void closeRawQuietly(int descriptor);
    public static native long size(FileDescriptor descriptor) throws IOException;
    public static native byte[] readRegion(FileDescriptor descriptor, long offset, long length) throws IOException;
}
