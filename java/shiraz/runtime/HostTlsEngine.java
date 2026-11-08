// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.nio.ByteBuffer;
import javax.net.ssl.*;

/** Nonblocking wolfJSSE engine with the same explicit HTTPS policy as our sockets. */
final class HostTlsEngine extends SSLEngine {
    private final SSLEngine delegate;
    private String endpoint;
    private boolean verified;
    private SSLException failure;
    HostTlsEngine(SSLEngine delegate, String host, int port) {
        super(host, port);
        this.delegate = delegate;
    }
    private void healthy() throws SSLException { if (failure != null) throw failure; }
    private void verify(SSLEngineResult result) throws SSLException {
        if (verified || result.getStatus() == SSLEngineResult.Status.CLOSED) return;
        if (result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.FINISHED) return;
        try {
            if (getUseClientMode() && endpoint != null && !endpoint.isEmpty()) {
                if (getPeerHost() == null) throw new SSLHandshakeException("HTTPS endpoint requires a peer hostname");
                HostnameVerifier verifier = (HostnameVerifier) Class.forName(
                    "com.android.okhttp.internal.tls.OkHostnameVerifier").getField("INSTANCE").get(null);
                if (!verifier.verify(getPeerHost(), delegate.getSession()))
                    throw new SSLHandshakeException("TLS engine endpoint hostname mismatch");
            }
            verified = true;
        } catch (ReflectiveOperationException error) {
            throw new SSLException("Pinned hostname verifier unavailable", error);
        }
    }
    private SSLException failed(SSLException error) {
        failure = error;
        try { delegate.getSession().invalidate(); }
        catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
        try { delegate.closeOutbound(); }
        catch (RuntimeException cleanup) { error.addSuppressed(cleanup); }
        return error;
    }
    public SSLEngineResult wrap(ByteBuffer[] src, int offset, int length, ByteBuffer dst)
            throws SSLException {
        healthy();
        int position = dst.position();
        try {
            SSLEngineResult result = delegate.wrap(src, offset, length, dst);
            verify(result);
            return result;
        } catch (SSLException error) { dst.position(position); throw failed(error); }
    }
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dst, int offset, int length)
            throws SSLException {
        healthy();
        if (src == null || dst == null) throw new IllegalArgumentException("Null TLS buffer");
        if (offset < 0 || length < 0 || offset > dst.length - length)
            throw new IndexOutOfBoundsException("TLS output buffer range");
        for (int i = 0; i < length; i++)
            if (dst[offset + i] == null) throw new IllegalArgumentException("Null TLS output buffer");
        int[] positions = new int[length];
        for (int i = 0; i < length; i++) positions[i] = dst[offset + i].position();
        try {
            SSLEngineResult result = delegate.unwrap(src, dst, offset, length);
            verify(result);
            return result;
        } catch (SSLException error) {
            for (int i = 0; i < length; i++) dst[offset + i].position(positions[i]);
            throw failed(error);
        }
    }
    public SSLParameters getSSLParameters() {
        SSLParameters p = delegate.getSSLParameters();
        p.setEndpointIdentificationAlgorithm(endpoint);
        return p;
    }
    public void setSSLParameters(SSLParameters p) {
        if (verified || failure != null) throw new IllegalStateException("TLS engine policy is closed");
        String algorithm = p.getEndpointIdentificationAlgorithm();
        if (algorithm != null && !algorithm.isEmpty() && !algorithm.equalsIgnoreCase("HTTPS"))
            throw new UnsupportedOperationException("Only HTTPS endpoint identification is implemented");
        if (p.getServerNames() != null || p.getSNIMatchers() != null || p.getAlgorithmConstraints() != null)
            throw new UnsupportedOperationException("Custom SNI/algorithm constraints are not implemented");
        delegate.setSSLParameters(p);
        endpoint = algorithm;
    }
    public void beginHandshake() throws SSLException {
        healthy();
        if (verified) throw new SSLException("TLS engine renegotiation is not supported");
        delegate.beginHandshake();
    }
    public Runnable getDelegatedTask() { return delegate.getDelegatedTask(); }
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        // FINISHED belongs only in wrap/unwrap results, never this query.
        SSLEngineResult.HandshakeStatus status = delegate.getHandshakeStatus();
        return status == SSLEngineResult.HandshakeStatus.FINISHED
            ? SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING : status;
    }
    public SSLSession getSession() { return delegate.getSession(); }
    public void closeInbound() throws SSLException { delegate.closeInbound(); }
    public void closeOutbound() { delegate.closeOutbound(); }
    public boolean isInboundDone() { return delegate.isInboundDone(); }
    public boolean isOutboundDone() { return delegate.isOutboundDone(); }
    public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
    public String[] getEnabledCipherSuites() { return delegate.getEnabledCipherSuites(); }
    public void setEnabledCipherSuites(String[] value) { delegate.setEnabledCipherSuites(value); }
    public String[] getSupportedProtocols() { return delegate.getSupportedProtocols(); }
    public String[] getEnabledProtocols() { return delegate.getEnabledProtocols(); }
    public void setEnabledProtocols(String[] value) { delegate.setEnabledProtocols(value); }
    public boolean getUseClientMode() { return delegate.getUseClientMode(); }
    public void setUseClientMode(boolean value) { delegate.setUseClientMode(value); }
    public boolean getNeedClientAuth() { return delegate.getNeedClientAuth(); }
    public void setNeedClientAuth(boolean value) { delegate.setNeedClientAuth(value); }
    public boolean getWantClientAuth() { return delegate.getWantClientAuth(); }
    public void setWantClientAuth(boolean value) { delegate.setWantClientAuth(value); }
    public boolean getEnableSessionCreation() { return delegate.getEnableSessionCreation(); }
    public void setEnableSessionCreation(boolean value) { delegate.setEnableSessionCreation(value); }
}
