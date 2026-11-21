// SPDX-License-Identifier: GPL-3.0-only
package android.net.rtp;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.SocketException;

public class RtpStream {
    public static final int MODE_NORMAL = 0;
    public static final int MODE_SEND_ONLY = 1;
    public static final int MODE_RECEIVE_ONLY = 2;

    private final InetAddress mLocalAddress;
    private final int mLocalPort;
    private InetAddress mRemoteAddress;
    private int mRemotePort = -1;
    private int mMode = MODE_NORMAL;
    private DatagramSocket mSocket;

    RtpStream(InetAddress address) throws SocketException {
        if (address == null) {
            throw new NullPointerException("address");
        }
        DatagramSocket sock = new DatagramSocket(0, address);
        if ((sock.getLocalPort() & 1) != 0) {
            DatagramSocket retry = new DatagramSocket(0, address);
            sock.close();
            sock = retry;
        }
        this.mSocket = sock;
        this.mLocalAddress = sock.getLocalAddress();
        this.mLocalPort = sock.getLocalPort();
    }

    public InetAddress getLocalAddress() {
        return mLocalAddress;
    }

    public int getLocalPort() {
        return mLocalPort;
    }

    public InetAddress getRemoteAddress() {
        return mRemoteAddress;
    }

    public int getRemotePort() {
        return mRemotePort;
    }

    public boolean isBusy() {
        return false;
    }

    public int getMode() {
        return mMode;
    }

    public void setMode(int mode) {
        if (isBusy()) {
            throw new IllegalStateException("Busy");
        }
        if (mode < MODE_NORMAL || mode > MODE_RECEIVE_ONLY) {
            throw new IllegalArgumentException("Invalid mode");
        }
        this.mMode = mode;
    }

    public void associate(InetAddress address, int port) {
        if (isBusy()) {
            throw new IllegalStateException("Busy");
        }
        if (!(address instanceof Inet4Address && mLocalAddress instanceof Inet4Address)
                && !(address instanceof Inet6Address && mLocalAddress instanceof Inet6Address)) {
            throw new IllegalArgumentException("Unsupported address");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port");
        }
        this.mRemoteAddress = address;
        this.mRemotePort = port;
    }

    public synchronized void release() {
        if (isBusy()) {
            throw new IllegalStateException("Busy");
        }
        if (mSocket != null) {
            mSocket.close();
            mSocket = null;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (mSocket != null) {
            mSocket.close();
            mSocket = null;
        }
        super.finalize();
    }
}
