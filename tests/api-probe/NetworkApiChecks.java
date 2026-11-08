// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import android.net.*;
import android.os.Parcel;
import android.system.*;
import java.io.*;
import java.net.*;

/** Exercises public Network APIs rather than reaching through to java.net directly. */
final class NetworkApiChecks {
    static String runPolicy(boolean expected) {
        android.security.NetworkSecurityPolicy policy = android.security.NetworkSecurityPolicy.getInstance();
        NetworkChecks.require(policy.isCleartextTrafficPermitted() == expected, "application cleartext policy");
        NetworkChecks.require(policy.isCleartextTrafficPermitted("localhost") == expected, "host cleartext policy");
        return "Public cleartext policy agrees with the application manifest flag";
    }
    static Network unknown() {
        Parcel p = Parcel.obtain();
        try { p.writeInt(Integer.MAX_VALUE); p.setDataPosition(0); return Network.CREATOR.createFromParcel(p); }
        finally { p.recycle(); }
    }
    static String runTls(ConnectivityManager manager, TlsNetworkChecks.Assets assets) throws Exception {
        Network network = manager.getActiveNetwork();
        if (network == null) throw new IOException("No active network to exercise Network HTTPS");
        return TlsNetworkChecks.run("network.tls_handshake", assets, url -> network.openConnection(url));
    }
    static String run(String id, ConnectivityManager manager) throws Exception {
        Network network = manager.getActiveNetwork();
        if (network == null) throw new IOException("No active network to exercise Network APIs");
        switch (id) {
            case "network.network_identity": {
                NetworkChecks.require(Network.fromNetworkHandle(network.getNetworkHandle()).equals(network), "network handle round trip");
                Parcel p = Parcel.obtain();
                try {
                    network.writeToParcel(p, 0); p.setDataPosition(0);
                    Network copy = Network.CREATOR.createFromParcel(p);
                    NetworkChecks.require(copy.equals(network) && copy.hashCode() == network.hashCode(), "network Parcel identity");
                    NetworkChecks.require(copy.getNetworkHandle() == network.getNetworkHandle(), "parceled handle");
                    NetworkChecks.require(network.describeContents() == 0 && Network.CREATOR.newArray(2).length == 2, "Parcelable contract");
                } finally { p.recycle(); }
                for (long invalid : new long[] {0, 100, -1}) {
                    try { Network.fromNetworkHandle(invalid); throw new AssertionError("malformed handle accepted"); }
                    catch (IllegalArgumentException expected) { }
                }
                return "Network handle and Parcelable retain identity; malformed handles are rejected";
            }
            case "network.network_dns": {
                InetAddress[] addresses = network.getAllByName("localhost");
                NetworkChecks.require(addresses.length > 0, "network DNS empty");
                for (InetAddress address : addresses) NetworkChecks.require(address.isLoopbackAddress(), "network localhost address");
                NetworkChecks.require(network.getByName("127.0.0.1").isLoopbackAddress(), "network numeric address");
                return "Network DNS resolves loopback hostname and literal address";
            }
            case "network.network_socket": {
                NetworkChecks.local(peer -> {
                    NetworkChecks.require(peer.getInputStream().read() == 42, "network factory request");
                    peer.getOutputStream().write(43);
                }, url -> {
                    try (Socket client = network.getSocketFactory().createSocket(url.getHost(), url.getPort())) {
                        client.setSoTimeout(1000); client.getOutputStream().write(42);
                        NetworkChecks.require(client.getInputStream().read() == 43, "network factory reply");
                    }
                });
                return "Network SocketFactory exchanges request/reply with a local peer";
            }
            case "network.network_http": {
                for (boolean explicit : new boolean[] {false, true}) {
                    NetworkChecks.local(peer -> {
                        NetworkChecks.headers(peer);
                        NetworkChecks.response(peer, "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nprobe");
                    }, url -> {
                        HttpURLConnection c = (HttpURLConnection)(explicit
                            ? network.openConnection(url, java.net.Proxy.NO_PROXY) : network.openConnection(url));
                        c.setConnectTimeout(1000); c.setReadTimeout(1000);
                        try {
                            NetworkChecks.require(c.getResponseCode() == 200, "Network HTTP status");
                            NetworkChecks.require("probe".equals(NetworkChecks.body(c.getInputStream())), "Network HTTP body");
                        } finally { c.disconnect(); }
                    });
                }
                try { network.openConnection(new URL("file:/")); throw new AssertionError("Network accepted non-HTTP protocol"); }
                catch (MalformedURLException expected) { }
                try { network.openConnection(new URL("http://127.0.0.1/"), null); throw new AssertionError("Network accepted null proxy"); }
                catch (IllegalArgumentException expected) { }
                return "Network URL APIs read HTTP bodies and reject invalid protocol/proxy";
            }
            case "network.network_bind": {
                NetworkChecks.local(peer -> {
                    NetworkChecks.require(peer.getInputStream().read() == 42, "bound socket request");
                    peer.getOutputStream().write(43);
                }, url -> {
                    try (Socket client = new Socket()) {
                        network.bindSocket(client);
                        client.connect(new InetSocketAddress(url.getHost(), url.getPort()), 1000);
                        client.setSoTimeout(1000); client.getOutputStream().write(42);
                        NetworkChecks.require(client.getInputStream().read() == 43, "bound socket reply");
                        try { network.bindSocket(client); throw new AssertionError("connected TCP socket bound"); }
                        catch (IOException expected) { }
                    }
                });
                try (DatagramSocket datagram = new DatagramSocket()) {
                    network.bindSocket(datagram);
                    datagram.connect(InetAddress.getByName("127.0.0.1"), 9);
                    try { network.bindSocket(datagram); throw new AssertionError("connected UDP socket bound"); }
                    catch (IOException expected) { }
                }
                FileDescriptor fd = Os.socket(OsConstants.AF_INET, OsConstants.SOCK_STREAM, 0);
                try { network.bindSocket(fd); } finally { Os.close(fd); }
                try { network.bindSocket(fd); throw new AssertionError("closed descriptor bound"); }
                catch (IOException expected) { }
                return "Default Network accepts unconnected sockets and rejects connected/closed sockets";
            }
            case "network.network_unknown": {
                Network missing = unknown();
                NetworkChecks.require(manager.getNetworkInfo(missing) == null && manager.getNetworkCapabilities(missing) == null
                    && manager.getLinkProperties(missing) == null, "unknown network advertised as active");
                try { missing.getByName("localhost"); throw new AssertionError("unknown network used default DNS"); }
                catch (UnknownHostException expected) { }
                try (Socket unexpected = missing.getSocketFactory().createSocket()) { throw new AssertionError("unknown network used default sockets"); }
                catch (IOException expected) { }
                try { missing.openConnection(new URL("http://127.0.0.1/")); throw new AssertionError("unknown network used default HTTP"); }
                catch (IOException expected) { }
                try (Socket client = new Socket()) {
                    try { missing.bindSocket(client); throw new AssertionError("unknown network accepted socket binding"); }
                    catch (IOException expected) { }
                }
                return "Unavailable Network never aliases default network metadata, DNS, sockets or HTTP";
            }
            default: throw new IllegalArgumentException("Unknown Network case: " + id);
        }
    }
}
