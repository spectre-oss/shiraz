// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class ConnectivitySmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void write(File path, String value) throws IOException {
        path.getParentFile().mkdirs();
        Files.write(path.toPath(), value.getBytes(StandardCharsets.US_ASCII));
    }
    private static String route(String name, int metric) {
        return name + " 00000000 0101A8C0 0003 0 0 " + metric + " 00000000 0 0 0\n";
    }
    private static String route6(String name) {
        return "00000000000000000000000000000000 00 00000000000000000000000000000000 00 " +
                "fe800000000000000000000000000001 00000064 00000000 00000000 00000003 " + name + "\n";
    }
    private static void link(File sys, String name, boolean wifi) throws IOException {
        File dir = new File(sys, name);
        write(new File(dir, "flags"), "0x1003\n");
        write(new File(dir, "operstate"), "up\n");
        write(new File(dir, "carrier"), "1\n");
        write(new File(dir, "type"), "1\n");
        if (wifi) new File(dir, "wireless").mkdirs();
    }
    public static void main(String[] args) throws Exception {
        require(TrafficStats.getThreadStatsTag() == -1, "initial thread tag");
        TrafficStats.setThreadStatsTag(42);
        require(TrafficStats.getAndSetThreadStatsTag(Integer.MIN_VALUE) == 42, "replace returns prior tag");
        final Throwable[] threadError = new Throwable[1];
        Thread other = new Thread(new Runnable() {
            public void run() {
                try {
                    require(TrafficStats.getThreadStatsTag() == -1, "tag must not be inherited");
                    TrafficStats.setThreadStatsTag(71);
                    require(TrafficStats.getThreadStatsTag() == 71, "other thread stores tag");
                    TrafficStats.clearThreadStatsTag();
                    require(TrafficStats.getThreadStatsTag() == -1, "clear restores default");
                } catch (Throwable error) { threadError[0] = error; }
            }
        });
        other.start(); other.join();
        if (threadError[0] != null) throw new AssertionError("thread tag isolation", threadError[0]);
        require(TrafficStats.getThreadStatsTag() == Integer.MIN_VALUE, "caller tag remains independent");
        TrafficStats.clearThreadStatsTag();
        require(TrafficStats.getThreadStatsTag() == -1, "caller clear");
        File root = new File(args[0]);
        File proc = new File(root, "proc"), sys = new File(root, "sys");
        write(new File(proc, "route"), "Iface Destination Gateway Flags RefCnt Use Metric Mask MTU Window IRTT\n");
        write(new File(proc, "ipv6_route"), "");
        require(HostConnectivity.read(proc, sys) == null, "empty route table must be offline");
        link(sys, "ether0", false); link(sys, "wifi0", true);
        write(new File(proc, "route"), route("ether0", 100) + route("wifi0", 200));
        NetworkInfo wired = HostConnectivity.read(proc, sys);
        require(wired.isConnected() && wired.isAvailable() && wired.getType() == ConnectivityManager.TYPE_ETHERNET, "wired default");
        require(wired.getState() == NetworkInfo.State.CONNECTED && !wired.isRoaming(), "state snapshot");
        wired.setDetailedState(NetworkInfo.DetailedState.DISCONNECTED, "test", null);
        require(HostConnectivity.read(proc, sys).isConnected(), "mutating a snapshot must not change host state");
        write(new File(sys, "ether0/carrier"), "0\n");
        NetworkInfo wifi = HostConnectivity.read(proc, sys);
        require(wifi.getType() == ConnectivityManager.TYPE_WIFI, "skip unavailable lower metric link");
        write(new File(sys, "wifi0/flags"), "0x1002\n");
        require(HostConnectivity.read(proc, sys) == null, "admin down is offline");
        write(new File(sys, "wifi0/flags"), "0x1003\n");
        write(new File(sys, "wifi0/operstate"), "dormant\n");
        require(HostConnectivity.read(proc, sys) == null, "dormant is not connected");
        write(new File(sys, "wifi0/operstate"), "up\n");
        write(new File(proc, "route"), "");
        write(new File(proc, "ipv6_route"), route6("wifi0"));
        require(HostConnectivity.read(proc, sys).getType() == ConnectivityManager.TYPE_WIFI, "IPv6-only default");
        write(new File(proc, "ipv6_route"), route6("wifi0").replace("00000003 wifi0", "00000201 wifi0"));
        require(HostConnectivity.read(proc, sys) == null, "reject route must not connect");
        write(new File(proc, "ipv6_route"), route6("wifi0").replace(" 00 00000000000000000000000000000000 00 ", " 00 00000000000000000000000000000000 40 "));
        require(HostConnectivity.read(proc, sys) == null, "source-specific IPv6 route is not a general default");
        write(new File(proc, "ipv6_route"), "");
        write(new File(proc, "route"), route("../escape", 0));
        try { HostConnectivity.read(proc, sys); throw new AssertionError("unsafe name"); } catch (IOException expected) {}
        write(new File(proc, "route"), "malformed\n");
        try { HostConnectivity.read(proc, sys); throw new AssertionError("malformed route"); } catch (IOException expected) {}
        write(new File(proc, "route"), route("ether0", 100));
        write(new File(sys, "ether0/carrier"), "1\n");
        write(new File(sys, "ether0/tun_flags"), "1\n");
        try { HostConnectivity.read(proc, sys); throw new AssertionError("unsupported tunnel must not masquerade as ethernet"); } catch (IOException expected) {}
        NetworkInfo state = new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0, "WIFI", "");
        state.setDetailedState(NetworkInfo.DetailedState.AUTHENTICATING, null, null);
        require(state.isConnectedOrConnecting() && !state.isConnected(), "detailed/coarse connecting");
        state.setDetailedState(NetworkInfo.DetailedState.SCANNING, null, null);
        require(state.getState() == NetworkInfo.State.DISCONNECTED, "AOSP scanning state");
        state.setIsAvailable(true);
        NetworkInfo copy = new NetworkInfo(state);
        require(copy.getDetailedState() == state.getDetailedState() && copy.isAvailable() && copy.getType() == 1, "snapshot copy");
        state.setIsAvailable(false);
        require(copy.isAvailable(), "copy independence");
        try { copy.writeToParcel(null, 0); throw new AssertionError("unsupported transport accepted"); } catch (UnsupportedOperationException expected) {}
        // Exercise the real host readers under both VMs, without requiring a live network.
        NetworkInfo host = ConnectivityManager.shirazForHost().getActiveNetworkInfo();
        require(host == null || host.isConnected(), "host snapshot");
        File wifiRoot=new File(args[0],"wifi-status");wifiRoot.mkdirs();require(!HostConnectivity.wifiEnabled(wifiRoot),"No wireless interfaces");
        File wireless=new File(wifiRoot,"wlan-test");new File(wireless,"wireless").mkdirs();write(new File(wireless,"flags"),"0x0\n");require(!HostConnectivity.wifiEnabled(wifiRoot),"Disabled wireless interface");write(new File(wireless,"flags"),"0x1\n");require(HostConnectivity.wifiEnabled(wifiRoot),"Enabled wireless interface");
        File auditRoot=new File(args[0],"wifi-audit");CapturePermissions audit=new CapturePermissions(auditRoot,"Test",new java.util.Properties());
        android.net.wifi.WifiManager manager=android.net.wifi.WifiManager.shirazForHost(audit);audit.wifiAccess("service:requested");manager.isWifiEnabled();manager.getWifiState();require("<unknown ssid>".equals(manager.getConnectionInfo().getSSID()),"SSID remains redacted");
        String history=new String(Files.readAllBytes(new File(auditRoot,"privacy/access-history.log").toPath()),StandardCharsets.UTF_8);require(history.contains("Wi-Fi\tservice:requested")&&history.contains("isWifiEnabled:requested")&&history.contains("getConnectionInfo:redacted")&&history.contains("getSSID:redacted"),"Persistent Wi-Fi audit entries");
        require(!history.contains("<unknown ssid>")&&!history.contains("wlan-test"),"Audit contains metadata only");require(new CapturePermissions(auditRoot,"Test",new java.util.Properties()).history().contains("Network name requested (redacted)"),"Audit survives restart");
        try{audit.wifiAccess("getSSID:private-network-name");throw new AssertionError("Arbitrary audit content accepted");}catch(IllegalArgumentException expected){}
        System.out.println("CONNECTIVITY_HOST_ROUTES_OK");
    }
}
