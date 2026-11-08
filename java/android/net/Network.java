// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.*;
import javax.net.SocketFactory;

/**
 * A network identity. Shiraz exposes only the host's current default route;
 * alternate network identities never fall back to that route. There is no netd
 * or per-interface/process routing namespace behind this adapter.
 */
public class Network implements Parcelable {
    private static final long HANDLE_MAGIC = 0xcafed00dL;
    private final int netId;
    /** Host adapter constructor; the argument is a network ID, not an NDK handle. */
    public Network(long netId) {
        if (netId < Integer.MIN_VALUE || netId > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Network ID exceeds 32 bits");
        this.netId = (int) netId;
    }
    private boolean hostDefault() { return netId == 100; }
    private void requireDefault() throws SocketException {
        if (!hostDefault()) throw new SocketException("Network is unavailable on this host: " + netId);
    }
    public InetAddress[] getAllByName(String host) throws UnknownHostException {
        if (!hostDefault()) throw new UnknownHostException("Network is unavailable on this host: " + netId);
        return InetAddress.getAllByName(host);
    }
    public InetAddress getByName(String host) throws UnknownHostException { return getAllByName(host)[0]; }
    public SocketFactory getSocketFactory() {
        return new SocketFactory() {
            public Socket createSocket() throws IOException { requireDefault(); return SocketFactory.getDefault().createSocket(); }
            public Socket createSocket(String host, int port) throws IOException {
                requireDefault(); return SocketFactory.getDefault().createSocket(host, port);
            }
            public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
                requireDefault(); return SocketFactory.getDefault().createSocket(host, port, local, localPort);
            }
            public Socket createSocket(InetAddress host, int port) throws IOException {
                requireDefault(); return SocketFactory.getDefault().createSocket(host, port);
            }
            public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
                requireDefault(); return SocketFactory.getDefault().createSocket(host, port, local, localPort);
            }
        };
    }
    public URLConnection openConnection(URL url) throws IOException {
        requireDefault(); requireHttp(url);
        return url.openConnection();
    }
    public URLConnection openConnection(URL url, java.net.Proxy proxy) throws IOException {
        if (proxy == null) throw new IllegalArgumentException("proxy must not be null");
        requireDefault(); requireHttp(url);
        return url.openConnection(proxy);
    }
    private static void requireHttp(URL url) throws MalformedURLException {
        String protocol = url.getProtocol();
        if (!protocol.equals("http") && !protocol.equals("https"))
            throw new MalformedURLException("Network URL protocol must be HTTP or HTTPS");
    }
    /** The only represented network is already the kernel default for all host sockets. */
    public void bindSocket(Socket socket) throws IOException {
        requireDefault();
        socket.getReuseAddress(); // Materialize/check the fd without connecting it.
        if (socket.isConnected()) throw new SocketException("Socket is already connected");
    }
    public void bindSocket(DatagramSocket socket) throws IOException {
        requireDefault();
        socket.getReuseAddress();
        if (socket.isConnected()) throw new SocketException("Datagram socket is already connected");
    }
    public void bindSocket(FileDescriptor fd) throws IOException {
        requireDefault();
        if (fd == null || !fd.valid()) throw new SocketException("Invalid socket descriptor");
        try {
            if (!(Os.getsockname(fd) instanceof InetSocketAddress))
                throw new SocketException("Only AF_INET/AF_INET6 sockets are supported");
            try {
                SocketAddress peer = Os.getpeername(fd);
                if (!(peer instanceof InetSocketAddress)
                        || !((InetSocketAddress)peer).getAddress().isAnyLocalAddress())
                    throw new SocketException("Socket is already connected");
            } catch (ErrnoException error) {
                if (error.errno != OsConstants.ENOTCONN) throw error;
            }
        } catch (ErrnoException error) { throw error.rethrowAsIOException(); }
    }
    // Encoding used by AOSP's public fromNetworkHandle API (Android 9+).
    public long getNetworkHandle() { return ((long)netId << 32) | HANDLE_MAGIC; }
    public static Network fromNetworkHandle(long handle) {
        if (handle < 0 || (handle & 0xffffffffL) != HANDLE_MAGIC)
            throw new IllegalArgumentException("Not a network handle");
        return new Network(handle >>> 32);
    }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel destination, int flags) { destination.writeInt(netId); }
    public static final Parcelable.Creator<Network> CREATOR = new Parcelable.Creator<Network>() {
        public Network createFromParcel(Parcel source) { return new Network(source.readInt()); }
        public Network[] newArray(int size) { return new Network[size]; }
    };
    @Override public boolean equals(Object other) { return other instanceof Network && ((Network)other).netId == netId; }
    @Override public int hashCode() { return netId; }
    @Override public String toString() { return Integer.toString(netId); }
}
