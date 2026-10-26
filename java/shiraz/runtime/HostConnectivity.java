// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Linux main-table default-route/link snapshots. Does not send probe traffic.
 * Policy routing and destination-specific VPN routes are not modelled here.
 */
public final class HostConnectivity {
    private HostConnectivity() {}

    public static NetworkInfo activeNetwork() {
        if (DeviceProfile.selected()) {
            HostAppInstaller.recordAccess("connectivity:activeNetwork");
            NetworkInfo info=new NetworkInfo(ConnectivityManager.TYPE_WIFI,0,"WIFI","");
            info.setIsAvailable(true);
            info.setDetailedState(NetworkInfo.DetailedState.CONNECTED,null,null);
            return info;
        }
        android.os.StrictMode.ThreadPolicy old = android.os.StrictMode.allowThreadDiskReads();
        try {
            return read(new File("/proc/net"), new File("/sys/class/net"));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read host connectivity", error);
        } finally {
            android.os.StrictMode.setThreadPolicy(old);
        }
    }

    /** Whether a wireless interface is administratively up; no scan or network identifiers. */
    public static boolean wifiEnabled(){if(DeviceProfile.selected()){HostAppInstaller.recordAccess("connectivity:wifiEnabled");return Boolean.parseBoolean(DeviceProfile.get("wifi.enabled","false"));}android.os.StrictMode.ThreadPolicy old=android.os.StrictMode.allowThreadDiskReads();try{return wifiEnabled(new File("/sys/class/net"));}catch(IOException error){throw new IllegalStateException("Cannot read Wi-Fi interface status",error);}finally{android.os.StrictMode.setThreadPolicy(old);}}
    static boolean wifiEnabled(File sysNet)throws IOException{
        File[] links=sysNet.listFiles();if(links==null)throw new IOException("Network interfaces unavailable");
        for(File link:links){if(!new File(link,"wireless").isDirectory())continue;String flags=text(new File(link,"flags")).trim();long value=number(flags.startsWith("0x")?flags.substring(2):flags,16);if((value&1)!=0)return true;}return false;
    }

    private static final class Route {
        final String name;
        final long metric;
        final int family;
        Route(String name, long metric, int family) {
            this.name = name; this.metric = metric; this.family = family;
        }
    }

    // Package-private filesystem injection is for parser/link tests, not application overrides.
    static NetworkInfo read(File procNet, File sysNet) throws IOException {
        List<Route> routes = new ArrayList<Route>();
        File ipv4 = new File(procNet, "route"), ipv6 = new File(procNet, "ipv6_route");
        if (!ipv4.isFile() && !ipv6.isFile()) throw new IOException("Host route tables unavailable");
        if (ipv4.isFile()) {
            for (String line : text(ipv4).split("\n")) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length == 0 || fields[0].equals("Iface") || fields[0].isEmpty()) continue;
                if (fields.length != 11) throw new IOException("Malformed IPv4 route row");
                if (!fields[1].equals("00000000") || !fields[7].equals("00000000")) continue;
                long flags = number(fields[3], 16);
                if ((flags & 1) == 0 || (flags & 0x200) != 0) continue;
                routes.add(new Route(interfaceName(fields[0]), number(fields[6], 10), 4));
            }
        }
        if (ipv6.isFile()) {
            String zero = "00000000000000000000000000000000";
            for (String line : text(ipv6).split("\n")) {
                if (line.trim().isEmpty()) continue;
                String[] fields = line.trim().split("\\s+");
                if (fields.length != 10) throw new IOException("Malformed IPv6 route row");
                if (!fields[0].equals(zero) || !fields[1].equals("00") ||
                        !fields[2].equals(zero) || !fields[3].equals("00")) continue;
                long flags = number(fields[8], 16);
                if ((flags & 1) == 0 || (flags & 0x200) != 0) continue;
                routes.add(new Route(interfaceName(fields[9]), number(fields[5], 16), 6));
            }
        }
        // The legacy API exposes a single network. Prefer an available IPv4 default,
        // otherwise IPv6; metrics rank candidates only within the same family.
        Collections.sort(routes, new Comparator<Route>() {
            public int compare(Route a, Route b) {
                int family = Integer.compare(a.family, b.family);
                return family != 0 ? family : Long.compare(a.metric, b.metric);
            }
        });
        for (Route route : routes) {
            File link = new File(sysNet, route.name);
            if (!link.isDirectory()) continue; // Interface may disappear between snapshots.
            String flagsText = text(new File(link, "flags")).trim();
            long flags = number(flagsText.startsWith("0x") ? flagsText.substring(2) : flagsText, 16);
            if ((flags & 1) == 0 || (flags & 8) != 0) continue; // administratively down/loopback
            String state = text(new File(link, "operstate")).trim();
            if (!state.equals("up") && !state.equals("unknown")) continue;
            if (!text(new File(link, "carrier")).trim().equals("1")) continue;
            int type;
            String name;
            if (new File(link, "wireless").isDirectory()) {
                type = ConnectivityManager.TYPE_WIFI; name = "WIFI";
            } else if (!new File(link, "tun_flags").isFile() && text(new File(link, "type")).trim().equals("1")) {
                type = ConnectivityManager.TYPE_ETHERNET; name = "ETHERNET";
            } else {
                throw new IOException("Unsupported default-route interface transport");
            }
            NetworkInfo info = new NetworkInfo(type, 0, name, "");
            info.setIsAvailable(true);
            info.setDetailedState(NetworkInfo.DetailedState.CONNECTED, null, null);
            return info;
        }
        return null;
    }

    private static String interfaceName(String value) throws IOException {
        if (value.isEmpty() || value.length() > 15 || value.equals(".") || value.equals("..") ||
                value.indexOf('/') >= 0 || value.indexOf('\0') >= 0) throw new IOException("Invalid route interface name");
        return value;
    }
    private static long number(String value, int radix) throws IOException {
        try {
            if (value.isEmpty() || value.charAt(0) == '+' || value.charAt(0) == '-') throw new NumberFormatException();
            long result = Long.parseLong(value, radix);
            if (result < 0 || result > 0xffffffffL) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException error) { throw new IOException("Invalid route/link integer", error); }
    }
    private static String text(File path) throws IOException {
        try (FileInputStream in = new FileInputStream(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > 1024 * 1024) throw new IOException("Host network table exceeds limit");
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), StandardCharsets.US_ASCII);
        }
    }
}
