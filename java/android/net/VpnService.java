// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Base service for application-managed virtual private networks. */
public class VpnService extends Service {
    public static final String SERVICE_INTERFACE = "android.net.VpnService";
    public static final String SERVICE_META_DATA_SUPPORTS_ALWAYS_ON =
            "android.net.VpnService.SUPPORTS_ALWAYS_ON";

    private final IBinder mBinder = new Binder();

    public VpnService() {
    }

    public static Intent prepare(Context context) {
        Objects.requireNonNull(context, "context");
        return null;
    }

    public boolean protect(int socket) {
        return socket >= 0;
    }

    public boolean protect(Socket socket) {
        return socket != null && !socket.isClosed();
    }

    public boolean protect(DatagramSocket socket) {
        return socket != null && !socket.isClosed();
    }

    public boolean setUnderlyingNetworks(Network[] networks) {
        return false;
    }

    public final boolean isAlwaysOn() {
        return false;
    }

    public final boolean isLockdownEnabled() {
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return mBinder;
        }
        return null;
    }

    public void onRevoke() {
        stopSelf();
    }

    /** Non-static builder for configuring a TUN interface session. */
    public class Builder {
        private String mSession;
        private PendingIntent mConfigureIntent;
        private int mMtu = 1500;
        private ProxyInfo mHttpProxy;
        private final List<LinkAddress> mAddresses = new ArrayList<LinkAddress>();
        private final List<RouteInfo> mRoutes = new ArrayList<RouteInfo>();
        private final List<IpPrefix> mExcludedRoutes = new ArrayList<IpPrefix>();
        private final List<InetAddress> mDnsServers = new ArrayList<InetAddress>();
        private final List<String> mSearchDomains = new ArrayList<String>();
        private final List<String> mAllowedApplications = new ArrayList<String>();
        private final List<String> mDisallowedApplications = new ArrayList<String>();
        private boolean mAllowBypass;
        private boolean mBlocking;
        private Network[] mUnderlyingNetworks;
        private boolean mMetered = true;

        public Builder() {
        }

        public Builder setSession(String session) {
            this.mSession = session;
            return this;
        }

        public Builder setConfigureIntent(PendingIntent intent) {
            this.mConfigureIntent = intent;
            return this;
        }

        public Builder setMtu(int mtu) {
            if (mtu <= 0) {
                throw new IllegalArgumentException("Bad mtu");
            }
            this.mMtu = mtu;
            return this;
        }

        public Builder setHttpProxy(ProxyInfo proxyInfo) {
            this.mHttpProxy = proxyInfo;
            return this;
        }

        private void validateAddress(InetAddress address, int prefixLength) {
            Objects.requireNonNull(address, "address");
            if (address.isLoopbackAddress()) {
                throw new IllegalArgumentException("Bad address: loopback");
            }
            if (address instanceof Inet4Address) {
                if (prefixLength < 0 || prefixLength > 32) {
                    throw new IllegalArgumentException("Bad prefixLength");
                }
            } else if (address instanceof Inet6Address) {
                if (prefixLength < 0 || prefixLength > 128) {
                    throw new IllegalArgumentException("Bad prefixLength");
                }
            } else {
                throw new IllegalArgumentException("Unsupported family");
            }
        }

        public Builder addAddress(InetAddress address, int prefixLength) {
            validateAddress(address, prefixLength);
            if (address.isAnyLocalAddress()) {
                throw new IllegalArgumentException("Bad address: wildcard");
            }
            mAddresses.add(new LinkAddress(address, prefixLength));
            return this;
        }

        public Builder addAddress(String address, int prefixLength) {
            return addAddress(InetAddresses.parseNumericAddress(address), prefixLength);
        }

        public Builder addRoute(InetAddress address, int prefixLength) {
            validateAddress(address, prefixLength);
            mRoutes.add(new RouteInfo(new IpPrefix(address, prefixLength), null, null));
            return this;
        }

        public Builder addRoute(IpPrefix prefix) {
            Objects.requireNonNull(prefix, "prefix");
            return addRoute(prefix.getAddress(), prefix.getPrefixLength());
        }

        public Builder addRoute(String address, int prefixLength) {
            return addRoute(InetAddresses.parseNumericAddress(address), prefixLength);
        }

        public Builder excludeRoute(IpPrefix prefix) {
            Objects.requireNonNull(prefix, "prefix");
            mExcludedRoutes.add(prefix);
            return this;
        }

        public Builder addDnsServer(InetAddress address) {
            Objects.requireNonNull(address, "address");
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                throw new IllegalArgumentException("Bad DNS address");
            }
            mDnsServers.add(address);
            return this;
        }

        public Builder addDnsServer(String address) {
            return addDnsServer(InetAddresses.parseNumericAddress(address));
        }

        public Builder addSearchDomain(String domain) {
            Objects.requireNonNull(domain, "domain");
            mSearchDomains.add(domain);
            return this;
        }

        public Builder allowFamily(int family) {
            return this;
        }

        public Builder addAllowedApplication(String packageName)
                throws PackageManager.NameNotFoundException {
            Objects.requireNonNull(packageName, "packageName");
            if (!mDisallowedApplications.isEmpty()) {
                throw new IllegalArgumentException("Multiple access lists are not supported");
            }
            mAllowedApplications.add(packageName);
            return this;
        }

        public Builder addDisallowedApplication(String packageName)
                throws PackageManager.NameNotFoundException {
            Objects.requireNonNull(packageName, "packageName");
            if (!mAllowedApplications.isEmpty()) {
                throw new IllegalArgumentException("Multiple access lists are not supported");
            }
            mDisallowedApplications.add(packageName);
            return this;
        }

        public Builder allowBypass() {
            this.mAllowBypass = true;
            return this;
        }

        public Builder setBlocking(boolean blocking) {
            this.mBlocking = blocking;
            return this;
        }

        public Builder setUnderlyingNetworks(Network[] networks) {
            this.mUnderlyingNetworks = networks == null ? null : networks.clone();
            return this;
        }

        public Builder setMetered(boolean isMetered) {
            this.mMetered = isMetered;
            return this;
        }

        public ParcelFileDescriptor establish() {
            // Host session does not create privileged kernel TUN devices.
            return null;
        }
    }
}
