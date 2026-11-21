// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.content.Context;

/** Legacy HTTP proxy lookup utilities backed by JVM system proxy properties when set. */
public final class Proxy {
    public static final String PROXY_CHANGE_ACTION = "android.intent.action.PROXY_CHANGE";

    public Proxy() {
    }

    @Deprecated
    public static String getHost(Context ctx) {
        return getDefaultHost();
    }

    @Deprecated
    public static int getPort(Context ctx) {
        return getDefaultPort();
    }

    @Deprecated
    public static String getDefaultHost() {
        String host = System.getProperty("http.proxyHost");
        return (host == null || host.isEmpty()) ? null : host;
    }

    @Deprecated
    public static int getDefaultPort() {
        if (getDefaultHost() == null) {
            return -1;
        }
        try {
            return Integer.parseInt(System.getProperty("http.proxyPort", "8080"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
