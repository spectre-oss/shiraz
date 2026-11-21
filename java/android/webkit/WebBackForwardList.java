// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

import java.io.Serializable;

public abstract class WebBackForwardList implements Cloneable, Serializable {
    private static final long serialVersionUID = 1L;

    public WebBackForwardList() {
    }

    public abstract WebHistoryItem getCurrentItem();
    public abstract int getCurrentIndex();
    public abstract WebHistoryItem getItemAtIndex(int index);
    public abstract int getSize();

    @Override
    protected abstract WebBackForwardList clone();
}
