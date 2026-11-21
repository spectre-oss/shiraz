// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import java.io.Closeable;
import java.io.IOException;

public final class BluetoothServerSocket implements Closeable {
    private final int mType;
    private final int mChannel;
    private String mServiceName;
    private volatile boolean mClosed;

    BluetoothServerSocket(int type, boolean auth, boolean encrypt, int port) {
        mType = type;
        mChannel = port;
    }

    public BluetoothSocket accept() throws IOException {
        return accept(-1);
    }

    public BluetoothSocket accept(int timeout) throws IOException {
        if (mClosed) {
            throw new IOException("Server socket closed");
        }
        throw new BluetoothSocketException(BluetoothSocketException.BLUETOOTH_OFF_FAILURE, "Host Bluetooth radio is off");
    }

    public void close() throws IOException {
        mClosed = true;
    }

    public int getPsm() {
        return mChannel;
    }

    void setServiceName(String serviceName) {
        mServiceName = serviceName;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ServerSocket: Type: ");
        switch (mType) {
            case BluetoothSocket.TYPE_RFCOMM:
                sb.append("TYPE_RFCOMM");
                break;
            case BluetoothSocket.TYPE_L2CAP:
                sb.append("TYPE_L2CAP");
                break;
            case BluetoothSocket.TYPE_SCO:
                sb.append("TYPE_SCO");
                break;
            default:
                sb.append(mType);
                break;
        }
        sb.append(" Channel: ").append(mChannel);
        if (mServiceName != null) {
            sb.append(" ServiceName: ").append(mServiceName);
        }
        return sb.toString();
    }
}
