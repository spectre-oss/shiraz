// SPDX-License-Identifier: GPL-3.0-only
package android.security;

/** Public Android policy facade; decisions come from the installed libcore policy. */
public class NetworkSecurityPolicy {
    private static final NetworkSecurityPolicy INSTANCE = new NetworkSecurityPolicy();
    private NetworkSecurityPolicy() { }
    public static NetworkSecurityPolicy getInstance() { return INSTANCE; }
    public boolean isCleartextTrafficPermitted() {
        return libcore.net.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted();
    }
    public boolean isCleartextTrafficPermitted(String hostname) {
        return libcore.net.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(hostname);
    }
}
