// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import java.nio.ByteBuffer;
import javax.net.ssl.*;

/** In-memory TLS transport: no blocking socket can hide a broken engine implementation. */
final class TlsEngineChecks {
    static final ByteBuffer EMPTY = ByteBuffer.allocate(0);
    static void tasks(SSLEngine engine) {
        for (Runnable task; (task = engine.getDelegatedTask()) != null;) task.run();
    }
    static void wrap(SSLEngine engine, ByteBuffer wire) throws SSLException {
        if (engine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            SSLEngineResult r = engine.wrap(EMPTY.duplicate(), wire);
            NetworkChecks.require(r.getStatus() != SSLEngineResult.Status.BUFFER_OVERFLOW, "TLS packet capacity");
        }
    }
    static void unwrap(SSLEngine engine, ByteBuffer wire, ByteBuffer plain) throws SSLException {
        if (wire.position() == 0) return;
        wire.flip();
        try {
            while (wire.hasRemaining()) {
                SSLEngineResult r = engine.unwrap(wire, plain);
                NetworkChecks.require(r.getStatus() != SSLEngineResult.Status.BUFFER_OVERFLOW, "TLS plaintext capacity");
                if (r.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW
                        || r.getStatus() == SSLEngineResult.Status.CLOSED
                        || (r.bytesConsumed() == 0 && r.bytesProduced() == 0)) break;
            }
        } finally { wire.compact(); }
    }
    static String run(SSLContext clientContext, SSLContext serverContext, String host,
            boolean reject) throws Exception {
        String result = runProtocol(clientContext, serverContext, host, reject, "TLSv1.2");
        if (java.util.Arrays.asList(clientContext.getSupportedSSLParameters().getProtocols()).contains("TLSv1.3")) {
            runProtocol(clientContext, serverContext, host, reject, "TLSv1.3");
            return result + "; verified TLSv1.2 and TLSv1.3";
        }
        return result + "; verified TLSv1.2 (TLSv1.3 unavailable)";
    }
    private static String runProtocol(SSLContext clientContext, SSLContext serverContext, String host,
            boolean reject, String protocol) throws Exception {
        SSLEngine client = clientContext.createSSLEngine(host, 443);
        SSLEngine server = serverContext.createSSLEngine();
        client.setUseClientMode(true); server.setUseClientMode(false);
        // Keep this deterministic across host JVMs and the pinned TLS backend.
        client.setEnabledProtocols(new String[] {protocol});
        server.setEnabledProtocols(new String[] {protocol});
        SSLParameters parameters = client.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        client.setSSLParameters(parameters);
        ByteBuffer toServer = ByteBuffer.allocate(65536), toClient = ByteBuffer.allocate(65536);
        ByteBuffer clientPlain = ByteBuffer.allocate(32768), serverPlain = ByteBuffer.allocate(32768);
        boolean clientRejected = false, complete = false;
        try {
            client.beginHandshake(); server.beginHandshake();
            long deadline = System.nanoTime() + 4000000000L;
            for (int i = 0; i < 512 && System.nanoTime() < deadline; i++) {
                try {
                    tasks(client); wrap(client, toServer); unwrap(client, toClient, clientPlain);
                } catch (SSLException expected) {
                    if (!reject) throw expected;
                    clientRejected = true;
                    break;
                }
                tasks(server); unwrap(server, toServer, serverPlain); tasks(server); wrap(server, toClient);
                if (client.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
                        && server.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                    complete = true;
                    break;
                }
            }
            NetworkChecks.require(clientRejected == reject, "engine certificate rejection result");
            if (reject) {
                NetworkChecks.require(serverPlain.position() == 0, "rejected engine sent application bytes");
                NetworkChecks.require(!client.getSession().isValid(), "failed TLS engine retained a valid session");
                return "TLS client engine rejects invalid peer before application bytes";
            }
            NetworkChecks.require(complete, "engine handshake did not complete within bound: client=" + client.getHandshakeStatus() + " server=" + server.getHandshakeStatus() + " toClient=" + toClient.position() + " toServer=" + toServer.position());
            ByteBuffer request = ByteBuffer.wrap("probe".getBytes("US-ASCII"));
            SSLEngineResult wrapped = client.wrap(request, toServer);
            NetworkChecks.require(wrapped.bytesConsumed() == 5, "engine consumed application request");
            unwrap(server, toServer, serverPlain);
            serverPlain.flip();
            byte[] bytes = new byte[serverPlain.remaining()]; serverPlain.get(bytes);
            NetworkChecks.require("probe".equals(new String(bytes, "US-ASCII")), "decrypted engine payload");
            ByteBuffer reply = ByteBuffer.wrap("reply".getBytes("US-ASCII"));
            NetworkChecks.require(server.wrap(reply, toClient).bytesConsumed() == 5, "engine reply consumed");
            unwrap(client, toClient, clientPlain);
            clientPlain.flip(); bytes = new byte[clientPlain.remaining()]; clientPlain.get(bytes);
            NetworkChecks.require("reply".equals(new String(bytes, "US-ASCII")), "engine reply decrypted");
            return "TLS engines handshake, verify peer and exchange encrypted request/reply";
        } finally {
            client.closeOutbound(); server.closeOutbound();
        }
    }
}
