// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

public class WebMessage {
    private final String mData;
    private final WebMessagePort[] mPorts;

    public WebMessage(String data) {
        mData = data;
        mPorts = null;
    }

    public WebMessage(String data, WebMessagePort[] ports) {
        mData = data;
        mPorts = ports != null ? ports.clone() : null;
    }

    public String getData() {
        return mData;
    }

    public WebMessagePort[] getPorts() {
        return mPorts != null ? mPorts.clone() : null;
    }
}
