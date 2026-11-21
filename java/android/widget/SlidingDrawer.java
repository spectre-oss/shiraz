// SPDX-License-Identifier: GPL-3.0-only
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/** Collapsible two-child drawer container with handle and content views. */
@Deprecated
public class SlidingDrawer extends ViewGroup {
    public static final int ORIENTATION_HORIZONTAL = 0;
    public static final int ORIENTATION_VERTICAL = 1;

    public interface OnDrawerOpenListener {
        void onDrawerOpened();
    }

    public interface OnDrawerCloseListener {
        void onDrawerClosed();
    }

    public interface OnDrawerScrollListener {
        void onScrollStarted();

        void onScrollEnded();
    }

    private final int mHandleId;
    private final int mContentId;
    private View mHandle;
    private View mContent;
    private final boolean mVertical;
    private boolean mExpanded;
    private final int mBottomOffset;
    private final int mTopOffset;
    private int mHandleHeight;
    private int mHandleWidth;
    private OnDrawerOpenListener mOnDrawerOpenListener;
    private OnDrawerCloseListener mOnDrawerCloseListener;
    private OnDrawerScrollListener mOnDrawerScrollListener;
    private boolean mLocked;
    private boolean mAnimating;
    private final boolean mAllowSingleTap;

    public SlidingDrawer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingDrawer(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SlidingDrawer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        int orientation = ORIENTATION_VERTICAL;
        int bottomOffset = 0;
        int topOffset = 0;
        boolean allowSingleTap = true;
        int handleId = 0;
        int contentId = 0;

        if (attrs != null) {
            shiraz.runtime.XmlAttributes a =
                    new shiraz.runtime.XmlAttributes(context, attrs, defStyleAttr, defStyleRes);
            orientation = a.integer("orientation", ORIENTATION_VERTICAL);
            bottomOffset = a.dimension("bottomOffset", 0, false);
            topOffset = a.dimension("topOffset", 0, false);
            allowSingleTap = a.bool("allowSingleTap", true);
            handleId = a.reference("handle", 0);
            contentId = a.reference("content", 0);
            if (handleId == 0) {
                throw new IllegalArgumentException(
                        "The handle attribute is required and must refer to a valid child.");
            }
            if (contentId == 0) {
                throw new IllegalArgumentException(
                        "The content attribute is required and must refer to a valid child.");
            }
            if (handleId == contentId) {
                throw new IllegalArgumentException(
                        "The content and handle attributes must refer to different children.");
            }
        }
        mVertical = orientation == ORIENTATION_VERTICAL;
        mBottomOffset = bottomOffset;
        mTopOffset = topOffset;
        mAllowSingleTap = allowSingleTap;
        mHandleId = handleId;
        mContentId = contentId;
    }

