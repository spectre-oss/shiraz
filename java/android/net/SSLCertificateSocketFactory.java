// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.net.ssl.SSLSockets;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** Legacy SSLSocketFactory with configurable handshake timeout and trust/key managers. */
@Deprecated
public class SSLCertificateSocketFactory extends SSLSocketFactory {
    private static final TrustManager[] INSECURE_TRUST_MANAGER =
            new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };

    private final int mHandshakeTimeoutMillis;
    private final boolean mSecure;
    private TrustManager[] mTrustManagers;
    private KeyManager[] mKeyManagers;
    private byte[][] mNpnProtocols;
    private SSLSocketFactory mSecureFactory;
    private SSLSocketFactory mInsecureFactory;

    @Deprecated
    public SSLCertificateSocketFactory(int handshakeTimeoutMillis) {
        this(handshakeTimeoutMillis, null, true);
    }

    private SSLCertificateSocketFactory(
            int handshakeTimeoutMillis, SSLSessionCache cache, boolean secure) {
        this.mHandshakeTimeoutMillis = handshakeTimeoutMillis;
        this.mSecure = secure;
    }

    public static SocketFactory getDefault(int handshakeTimeoutMillis) {
        return new SSLCertificateSocketFactory(handshakeTimeoutMillis, null, true);
    }

    public static SSLSocketFactory getDefault(int handshakeTimeoutMillis, SSLSessionCache cache) {
        return new SSLCertificateSocketFactory(handshakeTimeoutMillis, cache, true);
    }

    public static SSLSocketFactory getInsecure(int handshakeTimeoutMillis, SSLSessionCache cache) {
        return new SSLCertificateSocketFactory(handshakeTimeoutMillis, cache, false);
    }

    private synchronized SSLSocketFactory getDelegate() {
        if (!mSecure) {
            if (mInsecureFactory == null) {
                mInsecureFactory = makeSocketFactory(mKeyManagers, INSECURE_TRUST_MANAGER);
            }
            return mInsecureFactory;
        }
        if (mSecureFactory == null) {
            mSecureFactory = makeSocketFactory(mKeyManagers, mTrustManagers);
        }
        return mSecureFactory;
    }

    private static SSLSocketFactory makeSocketFactory(
            KeyManager[] keyManagers, TrustManager[] trustManagers) {
        try {
            if (keyManagers == null && trustManagers == null) {
                return (SSLSocketFactory) SSLSocketFactory.getDefault();
            }
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(keyManagers, trustManagers, new SecureRandom());
            return ctx.getSocketFactory();
        } catch (GeneralSecurityException e) {
            return (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
    }

    public synchronized void setTrustManagers(TrustManager[] trustManager) {
        mTrustManagers = trustManager;
        mSecureFactory = null;
    }

    public void setNpnProtocols(byte[][] npnProtocols) {
        this.mNpnProtocols = npnProtocols;
    }

    public byte[] getNpnSelectedProtocol(Socket socket) {
        return null;
    }

    public synchronized void setKeyManagers(KeyManager[] keyManagers) {
        mKeyManagers = keyManagers;
        mSecureFactory = null;
        mInsecureFactory = null;
    }

    public void setUseSessionTickets(Socket socket, boolean useSessionTickets) {
        if (socket instanceof SSLSocket && SSLSockets.isSupportedSocket((SSLSocket) socket)) {
            SSLSockets.setUseSessionTickets((SSLSocket) socket, useSessionTickets);
        }
    }

    public void setHostname(Socket socket, String hostName) {
        if (socket instanceof SSLSocket && hostName != null) {
            SSLSocket ssl = (SSLSocket) socket;
            SSLParameters params = ssl.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            ssl.setSSLParameters(params);
        }
    }

    private Socket configureSocket(Socket s) throws IOException {
        if (s != null && mHandshakeTimeoutMillis > 0 && s.getSoTimeout() == 0) {
            s.setSoTimeout(mHandshakeTimeoutMillis);
        }
        return s;
    }

    @Override
    public Socket createSocket(Socket k, String host, int port, boolean close) throws IOException {
        Socket s = getDelegate().createSocket(k, host, port, close);
        if (mSecure && s instanceof SSLSocket) {
            setHostname(s, host);
        }
        return configureSocket(s);
    }

    @Override
    public Socket createSocket() throws IOException {
        return configureSocket(getDelegate().createSocket());
    }

    @Override
    public Socket createSocket(InetAddress addr, int port, InetAddress localAddr, int localPort)
            throws IOException {
        return configureSocket(getDelegate().createSocket(addr, port, localAddr, localPort));
    }

    @Override
    public Socket createSocket(InetAddress addr, int port) throws IOException {
        return configureSocket(getDelegate().createSocket(addr, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localAddr, int localPort)
            throws IOException {
        Socket s = getDelegate().createSocket(host, port, localAddr, localPort);
        if (mSecure) {
            setHostname(s, host);
        }
        return configureSocket(s);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket s = getDelegate().createSocket(host, port);
        if (mSecure) {
            setHostname(s, host);
        }
        return configureSocket(s);
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return getDelegate().getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return getDelegate().getSupportedCipherSuites();
    }
}
