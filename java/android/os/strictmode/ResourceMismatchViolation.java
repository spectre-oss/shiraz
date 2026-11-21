// SPDX-License-Identifier: GPL-3.0-only
package android.os.strictmode;

public final class ResourceMismatchViolation extends Violation {
    public ResourceMismatchViolation(Object tag) {
        super(tag == null ? null : tag.toString());
    }
}