    @Override
    protected void onFinishInflate() {
        mHandle = findViewById(mHandleId);
        if (mHandle == null && getChildCount() >= 1) {
            mHandle = getChildAt(0);
        }
        if (mHandle != null) {
            mHandle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mLocked) {
                        return;
                    }
                    if (mAllowSingleTap) {
                        toggle();
                    }
                }
            });
        }
        mContent = findViewById(mContentId);
        if (mContent == null && getChildCount() >= 2) {
            mContent = getChildAt(1);
        }
        if (mContent != null) {
            mContent.setVisibility(mExpanded ? VISIBLE : GONE);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthSpecMode == MeasureSpec.UNSPECIFIED || heightSpecMode == MeasureSpec.UNSPECIFIED) {
            widthSpecSize = widthSpecSize > 0 ? widthSpecSize : 320;
            heightSpecSize = heightSpecSize > 0 ? heightSpecSize : 480;
        }

        final View handle = mHandle != null ? mHandle : (getChildCount() > 0 ? getChildAt(0) : null);
        final View content = mContent != null ? mContent : (getChildCount() > 1 ? getChildAt(1) : null);
        if (handle != null) {
            measureChild(handle, widthMeasureSpec, heightMeasureSpec);
            mHandleWidth = handle.getMeasuredWidth();
            mHandleHeight = handle.getMeasuredHeight();
        }
        if (content != null) {
            if (mVertical) {
                int height = Math.max(0, heightSpecSize - mHandleHeight - mTopOffset);
                content.measure(
                        MeasureSpec.makeMeasureSpec(widthSpecSize, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            } else {
                int width = Math.max(0, widthSpecSize - mHandleWidth - mTopOffset);
                content.measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(heightSpecSize, MeasureSpec.EXACTLY));
            }
        }

        setMeasuredDimension(widthSpecSize, heightSpecSize);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = r - l;
        final int height = b - t;
        final View handle = mHandle != null ? mHandle : (getChildCount() > 0 ? getChildAt(0) : null);
        final View content = mContent != null ? mContent : (getChildCount() > 1 ? getChildAt(1) : null);

        if (handle != null) {
            int childWidth = handle.getMeasuredWidth();
            int childHeight = handle.getMeasuredHeight();
            int childLeft;
            int childTop;
            if (mVertical) {
                childLeft = (width - childWidth) / 2;
                childTop = mExpanded ? mTopOffset : height - childHeight + mBottomOffset;
                if (content != null) {
                    content.layout(0, mTopOffset + childHeight,
                            content.getMeasuredWidth(),
                            mTopOffset + childHeight + content.getMeasuredHeight());
                }
            } else {
                childLeft = mExpanded ? mTopOffset : width - childWidth + mBottomOffset;
                childTop = (height - childHeight) / 2;
                if (content != null) {
                    content.layout(mTopOffset + childWidth, 0,
                            mTopOffset + childWidth + content.getMeasuredWidth(),
                            content.getMeasuredHeight());
                }
            }
            handle.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
            mHandleHeight = handle.getHeight();
            mHandleWidth = handle.getWidth();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (mLocked) {
            return false;
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mLocked) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    public void toggle() {
        if (!mExpanded) {
            open();
        } else {
            close();
        }
    }

    public void animateToggle() {
        if (!mExpanded) {
            animateOpen();
        } else {
            animateClose();
        }
    }

    public void open() {
        openDrawer();
        invalidate();
        requestLayout();
    }

    public void close() {
        closeDrawer();
        invalidate();
        requestLayout();
    }

    public void animateClose() {
        if (mOnDrawerScrollListener != null) {
            mOnDrawerScrollListener.onScrollStarted();
        }
        close();
        if (mOnDrawerScrollListener != null) {
            mOnDrawerScrollListener.onScrollEnded();
        }
    }

    public void animateOpen() {
        if (mOnDrawerScrollListener != null) {
            mOnDrawerScrollListener.onScrollStarted();
        }
        open();
        if (mOnDrawerScrollListener != null) {
            mOnDrawerScrollListener.onScrollEnded();
        }
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return SlidingDrawer.class.getName();
    }

    private void closeDrawer() {
        if (mContent != null) {
            mContent.setVisibility(GONE);
        }
        if (!mExpanded) {
            return;
        }
        mExpanded = false;
        if (mOnDrawerCloseListener != null) {
            mOnDrawerCloseListener.onDrawerClosed();
        }
    }

    private void openDrawer() {
        if (mContent != null) {
            mContent.setVisibility(VISIBLE);
        }
        if (mExpanded) {
            return;
        }
        mExpanded = true;
        if (mOnDrawerOpenListener != null) {
            mOnDrawerOpenListener.onDrawerOpened();
        }
    }

    public void setOnDrawerOpenListener(OnDrawerOpenListener onDrawerOpenListener) {
        mOnDrawerOpenListener = onDrawerOpenListener;
    }

    public void setOnDrawerCloseListener(OnDrawerCloseListener onDrawerCloseListener) {
        mOnDrawerCloseListener = onDrawerCloseListener;
    }

    public void setOnDrawerScrollListener(OnDrawerScrollListener onDrawerScrollListener) {
        mOnDrawerScrollListener = onDrawerScrollListener;
    }

    public View getHandle() {
        return mHandle;
    }

    public View getContent() {
        return mContent;
    }

    public void unlock() {
        mLocked = false;
    }

    public void lock() {
        mLocked = true;
    }

    public boolean isOpened() {
        return mExpanded;
    }

    public boolean isMoving() {
        return mAnimating;
    }
}
