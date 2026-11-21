// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.UUID;

/** Opaque token representing rendered personalized content for a surface package. */
public class SurfacePackageToken {
    private final String mTokenString;

    SurfacePackageToken() {
        this(UUID.randomUUID().toString());
    }

    SurfacePackageToken(String tokenString) {
        this.mTokenString = tokenString;
    }

    String getTokenString() {
        return mTokenString;
    }
}
