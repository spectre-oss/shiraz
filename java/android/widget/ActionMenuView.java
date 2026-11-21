// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import shiraz.runtime.HostContextMenu;

/** Action bar menu item strip backed by a Menu model and optional overflow popup. */
public class ActionMenuView extends LinearLayout {
    public interface OnMenuItemClickListener {
        boolean onMenuItemClick(MenuItem item);
    }

    public static class LayoutParams extends LinearLayout.LayoutParams {
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(ViewGroup.LayoutParams other) {
            super(other);
        }

        public LayoutParams(LayoutParams other) {
            super((LinearLayout.LayoutParams) other);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }
    }

    private HostContextMenu mMenu;
    private int mPopupTheme;
    private Drawable mOverflowIcon;
    private OnMenuItemClickListener mOnMenuItemClickListener;
    private PopupMenu mOverflowPopup;
    private boolean mOverflowShowing;

    public ActionMenuView(Context context) {
        this(context, null);
    }

    public ActionMenuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBaselineAligned(false);
        setOrientation(HORIZONTAL);
    }

    public void setPopupTheme(int resId) {
        mPopupTheme = resId;
    }

    public int getPopupTheme() {
        return mPopupTheme;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        mOnMenuItemClickListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissPopupMenus();
    }

    public void setOverflowIcon(Drawable icon) {
        mOverflowIcon = icon;
    }

    public Drawable getOverflowIcon() {
        return mOverflowIcon;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        LayoutParams params = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_VERTICAL;
        return params;
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        if (p != null) {
            final LayoutParams result = p instanceof LayoutParams
                    ? new LayoutParams((LayoutParams) p)
                    : new LayoutParams(p);
            if (result.gravity <= 0) {
                result.gravity = Gravity.CENTER_VERTICAL;
            }
            return result;
        }
        return generateDefaultLayoutParams();
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    public Menu getMenu() {
        if (mMenu == null) {
            mMenu = new HostContextMenu(getContext());
            mMenu.shirazSetSelectionListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    return mOnMenuItemClickListener != null
                            && mOnMenuItemClickListener.onMenuItemClick(item);
                }
            });
        }
        return mMenu;
    }

    public boolean showOverflowMenu() {
        if (mOverflowShowing || !isAttachedToWindow()) {
            return false;
        }
        Menu src = getMenu();
        if (src.size() == 0) {
            return false;
        }
        mOverflowPopup = new PopupMenu(getContext(), this, Gravity.END);
        Menu dst = mOverflowPopup.getMenu();
        for (int i = 0; i < src.size(); i++) {
            MenuItem item = src.getItem(i);
            dst.add(item.getGroupId(), item.getItemId(), item.getOrder(), item.getTitle());
        }
        mOverflowPopup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return mOnMenuItemClickListener != null
                        && mOnMenuItemClickListener.onMenuItemClick(item);
            }
        });
        mOverflowPopup.setOnDismissListener(new PopupMenu.OnDismissListener() {
            @Override
            public void onDismiss(PopupMenu menu) {
                mOverflowShowing = false;
            }
        });
        mOverflowPopup.show();
        mOverflowShowing = true;
        return true;
    }

    public boolean hideOverflowMenu() {
        if (!mOverflowShowing || mOverflowPopup == null) {
            return false;
        }
        mOverflowPopup.dismiss();
        mOverflowShowing = false;
        return true;
    }

    public boolean isOverflowMenuShowing() {
        return mOverflowShowing;
    }

    public void dismissPopupMenus() {
        hideOverflowMenu();
    }
}
