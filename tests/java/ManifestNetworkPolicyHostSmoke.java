// SPDX-License-Identifier: GPL-3.0-only
import java.util.Properties;
import shiraz.runtime.ManifestNetworkPolicy;

/** Host metadata decisions must work without ART/libcore classes on the JVM. */
public final class ManifestNetworkPolicyHostSmoke {
    static void check(boolean condition) { if (!condition) throw new AssertionError("manifest policy decision"); }
    public static void main(String[] args) {
        Properties p = new Properties();
        p.setProperty("targetSdk", "27"); check(ManifestNetworkPolicy.cleartextPermitted(p));
        p.setProperty("targetSdk", "28"); check(!ManifestNetworkPolicy.cleartextPermitted(p));
        p.setProperty("usesCleartextTraffic", "true"); check(ManifestNetworkPolicy.cleartextPermitted(p));
        p.setProperty("targetSdk", "27"); p.setProperty("usesCleartextTraffic", "false");
        check(!ManifestNetworkPolicy.cleartextPermitted(p));
        p.setProperty("usesCleartextTraffic", "@bool/allow");
        try { ManifestNetworkPolicy.cleartextPermitted(p); throw new AssertionError("unresolved boolean accepted"); }
        catch (UnsupportedOperationException expected) { }
        System.out.println("HOST_MANIFEST_NETWORK_POLICY_OK (no libcore classpath)");
    }
}
