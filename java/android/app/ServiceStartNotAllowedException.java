// SPDX-License-Identifier: GPL-3.0-only
package android.app;

public abstract class ServiceStartNotAllowedException extends IllegalStateException {
    ServiceStartNotAllowedException(String message) {
        super(message);
    }

    @Override
    public synchronized Throwable getCause() {
        return null;
    }
}
