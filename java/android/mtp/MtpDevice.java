// SPDX-License-Identifier: GPL-3.0-only
package android.mtp;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.util.Objects;

public final class MtpDevice {
    private final UsbDevice mDevice;
    private UsbDeviceConnection mConnection;
    private volatile boolean mOpen;

    public MtpDevice(UsbDevice device) {
        mDevice = Objects.requireNonNull(device, "device");
    }

    public boolean open(UsbDeviceConnection connection) {
        Objects.requireNonNull(connection, "connection");
        if (connection.getFileDescriptor() < 0) {
            connection.close();
            return false;
        }
        mConnection = connection;
        mOpen = true;
        return true;
    }

    public void close() {
        mOpen = false;
        if (mConnection != null) {
            mConnection.close();
            mConnection = null;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    public String getDeviceName() {
        return mDevice.getDeviceName();
    }

    public int getDeviceId() {
        return mDevice.getDeviceId();
    }

    @Override
    public String toString() {
        return mDevice.getDeviceName();
    }

    public MtpDeviceInfo getDeviceInfo() {
        return null;
    }

    public int[] getStorageIds() {
        return null;
    }

    public int[] getObjectHandles(int storageId, int format, int objectHandle) {
        return null;
    }

    public byte[] getObject(int objectHandle, int objectSize) {
        if (objectSize < 0) {
            throw new IllegalArgumentException("objectSize should not be negative");
        }
        return null;
    }

    public long getPartialObject(int objectHandle, long offset, long size, byte[] buffer)
            throws IOException {
        Objects.requireNonNull(buffer, "buffer");
        if (!mOpen) {
            throw new IOException("MtpDevice is not open");
        }
        throw new IOException("No MTP transport active");
    }

    public long getPartialObject64(int objectHandle, long offset, long size, byte[] buffer)
            throws IOException {
        Objects.requireNonNull(buffer, "buffer");
        if (!mOpen) {
            throw new IOException("MtpDevice is not open");
        }
        throw new IOException("No MTP transport active");
    }

    public byte[] getThumbnail(int objectHandle) {
        return null;
    }

    public MtpStorageInfo getStorageInfo(int storageId) {
        return null;
    }

    public MtpObjectInfo getObjectInfo(int objectHandle) {
        return null;
    }

    public boolean deleteObject(int objectHandle) {
        return false;
    }

    public long getParent(int objectHandle) {
        return -1L;
    }

    public long getStorageId(int objectHandle) {
        return -1L;
    }

    public boolean importFile(int objectHandle, String destPath) {
        Objects.requireNonNull(destPath, "destPath");
        return false;
    }

    public boolean importFile(int objectHandle, ParcelFileDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return false;
    }

    public boolean sendObject(int objectHandle, long size, ParcelFileDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return false;
    }

    public MtpObjectInfo sendObjectInfo(MtpObjectInfo info) {
        Objects.requireNonNull(info, "info");
        return null;
    }

    public MtpEvent readEvent(CancellationSignal signal) throws IOException {
        if (signal != null) {
            signal.throwIfCanceled();
        }
        if (!mOpen) {
            throw new IOException("MtpDevice is not open");
        }
        throw new IOException("No MTP event interrupt endpoint active");
    }
}
