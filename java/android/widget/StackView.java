// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;

/** AdapterViewAnimator presenting items in a stacked card deck. */
@RemoteViews.RemoteView
public class StackView extends AdapterViewAnimator {
    private float mTouchDownY;

    public StackView(Context context) {
        super(context);
    }

    public StackView(Context context, AttributeSet attrs) {
        this(context, attrs, 0x0101043e);
    }

    public StackView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public StackView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void showNext() {
        super.showNext();
    }

    @Override
    public void showPrevious() {
        super.showPrevious();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_SCROLL && isEnabled()) {
            float vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (vscroll < 0) {
                showNext();
                return true;
            } else if (vscroll > 0) {
                showPrevious();
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            mTouchDownY = ev.getY();
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mTouchDownY = ev.getY();
                return true;
            case MotionEvent.ACTION_UP: {
                float dy = ev.getY() - mTouchDownY;
                float slop = 24f * getResources().getDisplayMetrics().density;
                if (dy < -slop) {
                    showNext();
                    return true;
                } else if (dy > slop) {
                    showPrevious();
                    return true;
                }
                break;
            }
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    public void advance() {
        showNext();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return StackView.class.getName();
    }
}
