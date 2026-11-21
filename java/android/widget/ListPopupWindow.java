// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/** Anchored popup window hosting a scrollable ListView of items from a ListAdapter. */
public class ListPopupWindow {
    public static final int POSITION_PROMPT_ABOVE = 0;
    public static final int POSITION_PROMPT_BELOW = 1;
    public static final int MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT;
    public static final int WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT;
    public static final int INPUT_METHOD_FROM_FOCUSABLE = PopupWindow.INPUT_METHOD_FROM_FOCUSABLE;
    public static final int INPUT_METHOD_NEEDED = PopupWindow.INPUT_METHOD_NEEDED;
    public static final int INPUT_METHOD_NOT_NEEDED = PopupWindow.INPUT_METHOD_NOT_NEEDED;

    private final Context mContext;
    private ListAdapter mAdapter;
    private ListView mDropDownList;
    private int mDropDownHeight = WRAP_CONTENT;
    private int mDropDownWidth = WRAP_CONTENT;
    private int mDropDownHorizontalOffset;
    private int mDropDownVerticalOffset;
    private int mDropDownGravity = Gravity.NO_GRAVITY;
    private View mPromptView;
    private int mPromptPosition = POSITION_PROMPT_ABOVE;
    private DataSetObserver mObserver;
    private View mDropDownAnchorView;
    private Drawable mDropDownListHighlight;
    private AdapterView.OnItemClickListener mItemClickListener;
    private AdapterView.OnItemSelectedListener mItemSelectedListener;
    private final Handler mHandler;
    private Rect mEpicenterBounds;
    private boolean mModal;
    private final PopupWindow mPopup;

    private final Runnable mShowDropDownRunnable = new Runnable() {
        @Override
        public void run() {
            View view = getAnchorView();
            if (view != null && view.isAttachedToWindow()) {
                show();
            }
        }
    };

    public ListPopupWindow(Context context) {
        this(context, null, 0x010102ff, 0);
    }

    public ListPopupWindow(Context context, AttributeSet attrs) {
        this(context, attrs, 0x010102ff, 0);
    }

