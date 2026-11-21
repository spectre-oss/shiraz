// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/** ImageButton that fires repeated click events while held down for zooming. */
@Deprecated
public class ZoomButton extends ImageButton implements View.OnLongClickListener {
    private long mZoomSpeed = 1000L;
    private boolean mIsInLongpress;

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            if (hasOnClickListeners() && mIsInLongpress && isEnabled()) {
                callOnClick();
                postDelayed(mRunnable, mZoomSpeed);
            }
        }
    };

    public ZoomButton(Context context) {
        this(context, null);
    }

    public ZoomButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ZoomButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setOnLongClickListener(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if ((event.getAction() == MotionEvent.ACTION_CANCEL)
                || (event.getAction() == MotionEvent.ACTION_UP)) {
            mIsInLongpress = false;
        }
        return super.onTouchEvent(event);
    }

    public void setZoomSpeed(long speed) {
        mZoomSpeed = speed;
    }

    @Override
    public boolean onLongClick(View v) {
        mIsInLongpress = true;
        post(mRunnable);
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        mIsInLongpress = false;
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled) {
            setPressed(false);
        }
        super.setEnabled(enabled);
    }

    public boolean dispatchUnhandledMove(View focused, int direction) {
        clearFocus();
        return false;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return ZoomButton.class.getName();
    }
}
