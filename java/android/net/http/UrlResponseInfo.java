// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

import java.util.List;

public abstract class UrlResponseInfo {
    public UrlResponseInfo() {}

    public abstract String getUrl();

    public abstract List<String> getUrlChain();

    public abstract int getHttpStatusCode();

    public abstract String getHttpStatusText();

    public abstract HeaderBlock getHeaders();

    public abstract boolean wasCached();

    public abstract String getNegotiatedProtocol();

    public abstract long getReceivedByteCount();
}
