// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.util.Properties;

/** Install application policy before loading any application classes. */
public final class HostNetworkPolicy {
    private HostNetworkPolicy() { }
    public static void install(Properties metadata) {
        String config = metadata.getProperty("networkSecurityConfig");
        if (config != null && !config.equals("@null") && !config.equals("@0x00000000") && !config.equals("0"))
            throw new UnsupportedOperationException("Custom networkSecurityConfig is not implemented; refusing to ignore application trust policy");
        libcore.net.NetworkSecurityPolicy.setInstance(new Applied(ManifestNetworkPolicy.cleartextPermitted(metadata)));
    }
    private static final class Applied extends libcore.net.NetworkSecurityPolicy {
        private final boolean cleartext;
        Applied(boolean cleartext) { this.cleartext = cleartext; }
        public boolean isCleartextTrafficPermitted() { return cleartext; }
        public boolean isCleartextTrafficPermitted(String hostname) { return cleartext; }
        public boolean isCertificateTransparencyVerificationRequired(String hostname) { return false; }
    }
}
