// SPDX-License-Identifier: GPL-3.0-only
package android.os.strictmode;

public final class ImplicitDirectBootViolation extends Violation {
    public ImplicitDirectBootViolation() {
        super("Implicitly relying on automatic Direct Boot filtering");
    }
}
