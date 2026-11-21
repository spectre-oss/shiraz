// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.animation.AnimatorInflater;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/** Base class for AdapterView subclasses that animate transitions between adapter views. */
public abstract class AdapterViewAnimator extends AdapterView<Adapter> implements Advanceable {
    private Adapter mAdapter;
    private DataSetObserver mDataSetObserver;
    private int mWhichChild = 0;
    private boolean mAnimateFirstTime = true;
    private boolean mLoopViews = true;
    private ObjectAnimator mInAnimation;
    private ObjectAnimator mOutAnimation;
    private View mCurrentChild;

    public AdapterViewAnimator(Context context) {
        this(context, null);
    }

    public AdapterViewAnimator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AdapterViewAnimator(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AdapterViewAnimator(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (attrs != null) {
            shiraz.runtime.XmlAttributes a =
                    new shiraz.runtime.XmlAttributes(context, attrs, defStyleAttr, defStyleRes);
            mAnimateFirstTime = a.bool("animateFirstView", true);
            mLoopViews = a.bool("loopViews", false);
        }
    }

    public void setDisplayedChild(int whichChild) {
        int count = getCount();
        if (count <= 0) {
            mWhichChild = 0;
            removeAllViewsInLayout();
            mCurrentChild = null;
            return;
        }
        if (whichChild >= count) {
            mWhichChild = mLoopViews ? 0 : count - 1;
        } else if (whichChild < 0) {
            mWhichChild = mLoopViews ? count - 1 : 0;
        } else {
            mWhichChild = whichChild;
        }
        shirazSelect(mWhichChild);
        rebuildDisplayedView();
    }

    private void rebuildDisplayedView() {
        removeAllViewsInLayout();
        mCurrentChild = null;
        if (mAdapter != null && mWhichChild >= 0 && mWhichChild < mAdapter.getCount()) {
            View v = mAdapter.getView(mWhichChild, null, this);
            if (v != null) {
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (lp == null) {
                    lp = new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                }
                shirazAddRow(v, 0, lp);
                mCurrentChild = v;
                if (mInAnimation != null) {
                    mInAnimation.setTarget(v);
                }
            }
        }
        firstPosition = mWhichChild >= 0 ? mWhichChild : 0;
        requestLayout();
        invalidate();
    }

    public int getDisplayedChild() {
        return mWhichChild;
    }

    public void showNext() {
        setDisplayedChild(mWhichChild + 1);
    }

    public void showPrevious() {
        setDisplayedChild(mWhichChild - 1);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_UP && mCurrentChild != null) {
            performItemClick(mCurrentChild, mWhichChild, getItemIdAtPosition(mWhichChild));
            return true;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = getPaddingLeft() + getPaddingRight();
        int h = getPaddingTop() + getPaddingBottom();
        if (mCurrentChild != null) {
            measureChild(mCurrentChild, widthMeasureSpec, heightMeasureSpec);
            w += mCurrentChild.getMeasuredWidth();
            h += mCurrentChild.getMeasuredHeight();
        }
        setMeasuredDimension(
                resolveSizeAndState(Math.max(w, getSuggestedMinimumWidth()), widthMeasureSpec, 0),
                resolveSizeAndState(Math.max(h, getSuggestedMinimumHeight()), heightMeasureSpec, 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mCurrentChild != null) {
            int l = getPaddingLeft();
            int t = getPaddingTop();
            mCurrentChild.layout(
                    l,
                    t,
                    l + mCurrentChild.getMeasuredWidth(),
                    t + mCurrentChild.getMeasuredHeight());
        }
    }

    private static final class SavedState extends BaseSavedState {
        final int whichChild;

        SavedState(Parcelable superState, int whichChild) {
            super(superState);
            this.whichChild = whichChild;
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        return new SavedState(super.onSaveInstanceState(), mWhichChild);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setDisplayedChild(ss.whichChild);
    }

    public View getCurrentView() {
        return mCurrentChild;
    }

    public ObjectAnimator getInAnimation() {
        return mInAnimation;
    }

    public void setInAnimation(ObjectAnimator inAnimation) {
        mInAnimation = inAnimation;
    }

    public ObjectAnimator getOutAnimation() {
        return mOutAnimation;
    }

    public void setOutAnimation(ObjectAnimator outAnimation) {
        mOutAnimation = outAnimation;
    }

    public void setInAnimation(Context context, int resourceID) {
        setInAnimation((ObjectAnimator) AnimatorInflater.loadAnimator(context, resourceID));
    }

    public void setOutAnimation(Context context, int resourceID) {
        setOutAnimation((ObjectAnimator) AnimatorInflater.loadAnimator(context, resourceID));
    }

    public void setAnimateFirstView(boolean animate) {
        mAnimateFirstTime = animate;
    }

    @Override
    public int getBaseline() {
        return mCurrentChild != null ? mCurrentChild.getBaseline() : super.getBaseline();
    }

    @Override
    public Adapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void setAdapter(Adapter adapter) {
        if (mAdapter != null && mDataSetObserver != null) {
            mAdapter.unregisterDataSetObserver(mDataSetObserver);
        }
        mAdapter = adapter;
        if (mAdapter != null) {
            mDataSetObserver = new DataSetObserver() {
                @Override
                public void onChanged() {
                    int count = mAdapter.getCount();
                    if (count == 0) {
                        mWhichChild = 0;
                        shirazSelect(INVALID_POSITION);
                    } else if (mWhichChild >= count) {
                        mWhichChild = count - 1;
                    }
                    shirazUpdateEmpty();
                    setDisplayedChild(mWhichChild);
                }

                @Override
                public void onInvalidated() {
                    mWhichChild = 0;
                    shirazSelect(INVALID_POSITION);
                    shirazUpdateEmpty(true);
                    removeAllViewsInLayout();
                    mCurrentChild = null;
                    requestLayout();
                }
            };
            mAdapter.registerDataSetObserver(mDataSetObserver);
        }
        mWhichChild = 0;
        shirazUpdateEmpty();
        setDisplayedChild(0);
    }

    public void setRemoteViewsAdapter(Intent intent) {
    }

    @Override
    public void setSelection(int position) {
        setDisplayedChild(position);
    }

    @Override
    public View getSelectedView() {
        return mCurrentChild;
    }

    public void deferNotifyDataSetChanged() {
    }

    public boolean onRemoteAdapterConnected() {
        return false;
    }

    public void onRemoteAdapterDisconnected() {
    }

    @Override
    public void advance() {
        showNext();
    }

    @Override
    public void fyiWillBeAdvancedByHostKThx() {
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return AdapterViewAnimator.class.getName();
    }
}