    public ListPopupWindow(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ListPopupWindow(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        mContext = context;
        Looper looper = context.getMainLooper();
        mHandler = looper != null ? new Handler(looper) : null;
        mPopup = new PopupWindow(context, attrs, defStyleAttr, defStyleRes);
        mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
    }

    public void setAdapter(ListAdapter adapter) {
        if (mObserver == null) {
            mObserver = new DataSetObserver() {
                @Override
                public void onChanged() {
                    if (isShowing()) {
                        show();
                    }
                }

                @Override
                public void onInvalidated() {
                    dismiss();
                }
            };
        } else if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mObserver);
        }
        mAdapter = adapter;
        if (mAdapter != null) {
            adapter.registerDataSetObserver(mObserver);
        }
        if (mDropDownList != null) {
            mDropDownList.setAdapter(mAdapter);
        }
    }

    public void setPromptPosition(int position) {
        mPromptPosition = position;
    }

    public int getPromptPosition() {
        return mPromptPosition;
    }

    public void setModal(boolean modal) {
        mModal = modal;
        mPopup.setFocusable(modal);
    }

    public boolean isModal() {
        return mModal;
    }

    public void setSoftInputMode(int mode) {
        mPopup.setSoftInputMode(mode);
    }

    public int getSoftInputMode() {
        return mPopup.getSoftInputMode();
    }

    public void setListSelector(Drawable selector) {
        mDropDownListHighlight = selector;
        if (mDropDownList != null && selector != null) {
            mDropDownList.setSelector(selector);
        }
    }

    public Drawable getBackground() {
        return mPopup.getBackground();
    }

    public void setBackgroundDrawable(Drawable d) {
        mPopup.setBackgroundDrawable(d);
    }

    public void setAnimationStyle(int animationStyle) {
        mPopup.setAnimationStyle(animationStyle);
    }

    public int getAnimationStyle() {
        return mPopup.getAnimationStyle();
    }

    public View getAnchorView() {
        return mDropDownAnchorView;
    }

    public void setAnchorView(View anchor) {
        mDropDownAnchorView = anchor;
    }

    public int getHorizontalOffset() {
        return mDropDownHorizontalOffset;
    }

    public void setHorizontalOffset(int offset) {
        mDropDownHorizontalOffset = offset;
    }

    public int getVerticalOffset() {
        return mDropDownVerticalOffset;
    }

    public void setVerticalOffset(int offset) {
        mDropDownVerticalOffset = offset;
    }

    public void setEpicenterBounds(Rect bounds) {
        mEpicenterBounds = bounds != null ? new Rect(bounds) : null;
    }

    public Rect getEpicenterBounds() {
        return mEpicenterBounds != null ? new Rect(mEpicenterBounds) : null;
    }

    public void setDropDownGravity(int gravity) {
        mDropDownGravity = gravity;
    }

    public int getWidth() {
        return mDropDownWidth;
    }

    public void setWidth(int width) {
        mDropDownWidth = width;
    }

    public void setContentWidth(int width) {
        Drawable popupBackground = mPopup.getBackground();
        if (popupBackground != null) {
            Rect tempRect = new Rect();
            popupBackground.getPadding(tempRect);
            mDropDownWidth = tempRect.left + tempRect.right + width;
        } else {
            setWidth(width);
        }
    }

    public int getHeight() {
        return mDropDownHeight;
    }

    public void setHeight(int height) {
        if (height < 0 && WRAP_CONTENT != height && MATCH_PARENT != height) {
            throw new IllegalArgumentException(
                    "Invalid height. Must be a positive value, MATCH_PARENT, or WRAP_CONTENT.");
        }
        mDropDownHeight = height;
    }

    public void setWindowLayoutType(int layoutType) {
        mPopup.setWindowLayoutType(layoutType);
    }

    public void setOnItemClickListener(AdapterView.OnItemClickListener clickListener) {
        mItemClickListener = clickListener;
        if (mDropDownList != null) {
            mDropDownList.setOnItemClickListener(clickListener);
        }
    }

    public void setOnItemSelectedListener(AdapterView.OnItemSelectedListener selectedListener) {
        mItemSelectedListener = selectedListener;
        if (mDropDownList != null) {
            mDropDownList.setOnItemSelectedListener(selectedListener);
        }
    }

    public void setPromptView(View prompt) {
        boolean showing = isShowing();
        if (showing) {
            removePromptView();
        }
        mPromptView = prompt;
        if (showing) {
            show();
        }
    }

    private void removePromptView() {
        if (mPromptView != null) {
            final ViewParent parent = mPromptView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(mPromptView);
            }
        }
    }

    public void postShow() {
        if (mHandler != null) {
            mHandler.post(mShowDropDownRunnable);
        }
    }

    public void show() {
        buildDropDown();
        int widthSpec;
        if (mDropDownWidth == MATCH_PARENT) {
            widthSpec = mDropDownAnchorView != null ? mDropDownAnchorView.getWidth() : MATCH_PARENT;
        } else if (mDropDownWidth == WRAP_CONTENT) {
            widthSpec = mDropDownAnchorView != null ? mDropDownAnchorView.getWidth() : WRAP_CONTENT;
        } else {
            widthSpec = mDropDownWidth;
        }
        int heightSpec = mDropDownHeight;
        mPopup.setOutsideTouchable(true);
        if (mPopup.isShowing()) {
            if (mDropDownAnchorView != null && !mDropDownAnchorView.isAttachedToWindow()) {
                return;
            }
            mPopup.update(
                    mDropDownAnchorView,
                    mDropDownHorizontalOffset,
                    mDropDownVerticalOffset,
                    widthSpec,
                    heightSpec);
        } else {
            mPopup.setWidth(widthSpec);
            mPopup.setHeight(heightSpec);
            if (mDropDownAnchorView != null && mDropDownAnchorView.isAttachedToWindow()) {
                mPopup.showAsDropDown(
                        mDropDownAnchorView,
                        mDropDownHorizontalOffset,
                        mDropDownVerticalOffset,
                        mDropDownGravity == Gravity.NO_GRAVITY ? Gravity.START : mDropDownGravity);
            }
        }
    }

    private void buildDropDown() {
        if (mDropDownList == null) {
            mDropDownList = new ListView(mContext);
            if (mDropDownListHighlight != null) {
                mDropDownList.setSelector(mDropDownListHighlight);
            }
            mDropDownList.setAdapter(mAdapter);
            mDropDownList.setOnItemClickListener(mItemClickListener);
            if (mItemSelectedListener != null) {
                mDropDownList.setOnItemSelectedListener(mItemSelectedListener);
            }
            View dropDownView = mDropDownList;
            if (mPromptView != null) {
                LinearLayout hintContainer = new LinearLayout(mContext);
                hintContainer.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams hintParams =
                        new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1.0f);
                switch (mPromptPosition) {
                    case POSITION_PROMPT_BELOW:
                        hintContainer.addView(dropDownView, hintParams);
                        hintContainer.addView(mPromptView);
                        break;
                    case POSITION_PROMPT_ABOVE:
                    default:
                        hintContainer.addView(mPromptView);
                        hintContainer.addView(dropDownView, hintParams);
                        break;
                }
                dropDownView = hintContainer;
            }
            mPopup.setContentView(dropDownView);
        }
    }

    public void dismiss() {
        mPopup.dismiss();
        removePromptView();
        mPopup.setContentView(null);
        mDropDownList = null;
        if (mHandler != null) {
            mHandler.removeCallbacks(mShowDropDownRunnable);
        }
    }

    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        mPopup.setOnDismissListener(listener);
    }

    public void setInputMethodMode(int mode) {
        mPopup.setInputMethodMode(mode);
    }

    public int getInputMethodMode() {
        return mPopup.getInputMethodMode();
    }

    public void setSelection(int position) {
        if (isShowing() && mDropDownList != null) {
            mDropDownList.setSelection(position);
        }
    }

    public void clearListSelection() {
        if (mDropDownList != null) {
            mDropDownList.clearChoices();
            mDropDownList.requestLayout();
        }
    }

    public boolean isShowing() {
        return mPopup.isShowing();
    }

    public boolean isInputMethodNotNeeded() {
        return mPopup.getInputMethodMode() == INPUT_METHOD_NOT_NEEDED;
    }

    public boolean performItemClick(int position) {
        if (isShowing() && mDropDownList != null && mAdapter != null) {
            if (position >= 0 && position < mAdapter.getCount()) {
                View child = mDropDownList.getChildAt(
                        position - mDropDownList.getFirstVisiblePosition());
                mDropDownList.performItemClick(child, position, mAdapter.getItemId(position));
                return true;
            }
        }
        return false;
    }

    public Object getSelectedItem() {
        if (!isShowing() || mDropDownList == null) {
            return null;
        }
        return mDropDownList.getSelectedItem();
    }

    public int getSelectedItemPosition() {
        if (!isShowing() || mDropDownList == null) {
            return AdapterView.INVALID_POSITION;
        }
        return mDropDownList.getSelectedItemPosition();
    }

    public long getSelectedItemId() {
        if (!isShowing() || mDropDownList == null) {
            return AdapterView.INVALID_ROW_ID;
        }
        return mDropDownList.getSelectedItemId();
    }

    public View getSelectedView() {
        if (!isShowing() || mDropDownList == null) {
            return null;
        }
        return mDropDownList.getSelectedView();
    }

    public ListView getListView() {
        return mDropDownList;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isShowing() && mDropDownList != null) {
            int curIndex = mDropDownList.getSelectedItemPosition();
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && mAdapter != null) {
                int next = Math.min(mAdapter.getCount() - 1, Math.max(0, curIndex + 1));
                mDropDownList.setSelection(next);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && curIndex > 0) {
                mDropDownList.setSelection(curIndex - 1);
                return true;
            }
        }
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isShowing() && mDropDownList != null && mDropDownList.getSelectedItemPosition() >= 0) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                performItemClick(mDropDownList.getSelectedItemPosition());
                dismiss();
                return true;
            }
        }
        return false;
    }

    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && isShowing()) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                dismiss();
            }
            return true;
        }
        return false;
    }

    public View.OnTouchListener createDragToOpenListener(final View src) {
        return new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        };
    }
}
