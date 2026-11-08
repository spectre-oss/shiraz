// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import java.io.*;
import android.system.ErrnoException;
import libcore.io.Libcore;

/** AF_UNIX listening socket; closing it releases only its owned descriptor/name. */
public class LocalServerSocket implements Closeable {
    private final LocalSocket socket;
    public LocalServerSocket(String name) throws IOException {
        socket = new LocalSocket();
        try { socket.bind(new LocalSocketAddress(name)); socket.listen(); }
        catch (IOException | RuntimeException error) { socket.close(); throw error; }
    }
    public LocalServerSocket(FileDescriptor descriptor) throws IOException {
        try { socket = new LocalSocket(Libcore.os.dup(descriptor)); }
        catch (ErrnoException error) { throw error.rethrowAsIOException(); }
        try { socket.listen(); }
        catch (IOException | RuntimeException error) { socket.close(); throw error; }
    }
    public LocalSocket accept() throws IOException { return socket.accept(); }
    public FileDescriptor getFileDescriptor() { return socket.getFileDescriptor(); }
    public LocalSocketAddress getLocalSocketAddress() { return socket.getLocalSocketAddress(); }
    public void close() throws IOException { socket.close(); }
}
