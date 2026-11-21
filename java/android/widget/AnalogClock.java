// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.util.AttributeSet;
import android.view.View;
import java.util.Calendar;
import java.util.TimeZone;

/** Analog clock widget rendering dial, hour, minute, and optional second hands. */
@RemoteViews.RemoteView
@Deprecated
public class AnalogClock extends View {
    private Drawable mDial;
    private ColorStateList mDialTintList;
    private BlendMode mDialBlendMode;
    private Drawable mHourHand;
    private ColorStateList mHourHandTintList;
    private BlendMode mHourHandBlendMode;
    private Drawable mMinuteHand;
    private ColorStateList mMinuteHandTintList;
    private BlendMode mMinuteHandBlendMode;
    private Drawable mSecondHand;
    private ColorStateList mSecondHandTintList;
    private BlendMode mSecondHandBlendMode;
    private String mTimeZone;
    private Calendar mCalendar;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AnalogClock(Context context) {
        this(context, null);
    }

    public AnalogClock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AnalogClock(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AnalogClock(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mCalendar = Calendar.getInstance();
    }

    public void setDial(Icon icon) {
        mDial = icon == null ? null : icon.loadDrawable(getContext());
        applyTint(mDial, mDialTintList, mDialBlendMode);
        requestLayout();
        invalidate();
    }

    public void setDialTintList(ColorStateList tint) {
        mDialTintList = tint;
        applyTint(mDial, mDialTintList, mDialBlendMode);
        invalidate();
    }

    public ColorStateList getDialTintList() {
        return mDialTintList;
    }

    public void setDialTintBlendMode(BlendMode blendMode) {
        mDialBlendMode = blendMode;
        applyTint(mDial, mDialTintList, mDialBlendMode);
        invalidate();
    }

    public BlendMode getDialTintBlendMode() {
        return mDialBlendMode;
    }

    public void setHourHand(Icon icon) {
        mHourHand = icon == null ? null : icon.loadDrawable(getContext());
        applyTint(mHourHand, mHourHandTintList, mHourHandBlendMode);
        invalidate();
    }

    public void setHourHandTintList(ColorStateList tint) {
        mHourHandTintList = tint;
        applyTint(mHourHand, mHourHandTintList, mHourHandBlendMode);
        invalidate();
    }

    public ColorStateList getHourHandTintList() {
        return mHourHandTintList;
    }

    public void setHourHandTintBlendMode(BlendMode blendMode) {
        mHourHandBlendMode = blendMode;
        applyTint(mHourHand, mHourHandTintList, mHourHandBlendMode);
        invalidate();
    }

    public BlendMode getHourHandTintBlendMode() {
        return mHourHandBlendMode;
    }

    public void setMinuteHand(Icon icon) {
        mMinuteHand = icon == null ? null : icon.loadDrawable(getContext());
        applyTint(mMinuteHand, mMinuteHandTintList, mMinuteHandBlendMode);
        invalidate();
    }

    public void setMinuteHandTintList(ColorStateList tint) {
        mMinuteHandTintList = tint;
        applyTint(mMinuteHand, mMinuteHandTintList, mMinuteHandBlendMode);
        invalidate();
    }

    public ColorStateList getMinuteHandTintList() {
        return mMinuteHandTintList;
    }

    public void setMinuteHandTintBlendMode(BlendMode blendMode) {
        mMinuteHandBlendMode = blendMode;
        applyTint(mMinuteHand, mMinuteHandTintList, mMinuteHandBlendMode);
        invalidate();
    }

    public BlendMode getMinuteHandTintBlendMode() {
        return mMinuteHandBlendMode;
    }

    public void setSecondHand(Icon icon) {
        mSecondHand = icon == null ? null : icon.loadDrawable(getContext());
        applyTint(mSecondHand, mSecondHandTintList, mSecondHandBlendMode);
        invalidate();
    }

    public void setSecondHandTintList(ColorStateList tint) {
        mSecondHandTintList = tint;
        applyTint(mSecondHand, mSecondHandTintList, mSecondHandBlendMode);
        invalidate();
    }

    public ColorStateList getSecondHandTintList() {
        return mSecondHandTintList;
    }

    public void setSecondHandTintBlendMode(BlendMode blendMode) {
        mSecondHandBlendMode = blendMode;
        applyTint(mSecondHand, mSecondHandTintList, mSecondHandBlendMode);
        invalidate();
    }

    public BlendMode getSecondHandTintBlendMode() {
        return mSecondHandBlendMode;
    }

    private static void applyTint(Drawable d, ColorStateList tint, BlendMode mode) {
        if (d != null) {
            if (tint != null) {
                d.setTintList(tint);
            }
            if (mode != null) {
                d.setTintBlendMode(mode);
            }
        }
    }

    public String getTimeZone() {
        return mTimeZone;
    }

    public void setTimeZone(String timeZone) {
        mTimeZone = timeZone;
        mCalendar = timeZone != null
                ? Calendar.getInstance(TimeZone.getTimeZone(timeZone))
                : Calendar.getInstance();
        invalidate();
    }

    public void onVisibilityAggregated(boolean isVisible) {
        if (isVisible) {
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultSize = Math.round(96f * getResources().getDisplayMetrics().density);
        int dialW = mDial != null ? Math.max(defaultSize, mDial.getIntrinsicWidth()) : defaultSize;
        int dialH = mDial != null ? Math.max(defaultSize, mDial.getIntrinsicHeight()) : defaultSize;
        setMeasuredDimension(
                resolveSizeAndState(dialW + getPaddingLeft() + getPaddingRight(), widthMeasureSpec, 0),
                resolveSizeAndState(dialH + getPaddingTop() + getPaddingBottom(), heightMeasureSpec, 0));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        int radius = Math.max(4, Math.min(cx, cy) - 4);

        if (mDial != null) {
            mDial.setBounds(cx - radius, cy - radius, cx + radius, cy + radius);
            mDial.draw(canvas);
        } else {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(2f);
            mPaint.setColor(0xff424242);
            canvas.drawCircle(cx, cy, radius, mPaint);
        }

        float second = mCalendar.get(Calendar.SECOND);
        float minute = mCalendar.get(Calendar.MINUTE) + second / 60f;
        float hour = mCalendar.get(Calendar.HOUR) + minute / 60f;

        drawHand(canvas, mHourHand, cx, cy, hour / 12f * 360f, radius * 0.5f, 4f);
        drawHand(canvas, mMinuteHand, cx, cy, minute / 60f * 360f, radius * 0.75f, 3f);
        if (mSecondHand != null) {
            drawHand(canvas, mSecondHand, cx, cy, second / 60f * 360f, radius * 0.85f, 1.5f);
        }
    }

    private void drawHand(
            Canvas canvas, Drawable hand, int cx, int cy, float degrees, float length, float stroke) {
        canvas.save();
        canvas.rotate(degrees, cx, cy);
        if (hand != null) {
            int w = Math.max(4, hand.getIntrinsicWidth());
            int h = Math.max(8, hand.getIntrinsicHeight());
            hand.setBounds(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            hand.draw(canvas);
        } else {
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(stroke);
            mPaint.setColor(0xff212121);
            canvas.drawLine(cx, cy, cx, cy - length, mPaint);
        }
        canvas.restore();
    }
}
