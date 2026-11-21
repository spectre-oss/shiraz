// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/** Controller managing floating ZoomControls anchored to an owner View. */
@Deprecated
public class ZoomButtonsController implements View.OnTouchListener {
    public interface OnZoomListener {
        void onVisibilityChanged(boolean visible);

        void onZoom(boolean zoomIn);
    }

    private final Context mContext;
    private final View mOwnerView;
    private final FrameLayout mContainer;
    private final ZoomControls mControls;
    private boolean mAutoDismissControls = true;
    private boolean mIsVisible;
    private boolean mFocusable;
    private OnZoomListener mCallback;
    private PopupWindow mPopup;

    public ZoomButtonsController(View ownerView) {
        mContext = ownerView.getContext();
        mOwnerView = ownerView;
        mContainer = new FrameLayout(mContext);
        mControls = new ZoomControls(mContext);
        mControls.setOnZoomInClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    mCallback.onZoom(true);
                }
            }
        });
        mControls.setOnZoomOutClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    mCallback.onZoom(false);
                }
            }
        });
        mContainer.addView(mControls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
    }

    public void setZoomInEnabled(boolean enabled) {
        mControls.setIsZoomInEnabled(enabled);
    }

    public void setZoomOutEnabled(boolean enabled) {
        mControls.setIsZoomOutEnabled(enabled);
    }

    public void setZoomSpeed(long speed) {
        mControls.setZoomSpeed(speed);
    }

    public void setOnZoomListener(OnZoomListener listener) {
        mCallback = listener;
    }

    public void setFocusable(boolean focusable) {
        mFocusable = focusable;
        if (mPopup != null) {
            mPopup.setFocusable(focusable);
        }
    }

    public boolean isAutoDismissed() {
        return mAutoDismissControls;
    }

    public void setAutoDismissed(boolean autoDismiss) {
        mAutoDismissControls = autoDismiss;
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public void setVisible(boolean visible) {
        if (visible == mIsVisible) {
            return;
        }
        mIsVisible = visible;
        if (visible) {
            if (mOwnerView.isAttachedToWindow()) {
                if (mPopup == null) {
                    mPopup = new PopupWindow(
                            mContainer,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            mFocusable);
                }
                mPopup.showAtLocation(mOwnerView, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0);
            }
        } else if (mPopup != null && mPopup.isShowing()) {
            mPopup.dismiss();
        }
        if (mCallback != null) {
            mCallback.onVisibilityChanged(visible);
        }
    }

    public ViewGroup getContainer() {
        return mContainer;
    }

    public View getZoomControls() {
        return mControls;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }
}
