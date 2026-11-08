// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.util.Properties;

/** Manifest decisions without a libcore dependency, also used by host metadata tools. */
public final class ManifestNetworkPolicy {
    private ManifestNetworkPolicy() { }
    public static boolean cleartextPermitted(Properties metadata) {
        String explicit = metadata.getProperty("usesCleartextTraffic");
        if (explicit != null) {
            if (explicit.equals("true")) return true;
            if (explicit.equals("false")) return false;
            throw new UnsupportedOperationException("Resource-valued usesCleartextTraffic requires resource policy support");
        }
        int target;
        try { target = Integer.parseInt(metadata.getProperty("targetSdk", metadata.getProperty("minSdk", "1"))); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Unsupported policy target SDK", error); }
        if (target < 1) throw new IllegalArgumentException("Invalid policy target SDK");
        return target < 28;
    }
}
