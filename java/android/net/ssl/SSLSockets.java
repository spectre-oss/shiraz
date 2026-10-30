// SPDX-License-Identifier: GPL-3.0-only
package android.net.ssl;

import javax.net.ssl.SSLSocket;
import com.android.org.conscrypt.OpenSSLSocketImpl;

/** Platform extensions backed by Shiraz's real TLS socket provider. */
public final class SSLSockets {
    private SSLSockets() {}
    public static boolean isSupportedSocket(SSLSocket socket) {
        return socket instanceof OpenSSLSocketImpl;
    }
    public static void setUseSessionTickets(SSLSocket socket, boolean enabled) {
        if (!isSupportedSocket(socket)) throw new IllegalArgumentException("Not a platform TLS socket");
        ((OpenSSLSocketImpl) socket).setUseSessionTickets(enabled);
    }
}
