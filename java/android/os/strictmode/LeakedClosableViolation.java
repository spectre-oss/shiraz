// SPDX-License-Identifier: GPL-3.0-only
package android.os.strictmode;

public final class LeakedClosableViolation extends Violation {
    public LeakedClosableViolation(String message, Throwable allocationSite) {
        super(message);
        initCause(allocationSite);
    }

    public LeakedClosableViolation(String message) {
        super(message);
    }
}
