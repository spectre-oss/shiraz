// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import android.net.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;

/** Local-socket semantics, with bounded waits and no public-network dependency. */
final class LocalSocketChecks {
    static String name() { return "shiraz-probe-" + android.os.Process.myPid() + "-" + System.nanoTime(); }
    static String run(String id) throws Exception {
        if (!java.util.Arrays.asList("network.local_stream", "network.local_timeout", "network.local_rebind",
                "network.local_close_read", "network.local_close_accept").contains(id))
            throw new IllegalArgumentException("Unknown local socket case: " + id);
        String name = name();
        if (id.equals("network.local_close_accept")) {
            try (LocalServerSocket server = new LocalServerSocket(name)) {
                CountDownLatch entered = new CountDownLatch(1);
                FutureTask<Void> accept = new FutureTask<>(() -> {
                    entered.countDown();
                    try (LocalSocket unexpected = server.accept()) {
                        throw new AssertionError("accept succeeded without a client");
                    } catch (IOException expected) { }
                    return null;
                });
                Thread thread = new Thread(accept, "probe-local-accept"); thread.setDaemon(true); thread.start();
                NetworkChecks.require(entered.await(1, TimeUnit.SECONDS), "local accept did not enter");
                Thread.sleep(30); server.close();
                accept.get(1500, TimeUnit.MILLISECONDS); thread.join(1000);
                NetworkChecks.require(!thread.isAlive(), "closed local listener remains blocked");
            }
            return "Closing local listener wakes a blocked accept";
        }
        if (id.equals("network.local_rebind")) {
            try (LocalServerSocket server = new LocalServerSocket(name)) {
                boolean rejected = false;
                try (LocalServerSocket duplicate = new LocalServerSocket(name)) { }
                catch (IOException expected) { rejected = true; }
                NetworkChecks.require(rejected, "active local socket name overwritten");
                try (LocalSocket client = new LocalSocket()) {
                    client.connect(new LocalSocketAddress(name), 1000);
                    try (LocalSocket peer = server.accept()) {
                        client.getOutputStream().write(42);
                        peer.setSoTimeout(1000);
                        NetworkChecks.require(peer.getInputStream().read() == 42, "original server lost name after collision");
                    }
                }
            }
            try (LocalServerSocket again = new LocalServerSocket(name)) {
                NetworkChecks.require(again.getFileDescriptor().valid(), "released abstract name cannot rebind");
            }
            return "Active name collisions fail without replacing server; close permits rebind";
        }
        try (LocalServerSocket server = new LocalServerSocket(name);
             LocalSocket client = new LocalSocket()) {
            client.connect(new LocalSocketAddress(name), 1000);
            try (LocalSocket peer = server.accept()) {
                client.setSoTimeout(1000); peer.setSoTimeout(1000);
                NetworkChecks.require(client.isConnected() && client.isBound() == false, "local client state");
                if (id.equals("network.local_timeout")) {
                    peer.setSoTimeout(50);
                    NetworkChecks.require(peer.getSoTimeout() > 0, "local read timeout setting");
                    try { peer.getInputStream().read(); throw new AssertionError("silent local peer did not time out"); }
                    catch (SocketTimeoutException expected) { }
                    return "Local socket read timeout raises SocketTimeoutException";
                }
                if (id.equals("network.local_close_read")) {
                    peer.setSoTimeout(0);
                    CountDownLatch entered = new CountDownLatch(1);
                    FutureTask<Void> read = new FutureTask<>(() -> {
                        entered.countDown();
                        try { peer.getInputStream().read(); }
                        catch (IOException expected) { }
                        return null;
                    });
                    Thread thread = new Thread(read, "probe-local-reader"); thread.setDaemon(true); thread.start();
                    NetworkChecks.require(entered.await(1, TimeUnit.SECONDS), "local reader did not enter");
                    Thread.sleep(30); peer.close();
                    read.get(1500, TimeUnit.MILLISECONDS);
                    thread.join(1000);
                    NetworkChecks.require(!thread.isAlive(), "closed local socket reader remains blocked");
                    return "Closing local socket wakes a blocked read";
                }
                NetworkChecks.require(peer.getPeerCredentials().getPid() == android.os.Process.myPid(), "local peer PID");
                client.setSendBufferSize(4096); peer.setReceiveBufferSize(4096);
                NetworkChecks.require(client.getSendBufferSize() > 0 && peer.getReceiveBufferSize() > 0, "local buffer settings");
                client.getOutputStream().write(new byte[] {7, 19, 42}); client.shutdownOutput();
                NetworkChecks.require(client.isOutputShutdown(), "local half close state");
                InputStream in = peer.getInputStream();
                NetworkChecks.require(in.read() == 7 && in.read() == 19 && in.read() == 42 && in.read() == -1, "local payload/EOF");
                peer.getOutputStream().write(43);
                NetworkChecks.require(client.getInputStream().read() == 43, "local reply after output shutdown");
            }
        }
        return "Local stream verifies peer identity, buffer settings, half-close, payload and EOF";
    }
}
