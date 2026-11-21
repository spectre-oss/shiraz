// SPDX-License-Identifier: GPL-3.0-only
package android.gesture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import java.util.ArrayList;

public class GestureOverlayView extends FrameLayout {
    public static final int GESTURE_STROKE_TYPE_SINGLE = 0;
    public static final int GESTURE_STROKE_TYPE_MULTIPLE = 1;

    public static final int ORIENTATION_HORIZONTAL = 0;
    public static final int ORIENTATION_VERTICAL = 1;

    private static final int FADE_ANIMATION_RATE = 16;
    private static final boolean GESTURE_RENDERING_ANTIALIAS = true;
    private static final boolean DITHER_FLAG = true;

    private final Paint mGesturePaint = new Paint();

    private long mFadeDuration = 150;
    private long mFadeOffset = 420;
    private int mFadingAlpha = 255;
    private boolean mFadeEnabled = true;

    private int mCurrentColor;
    private int mCertainGestureColor = 0xFFFFFF00;
    private int mUncertainGestureColor = 0x48FFFF00;
    private float mGestureStrokeWidth = 12.0f;
    private int mInvalidateExtraBorder = 10;

    private int mGestureStrokeType = GESTURE_STROKE_TYPE_SINGLE;
    private float mGestureStrokeLengthThreshold = 50.0f;
    private float mGestureStrokeSquarenessTreshold = 0.275f;
    private float mGestureStrokeAngleThreshold = 40.0f;

    private int mOrientation = ORIENTATION_VERTICAL;

    private final Rect mInvalidRect = new Rect();
    private final Path mPath = new Path();
    private boolean mGestureVisible = true;

    private float mX;
    private float mY;

    private float mCurveEndX;
    private float mCurveEndY;

    private float mTotalLength;
    private boolean mIsGesturing = false;
    private boolean mPreviousWasGesturing = false;
    private boolean mInterceptEvents = true;
    private boolean mIsListeningForGestures;
    private boolean mResetGesture;

    private Gesture mCurrentGesture;
    private final ArrayList<GesturePoint> mStrokeBuffer = new ArrayList<GesturePoint>(100);

    private final ArrayList<OnGestureListener> mOnGestureListeners =
            new ArrayList<OnGestureListener>();
    private final ArrayList<OnGesturePerformedListener> mOnGesturePerformedListeners =
            new ArrayList<OnGesturePerformedListener>();
    private boolean mHandleGestureActions;
    private final ArrayList<OnGesturingListener> mOnGesturingListeners =
            new ArrayList<OnGesturingListener>();

    private boolean mIsFadingOut = false;
    private float mFadingHasStarted;
    private long mFadingStart;

    private final FadeOutRunnable mFadingOut = new FadeOutRunnable();

    public static interface OnGesturingListener {
        void onGesturingStarted(GestureOverlayView overlay);

        void onGesturingEnded(GestureOverlayView overlay);
    }

    public static interface OnGestureListener {
        void onGestureStarted(GestureOverlayView overlay, MotionEvent event);

        void onGesture(GestureOverlayView overlay, MotionEvent event);

        void onGestureEnded(GestureOverlayView overlay, MotionEvent event);

        void onGestureCancelled(GestureOverlayView overlay, MotionEvent event);
    }

    public static interface OnGesturePerformedListener {
        void onGesturePerformed(GestureOverlayView overlay, Gesture gesture);
    }

    public GestureOverlayView(Context context) {
        super(context);
        init();
    }

    public GestureOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GestureOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public GestureOverlayView(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setWillNotDraw(false);

        final Paint gesturePaint = mGesturePaint;
        gesturePaint.setAntiAlias(GESTURE_RENDERING_ANTIALIAS);
        gesturePaint.setColor(mCertainGestureColor);
        gesturePaint.setStyle(Paint.Style.STROKE);
        gesturePaint.setStrokeJoin(Paint.Join.ROUND);
        gesturePaint.setStrokeCap(Paint.Cap.ROUND);
        gesturePaint.setStrokeWidth(mGestureStrokeWidth);
        gesturePaint.setDither(DITHER_FLAG);

        mCurrentColor = mCertainGestureColor;
        setPaintAlpha(255);
    }

    public ArrayList<GesturePoint> getCurrentStroke() {
        return mStrokeBuffer;
    }

