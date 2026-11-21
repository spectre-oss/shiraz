// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabWidget;

@Deprecated
public class TabActivity extends ActivityGroup {
    private static final int ID_TABHOST = 0x01020012;
    private static final int ID_TABS = 0x01020013;
    private static final int ID_TABCONTENT = 0x01020011;

    private TabHost mTabHost;
    private String mDefaultTab;
    private int mDefaultTabIndex = -1;

    public TabActivity() {
    }

    public void setDefaultTab(String tag) {
        mDefaultTab = tag;
        mDefaultTabIndex = -1;
    }

    public void setDefaultTab(int index) {
        mDefaultTab = null;
        mDefaultTabIndex = index;
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        ensureTabHost();
        String cur = state.getString("currentTab");
        if (cur != null) {
            mTabHost.setCurrentTabByTag(cur);
        }
        if (mTabHost.getCurrentTab() < 0) {
            if (mDefaultTab != null) {
                mTabHost.setCurrentTabByTag(mDefaultTab);
            } else if (mDefaultTabIndex >= 0) {
                mTabHost.setCurrentTab(mDefaultTabIndex);
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle icicle) {
        super.onPostCreate(icicle);
        ensureTabHost();
        if (mTabHost.getCurrentTab() == -1) {
            mTabHost.setCurrentTab(0);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mTabHost != null) {
            String currentTabTag = mTabHost.getCurrentTabTag();
            if (currentTabTag != null) {
                outState.putString("currentTab", currentTabTag);
            }
        }
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        View found = findViewById(ID_TABHOST);
        if (found instanceof TabHost) {
            mTabHost = (TabHost) found;
            mTabHost.setup(getLocalActivityManager());
        }
    }

    private void ensureTabHost() {
        if (mTabHost == null) {
            TabHost host = new TabHost(this);
            host.setId(ID_TABHOST);
            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            TabWidget tabs = new TabWidget(this);
            tabs.setId(ID_TABS);
            FrameLayout content = new FrameLayout(this);
            content.setId(ID_TABCONTENT);
            layout.addView(tabs);
            layout.addView(content);
            host.addView(layout);
            setContentView(host);
            if (mTabHost == null) {
                mTabHost = host;
                mTabHost.setup(getLocalActivityManager());
            }
        }
    }

    @Override
    protected void onChildTitleChanged(Activity childActivity, CharSequence title) {
        if (getLocalActivityManager().getCurrentActivity() == childActivity && mTabHost != null) {
            View tabView = mTabHost.getCurrentTabView();
            if (tabView instanceof android.widget.TextView) {
                ((android.widget.TextView) tabView).setText(title);
            }
        }
    }

    public TabHost getTabHost() {
        ensureTabHost();
        return mTabHost;
    }

    public TabWidget getTabWidget() {
        return getTabHost().getTabWidget();
    }
}
