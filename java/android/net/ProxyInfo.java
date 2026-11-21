// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Direct or PAC HTTP proxy configuration value. */
public class ProxyInfo implements Parcelable {
    private final String host;
    private final int port;
    private final String[] exclusions;
    private final Uri pacFileUrl;

    private ProxyInfo(String host, int port, List<String> exclusions) {
        if (host == null) {
            throw new NullPointerException("host");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port");
        }
        this.host = host;
        this.port = port;
        this.exclusions =
                exclusions == null
                        ? new String[0]
                        : exclusions.toArray(new String[exclusions.size()]);
        this.pacFileUrl = Uri.EMPTY;
    }

    private ProxyInfo(Uri pacFileUrl, int localProxyPort) {
        Objects.requireNonNull(pacFileUrl, "pacFileUrl");
        this.host = "localhost";
        this.port = localProxyPort;
        this.exclusions = new String[0];
        this.pacFileUrl = pacFileUrl;
    }

    public ProxyInfo(ProxyInfo source) {
        if (source != null) {
            this.host = source.host;
            this.port = source.port;
            this.exclusions = source.exclusions == null ? new String[0] : source.exclusions.clone();
            this.pacFileUrl = source.pacFileUrl == null ? Uri.EMPTY : source.pacFileUrl;
        } else {
            this.host = null;
            this.port = 0;
            this.exclusions = new String[0];
            this.pacFileUrl = Uri.EMPTY;
        }
    }

    public static ProxyInfo buildDirectProxy(String host, int port) {
        if (port < 1 && (host == null || !host.isEmpty())) {
            throw new IllegalArgumentException("port");
        }
        return new ProxyInfo(host, port, Collections.<String>emptyList());
    }

    public static ProxyInfo buildDirectProxy(String host, int port, List<String> exclusions) {
        if (port < 1 && (host == null || !host.isEmpty())) {
            throw new IllegalArgumentException("port");
        }
        Objects.requireNonNull(exclusions, "exclusions");
        return new ProxyInfo(host, port, exclusions);
    }

    public static ProxyInfo buildPacProxy(Uri pacUri) {
        return new ProxyInfo(pacUri, -1);
    }

    public static ProxyInfo buildPacProxy(Uri pacUri, int port) {
        return new ProxyInfo(pacUri, port);
    }

    public Uri getPacFileUrl() {
        return pacFileUrl;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String[] getExclusionList() {
        return exclusions.clone();
    }

    public boolean isValid() {
        if (!Uri.EMPTY.equals(pacFileUrl)) {
            return true;
        }
        if (host == null || host.isEmpty()) {
            return port == 0;
        }
        return port >= 1 && port <= 65535;
    }

    @Override
    public String toString() {
        if (!Uri.EMPTY.equals(pacFileUrl)) {
            return String.format(Locale.ROOT, "PAC Script: %s", pacFileUrl);
        }
        if (host != null) {
            return "[" + host + "] " + port;
        }
        return "[ProxyInfo is null]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProxyInfo)) {
            return false;
        }
        ProxyInfo p = (ProxyInfo) o;
        if (!Uri.EMPTY.equals(pacFileUrl)) {
            return pacFileUrl.equals(p.getPacFileUrl()) && port == p.port;
        }
        if (!Uri.EMPTY.equals(p.pacFileUrl)) {
            return false;
        }
        return port == p.port
                && Objects.equals(host, p.host)
                && Arrays.equals(exclusions, p.exclusions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, Integer.valueOf(port), pacFileUrl)
                * 31
                + Arrays.hashCode(exclusions);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (!Uri.EMPTY.equals(pacFileUrl)) {
            dest.writeByte((byte) 1);
            dest.writeString(pacFileUrl.toString());
            dest.writeInt(port);
            return;
        }
        dest.writeByte((byte) 0);
        if (host != null) {
            dest.writeByte((byte) 1);
            dest.writeString(host);
            dest.writeInt(port);
        } else {
            dest.writeByte((byte) 0);
        }
        dest.writeStringArray(exclusions);
    }

    public static final Parcelable.Creator<ProxyInfo> CREATOR =
            new Parcelable.Creator<ProxyInfo>() {
                @Override
                public ProxyInfo createFromParcel(Parcel in) {
                    if (in.readByte() != 0) {
                        Uri url = Uri.parse(in.readString());
                        int localPort = in.readInt();
                        return new ProxyInfo(url, localPort);
                    }
                    String h = null;
                    int p = 0;
                    if (in.readByte() != 0) {
                        h = in.readString();
                        p = in.readInt();
                    }
                    String[] excl = in.createStringArray();
                    List<String> exclList =
                            excl == null ? Collections.<String>emptyList() : Arrays.asList(excl);
                    return h == null
                            ? new ProxyInfo("", 0, exclList)
                            : new ProxyInfo(h, p, exclList);
                }

                @Override
                public ProxyInfo[] newArray(int size) {
                    return new ProxyInfo[size];
                }
            };
}
