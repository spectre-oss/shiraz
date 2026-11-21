// SPDX-License-Identifier: GPL-3.0-only
package android.os.strictmode;

import java.util.Arrays;

/** Root class for all {@link android.os.StrictMode} violations. */
public abstract class Violation extends Throwable {
    private int hashCode;
    private boolean hashCodeValid;

    Violation(String message) {
        super(message);
    }

    @Override
    public int hashCode() {
        synchronized (this) {
            if (hashCodeValid) {
                return hashCode;
            }
            String message = getMessage();
            Throwable cause = getCause();
            int hash = message != null ? message.hashCode() : getClass().hashCode();
            hash = hash * 37 + Arrays.hashCode(getStackTrace());
            if (cause != null) {
                hash = hash * 37 + cause.toString().hashCode();
            }
            hashCodeValid = true;
            return hashCode = hash;
        }
    }

    @Override
    public synchronized Throwable initCause(Throwable cause) {
        hashCodeValid = false;
        return super.initCause(cause);
    }

    @Override
    public void setStackTrace(StackTraceElement[] stackTrace) {
        super.setStackTrace(stackTrace);
        synchronized (this) {
            hashCodeValid = false;
        }
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        hashCodeValid = false;
        return super.fillInStackTrace();
    }
}
