// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.view.View;

@Deprecated
public abstract class FragmentContainer {
    public FragmentContainer() {
    }

    public abstract <T extends View> T onFindViewById(int id);

    public abstract boolean onHasView();
}
