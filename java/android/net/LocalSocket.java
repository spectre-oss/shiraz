// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.system.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import libcore.io.IoBridge;
import libcore.io.IoUtils;
import libcore.io.Libcore;

/** Real AF_UNIX sockets. Darwin abstract names map into a private runtime directory. */
public class LocalSocket implements Closeable {
    public static final int SOCKET_DGRAM = 1, SOCKET_STREAM = 2, SOCKET_SEQPACKET = 3;
    private final int type;
    private FileDescriptor fd;
    private boolean closed, connected, bound, inputShutdown, outputShutdown;
    private LocalSocketAddress local, remote;
    private String ownedPath;
    private long ownedInode;
    public LocalSocket() { this(SOCKET_STREAM); }
    public LocalSocket(int type) {
        if (type != SOCKET_STREAM && type != SOCKET_DGRAM && type != SOCKET_SEQPACKET)
            throw new IllegalArgumentException("Unknown local socket type");
        this.type = type;
    }
    LocalSocket(FileDescriptor fd) { this.type = SOCKET_STREAM; this.fd = fd; connected = true; bound = true; }
    private synchronized FileDescriptor descriptor() throws IOException {
        if (closed) throw new IOException("Local socket is closed");
        if (fd == null) {
            if (type == SOCKET_SEQPACKET && shiraz.runtime.HostOs.darwin())
                throw new UnsupportedOperationException("Darwin does not support AF_UNIX SOCK_SEQPACKET");
            try {
                int nativeType = type == SOCKET_STREAM ? OsConstants.SOCK_STREAM
                    : type == SOCKET_DGRAM ? OsConstants.SOCK_DGRAM : OsConstants.SOCK_SEQPACKET;
                fd = Libcore.os.socket(OsConstants.AF_UNIX, nativeType, 0);
                Libcore.os.fcntlInt(fd, OsConstants.F_SETFD, OsConstants.FD_CLOEXEC);
            } catch (ErrnoException error) {
                if (fd != null) { try { IoBridge.closeAndSignalBlockedThreads(fd); } catch (IOException ignored) { } fd = null; }
                throw error.rethrowAsIOException();
            }
        }
        return fd;
    }
    private static String darwinAbstractPath(String name) throws IOException {
        String base = System.getenv("SHIRAZ_LOCAL_SOCKET_DIR");
        if (base == null || !new File(base).isAbsolute())
            throw new IOException("Darwin local sockets require SHIRAZ_LOCAL_SOCKET_DIR");
        try {
            try { Libcore.os.mkdir(base, 0700); }
            catch (ErrnoException error) { if (error.errno != OsConstants.EEXIST) throw error; }
            StructStat stat = Libcore.os.lstat(base);
            if (!OsConstants.S_ISDIR(stat.st_mode) || stat.st_uid != Libcore.os.getuid()
                    || (stat.st_mode & 0777) != 0700)
                throw new IOException("Local socket directory must be private and owned by the current user");
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(name.getBytes(StandardCharsets.UTF_8));
            StringBuilder leaf = new StringBuilder();
            for (int i = 0; i < 16; i++) leaf.append(String.format(java.util.Locale.ROOT, "%02x", hash[i] & 255));
            return base + "/" + leaf;
        } catch (ErrnoException error) { throw error.rethrowAsIOException(); }
        catch (NoSuchAlgorithmException error) { throw new IOException("SHA-256 unavailable for socket namespace", error); }
    }
    private static UnixSocketAddress address(LocalSocketAddress address) throws IOException {
        if (address == null) throw new NullPointerException("address");
        String name = address.getName();
        switch (address.getNamespace()) {
            case ABSTRACT:
                if (!shiraz.runtime.HostOs.darwin()) return UnixSocketAddress.createAbstract(name);
                name = darwinAbstractPath(name); break;
            case RESERVED:
                if (shiraz.runtime.HostOs.darwin())
                    throw new UnsupportedOperationException("Android reserved socket services are unavailable on Darwin");
                if (name.contains("/")) throw new IllegalArgumentException("Invalid reserved socket name");
                name = "/dev/socket/" + name; break;
            case FILESYSTEM: break;
            default: throw new IllegalArgumentException("namespace");
        }
        if (name.getBytes(StandardCharsets.UTF_8).length >= (shiraz.runtime.HostOs.darwin() ? 104 : 108))
            throw new IOException("Local socket path is too long");
        return UnixSocketAddress.createFileSystem(name);
    }
    public void bind(LocalSocketAddress address) throws IOException {
        UnixSocketAddress nativeAddress = address(address);
        synchronized (this) {
            if (bound) throw new IOException("Local socket is already bound");
            try {
                Libcore.os.bind(descriptor(), nativeAddress);
                bound = true; local = address;
                if (shiraz.runtime.HostOs.darwin() && address.getNamespace() == LocalSocketAddress.Namespace.ABSTRACT) {
                    byte[] path = nativeAddress.getSunPath();
                    ownedPath = new String(path, 0, path.length - 1, StandardCharsets.UTF_8);
                    ownedInode = Libcore.os.lstat(ownedPath).st_ino;
                }
            } catch (ErrnoException error) { throw error.rethrowAsIOException(); }
        }
    }
    public void connect(LocalSocketAddress address) throws IOException { connect(address, 0); }
    public void connect(LocalSocketAddress address, int timeout) throws IOException {
        if (timeout < 0) throw new IllegalArgumentException("Negative connect timeout");
        UnixSocketAddress target = address(address);
        FileDescriptor socket = descriptor();
        synchronized (this) { if (connected) throw new IOException("Local socket is already connected"); }
        try {
            if (timeout == 0) Libcore.os.connect(socket, target);
            else {
                IoUtils.setBlocking(socket, false);
                try {
                    try { Libcore.os.connect(socket, target); }
                    catch (ErrnoException error) {
                        if (error.errno != OsConstants.EINPROGRESS && error.errno != OsConstants.EAGAIN) throw error;
                        StructPollfd poll = new StructPollfd(); poll.fd = socket; poll.events = (short) OsConstants.POLLOUT;
                        if (Libcore.os.poll(new StructPollfd[] {poll}, timeout) == 0)
                            throw new SocketTimeoutException("Local socket connect timeout");
                        int code = Libcore.os.getsockoptInt(socket, OsConstants.SOL_SOCKET, OsConstants.SO_ERROR);
                        if (code != 0) throw new ErrnoException("connect", code);
                    }
                } finally { IoUtils.setBlocking(socket, true); }
            }
            synchronized (this) { connected = true; remote = address; }
        } catch (ErrnoException error) { close(); throw error.rethrowAsIOException(); }
        catch (IOException error) { close(); throw error; }
    }
    void listen() throws IOException {
        try { Libcore.os.listen(descriptor(), 50); }
        catch (ErrnoException error) { throw error.rethrowAsIOException(); }
    }
    LocalSocket accept() throws IOException {
        try {
            FileDescriptor accepted = Libcore.os.accept(descriptor(), null);
            try { Libcore.os.fcntlInt(accepted, OsConstants.F_SETFD, OsConstants.FD_CLOEXEC); }
            catch (ErrnoException error) { IoBridge.closeAndSignalBlockedThreads(accepted); throw error; }
            return new LocalSocket(accepted);
        } catch (ErrnoException error) { throw error.rethrowAsIOException(); }
    }
    private void requireConnected() throws IOException {
        synchronized (this) { if (closed || !connected) throw new IOException("Local socket is not connected"); }
    }
    public InputStream getInputStream() throws IOException {
        requireConnected();
        return new InputStream() {
            public int read() throws IOException { byte[] one = new byte[1]; return read(one, 0, 1) < 0 ? -1 : one[0] & 255; }
            public int read(byte[] bytes, int offset, int count) throws IOException {
                if (offset < 0 || count < 0 || offset > bytes.length - count) throw new IndexOutOfBoundsException();
                FileDescriptor socket = descriptor();
                if (count == 0) return 0;
                try { int n = Libcore.os.read(socket, bytes, offset, count); return n == 0 ? -1 : n; }
                catch (ErrnoException error) {
                    if (error.errno == OsConstants.EAGAIN) throw new SocketTimeoutException("Local socket read timeout");
                    throw error.rethrowAsIOException();
                }
            }
            public int available() throws IOException { return IoBridge.available(descriptor()); }
            public void close() throws IOException { LocalSocket.this.close(); }
        };
    }
    public OutputStream getOutputStream() throws IOException {
        requireConnected();
        return new OutputStream() {
            public void write(int value) throws IOException { write(new byte[] {(byte)value}, 0, 1); }
            public void write(byte[] bytes, int offset, int count) throws IOException { IoBridge.write(descriptor(), bytes, offset, count); }
            public void close() throws IOException { LocalSocket.this.close(); }
        };
    }
    public void setSoTimeout(int timeout) throws IOException {
        if (timeout < 0) throw new IllegalArgumentException("Negative timeout");
        try {
            Libcore.os.setsockoptTimeval(descriptor(), OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO, StructTimeval.fromMillis(timeout));
            Libcore.os.setsockoptTimeval(descriptor(), OsConstants.SOL_SOCKET, OsConstants.SO_SNDTIMEO, StructTimeval.fromMillis(timeout));
        }
        catch (ErrnoException error) { throw error.rethrowAsIOException(); }
    }
    public int getSoTimeout() throws IOException {
        try { return (int) Libcore.os.getsockoptTimeval(descriptor(), OsConstants.SOL_SOCKET, OsConstants.SO_RCVTIMEO).toMillis(); }
        catch (ErrnoException error) { throw error.rethrowAsIOException(); }
    }
    private void option(int option, int size) throws IOException {
        if (size <= 0) throw new IllegalArgumentException("Socket buffer size");
        try { Libcore.os.setsockoptInt(descriptor(), OsConstants.SOL_SOCKET, option, size); }
        catch (ErrnoException error) { throw error.rethrowAsIOException(); }
    }
    private int option(int option) throws IOException {
        try { return Libcore.os.getsockoptInt(descriptor(), OsConstants.SOL_SOCKET, option); }
        catch (ErrnoException error) { throw error.rethrowAsIOException(); }
    }
    public void setReceiveBufferSize(int size) throws IOException { option(OsConstants.SO_RCVBUF, size); }
    public int getReceiveBufferSize() throws IOException { return option(OsConstants.SO_RCVBUF); }
    public void setSendBufferSize(int size) throws IOException { option(OsConstants.SO_SNDBUF, size); }
    public int getSendBufferSize() throws IOException { return option(OsConstants.SO_SNDBUF); }
    public Credentials getPeerCredentials() throws IOException {
        try { StructUcred peer = Libcore.os.getsockoptUcred(descriptor(), OsConstants.SOL_SOCKET, OsConstants.SO_PEERCRED); return new Credentials(peer.pid, peer.uid, peer.gid); }
        catch (ErrnoException error) { throw error.rethrowAsIOException(); }
    }
    public void shutdownInput() throws IOException { shutdown(OsConstants.SHUT_RD); synchronized (this) { inputShutdown = true; } }
    public void shutdownOutput() throws IOException { shutdown(OsConstants.SHUT_WR); synchronized (this) { outputShutdown = true; } }
    private void shutdown(int how) throws IOException {
        try { Libcore.os.shutdown(descriptor(), how); }
        catch (ErrnoException error) { throw error.rethrowAsIOException(); }
    }
    public synchronized boolean isConnected() { return connected; }
    public synchronized boolean isBound() { return bound; }
    public synchronized boolean isClosed() { return closed; }
    public synchronized boolean isInputShutdown() { return inputShutdown; }
    public synchronized boolean isOutputShutdown() { return outputShutdown; }
    public synchronized LocalSocketAddress getLocalSocketAddress() { return local; }
    public synchronized LocalSocketAddress getRemoteSocketAddress() { return remote; }
    public synchronized FileDescriptor getFileDescriptor() { return fd; }
    public FileDescriptor[] getAncillaryFileDescriptors() throws IOException { throw new UnsupportedOperationException("Local socket descriptor passing is not implemented"); }
    public void setFileDescriptorsForSend(FileDescriptor[] descriptors) { throw new UnsupportedOperationException("Local socket descriptor passing is not implemented"); }
    public void close() throws IOException {
        FileDescriptor socket;
        String path;
        long inode;
        synchronized (this) {
            if (closed) return;
            closed = true; socket = fd; path = ownedPath; inode = ownedInode;
        }
        try { if (socket != null) IoBridge.closeAndSignalBlockedThreads(socket); }
        finally {
            if (path != null) try {
                if (Libcore.os.lstat(path).st_ino == inode) Libcore.os.unlink(path);
            } catch (ErrnoException error) { if (error.errno != OsConstants.ENOENT) throw error.rethrowAsIOException(); }
        }
    }
}
