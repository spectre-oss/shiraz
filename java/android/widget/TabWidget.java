// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;

/** Horizontal row of tab indicator labels and optional bottom strip drawables. */
@Deprecated
public class TabWidget extends LinearLayout implements View.OnFocusChangeListener {
    interface OnTabSelectionChanged {
        void onTabSelectionChanged(int tabIndex, boolean clicked);
    }

    private OnTabSelectionChanged mSelectionChangedListener;
    private int mSelectedTab = -1;
    private Drawable mLeftStrip;
    private Drawable mRightStrip;
    private boolean mDrawBottomStrips = true;
    private boolean mStripMoved;

    public TabWidget(Context context) {
        this(context, null);
    }

    public TabWidget(Context context, AttributeSet attrs) {
        this(context, attrs, 0x01010083);
    }

    public TabWidget(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TabWidget(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setChildrenDrawingOrderEnabled(true);
        setOrientation(HORIZONTAL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mStripMoved = true;
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (mSelectedTab == -1) {
            return i;
        }
        if (i == childCount - 1) {
            return mSelectedTab;
        } else if (i >= mSelectedTab) {
            return i + 1;
        } else {
            return i;
        }
    }

    public View getChildTabViewAt(int index) {
        return getChildAt(index);
    }

    public int getTabCount() {
        return getChildCount();
    }

    @Override
    public void setDividerDrawable(Drawable drawable) {
        super.setDividerDrawable(drawable);
    }

    public void setDividerDrawable(int resId) {
        setDividerDrawable(resId == 0 ? null : getContext().getDrawable(resId));
    }

    public void setLeftStripDrawable(Drawable drawable) {
        mLeftStrip = drawable;
        requestLayout();
        invalidate();
    }

    public void setLeftStripDrawable(int resId) {
        setLeftStripDrawable(resId == 0 ? null : getContext().getDrawable(resId));
    }

    public Drawable getLeftStripDrawable() {
        return mLeftStrip;
    }

    public void setRightStripDrawable(Drawable drawable) {
        mRightStrip = drawable;
        requestLayout();
        invalidate();
    }

    public void setRightStripDrawable(int resId) {
        setRightStripDrawable(resId == 0 ? null : getContext().getDrawable(resId));
    }

    public Drawable getRightStripDrawable() {
        return mRightStrip;
    }

    public void setStripEnabled(boolean stripEnabled) {
        mDrawBottomStrips = stripEnabled;
        invalidate();
    }

    public boolean isStripEnabled() {
        return mDrawBottomStrips;
    }

    @Override
    public void childDrawableStateChanged(View child) {
        if (getTabCount() > 0 && child == getChildTabViewAt(mSelectedTab)) {
            invalidate();
        }
        super.childDrawableStateChanged(child);
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (getTabCount() == 0 || !mDrawBottomStrips || mSelectedTab < 0) {
            return;
        }
        final View selectedChild = getChildTabViewAt(mSelectedTab);
        final Drawable leftStrip = mLeftStrip;
        final Drawable rightStrip = mRightStrip;
        if (selectedChild != null) {
            if (leftStrip != null) {
                leftStrip.setState(getDrawableState());
                if (mStripMoved) {
                    leftStrip.setBounds(
                            Math.min(0, selectedChild.getLeft() - leftStrip.getIntrinsicWidth()),
                            getHeight() - leftStrip.getIntrinsicHeight(),
                            selectedChild.getLeft(),
                            getHeight());
                }
                leftStrip.draw(canvas);
            }
            if (rightStrip != null) {
                rightStrip.setState(getDrawableState());
                if (mStripMoved) {
                    rightStrip.setBounds(
                            selectedChild.getRight(),
                            getHeight() - rightStrip.getIntrinsicHeight(),
                            Math.max(getWidth(), selectedChild.getRight() + rightStrip.getIntrinsicWidth()),
                            getHeight());
                }
                rightStrip.draw(canvas);
            }
            mStripMoved = false;
        }
    }

    public void setCurrentTab(int index) {
        if (index < 0 || index >= getTabCount() || index == mSelectedTab) {
            return;
        }
        if (mSelectedTab != -1) {
            View prev = getChildTabViewAt(mSelectedTab);
            if (prev != null) {
                prev.setSelected(false);
            }
        }
        mSelectedTab = index;
        View cur = getChildTabViewAt(mSelectedTab);
        if (cur != null) {
            cur.setSelected(true);
        }
        mStripMoved = true;
        invalidate();
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return TabWidget.class.getName();
    }

    public void focusCurrentTab(int index) {
        final int oldTab = mSelectedTab;
        setCurrentTab(index);
        if (oldTab != index) {
            View cur = getChildTabViewAt(index);
            if (cur != null) {
                cur.requestFocus();
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        final int count = getTabCount();
        for (int i = 0; i < count; i++) {
            View child = getChildTabViewAt(i);
            if (child != null) {
                child.setEnabled(enabled);
            }
        }
    }

    @Override
    public void addView(View child) {
        if (child.getLayoutParams() == null) {
            final LinearLayout.LayoutParams lp = new LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            lp.setMargins(0, 0, 0, 0);
            child.setLayoutParams(lp);
        }
        child.setFocusable(true);
        child.setClickable(true);
        super.addView(child);
        final int tabIndex = getTabCount() - 1;
        child.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelectionChangedListener != null) {
                    mSelectionChangedListener.onTabSelectionChanged(tabIndex, true);
                }
            }
        });
        child.setOnFocusChangeListener(this);
    }

    @Override
    public void removeAllViews() {
        super.removeAllViews();
        mSelectedTab = -1;
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        if (!isEnabled()) {
            return null;
        }
        return PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_HAND);
    }

    void setTabSelectionListener(OnTabSelectionChanged listener) {
        mSelectionChangedListener = listener;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v == this && hasFocus && getTabCount() > 0 && mSelectedTab != -1) {
            getChildTabViewAt(mSelectedTab).requestFocus();
            return;
        }
        if (hasFocus) {
            int numTabs = getTabCount();
            for (int i = 0; i < numTabs; i++) {
                if (getChildTabViewAt(i) == v) {
                    setCurrentTab(i);
                    if (mSelectionChangedListener != null) {
                        mSelectionChangedListener.onTabSelectionChanged(i, false);
                    }
                    break;
                }
            }
        }
    }
}
