// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

@Deprecated
public class FragmentBreadCrumbs extends ViewGroup implements FragmentManager.OnBackStackChangedListener {
    public interface OnBreadCrumbClickListener {
        boolean onBreadCrumbClick(FragmentManager.BackStackEntry backStack, int flags);
    }

    private Activity mActivity;
    private int mMaxVisible = -1;
    private CharSequence mTitle;
    private CharSequence mShortTitle;
    private CharSequence mParentTitle;
    private CharSequence mParentShortTitle;
    private View.OnClickListener mParentClickListener;
    private OnBreadCrumbClickListener mOnBreadCrumbClickListener;

    public FragmentBreadCrumbs(Context context) {
        this(context, null);
    }

    public FragmentBreadCrumbs(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FragmentBreadCrumbs(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setActivity(Activity a) {
        mActivity = a;
        if (a != null && a.getFragmentManager() != null) {
            a.getFragmentManager().addOnBackStackChangedListener(this);
        }
        onBackStackChanged();
    }

    public void setMaxVisible(int visibleCrumbs) {
        if (visibleCrumbs < 1) {
            throw new IllegalArgumentException("visibleCrumbs must be greater than zero");
        }
        mMaxVisible = visibleCrumbs;
    }

    public void setParentTitle(CharSequence title, CharSequence shortTitle, View.OnClickListener listener) {
        mParentTitle = title;
        mParentShortTitle = shortTitle;
        mParentClickListener = listener;
    }

    public void setOnBreadCrumbClickListener(OnBreadCrumbClickListener listener) {
        mOnBreadCrumbClickListener = listener;
    }

    public void setTitle(CharSequence title, CharSequence shortTitle) {
        mTitle = title;
        mShortTitle = shortTitle;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int left = getPaddingLeft();
        int top = getPaddingTop();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                int w = child.getMeasuredWidth();
                int h = child.getMeasuredHeight();
                child.layout(left, top, left + w, top + h);
                left += w;
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();
        int maxHeight = 0;
        int totalWidth = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
                totalWidth += child.getMeasuredWidth();
                maxHeight = Math.max(maxHeight, child.getMeasuredHeight());
            }
        }
        totalWidth += getPaddingLeft() + getPaddingRight();
        maxHeight += getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(
                resolveSizeAndState(Math.max(totalWidth, getSuggestedMinimumWidth()), widthMeasureSpec, 0),
                resolveSizeAndState(Math.max(maxHeight, getSuggestedMinimumHeight()), heightMeasureSpec, 0));
    }

    @Override
    public void onBackStackChanged() {
        requestLayout();
    }
}