    public int getOrientation() {
        return mOrientation;
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }

    public void setGestureColor(int color) {
        mCertainGestureColor = color;
        if (!mHandleGestureActions || mIsGesturing) {
            setCurrentColor(color);
        }
    }

    public void setUncertainGestureColor(int color) {
        mUncertainGestureColor = color;
    }

    public int getUncertainGestureColor() {
        return mUncertainGestureColor;
    }

    public int getGestureColor() {
        return mCertainGestureColor;
    }

    public float getGestureStrokeWidth() {
        return mGestureStrokeWidth;
    }

    public void setGestureStrokeWidth(float gestureStrokeWidth) {
        mGestureStrokeWidth = gestureStrokeWidth;
        mInvalidateExtraBorder = Math.max(1, ((int) gestureStrokeWidth) - 1);
        mGesturePaint.setStrokeWidth(gestureStrokeWidth);
    }

    public int getGestureStrokeType() {
        return mGestureStrokeType;
    }

    public void setGestureStrokeType(int gestureStrokeType) {
        mGestureStrokeType = gestureStrokeType;
    }

    public float getGestureStrokeLengthThreshold() {
        return mGestureStrokeLengthThreshold;
    }

    public void setGestureStrokeLengthThreshold(float gestureStrokeLengthThreshold) {
        mGestureStrokeLengthThreshold = gestureStrokeLengthThreshold;
    }

    public float getGestureStrokeSquarenessTreshold() {
        return mGestureStrokeSquarenessTreshold;
    }

    public void setGestureStrokeSquarenessTreshold(float gestureStrokeSquarenessTreshold) {
        mGestureStrokeSquarenessTreshold = gestureStrokeSquarenessTreshold;
    }

    public float getGestureStrokeAngleThreshold() {
        return mGestureStrokeAngleThreshold;
    }

    public void setGestureStrokeAngleThreshold(float gestureStrokeAngleThreshold) {
        mGestureStrokeAngleThreshold = gestureStrokeAngleThreshold;
    }

    public boolean isEventsInterceptionEnabled() {
        return mInterceptEvents;
    }

    public void setEventsInterceptionEnabled(boolean enabled) {
        mInterceptEvents = enabled;
    }

    public boolean isFadeEnabled() {
        return mFadeEnabled;
    }

    public void setFadeEnabled(boolean fadeEnabled) {
        mFadeEnabled = fadeEnabled;
    }

    public Gesture getGesture() {
        return mCurrentGesture;
    }

    public void setGesture(Gesture gesture) {
        if (mCurrentGesture != null) {
            clear(false);
        }

        setCurrentColor(mCertainGestureColor);
        mCurrentGesture = gesture;

        final Path path = mCurrentGesture.toPath();
        final RectF bounds = new RectF();
        path.computeBounds(bounds, true);

        mPath.rewind();
        float offsetX = -bounds.left + (getWidth() - bounds.width()) / 2.0f;
        float offsetY = -bounds.top + (getHeight() - bounds.height()) / 2.0f;
        if (getWidth() == 0 && getHeight() == 0) {
            mPath.addPath(path);
        } else {
            mPath.addPath(path, offsetX, offsetY);
        }

        mResetGesture = true;
        invalidate();
    }

    public Path getGesturePath() {
        return mPath;
    }

    public Path getGesturePath(Path path) {
        path.set(mPath);
        return path;
    }

    public boolean isGestureVisible() {
        return mGestureVisible;
    }

    public void setGestureVisible(boolean visible) {
        mGestureVisible = visible;
    }

    public long getFadeOffset() {
        return mFadeOffset;
    }

    public void setFadeOffset(long fadeOffset) {
        mFadeOffset = fadeOffset;
    }

    public void addOnGestureListener(OnGestureListener listener) {
        mOnGestureListeners.add(listener);
    }

    public void removeOnGestureListener(OnGestureListener listener) {
        mOnGestureListeners.remove(listener);
    }

    public void removeAllOnGestureListeners() {
        mOnGestureListeners.clear();
    }

    public void addOnGesturePerformedListener(OnGesturePerformedListener listener) {
        mOnGesturePerformedListeners.add(listener);
        if (mOnGesturePerformedListeners.size() > 0) {
            mHandleGestureActions = true;
        }
    }

