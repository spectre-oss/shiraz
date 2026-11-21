// SPDX-License-Identifier: GPL-3.0-only
package android.os.strictmode;

public final class IncorrectContextUseViolation extends Violation {
    public IncorrectContextUseViolation(String message, Throwable originStack) {
        super(message);
        initCause(originStack);
    }
}
