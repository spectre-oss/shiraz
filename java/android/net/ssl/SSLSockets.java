// SPDX-License-Identifier: GPL-3.0-only
package android.net.ssl;

import com.android.org.conscrypt.OpenSSLSocketImpl;
import java.util.Objects;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

/** Platform extensions backed by Shiraz's real TLS socket provider. */
public class SSLSockets {
    private SSLSockets() {
    }

    public static boolean isSupportedSocket(SSLSocket socket) {
        return socket instanceof OpenSSLSocketImpl;
    }

    public static void setUseSessionTickets(SSLSocket socket, boolean enabled) {
        if (!isSupportedSocket(socket)) {
            throw new IllegalArgumentException("Not a platform TLS socket");
        }
        ((OpenSSLSocketImpl) socket).setUseSessionTickets(enabled);
    }

    public static byte[] exportKeyingMaterial(
            SSLSocket socket, String label, byte[] context, int length) throws SSLException {
        if (!isSupportedSocket(socket)) {
            throw new IllegalArgumentException("Not a platform TLS socket");
        }
        Objects.requireNonNull(label, "label");
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative");
        }
        throw new SSLException("RFC 5705 keying material export is not supported by host TLS backend");
    }
}