    public void removeOnGesturePerformedListener(OnGesturePerformedListener listener) {
        mOnGesturePerformedListeners.remove(listener);
        if (mOnGesturePerformedListeners.size() <= 0) {
            mHandleGestureActions = false;
        }
    }

    public void removeAllOnGesturePerformedListeners() {
        mOnGesturePerformedListeners.clear();
        mHandleGestureActions = false;
    }

    public void addOnGesturingListener(OnGesturingListener listener) {
        mOnGesturingListeners.add(listener);
    }

    public void removeOnGesturingListener(OnGesturingListener listener) {
        mOnGesturingListeners.remove(listener);
    }

    public void removeAllOnGesturingListeners() {
        mOnGesturingListeners.clear();
    }

    public boolean isGesturing() {
        return mIsGesturing;
    }

    private void setCurrentColor(int color) {
        mCurrentColor = color;
        if (mFadingHasStarted != 0.0f) {
            setPaintAlpha((int) (255 * mFadingAlpha / 255.0f));
        } else {
            setPaintAlpha(255);
        }
        invalidate();
    }

    private void setPaintAlpha(int alpha) {
        alpha += alpha >> 7;
        final int baseAlpha = mCurrentColor >>> 24;
        final int useAlpha = (baseAlpha * alpha) >> 8;
        mGesturePaint.setColor(((mCurrentColor << 8) >>> 8) | (useAlpha << 24));
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mCurrentGesture != null && mGestureVisible) {
            canvas.drawPath(mPath, mGesturePaint);
        }
    }

    public void clear(boolean animated) {
        clear(animated, false, true);
    }

    private void clear(boolean animated, boolean fireActionPerformed, boolean immediate) {
        setPaintAlpha(255);
        removeCallbacks(mFadingOut);
        mResetGesture = false;
        mFadingOut.fireActionPerformed = fireActionPerformed;
        mFadingOut.resetMultipleStrokes = false;

        if (animated && mCurrentGesture != null) {
            mFadingAlpha = 255;
            mIsFadingOut = true;
            mFadingHasStarted = 0.0f;
            mFadingStart = AnimationUtils.currentAnimationTimeMillis() + mFadeOffset;
            postDelayed(mFadingOut, mFadeOffset);
        } else {
            mFadingAlpha = 255;
            mIsFadingOut = false;
            mFadingHasStarted = 0.0f;

            if (immediate) {
                mCurrentGesture = null;
                mPath.rewind();
                invalidate();
            } else if (fireActionPerformed) {
                postDelayed(mFadingOut, mFadeOffset);
            } else if (mGestureStrokeType == GESTURE_STROKE_TYPE_MULTIPLE) {
                mFadingOut.resetMultipleStrokes = true;
                postDelayed(mFadingOut, mFadeOffset);
            } else {
                mCurrentGesture = null;
                mPath.rewind();
                invalidate();
            }
        }
    }

    public void cancelClearAnimation() {
        setPaintAlpha(255);
        mIsFadingOut = false;
        mFadingHasStarted = 0.0f;
        removeCallbacks(mFadingOut);
        mPath.rewind();
        mCurrentGesture = null;
    }

    public void cancelGesture() {
        mIsListeningForGestures = false;

        if (mCurrentGesture != null && !mStrokeBuffer.isEmpty()) {
            mCurrentGesture.addStroke(new GestureStroke(mStrokeBuffer));
        }

        final long now = AnimationUtils.currentAnimationTimeMillis();
        final MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL,
                0.0f, 0.0f, 0);

        final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
        int count = listeners.size();
        for (int i = 0; i < count; i++) {
            listeners.get(i).onGestureCancelled(this, event);
        }

        event.recycle();

        clear(false);
        mIsGesturing = false;
        mPreviousWasGesturing = false;
        mStrokeBuffer.clear();

        final ArrayList<OnGesturingListener> otherListeners = mOnGesturingListeners;
        count = otherListeners.size();
        for (int i = 0; i < count; i++) {
            otherListeners.get(i).onGesturingEnded(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelClearAnimation();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isEnabled()) {
            final boolean cancelDispatch = (mIsGesturing || (mCurrentGesture != null
                    && mCurrentGesture.getStrokesCount() > 0 && mPreviousWasGesturing))
                    && mInterceptEvents;

            processEvent(event);

            if (cancelDispatch) {
                event.setAction(MotionEvent.ACTION_CANCEL);
            }

            super.dispatchTouchEvent(event);
            return true;
        }

        return super.dispatchTouchEvent(event);
    }

    private boolean processEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDown(event);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mIsListeningForGestures) {
                    Rect rect = touchMove(event);
                    if (rect != null) {
                        invalidate(rect);
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsListeningForGestures) {
                    touchUp(event, false);
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsListeningForGestures) {
                    touchUp(event, true);
                    invalidate();
                    return true;
                }
                break;
        }
        return false;
    }

    private void touchDown(MotionEvent event) {
        mIsListeningForGestures = true;

        float x = event.getX();
        float y = event.getY();

        mX = x;
        mY = y;

        mTotalLength = 0.0f;
        mIsGesturing = false;

        if (mGestureStrokeType == GESTURE_STROKE_TYPE_SINGLE || mResetGesture) {
            if (mHandleGestureActions) {
                setCurrentColor(mUncertainGestureColor);
            }
            mResetGesture = false;
            mCurrentGesture = null;
            mPath.rewind();
        } else if (mCurrentGesture == null || mCurrentGesture.getStrokesCount() == 0) {
            if (mHandleGestureActions) {
                setCurrentColor(mUncertainGestureColor);
            }
        }

        if (mFadingHasStarted != 0.0f) {
            cancelClearAnimation();
        } else if (mIsFadingOut) {
            setPaintAlpha(255);
            mIsFadingOut = false;
            mFadingHasStarted = 0.0f;
            removeCallbacks(mFadingOut);
        }

        if (mCurrentGesture == null) {
            mCurrentGesture = new Gesture();
        }

        mStrokeBuffer.add(new GesturePoint(x, y, event.getEventTime()));
        mPath.moveTo(x, y);

        final int border = mInvalidateExtraBorder;
        mInvalidRect.set((int) x - border, (int) y - border, (int) x + border, (int) y + border);

        mCurveEndX = x;
        mCurveEndY = y;

        final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
        final int count = listeners.size();
        for (int i = 0; i < count; i++) {
            listeners.get(i).onGestureStarted(this, event);
        }
    }

    private Rect touchMove(MotionEvent event) {
        Rect areaToRefresh = null;

        final float x = event.getX();
        final float y = event.getY();

        final float previousX = mX;
        final float previousY = mY;

        final float dx = Math.abs(x - previousX);
        final float dy = Math.abs(y - previousY);

        if (dx >= GestureStroke.TOUCH_TOLERANCE || dy >= GestureStroke.TOUCH_TOLERANCE) {
            areaToRefresh = mInvalidRect;

            final int border = mInvalidateExtraBorder;
            areaToRefresh.set((int) mCurveEndX - border, (int) mCurveEndY - border,
                    (int) mCurveEndX + border, (int) mCurveEndY + border);

            float cX = mCurveEndX = (x + previousX) / 2.0f;
            float cY = mCurveEndY = (y + previousY) / 2.0f;

            mPath.quadTo(previousX, previousY, cX, cY);

            areaToRefresh.union((int) previousX - border, (int) previousY - border,
                    (int) previousX + border, (int) previousY + border);
            areaToRefresh.union((int) cX - border, (int) cY - border,
                    (int) cX + border, (int) cY + border);

            mX = x;
            mY = y;

            mStrokeBuffer.add(new GesturePoint(x, y, event.getEventTime()));

            if (mHandleGestureActions && !mIsGesturing) {
                mTotalLength += (float) Math.hypot(dx, dy);

                if (mTotalLength > mGestureStrokeLengthThreshold) {
                    final OrientedBoundingBox box =
                            GestureUtils.computeOrientedBoundingBox(mStrokeBuffer);

                    float angle = Math.abs(box.orientation);
                    if (angle > 90.0f) {
                        angle = 180.0f - angle;
                    }

                    if (box.squareness > mGestureStrokeSquarenessTreshold ||
                            (mOrientation == ORIENTATION_VERTICAL ?
                                    angle < mGestureStrokeAngleThreshold :
                                    angle > mGestureStrokeAngleThreshold)) {

                        mIsGesturing = true;
                        setCurrentColor(mCertainGestureColor);

                        final ArrayList<OnGesturingListener> listeners = mOnGesturingListeners;
                        int count = listeners.size();
                        for (int i = 0; i < count; i++) {
                            listeners.get(i).onGesturingStarted(this);
                        }
                    }
                }
            }

            final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
            final int count = listeners.size();
            for (int i = 0; i < count; i++) {
                listeners.get(i).onGesture(this, event);
            }
        }

        return areaToRefresh;
    }

    private void touchUp(MotionEvent event, boolean cancel) {
        mIsListeningForGestures = false;

        if (mCurrentGesture != null) {
            if (!mStrokeBuffer.isEmpty()) {
                float x = event.getX();
                float y = event.getY();
                GesturePoint last = mStrokeBuffer.get(mStrokeBuffer.size() - 1);
                if (last.x != x || last.y != y) {
                    mStrokeBuffer.add(new GesturePoint(x, y, event.getEventTime()));
                    mPath.lineTo(x, y);
                }
                mCurrentGesture.addStroke(new GestureStroke(mStrokeBuffer));
            }

            if (!cancel) {
                final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
                int count = listeners.size();
                for (int i = 0; i < count; i++) {
                    listeners.get(i).onGestureEnded(this, event);
                }

                if (mGestureStrokeType == GESTURE_STROKE_TYPE_SINGLE) {
                    if (mHandleGestureActions && mIsGesturing && !mFadeEnabled) {
                        fireOnGesturePerformed();
                    }
                    clear(mHandleGestureActions && mFadeEnabled,
                            mHandleGestureActions && mIsGesturing && mFadeEnabled, false);
                } else {
                    clear(mHandleGestureActions && mFadeEnabled,
                            mHandleGestureActions && mIsGesturing, false);
                }
            } else {
                cancelGesture(event);
            }
        } else {
            cancelGesture(event);
        }

        mStrokeBuffer.clear();
        mPreviousWasGesturing = mIsGesturing;
        mIsGesturing = false;

        final ArrayList<OnGesturingListener> listeners = mOnGesturingListeners;
        int count = listeners.size();
        for (int i = 0; i < count; i++) {
            listeners.get(i).onGesturingEnded(this);
        }
    }

    private void cancelGesture(MotionEvent event) {
        final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
        final int count = listeners.size();
        for (int i = 0; i < count; i++) {
            listeners.get(i).onGestureCancelled(this, event);
        }
        clear(false);
    }

    private void fireOnGesturePerformed() {
        final Gesture gesture = mCurrentGesture;
        if (gesture == null || gesture.getStrokesCount() == 0) {
            return;
        }
        final ArrayList<OnGesturePerformedListener> actionListeners = mOnGesturePerformedListeners;
        final int count = actionListeners.size();
        for (int i = 0; i < count; i++) {
            actionListeners.get(i).onGesturePerformed(this, gesture);
        }
    }

    private class FadeOutRunnable implements Runnable {
        boolean fireActionPerformed;
        boolean resetMultipleStrokes;

        @Override
        public void run() {
            if (mIsFadingOut) {
                final long now = AnimationUtils.currentAnimationTimeMillis();
                final long duration = now - mFadingStart;

                if (duration > mFadeDuration) {
                    if (fireActionPerformed) {
                        fireOnGesturePerformed();
                    }

                    mPreviousWasGesturing = false;
                    mIsFadingOut = false;
                    mFadingHasStarted = 0.0f;
                    mPath.rewind();
                    mCurrentGesture = null;
                    setPaintAlpha(255);
                } else {
                    mFadingHasStarted = 1.0f;
                    float interpolatedTime = Math.max(0.0f,
                            Math.min(1.0f, duration / (float) mFadeDuration));
                    mFadingAlpha = (int) (255.0f * (1.0f - interpolatedTime));
                    setPaintAlpha(mFadingAlpha);
                    postDelayed(this, FADE_ANIMATION_RATE);
                }
            } else if (resetMultipleStrokes) {
                mResetGesture = true;
            } else {
                if (fireActionPerformed) {
                    fireOnGesturePerformed();
                }
                mFadingHasStarted = 0.0f;
                mPath.rewind();
                mCurrentGesture = null;
                mPreviousWasGesturing = false;
                setPaintAlpha(255);
            }

            invalidate();
        }
    }
}
