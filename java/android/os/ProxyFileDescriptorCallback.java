// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import android.system.ErrnoException;

/** Callback for servicing virtual/proxy file descriptor I/O operations. */
public abstract class ProxyFileDescriptorCallback {
    private static final int EBADF = resolveEbadf();

    private static int resolveEbadf() {
        try {
            return android.system.OsConstants.class.getField("EBADF").getInt(null);
        } catch (Throwable unavailable) {
            return 9;
        }
    }

    public ProxyFileDescriptorCallback() {
    }

    public long onGetSize() throws ErrnoException {
        throw new ErrnoException("onGetSize", EBADF);
    }

    public int onRead(long offset, int size, byte[] data) throws ErrnoException {
        throw new ErrnoException("onRead", EBADF);
    }

    public int onWrite(long offset, int size, byte[] data) throws ErrnoException {
        throw new ErrnoException("onWrite", EBADF);
    }

    public void onFsync() throws ErrnoException {
        throw new ErrnoException("onFsync", EBADF);
    }

    public abstract void onRelease();
}
