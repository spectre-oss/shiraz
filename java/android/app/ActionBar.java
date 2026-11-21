// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SpinnerAdapter;
import java.util.ArrayList;

/**
 * Minimal action bar state. Shiraz does not draw an action bar yet: title,
 * subtitle, display flags and the custom view are kept so apps can read them
 * back, and the custom view is inflated so its children can be looked up.
 */
public abstract class ActionBar {
    public static final int NAVIGATION_MODE_STANDARD = 0;
    public static final int NAVIGATION_MODE_LIST = 1;
    public static final int NAVIGATION_MODE_TABS = 2;

    public static final int DISPLAY_USE_LOGO = 1;
    public static final int DISPLAY_SHOW_HOME = 2;
    public static final int DISPLAY_HOME_AS_UP = 4;
    public static final int DISPLAY_SHOW_TITLE = 8;
    public static final int DISPLAY_SHOW_CUSTOM = 16;

    public interface OnNavigationListener {
        boolean onNavigationItemSelected(int itemPosition, long itemId);
    }

    public interface OnMenuVisibilityListener {
        void onMenuVisibilityChanged(boolean isVisible);
    }

    @Deprecated
    public static abstract class Tab {
        public static final int INVALID_POSITION = -1;

        public Tab() {}
        public abstract int getPosition();
        public abstract Drawable getIcon();
        public abstract CharSequence getText();
        public abstract Tab setIcon(Drawable icon);
        public abstract Tab setIcon(int resId);
        public abstract Tab setText(CharSequence text);
        public abstract Tab setText(int resId);
        public abstract Tab setCustomView(View view);
        public abstract Tab setCustomView(int layoutResId);
        public abstract View getCustomView();
        public abstract Tab setTag(Object obj);
        public abstract Object getTag();
        public abstract Tab setTabListener(TabListener listener);
        public abstract void select();
        public abstract Tab setContentDescription(int resId);
        public abstract Tab setContentDescription(CharSequence contentDesc);
        public abstract CharSequence getContentDescription();
    }

    @Deprecated
    public interface TabListener {
        void onTabSelected(Tab tab, FragmentTransaction ft);
        void onTabUnselected(Tab tab, FragmentTransaction ft);
        void onTabReselected(Tab tab, FragmentTransaction ft);
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public int gravity;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, int gravity) {
            super(width, height);
            this.gravity = gravity;
        }

