// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.usb;

import java.nio.ByteBuffer;

public class UsbRequest {
    private UsbDeviceConnection mConnection;
    private UsbEndpoint mEndpoint;
    private Object mClientData;
    private ByteBuffer mBuffer;

    public UsbRequest() {
    }

    public boolean initialize(UsbDeviceConnection connection, UsbEndpoint endpoint) {
        if (connection == null) {
            throw new NullPointerException("connection");
        }
        if (endpoint == null) {
            throw new NullPointerException("endpoint");
        }
        mEndpoint = endpoint;
        mConnection = connection;
        return connection.isOpen();
    }

    public void close() {
        mEndpoint = null;
        mConnection = null;
        mBuffer = null;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    public UsbEndpoint getEndpoint() {
        return mEndpoint;
    }

    public Object getClientData() {
        return mClientData;
    }

    public void setClientData(Object data) {
        mClientData = data;
    }

    @Deprecated
    public boolean queue(ByteBuffer buffer, int length) {
        if (mConnection == null || !mConnection.isOpen() || mEndpoint == null) {
            return false;
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0");
        }
        if (buffer != null && buffer.capacity() < length) {
            throw new IllegalArgumentException("buffer capacity < length");
        }
        mBuffer = buffer;
        return false;
    }

    public boolean queue(ByteBuffer buffer) {
        if (mConnection == null || mEndpoint == null) {
            throw new NullPointerException("UsbRequest is not initialized");
        }
        if (!mConnection.isOpen()) {
            return false;
        }
        mBuffer = buffer;
        return false;
    }

    public boolean cancel() {
        if (mConnection == null || !mConnection.isOpen()) {
            return false;
        }
        mBuffer = null;
        return false;
    }
}
