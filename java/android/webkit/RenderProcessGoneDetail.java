// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

public abstract class RenderProcessGoneDetail {
    public RenderProcessGoneDetail() {
    }

    public abstract boolean didCrash();
    public abstract int rendererPriorityAtExit();
}