        public LayoutParams(int gravity) {
            this(WRAP_CONTENT, MATCH_PARENT, gravity);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            if (source != null) {
                this.gravity = source.gravity;
            }
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    private boolean hideOnContentScroll;
    private int hideOffset;
    private float elevation;

    public ActionBar() {}

    public abstract void setCustomView(View view);
    public abstract void setCustomView(View view, LayoutParams layoutParams);
    public abstract void setCustomView(int resId);
    public abstract void setIcon(int resId);
    public abstract void setIcon(Drawable icon);
    public abstract void setLogo(int resId);
    public abstract void setLogo(Drawable logo);
    @Deprecated
    public abstract void setListNavigationCallbacks(SpinnerAdapter adapter, OnNavigationListener callback);
    @Deprecated
    public abstract void setSelectedNavigationItem(int position);
    @Deprecated
    public abstract int getSelectedNavigationIndex();
    @Deprecated
    public abstract int getNavigationItemCount();
    public abstract void setTitle(CharSequence title);
    public abstract void setTitle(int resId);
    public abstract void setSubtitle(CharSequence subtitle);
    public abstract void setSubtitle(int resId);
    public abstract void setDisplayOptions(int options);
    public abstract void setDisplayOptions(int options, int mask);
    public abstract void setDisplayUseLogoEnabled(boolean useLogo);
    public abstract void setDisplayShowHomeEnabled(boolean showHome);
    public abstract void setDisplayHomeAsUpEnabled(boolean showHomeAsUp);
    public abstract void setDisplayShowTitleEnabled(boolean showTitle);
    public abstract void setDisplayShowCustomEnabled(boolean showCustom);
    public abstract void setBackgroundDrawable(Drawable d);
    public void setStackedBackgroundDrawable(Drawable d) {}
    public void setSplitBackgroundDrawable(Drawable d) {}
    public abstract View getCustomView();
    public abstract CharSequence getTitle();
    public abstract CharSequence getSubtitle();
    @Deprecated
    public abstract int getNavigationMode();
    @Deprecated
    public abstract void setNavigationMode(int mode);
    public abstract int getDisplayOptions();
    @Deprecated
    public abstract Tab newTab();
    @Deprecated
    public abstract void addTab(Tab tab);
    @Deprecated
    public abstract void addTab(Tab tab, boolean setSelected);
    @Deprecated
    public abstract void addTab(Tab tab, int position);
    @Deprecated
    public abstract void addTab(Tab tab, int position, boolean setSelected);
    @Deprecated
    public abstract void removeTab(Tab tab);
    @Deprecated
    public abstract void removeTabAt(int position);
    @Deprecated
    public abstract void removeAllTabs();
    @Deprecated
    public abstract void selectTab(Tab tab);
    @Deprecated
    public abstract Tab getSelectedTab();
    @Deprecated
    public abstract Tab getTabAt(int index);
    @Deprecated
    public abstract int getTabCount();
    public abstract int getHeight();
    public abstract void show();
    public abstract void hide();
    public abstract boolean isShowing();
    public abstract void addOnMenuVisibilityListener(OnMenuVisibilityListener listener);
    public abstract void removeOnMenuVisibilityListener(OnMenuVisibilityListener listener);

    public void setHomeButtonEnabled(boolean enabled) {}
    public Context getThemedContext() { return null; }
    public void setHomeAsUpIndicator(Drawable indicator) {}
    public void setHomeAsUpIndicator(int resId) {}
    public void setHomeActionContentDescription(CharSequence description) {}
    public void setHomeActionContentDescription(int resId) {}

    public void setHideOnContentScrollEnabled(boolean hideOnContentScroll) {
        this.hideOnContentScroll = hideOnContentScroll;
    }

    public boolean isHideOnContentScrollEnabled() {
        return hideOnContentScroll;
    }

    public int getHideOffset() {
        return hideOffset;
    }

    public void setHideOffset(int offset) {
        this.hideOffset = offset;
    }

    public void setElevation(float elevation) {
        this.elevation = elevation;
    }

    public float getElevation() {
        return elevation;
    }

    static final class Impl extends ActionBar {
        private final Activity activity;
        private View custom;
        private CharSequence title;
        private CharSequence subtitle;
        private int options = DISPLAY_SHOW_HOME | DISPLAY_SHOW_TITLE;
        private int navigationMode = NAVIGATION_MODE_STANDARD;
        private SpinnerAdapter listAdapter;
        private OnNavigationListener navigationListener;
        private int selectedNavigationIndex = -1;
        private boolean showing = true;
        private final android.widget.Toolbar bar;
        private final ArrayList<TabImpl> tabs = new ArrayList<TabImpl>();
        private TabImpl selectedTab;
        private final ArrayList<OnMenuVisibilityListener> menuVisibilityListeners =
                new ArrayList<OnMenuVisibilityListener>();

        private final class TabImpl extends Tab {
            int position = INVALID_POSITION;
            Drawable icon;
            CharSequence text;
            CharSequence contentDesc;
            View customView;
            Object tag;
            TabListener callback;

            @Override public int getPosition() { return position; }
            @Override public Drawable getIcon() { return icon; }
            @Override public CharSequence getText() { return text; }
            @Override public Tab setIcon(Drawable d) { icon = d; return this; }
            @Override public Tab setIcon(int resId) { icon = resId != 0 ? activity.getDrawable(resId) : null; return this; }
            @Override public Tab setText(CharSequence t) { text = t; return this; }
            @Override public Tab setText(int resId) { text = activity.getText(resId); return this; }
            @Override public Tab setCustomView(View view) { customView = view; return this; }
            @Override public Tab setCustomView(int layoutResId) {
                customView = LayoutInflater.from(activity).inflate(layoutResId, null, false);
                return this;
            }
            @Override public View getCustomView() { return customView; }
            @Override public Tab setTag(Object obj) { tag = obj; return this; }
            @Override public Object getTag() { return tag; }
            @Override public Tab setTabListener(TabListener listener) { callback = listener; return this; }
            @Override public void select() { selectTab(this); }
            @Override public Tab setContentDescription(int resId) { contentDesc = activity.getText(resId); return this; }
            @Override public Tab setContentDescription(CharSequence desc) { contentDesc = desc; return this; }
            @Override public CharSequence getContentDescription() { return contentDesc; }
        }

        Impl(Activity activity) {
            this.activity = activity;
            title = activity.getTitle();
            bar = activity.getWindow().shirazActionBarView();
        }

        @Override public Context getThemedContext() { return activity; }
        @Override public void setCustomView(View view) { custom = view; }
        @Override public void setCustomView(View view, LayoutParams layoutParams) {
            if (view != null && layoutParams != null) {
                view.setLayoutParams(layoutParams);
            }
            custom = view;
        }
        @Override public void setCustomView(int resource) {
            Context context = activity;
            custom = LayoutInflater.from(context).inflate(resource, null, false);
        }
        @Override public View getCustomView() { return custom; }
        @Override public void setIcon(int resId) {}
        @Override public void setIcon(Drawable icon) {}
        @Override public void setLogo(int resId) {}
        @Override public void setLogo(Drawable logo) {}

        @Override
        public void setListNavigationCallbacks(SpinnerAdapter adapter, OnNavigationListener callback) {
            listAdapter = adapter;
            navigationListener = callback;
        }

        @Override
        public void setSelectedNavigationItem(int position) {
            if (navigationMode == NAVIGATION_MODE_TABS) {
                if (position >= 0 && position < tabs.size()) {
                    selectTab(tabs.get(position));
                }
            } else {
                selectedNavigationIndex = position;
                if (navigationListener != null && listAdapter != null && position >= 0 && position < listAdapter.getCount()) {
                    navigationListener.onNavigationItemSelected(position, listAdapter.getItemId(position));
                }
            }
        }

        @Override
        public int getSelectedNavigationIndex() {
            if (navigationMode == NAVIGATION_MODE_TABS) {
                return selectedTab != null ? selectedTab.getPosition() : -1;
            }
            if (navigationMode == NAVIGATION_MODE_LIST) {
                return selectedNavigationIndex;
            }
            return -1;
        }

        @Override
        public int getNavigationItemCount() {
            if (navigationMode == NAVIGATION_MODE_TABS) {
                return tabs.size();
            }
            if (navigationMode == NAVIGATION_MODE_LIST) {
                return listAdapter != null ? listAdapter.getCount() : 0;
            }
            return 0;
        }

        @Override public void setTitle(CharSequence value) { title = value; activity.setTitle(value); }
        @Override public void setTitle(int resource) { setTitle(activity.getString(resource)); }
        @Override public CharSequence getTitle() { return title; }
        @Override public void setSubtitle(CharSequence value) {
            subtitle = value;
            if (bar != null) bar.setSubtitle(value);
        }
        @Override public void setSubtitle(int resource) { setSubtitle(activity.getString(resource)); }
        @Override public CharSequence getSubtitle() { return subtitle; }
        @Override public void setDisplayOptions(int value) { options = value; }
        @Override public void setDisplayOptions(int value, int mask) { options = (options & ~mask) | (value & mask); }
        @Override public int getDisplayOptions() { return options; }
        @Override public void setDisplayHomeAsUpEnabled(boolean value) {
            setDisplayOptions(value ? DISPLAY_HOME_AS_UP : 0, DISPLAY_HOME_AS_UP);
        }
        @Override public void setDisplayShowCustomEnabled(boolean value) {
            setDisplayOptions(value ? DISPLAY_SHOW_CUSTOM : 0, DISPLAY_SHOW_CUSTOM);
        }
        @Override public void setDisplayShowTitleEnabled(boolean value) {
            setDisplayOptions(value ? DISPLAY_SHOW_TITLE : 0, DISPLAY_SHOW_TITLE);
        }
        @Override public void setDisplayShowHomeEnabled(boolean value) {
            setDisplayOptions(value ? DISPLAY_SHOW_HOME : 0, DISPLAY_SHOW_HOME);
        }
        @Override public void setDisplayUseLogoEnabled(boolean value) {
            setDisplayOptions(value ? DISPLAY_USE_LOGO : 0, DISPLAY_USE_LOGO);
        }
        @Override public void setBackgroundDrawable(Drawable d) {}
        @Override public int getNavigationMode() { return navigationMode; }
        @Override public void setNavigationMode(int mode) { navigationMode = mode; }

        @Override public Tab newTab() { return new TabImpl(); }
        @Override public void addTab(Tab tab) { addTab(tab, tabs.isEmpty()); }
        @Override public void addTab(Tab tab, boolean setSelected) { addTab(tab, tabs.size(), setSelected); }
        @Override public void addTab(Tab tab, int position) { addTab(tab, position, tabs.isEmpty()); }
        @Override public void addTab(Tab tab, int position, boolean setSelected) {
            TabImpl impl = (TabImpl) tab;
            tabs.add(position, impl);
            for (int i = position; i < tabs.size(); i++) {
                tabs.get(i).position = i;
            }
            if (setSelected) {
                selectTab(tab);
            }
        }
        @Override public void removeTab(Tab tab) {
            if (tab != null) {
                removeTabAt(tab.getPosition());
            }
        }
        @Override public void removeTabAt(int position) {
            if (position < 0 || position >= tabs.size()) return;
            TabImpl removed = tabs.remove(position);
            removed.position = Tab.INVALID_POSITION;
            for (int i = position; i < tabs.size(); i++) {
                tabs.get(i).position = i;
            }
            if (selectedTab == removed) {
                selectTab(tabs.isEmpty() ? null : tabs.get(Math.max(0, position - 1)));
            }
        }
        @Override public void removeAllTabs() {
            tabs.clear();
            if (selectedTab != null) {
                selectTab(null);
            }
        }
        @Override public void selectTab(Tab tab) {
            TabImpl next = (TabImpl) tab;
            if (selectedTab == next) {
                if (selectedTab != null && selectedTab.callback != null) {
                    selectedTab.callback.onTabReselected(selectedTab, null);
                }
                return;
            }
            if (selectedTab != null && selectedTab.callback != null) {
                selectedTab.callback.onTabUnselected(selectedTab, null);
            }
            selectedTab = next;
            if (selectedTab != null && selectedTab.callback != null) {
                selectedTab.callback.onTabSelected(selectedTab, null);
            }
        }
        @Override public Tab getSelectedTab() { return selectedTab; }
        @Override public Tab getTabAt(int index) {
            return (index >= 0 && index < tabs.size()) ? tabs.get(index) : null;
        }
        @Override public int getTabCount() { return tabs.size(); }

        @Override public void show() { showing = true; activity.getWindow().shirazSetActionBarShown(true); }
        @Override public void hide() { showing = false; activity.getWindow().shirazSetActionBarShown(false); }
        @Override public boolean isShowing() { return showing; }
        @Override public int getHeight() { return bar == null || !showing ? 0 : bar.getHeight(); }

        @Override
        public void addOnMenuVisibilityListener(OnMenuVisibilityListener listener) {
            if (listener != null && !menuVisibilityListeners.contains(listener)) {
                menuVisibilityListeners.add(listener);
            }
        }

        @Override
        public void removeOnMenuVisibilityListener(OnMenuVisibilityListener listener) {
            menuVisibilityListeners.remove(listener);
        }
    }
}
