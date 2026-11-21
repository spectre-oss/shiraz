// SPDX-License-Identifier: GPL-3.0-only
package android.security.identity;

import java.time.Instant;

public final class AuthenticationKeyMetadata {
    private final int mUsageCount;
    private final Instant mExpirationDate;

    public AuthenticationKeyMetadata(int usageCount, Instant expirationDate) {
        mUsageCount = usageCount;
        mExpirationDate = expirationDate;
    }

    public int getUsageCount() {
        return mUsageCount;
    }

    public Instant getExpirationDate() {
        return mExpirationDate;
    }
}
