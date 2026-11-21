// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike.exceptions;

import java.io.IOException;
import java.util.Objects;

public final class IkeIOException extends IkeNonProtocolException {
    public IkeIOException(IOException cause) {
        super(Objects.requireNonNull(cause, "cause must not be null"));
    }

    @Override
    public IOException getCause() {
        return (IOException) super.getCause();
    }

    @Override
    public synchronized Throwable initCause(Throwable cause) {
        throw new UnsupportedOperationException("It is not allowed to set cause with this method");
    }
}
