// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.usb;

import java.util.concurrent.TimeoutException;

public class UsbDeviceConnection {
    private final UsbDevice mDevice;
    private volatile boolean mOpen;

    /** @hide */
    public UsbDeviceConnection(UsbDevice device) {
        mDevice = device;
        mOpen = false;
    }

    boolean isOpen() {
        return mOpen;
    }

    public void close() {
        mOpen = false;
    }

    public int getFileDescriptor() {
        return -1;
    }

    public byte[] getRawDescriptors() {
        return null;
    }

    public boolean claimInterface(UsbInterface intf, boolean force) {
        if (intf == null) {
            throw new NullPointerException("intf");
        }
        return mOpen;
    }

    public boolean releaseInterface(UsbInterface intf) {
        if (intf == null) {
            throw new NullPointerException("intf");
        }
        return mOpen;
    }

    public boolean setInterface(UsbInterface intf) {
        if (intf == null) {
            throw new NullPointerException("intf");
        }
        return mOpen;
    }

    public boolean setConfiguration(UsbConfiguration configuration) {
        if (configuration == null) {
            throw new NullPointerException("configuration");
        }
        return mOpen;
    }

    private static void checkBounds(byte[] buffer, int start, int length) {
        final int bufferLength = (buffer != null ? buffer.length : 0);
        if (length < 0 || start < 0 || start + length > bufferLength) {
            throw new IllegalArgumentException("Buffer start or length out of bounds.");
        }
    }

    public int controlTransfer(
            int requestType,
            int request,
            int value,
            int index,
            byte[] buffer,
            int length,
            int timeout) {
        return controlTransfer(
                requestType, request, value, index, buffer, 0, length, timeout);
    }

    public int controlTransfer(
            int requestType,
            int request,
            int value,
            int index,
            byte[] buffer,
            int offset,
            int length,
            int timeout) {
        checkBounds(buffer, offset, length);
        return -1;
    }

    public int bulkTransfer(
            UsbEndpoint endpoint, byte[] buffer, int length, int timeout) {
        return bulkTransfer(endpoint, buffer, 0, length, timeout);
    }

    public int bulkTransfer(
            UsbEndpoint endpoint, byte[] buffer, int offset, int length, int timeout) {
        if (endpoint == null) {
            throw new NullPointerException("endpoint");
        }
        checkBounds(buffer, offset, length);
        return -1;
    }

    public UsbRequest requestWait() {
        return null;
    }

    public UsbRequest requestWait(long timeout) throws TimeoutException {
        if (timeout < 0L) {
            throw new IllegalArgumentException("timeout must be >= 0");
        }
        if (!mOpen) {
            return null;
        }
        throw new TimeoutException("Timed out waiting for USB request");
    }

    public String getSerial() {
        return mDevice != null ? mDevice.getSerialNumber() : null;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
}
