// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.app.LocalActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import java.util.ArrayList;
import java.util.List;

/** Container for a tabbed window view hosting a TabWidget and a FrameLayout tab content area. */
@Deprecated
public class TabHost extends FrameLayout implements ViewTreeObserver.OnTouchModeChangeListener {
    public interface OnTabChangeListener {
        void onTabChanged(String tabId);
    }

    public interface TabContentFactory {
        View createTabContent(String tag);
    }

    private static final int ID_TABS = 0x01020013;
    private static final int ID_TABCONTENT = 0x01020011;

    private TabWidget mTabWidget;
    private FrameLayout mTabContent;
    private final List<TabSpec> mTabSpecs = new ArrayList<TabSpec>(2);
    protected int mCurrentTab = -1;
    private View mCurrentView = null;
    protected LocalActivityManager mLocalActivityManager = null;
    private OnTabChangeListener mOnTabChangeListener;

    public TabHost(Context context) {
        this(context, null);
    }

    public TabHost(Context context, AttributeSet attrs) {
        this(context, attrs, 0x01010083);
    }

    public TabHost(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TabHost(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initTabHost();
    }

    private void initTabHost() {
        setFocusableInTouchMode(true);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        mCurrentTab = -1;
        mCurrentView = null;
    }

    public TabSpec newTabSpec(String tag) {
        if (tag == null) {
            throw new IllegalArgumentException("tag must be non-null");
        }
        return new TabSpec(tag);
    }

    public void setup() {
        mTabWidget = findViewById(ID_TABS);
        if (mTabWidget == null) {
            throw new RuntimeException(
                    "Your TabHost must have a TabWidget whose id attribute is 'android.R.id.tabs'");
        }
        mTabWidget.setTabSelectionListener(new TabWidget.OnTabSelectionChanged() {
            @Override
            public void onTabSelectionChanged(int tabIndex, boolean clicked) {
                setCurrentTab(tabIndex);
                if (clicked && mTabContent != null) {
                    mTabContent.requestFocus(View.FOCUS_FORWARD);
                }
            }
        });
        mTabContent = findViewById(ID_TABCONTENT);
        if (mTabContent == null) {
            throw new RuntimeException(
                    "Your TabHost must have a FrameLayout whose id attribute is "
                            + "'android.R.id.tabcontent'");
        }
    }

    public void setup(LocalActivityManager activityGroup) {
        setup();
        mLocalActivityManager = activityGroup;
    }

    @Override
    public void onTouchModeChanged(boolean isInTouchMode) {
    }

    public void addTab(TabSpec tabSpec) {
        if (tabSpec.mIndicatorStrategy == null) {
            throw new IllegalArgumentException("you must specify a way to create the tab indicator.");
        }
        if (tabSpec.mContentStrategy == null) {
            throw new IllegalArgumentException("you must specify a way to create the tab content");
        }
        View tabIndicator = tabSpec.mIndicatorStrategy.createIndicatorView();
        mTabWidget.addView(tabIndicator);
        mTabSpecs.add(tabSpec);
        if (mCurrentTab == -1) {
            setCurrentTab(0);
        }
    }

    public void clearAllTabs() {
        if (mTabWidget != null) {
            mTabWidget.removeAllViews();
        }
        initTabHost();
        if (mTabContent != null) {
            mTabContent.removeAllViews();
        }
        mTabSpecs.clear();
        requestLayout();
        invalidate();
    }

    public TabWidget getTabWidget() {
        return mTabWidget;
    }

    public int getCurrentTab() {
        return mCurrentTab;
    }

    public String getCurrentTabTag() {
        if (mCurrentTab >= 0 && mCurrentTab < mTabSpecs.size()) {
            return mTabSpecs.get(mCurrentTab).getTag();
        }
        return null;
    }

    public View getCurrentTabView() {
        if (mCurrentTab >= 0 && mCurrentTab < mTabSpecs.size() && mTabWidget != null) {
            return mTabWidget.getChildTabViewAt(mCurrentTab);
        }
        return null;
    }

    public View getCurrentView() {
        return mCurrentView;
    }

    public void setCurrentTabByTag(String tag) {
        for (int i = 0, count = mTabSpecs.size(); i < count; i++) {
            if (mTabSpecs.get(i).getTag().equals(tag)) {
                setCurrentTab(i);
                break;
            }
        }
    }

    public FrameLayout getTabContentView() {
        return mTabContent;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void dispatchWindowFocusChanged(boolean hasFocus) {
        if (mCurrentView != null) {
            mCurrentView.dispatchWindowFocusChanged(hasFocus);
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return TabHost.class.getName();
    }

    public void setCurrentTab(int index) {
        if (index < 0 || index >= mTabSpecs.size()) {
            return;
        }
        if (index == mCurrentTab) {
            return;
        }
        if (mCurrentTab != -1) {
            mTabSpecs.get(mCurrentTab).mContentStrategy.tabClosed();
        }
        mCurrentTab = index;
        final TabHost.TabSpec spec = mTabSpecs.get(index);
        if (mTabWidget != null) {
            mTabWidget.focusCurrentTab(mCurrentTab);
        }
        mCurrentView = spec.mContentStrategy.getContentView();
        if (mCurrentView != null && mCurrentView.getParent() == null && mTabContent != null) {
            mTabContent.addView(
                    mCurrentView,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
        }
        invokeOnTabChangeListener();
    }

    public void setOnTabChangedListener(OnTabChangeListener l) {
        mOnTabChangeListener = l;
    }

    private void invokeOnTabChangeListener() {
        if (mOnTabChangeListener != null) {
            mOnTabChangeListener.onTabChanged(getCurrentTabTag());
        }
    }

    private interface IndicatorStrategy {
        View createIndicatorView();
    }

    private interface ContentStrategy {
        View getContentView();

        void tabClosed();
    }

    public class TabSpec {
        private final String mTag;
        private IndicatorStrategy mIndicatorStrategy;
        private ContentStrategy mContentStrategy;

        private TabSpec(String tag) {
            mTag = tag;
        }

        public TabSpec setIndicator(CharSequence label) {
            mIndicatorStrategy = new LabelAndIconIndicatorStrategy(label, null);
            return this;
        }

        public TabSpec setIndicator(CharSequence label, Drawable icon) {
            mIndicatorStrategy = new LabelAndIconIndicatorStrategy(label, icon);
            return this;
        }

        public TabSpec setIndicator(View view) {
            final View indicatorView = view;
            mIndicatorStrategy = new IndicatorStrategy() {
                @Override
                public View createIndicatorView() {
                    return indicatorView;
                }
            };
            return this;
        }

        public TabSpec setContent(int viewId) {
            mContentStrategy = new ViewIdContentStrategy(viewId);
            return this;
        }

        public TabSpec setContent(TabContentFactory contentFactory) {
            mContentStrategy = new FactoryContentStrategy(mTag, contentFactory);
            return this;
        }

        public TabSpec setContent(Intent intent) {
            mContentStrategy = new IntentContentStrategy(mTag, intent);
            return this;
        }

        public String getTag() {
            return mTag;
        }
    }

    private final class LabelAndIconIndicatorStrategy implements IndicatorStrategy {
        private final CharSequence mLabel;
        private final Drawable mIcon;

        LabelAndIconIndicatorStrategy(CharSequence label, Drawable icon) {
            mLabel = label;
            mIcon = icon;
        }

        @Override
        public View createIndicatorView() {
            final Context context = getContext();
            LinearLayout tab = new LinearLayout(context);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            int pad = Math.round(8f * context.getResources().getDisplayMetrics().density);
            tab.setPadding(pad, pad, pad, pad);
            if (mIcon != null) {
                ImageView iv = new ImageView(context);
                iv.setImageDrawable(mIcon);
                tab.addView(iv, new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            }
            TextView tv = new TextView(context);
            tv.setText(mLabel != null ? mLabel : "");
            tv.setGravity(Gravity.CENTER);
            tab.addView(tv, new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            return tab;
        }
    }

    private final class ViewIdContentStrategy implements ContentStrategy {
        private final View mView;

        ViewIdContentStrategy(int viewId) {
            if (mTabContent != null) {
                mView = mTabContent.findViewById(viewId);
                if (mView != null) {
                    mView.setVisibility(View.GONE);
                } else {
                    throw new RuntimeException(
                            "Could not create tab content because could not find view with id "
                                    + viewId);
                }
            } else {
                throw new IllegalStateException("Did you forget to call 'public void setup()'?");
            }
        }

        @Override
        public View getContentView() {
            mView.setVisibility(View.VISIBLE);
            return mView;
        }

        @Override
        public void tabClosed() {
            mView.setVisibility(View.GONE);
        }
    }

    private static final class FactoryContentStrategy implements ContentStrategy {
        private View mTabContent;
        private final CharSequence mTag;
        private final TabContentFactory mFactory;

        FactoryContentStrategy(CharSequence tag, TabContentFactory factory) {
            mTag = tag;
            mFactory = factory;
        }

        @Override
        public View getContentView() {
            if (mTabContent == null) {
                mTabContent = mFactory.createTabContent(mTag.toString());
            }
            if (mTabContent != null) {
                mTabContent.setVisibility(View.VISIBLE);
            }
            return mTabContent;
        }

        @Override
        public void tabClosed() {
            if (mTabContent != null) {
                mTabContent.setVisibility(View.GONE);
            }
        }
    }

    private final class IntentContentStrategy implements ContentStrategy {
        private final String mTag;
        private final Intent mIntent;
        private View mLaunchedView;

        IntentContentStrategy(String tag, Intent intent) {
            mTag = tag;
            mIntent = intent;
        }

        @Override
        public View getContentView() {
            if (mLocalActivityManager == null) {
                throw new IllegalStateException(
                        "Did you forget to call 'public void setup(LocalActivityManager activityGroup)'?");
            }
            final Window w = mLocalActivityManager.startActivity(mTag, mIntent);
            final View wd = w != null ? w.getDecorView() : null;
            if (mLaunchedView != wd && mLaunchedView != null) {
                if (mLaunchedView.getParent() != null && mTabContent != null) {
                    mTabContent.removeView(mLaunchedView);
                }
            }
            mLaunchedView = wd;
            if (mLaunchedView != null) {
                mLaunchedView.setVisibility(View.VISIBLE);
                mLaunchedView.setFocusableInTouchMode(true);
                if (mLaunchedView instanceof ViewGroup) {
                    ((ViewGroup) mLaunchedView).setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
                }
            }
            return mLaunchedView;
        }

        @Override
        public void tabClosed() {
            if (mLaunchedView != null) {
                mLaunchedView.setVisibility(View.GONE);
            }
        }
    }
}
