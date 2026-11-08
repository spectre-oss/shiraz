// SPDX-License-Identifier: GPL-3.0-only
import android.security.NetworkSecurityPolicy;
import java.io.*;
import java.net.*;
import java.util.Properties;
import shiraz.runtime.HostNetworkPolicy;

public final class NetworkPolicySmoke {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static Properties facts(int target, String explicit) {
        Properties p = new Properties(); p.setProperty("targetSdk", Integer.toString(target));
        if (explicit != null) p.setProperty("usesCleartextTraffic", explicit);
        return p;
    }
    static void state(Properties p, boolean expected) throws Exception {
        HostNetworkPolicy.install(p);
        check(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted() == expected, "global policy");
        check(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("localhost") == expected, "host policy");
        Properties metadata = new Properties(); metadata.putAll(p);
        metadata.setProperty("package", "policy.test"); metadata.setProperty("sourceDir", "/fixture.apk"); metadata.setProperty("dataDir", "/data");
        android.content.pm.ApplicationInfo info = new shiraz.runtime.HostPackageManager(metadata, null).getApplicationInfo("policy.test", 0);
        check(((info.flags & android.content.pm.ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0) == expected, "ApplicationInfo cleartext flag");
    }
    public static void main(String[] args) throws Exception {
        shiraz.runtime.HostTlsProvider.install();
        state(facts(27, null), true); state(facts(28, null), false);
        state(facts(35, "true"), true); state(facts(27, "false"), false);
        for (String config : new String[] {"@xml/policy", "@0x7f010001"}) {
            Properties p = facts(35, "true"); p.setProperty("networkSecurityConfig", config);
            try { HostNetworkPolicy.install(p); throw new AssertionError("custom trust policy ignored"); }
            catch (IllegalArgumentException expected) { }
            check(!NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(), "failed install replaced deny policy");
        }
        try { HostNetworkPolicy.install(facts(27, "@bool/cleartext")); throw new AssertionError("unresolved policy boolean ignored"); }
        catch (IllegalArgumentException expected) { }
        HttpURLConnection denied = (HttpURLConnection)new URL(args[0]+"/forbidden").openConnection();
        denied.setConnectTimeout(1000); denied.setReadTimeout(1000);
        try {
            try { denied.getResponseCode(); throw new AssertionError("denied HTTP request sent"); }
            catch (IOException expected) { check(expected.getMessage().toLowerCase(java.util.Locale.ROOT).contains("cleartext"), "cleartext denial reason"); }
        } finally { denied.disconnect(); }
        state(facts(35, "true"), true);
        HttpURLConnection allowed = (HttpURLConnection)new URL(args[0]+"/body").openConnection();
        allowed.setConnectTimeout(1000); allowed.setReadTimeout(1000);
        try {
            check(allowed.getResponseCode() == 200, "allowed HTTP status");
            try (InputStream in = allowed.getInputStream()) { check(in.read() == 42 && in.read() == -1, "allowed HTTP body"); }
        } finally { allowed.disconnect(); }
        NetworkConfigSmoke.run(args[1],args[0]);
        System.out.println("NETWORK_POLICY_VERIFIED");
    }
}
