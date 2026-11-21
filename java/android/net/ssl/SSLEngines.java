// SPDX-License-Identifier: GPL-3.0-only
package android.net.ssl;

import java.util.Objects;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

/** Platform extensions for {@link SSLEngine} instances created by Shiraz's TLS provider. */
public class SSLEngines {
    private SSLEngines() {
    }

    public static boolean isSupportedEngine(SSLEngine engine) {
        return engine != null
                && "shiraz.runtime.HostTlsEngine".equals(engine.getClass().getName());
    }

    public static void setUseSessionTickets(SSLEngine engine, boolean useSessionTickets) {
        if (!isSupportedEngine(engine)) {
            throw new IllegalArgumentException("Engine is not a supported platform SSLEngine");
        }
    }

    public static byte[] exportKeyingMaterial(
            SSLEngine engine, String label, byte[] context, int length) throws SSLException {
        if (!isSupportedEngine(engine)) {
            throw new IllegalArgumentException("Engine is not a supported platform SSLEngine");
        }
        Objects.requireNonNull(label, "label");
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative");
        }
        throw new SSLException("RFC 5705 keying material export is not supported by host TLS backend");
    }
}
