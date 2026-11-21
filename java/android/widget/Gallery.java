// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Transformation;

/** Center-locked horizontally scrolling list view backed by a SpinnerAdapter. */
@Deprecated
public class Gallery extends AbsSpinner implements GestureDetector.OnGestureListener {
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    private int mSpacing = 0;
    private int mAnimationDuration = 400;
    private float mUnselectedAlpha = 0.5f;
    private int mGravity = Gravity.CENTER_VERTICAL;
    private final GestureDetector mGestureDetector;
    private boolean mShouldCallbackDuringFling = true;
    private AdapterContextMenuInfo mContextMenuInfo;

    public Gallery(Context context) {
        this(context, null);
    }

    public Gallery(Context context, AttributeSet attrs) {
        this(context, attrs, 0x01010070);
    }

    public Gallery(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public Gallery(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mGestureDetector = new GestureDetector(context, this);
        mGestureDetector.setIsLongpressEnabled(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    public void setCallbackDuringFling(boolean shouldCallback) {
        mShouldCallbackDuringFling = shouldCallback;
    }

    public void setAnimationDuration(int animationDurationMillis) {
        mAnimationDuration = animationDurationMillis;
    }

    public void setSpacing(int spacing) {
        mSpacing = spacing;
        requestLayout();
    }

    public void setUnselectedAlpha(float unselectedAlpha) {
        mUnselectedAlpha = unselectedAlpha;
        invalidate();
    }

    protected boolean getChildStaticTransformation(View child, Transformation t) {
        t.clear();
        t.setAlpha(child == getSelectedView() ? 1.0f : mUnselectedAlpha);
        return true;
    }

    @Override
    protected int computeHorizontalScrollExtent() {
        return 1;
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        return Math.max(0, getSelectedItemPosition());
    }

    @Override
    protected int computeHorizontalScrollRange() {
        return getCount();
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new Gallery.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        removeAllViewsInLayout();
        SpinnerAdapter adapter = getAdapter();
        int count = getCount();
        if (adapter == null || count == 0) {
            firstPosition = 0;
            return;
        }
        int sel = getSelectedItemPosition();
        if (sel < 0 || sel >= count) {
            sel = 0;
            shirazSelect(0);
        }
        firstPosition = sel;
        int centerX = (r - l - getPaddingLeft() - getPaddingRight()) / 2 + getPaddingLeft();
        int availH = Math.max(0, b - t - getPaddingTop() - getPaddingBottom());
        View selView = makeAndAddView(sel, 0, centerX, true, availH);
        if (selView != null) {
            int nextX = selView.getRight() + mSpacing;
            for (int pos = sel + 1; pos < count && nextX < r - l - getPaddingRight(); pos++) {
                View child = makeAndAddView(pos, getChildCount(), nextX, true, availH);
                if (child == null) {
                    break;
                }
                nextX = child.getRight() + mSpacing;
            }
            int prevX = selView.getLeft() - mSpacing;
            for (int pos = sel - 1; pos >= 0 && prevX > getPaddingLeft(); pos--) {
                View child = makeAndAddView(pos, 0, prevX, false, availH);
                if (child == null) {
                    break;
                }
                firstPosition = pos;
                prevX = child.getLeft() - mSpacing;
            }
        }
        invalidate();
    }

    private View makeAndAddView(int position, int addIndex, int x, boolean fromLeft, int availH) {
        SpinnerAdapter adapter = getAdapter();
        if (adapter == null) {
            return null;
        }
        View child = adapter.getView(position, null, this);
        if (child == null) {
            return null;
        }
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp == null) {
            lp = generateDefaultLayoutParams();
        }
        shirazAddRow(child, addIndex, lp);
        child.setSelected(position == getSelectedItemPosition());
        int childHeightSpec = ViewGroup.getChildMeasureSpec(
                mHeightMeasureSpec, mSpinnerPadding.top + mSpinnerPadding.bottom, lp.height);
        int childWidthSpec = ViewGroup.getChildMeasureSpec(
                mWidthMeasureSpec, mSpinnerPadding.left + mSpinnerPadding.right, lp.width);
        child.measure(childWidthSpec, childHeightSpec);
        int w = child.getMeasuredWidth();
        int h = child.getMeasuredHeight();
        int top = getPaddingTop();
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.CENTER_VERTICAL) {
            top += (availH - h) / 2;
        } else if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
            top += availH - h;
        }
        int left;
        if (position == getSelectedItemPosition()) {
            left = x - w / 2;
        } else if (fromLeft) {
            left = x;
        } else {
            left = x - w;
        }
        child.layout(left, top, left + w, top + h);
        return child;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mGestureDetector.onTouchEvent(event);
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        int pos = pointToPosition((int) e.getX(), (int) e.getY());
        if (pos >= 0) {
            setSelection(pos);
            View v = getChildAt(pos - firstPosition);
            performItemClick(v, pos, getItemIdAtPosition(pos));
            return true;
        }
        return false;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (velocityX < 0) {
            moveNext();
        } else if (velocityX > 0) {
            movePrevious();
        }
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (distanceX > 24 && getSelectedItemPosition() + 1 < getCount()) {
            setSelection(getSelectedItemPosition() + 1);
        } else if (distanceX < -24 && getSelectedItemPosition() > 0) {
            setSelection(getSelectedItemPosition() - 1);
        }
        return true;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        int pos = pointToPosition((int) e.getX(), (int) e.getY());
        if (pos >= 0) {
            View v = getChildAt(pos - firstPosition);
            OnItemLongClickListener l = getOnItemLongClickListener();
            if (l != null) {
                l.onItemLongClick(this, v, pos, getItemIdAtPosition(pos));
            }
        }
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    public void dispatchSetSelected(boolean selected) {
    }

    protected void dispatchSetPressed(boolean pressed) {
        View selectedView = getSelectedView();
        if (selectedView != null) {
            selectedView.setPressed(pressed);
        }
    }

    protected ContextMenu.ContextMenuInfo getContextMenuInfo() {
        return mContextMenuInfo;
    }

    public boolean showContextMenuForChild(View originalView) {
        int longPressPosition = getPositionForView(originalView);
        if (longPressPosition < 0) {
            return false;
        }
        mContextMenuInfo = new AdapterContextMenuInfo(
                originalView, longPressPosition, getItemIdAtPosition(longPressPosition));
        return showContextMenu();
    }

    public boolean showContextMenuForChild(View originalView, float x, float y) {
        return showContextMenuForChild(originalView);
    }

    @Override
    public boolean showContextMenu() {
        int sel = getSelectedItemPosition();
        if (isPressed() && sel >= 0) {
            View v = getChildAt(sel - firstPosition);
            mContextMenuInfo = new AdapterContextMenuInfo(v, sel, getSelectedItemId());
        }
        return super.showContextMenu();
    }

    @Override
    public boolean showContextMenu(float x, float y) {
        return showContextMenu();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return event.dispatch(this, null, null);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (movePrevious()) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (moveNext()) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            int sel = getSelectedItemPosition();
            if (sel >= 0 && getCount() > 0) {
                performItemClick(getSelectedView(), sel, getSelectedItemId());
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean movePrevious() {
        int sel = getSelectedItemPosition();
        if (getCount() > 0 && sel > 0) {
            setSelection(sel - 1);
            return true;
        }
        return false;
    }

    private boolean moveNext() {
        int sel = getSelectedItemPosition();
        if (getCount() > 0 && sel < getCount() - 1) {
            setSelection(sel + 1);
            return true;
        }
        return false;
    }

    public void setGravity(int gravity) {
        if (mGravity != gravity) {
            mGravity = gravity;
            requestLayout();
        }
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        int selectedIndex = getSelectedItemPosition() - firstPosition;
        if (selectedIndex < 0) {
            return i;
        }
        if (i == childCount - 1) {
            return selectedIndex;
        } else if (i >= selectedIndex) {
            return i + 1;
        } else {
            return i;
        }
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        View selected = getSelectedView();
        if (gainFocus && selected != null) {
            selected.requestFocus(direction);
            selected.setSelected(true);
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Gallery.class.getName();
    }
}
