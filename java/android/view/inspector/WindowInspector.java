// SPDX-License-Identifier: GPL-3.0-only

package android.view.inspector;

import android.view.View;
import java.util.Collections;
import java.util.List;

public final class WindowInspector {
    private WindowInspector() {
    }

    public static List<View> getGlobalWindowViews() {
        return Collections.emptyList();
    }
}
