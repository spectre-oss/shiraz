// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;

import android.text.Layout;
import android.text.NoCopySpan;
import android.text.Spannable;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

/** Touch dragging and clamped scrolling helper for TextViews. */
public class Touch {
    private Touch() {
    }

    public static void scrollTo(TextView widget, Layout layout, int x, int y) {
        int horizontalPadding = widget.getTotalPaddingLeft() + widget.getTotalPaddingRight();
        int availableWidth = widget.getWidth() - horizontalPadding;

        int top = layout.getLineForVertical(y);
        int bottom = layout.getLineForVertical(y + widget.getHeight()
                - (widget.getTotalPaddingTop() + widget.getTotalPaddingBottom()));

        int left = Integer.MAX_VALUE;
        int right = 0;
        for (int i = top; i <= bottom && i < layout.getLineCount(); i++) {
            left = (int) Math.min(left, layout.getLineLeft(i));
            right = (int) Math.max(right, layout.getLineRight(i));
        }
        if (left == Integer.MAX_VALUE) {
            left = 0;
        }
        int actualWidth = right - left;
        if (actualWidth < availableWidth) {
            x = left;
        } else {
            x = Math.min(x, right - availableWidth);
            x = Math.max(x, left);
        }
        widget.scrollTo(x, y);
    }

    public static boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        DragState[] ds;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ds = buffer.getSpans(0, buffer.length(), DragState.class);
                for (int i = 0; i < ds.length; i++) {
                    buffer.removeSpan(ds[i]);
                }
                buffer.setSpan(
                        new DragState(event.getX(), event.getY(),
                                widget.getScrollX(), widget.getScrollY()),
                        0, 0, Spannable.SPAN_MARK_MARK);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                ds = buffer.getSpans(0, buffer.length(), DragState.class);
                for (int i = 0; i < ds.length; i++) {
                    buffer.removeSpan(ds[i]);
                }
                return ds.length > 0 && ds[0].mUsed;

            case MotionEvent.ACTION_MOVE:
                ds = buffer.getSpans(0, buffer.length(), DragState.class);
                if (ds.length > 0) {
                    if (!ds[0].mFarEnough) {
                        int slop = ViewConfiguration.get(widget.getContext()).getScaledTouchSlop();
                        if (Math.abs(event.getX() - ds[0].mX) >= slop
                                || Math.abs(event.getY() - ds[0].mY) >= slop) {
                            ds[0].mFarEnough = true;
                        }
                    }
                    if (ds[0].mFarEnough) {
                        ds[0].mUsed = true;
                        float dx = ds[0].mX - event.getX();
                        float dy = ds[0].mY - event.getY();
                        ds[0].mX = event.getX();
                        ds[0].mY = event.getY();

                        int nx = widget.getScrollX() + (int) dx;
                        int ny = widget.getScrollY() + (int) dy;

                        int padding = widget.getTotalPaddingTop() + widget.getTotalPaddingBottom();
                        Layout layout = widget.getLayout();
                        if (layout != null) {
                            ny = Math.min(ny, layout.getHeight() - (widget.getHeight() - padding));
                            ny = Math.max(ny, 0);
                            int oldX = widget.getScrollX();
                            int oldY = widget.getScrollY();
                            scrollTo(widget, layout, nx, ny);
                            if (oldX != widget.getScrollX() || oldY != widget.getScrollY()) {
                                widget.cancelLongPress();
                            }
                        }
                        return true;
                    }
                }
                break;
        }
        return false;
    }

    public static int getInitialScrollX(TextView widget, Spannable buffer) {
        DragState[] ds = buffer.getSpans(0, buffer.length(), DragState.class);
        return ds.length > 0 ? ds[0].mScrollX : -1;
    }

    public static int getInitialScrollY(TextView widget, Spannable buffer) {
        DragState[] ds = buffer.getSpans(0, buffer.length(), DragState.class);
        return ds.length > 0 ? ds[0].mScrollY : -1;
    }

    private static class DragState implements NoCopySpan {
        float mX;
        float mY;
        int mScrollX;
        int mScrollY;
        boolean mFarEnough;
        boolean mUsed;

        DragState(float x, float y, int scrollX, int scrollY) {
            mX = x;
            mY = y;
            mScrollX = scrollX;
            mScrollY = scrollY;
        }
    }
}
