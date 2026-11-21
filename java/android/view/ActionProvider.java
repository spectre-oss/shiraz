// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.content.Context;

/** Provider for rich menu item action views and submenus. */
public abstract class ActionProvider {
    public interface VisibilityListener {
        void onActionProviderVisibilityChanged(boolean isVisible);
    }

    private final Context mContext;
    private VisibilityListener mVisibilityListener;

    public ActionProvider(Context context) {
        this.mContext = context;
    }

    @Deprecated
    public abstract View onCreateActionView();

    public View onCreateActionView(MenuItem forItem) {
        return onCreateActionView();
    }

    public boolean overridesItemVisibility() {
        return false;
    }

    public boolean isVisible() {
        return true;
    }

    public void refreshVisibility() {
        if (mVisibilityListener != null && overridesItemVisibility()) {
            mVisibilityListener.onActionProviderVisibilityChanged(isVisible());
        }
    }

    public boolean onPerformDefaultAction() {
        return false;
    }

    public boolean hasSubMenu() {
        return false;
    }

    public void onPrepareSubMenu(SubMenu subMenu) {
    }

    public void setVisibilityListener(VisibilityListener listener) {
        this.mVisibilityListener = listener;
    }
}
