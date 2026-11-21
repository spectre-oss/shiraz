// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.autofill.AutofillValue;

/** Abstract base class for spinner widgets backed by a SpinnerAdapter. */
public abstract class AbsSpinner extends AdapterView<SpinnerAdapter> {
    SpinnerAdapter mAdapter;
    int mHeightMeasureSpec;
    int mWidthMeasureSpec;
    int mSelectionLeftPadding;
    int mSelectionTopPadding;
    int mSelectionRightPadding;
    int mSelectionBottomPadding;
    final Rect mSpinnerPadding = new Rect();
    private DataSetObserver mDataSetObserver;
    private Rect mTouchFrame;

    public AbsSpinner(Context context) {
        this(context, null);
    }

    public AbsSpinner(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AbsSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AbsSpinner(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setFocusable(true);
        setWillNotDraw(false);
    }

    @Override
    public void setAdapter(SpinnerAdapter adapter) {
        if (mAdapter != null && mDataSetObserver != null) {
            mAdapter.unregisterDataSetObserver(mDataSetObserver);
        }
        resetList();
        mAdapter = adapter;
        if (mAdapter != null) {
            mDataSetObserver = new DataSetObserver() {
                @Override
                public void onChanged() {
                    int count = mAdapter.getCount();
                    int sel = getSelectedItemPosition();
                    if (count == 0) {
                        shirazSelect(INVALID_POSITION);
                    } else if (sel < 0 || sel >= count) {
                        shirazSelect(0);
                    }
                    shirazUpdateEmpty();
                    requestLayout();
                    invalidate();
                }

                @Override
                public void onInvalidated() {
                    shirazSelect(INVALID_POSITION);
                    shirazUpdateEmpty(true);
                    requestLayout();
                    invalidate();
                }
            };
            mAdapter.registerDataSetObserver(mDataSetObserver);
            int position = mAdapter.getCount() > 0 ? 0 : INVALID_POSITION;
            shirazSelect(position);
        } else {
            shirazSelect(INVALID_POSITION);
        }
        shirazUpdateEmpty();
        requestLayout();
    }

    void resetList() {
        removeAllViewsInLayout();
        firstPosition = 0;
        shirazSelect(INVALID_POSITION);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        mSpinnerPadding.left = Math.max(getPaddingLeft(), mSelectionLeftPadding);
        mSpinnerPadding.top = Math.max(getPaddingTop(), mSelectionTopPadding);
        mSpinnerPadding.right = Math.max(getPaddingRight(), mSelectionRightPadding);
        mSpinnerPadding.bottom = Math.max(getPaddingBottom(), mSelectionBottomPadding);

        int preferredHeight = 0;
        int preferredWidth = 0;
        boolean needsMeasuring = true;

        int selectedPosition = getSelectedItemPosition();
        if (selectedPosition >= 0 && mAdapter != null && selectedPosition < mAdapter.getCount()) {
            View view = mAdapter.getView(selectedPosition, null, this);
            if (view != null) {
                if (view.getLayoutParams() == null) {
                    view.setLayoutParams(generateDefaultLayoutParams());
                }
                measureChild(view, widthMeasureSpec, heightMeasureSpec);
                preferredHeight = view.getMeasuredHeight()
                        + mSpinnerPadding.top + mSpinnerPadding.bottom;
                preferredWidth = view.getMeasuredWidth()
                        + mSpinnerPadding.left + mSpinnerPadding.right;
                needsMeasuring = false;
            }
        }

        if (needsMeasuring) {
            preferredHeight = mSpinnerPadding.top + mSpinnerPadding.bottom;
            if (widthMode == MeasureSpec.UNSPECIFIED) {
                preferredWidth = mSpinnerPadding.left + mSpinnerPadding.right;
            }
        }

        preferredHeight = Math.max(preferredHeight, getSuggestedMinimumHeight());
        preferredWidth = Math.max(preferredWidth, getSuggestedMinimumWidth());

        int heightSize = resolveSizeAndState(preferredHeight, heightMeasureSpec, 0);
        int widthSize = resolveSizeAndState(preferredWidth, widthMeasureSpec, 0);

        setMeasuredDimension(widthSize, heightSize);
        mHeightMeasureSpec = heightMeasureSpec;
        mWidthMeasureSpec = widthMeasureSpec;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public void setSelection(int position, boolean animate) {
        int count = getCount();
        if (count > 0 && position >= 0 && position < count) {
            shirazSelect(position);
            requestLayout();
            invalidate();
        }
    }

    @Override
    public void setSelection(int position) {
        setSelection(position, false);
    }

    @Override
    public View getSelectedView() {
        int count = getCount();
        int sel = getSelectedItemPosition();
        if (count > 0 && sel >= 0) {
            return getChildAt(sel - firstPosition);
        }
        return null;
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
    }

    @Override
    public SpinnerAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public int getCount() {
        return mAdapter != null ? mAdapter.getCount() : 0;
    }

    public int pointToPosition(int x, int y) {
        Rect frame = mTouchFrame;
        if (frame == null) {
            mTouchFrame = new Rect();
            frame = mTouchFrame;
        }
        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                child.getHitRect(frame);
                if (frame.contains(x, y)) {
                    return firstPosition + i;
                }
            }
        }
        return INVALID_POSITION;
    }

    private static final class SavedState extends BaseSavedState {
        final long selectedId;
        final int position;

        SavedState(Parcelable superState, long selectedId, int position) {
            super(superState);
            this.selectedId = selectedId;
            this.position = position;
        }
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        super.dispatchRestoreInstanceState(container);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        long selectedId = getSelectedItemId();
        int pos = selectedId >= 0 ? getSelectedItemPosition() : INVALID_POSITION;
        return new SavedState(superState, selectedId, pos);
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        if (ss.position >= 0) {
            setSelection(ss.position);
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return AbsSpinner.class.getName();
    }

    public void autofill(AutofillValue value) {
        if (!isEnabled() || value == null || !value.isList()) {
            return;
        }
        setSelection(value.getListValue());
    }

    public int getAutofillType() {
        return isEnabled() ? 3 : 0;
    }

    public AutofillValue getAutofillValue() {
        return isEnabled() ? AutofillValue.forList(getSelectedItemPosition()) : null;
    }
}
