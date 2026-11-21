// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.view.ActionProvider;
import android.view.MenuItem;
import android.view.View;

public class MediaRouteActionProvider extends ActionProvider {
    private final Context mContext;
    private int mRouteTypes;
    private View.OnClickListener mExtendedSettingsListener;
    private MediaRouteButton mButton;

    public MediaRouteActionProvider(Context context) {
        super(context);
        mContext = context;
    }

    public void setRouteTypes(int types) {
        mRouteTypes = types;
        if (mButton != null) {
            mButton.setRouteTypes(types);
        }
    }

    public void setExtendedSettingsClickListener(View.OnClickListener listener) {
        mExtendedSettingsListener = listener;
        if (mButton != null) {
            mButton.setExtendedSettingsClickListener(listener);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public View onCreateActionView() {
        throw new UnsupportedOperationException("Use onCreateActionView(MenuItem) instead.");
    }

    @Override
    public View onCreateActionView(MenuItem item) {
        mButton = new MediaRouteButton(mContext);
        mButton.setRouteTypes(mRouteTypes);
        mButton.setExtendedSettingsClickListener(mExtendedSettingsListener);
        return mButton;
    }

    @Override
    public boolean onPerformDefaultAction() {
        if (mButton != null) {
            return mButton.performClick();
        }
        return false;
    }

    @Override
    public boolean overridesItemVisibility() {
        return true;
    }

    @Override
    public boolean isVisible() {
        return mRouteTypes != 0;
    }
}
