// SPDX-License-Identifier: GPL-3.0-only
package android.os.strictmode;

public final class ContentUriWithoutPermissionViolation extends Violation {
    public ContentUriWithoutPermissionViolation(android.net.Uri uri, String location) {
        super(uri + " exposed beyond app through " + location + " without permission grant flags");
    }
}
