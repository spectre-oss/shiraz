// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/** Immutable representation of a network route entry. */
public final class RouteInfo implements Parcelable {
    public static final int RTN_UNICAST = 1;
    public static final int RTN_UNREACHABLE = 7;
    public static final int RTN_THROW = 9;

    private final IpPrefix mDestination;
    private final InetAddress mGateway;
    private final String mInterface;
    private final int mType;
    private final boolean mHasGateway;

    RouteInfo(IpPrefix destination, InetAddress gateway, String iface, int type) {
        if (type != RTN_UNICAST && type != RTN_UNREACHABLE && type != RTN_THROW) {
            throw new IllegalArgumentException("Unknown route type " + type);
        }
        if (destination == null) {
            if (gateway != null) {
                if (gateway instanceof Inet4Address) {
                    destination = new IpPrefix(InetAddresses.parseNumericAddress("0.0.0.0"), 0);
                } else {
                    destination = new IpPrefix(InetAddresses.parseNumericAddress("::"), 0);
                }
            } else {
                throw new IllegalArgumentException(
                        "Invalid arguments passed in: " + gateway + "," + destination);
            }
        }
        if (gateway == null) {
            if (destination.getAddress() instanceof Inet4Address) {
                gateway = InetAddresses.parseNumericAddress("0.0.0.0");
            } else {
                gateway = InetAddresses.parseNumericAddress("::");
            }
        }
        if ((destination.getAddress() instanceof Inet4Address) != (gateway instanceof Inet4Address)
                && (destination.getAddress() instanceof Inet6Address)
                        != (gateway instanceof Inet6Address)) {
            throw new IllegalArgumentException("Address family mismatch in RouteInfo");
        }
        this.mDestination = new IpPrefix(destination.getAddress(), destination.getPrefixLength());
        this.mGateway = gateway;
        this.mInterface = iface;
        this.mType = type;
        this.mHasGateway = !gateway.isAnyLocalAddress();
    }

    RouteInfo(IpPrefix destination, InetAddress gateway, String iface) {
        this(destination, gateway, iface, RTN_UNICAST);
    }

    public IpPrefix getDestination() {
        return mDestination;
    }

    public InetAddress getGateway() {
        return mGateway;
    }

    public String getInterface() {
        return mInterface;
    }

    public int getType() {
        return mType;
    }

    public boolean isDefaultRoute() {
        return mType == RTN_UNICAST && mDestination.getPrefixLength() == 0;
    }

    public boolean hasGateway() {
        return mHasGateway;
    }

    public boolean matches(InetAddress destination) {
        return mDestination.contains(destination);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(mDestination);
        if (mType == RTN_UNREACHABLE) {
            sb.append(" unreachable");
        } else if (mType == RTN_THROW) {
            sb.append(" throw");
        } else {
            sb.append(" -> ").append(mGateway.getHostAddress());
            if (mInterface != null) {
                sb.append(' ').append(mInterface);
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RouteInfo)) {
            return false;
        }
        RouteInfo target = (RouteInfo) obj;
        return Objects.equals(mDestination, target.mDestination)
                && Objects.equals(mGateway, target.mGateway)
                && Objects.equals(mInterface, target.mInterface)
                && mType == target.mType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDestination, mGateway, mInterface, Integer.valueOf(mType));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mDestination, flags);
        byte[] gwBytes = mGateway == null ? null : mGateway.getAddress();
        dest.writeByteArray(gwBytes);
        dest.writeString(mInterface);
        dest.writeInt(mType);
    }

    public static final Parcelable.Creator<RouteInfo> CREATOR =
            new Parcelable.Creator<RouteInfo>() {
                @Override
                public RouteInfo createFromParcel(Parcel in) {
                    IpPrefix destPrefix = in.readTypedObject(IpPrefix.CREATOR);
                    byte[] gwBytes = in.createByteArray();
                    InetAddress gw = null;
                    if (gwBytes != null) {
                        try {
                            gw = InetAddress.getByAddress(gwBytes);
                        } catch (UnknownHostException ignored) {
                        }
                    }
                    String iface = in.readString();
                    int type = in.readInt();
                    return new RouteInfo(destPrefix, gw, iface, type);
                }

                @Override
                public RouteInfo[] newArray(int size) {
                    return new RouteInfo[size];
                }
            };
}
