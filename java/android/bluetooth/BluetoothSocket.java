// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.ParcelUuid;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class BluetoothSocket implements Closeable {
    public static final int TYPE_RFCOMM = 1;
    public static final int TYPE_SCO = 2;
    public static final int TYPE_L2CAP = 3;

    private final int mType;
    private final BluetoothDevice mDevice;
    private final int mPort;
    private final ParcelUuid mUuid;
    private volatile boolean mClosed;

    BluetoothSocket(int type, boolean auth, boolean encrypt, BluetoothDevice device, int port, ParcelUuid uuid) {
        mType = type;
        mDevice = device;
        mPort = port;
        mUuid = uuid;
    }

    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    public BluetoothDevice getRemoteDevice() {
        return mDevice;
    }

    public InputStream getInputStream() throws IOException {
        if (mClosed) {
            throw new IOException("socket closed");
        }
        throw new IOException("Bluetooth socket is not connected");
    }

    public OutputStream getOutputStream() throws IOException {
        if (mClosed) {
            throw new IOException("socket closed");
        }
        throw new IOException("Bluetooth socket is not connected");
    }

    public boolean isConnected() {
        return false;
    }

    public void connect() throws IOException {
        if (mClosed) {
            throw new BluetoothSocketException(BluetoothSocketException.SOCKET_CLOSED, "socket closed");
        }
        if (mDevice == null) {
            throw new BluetoothSocketException(BluetoothSocketException.NULL_DEVICE, "Remote device is null");
        }
        throw new BluetoothSocketException(BluetoothSocketException.BLUETOOTH_OFF_FAILURE, "Host Bluetooth radio is off");
    }

    public void close() throws IOException {
        mClosed = true;
    }

    public int getMaxTransmitPacketSize() {
        return 0;
    }

    public int getMaxReceivePacketSize() {
        return 0;
    }

    public int getConnectionType() {
        return mType;
    }

    public String toString() {
        return mDevice != null ? mDevice.getAddress() : "BluetoothSocket[type=" + mType + ", port=" + mPort + ", uuid=" + mUuid + "]";
    }
}
