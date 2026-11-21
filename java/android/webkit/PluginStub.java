// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

import android.content.Context;
import android.view.View;

public interface PluginStub {
    View getEmbeddedView(int NPP, Context context);
    View getFullScreenView(int NPP, Context context);
}
