// SPDX-License-Identifier: GPL-3.0-only
package android.view;
/** In-process view hierarchy contract; accessibility and nested scrolling are separate. */
public interface ViewParent {
    boolean onStartNestedScroll(View child,View target,int axes);
    void onNestedScrollAccepted(View child,View target,int axes);
    void onStopNestedScroll(View target);
    void onNestedScroll(View target,int dxConsumed,int dyConsumed,int dxUnconsumed,int dyUnconsumed);
    void onNestedPreScroll(View target,int dx,int dy,int[] consumed);
    boolean onNestedFling(View target,float velocityX,float velocityY,boolean consumed);
    boolean onNestedPreFling(View target,float velocityX,float velocityY);
    boolean requestSendAccessibilityEvent(View child,android.view.accessibility.AccessibilityEvent event);
    boolean requestChildRectangleOnScreen(View child,android.graphics.Rect rectangle,boolean immediate);
    ViewParent getParent();
    void focusableViewAvailable(View view);
    void bringChildToFront(View child);
    void requestLayout();
    boolean isLayoutRequested();
    void childDrawableStateChanged(View child);
    void onDescendantInvalidated(View child,View target);
    void requestDisallowInterceptTouchEvent(boolean disallow);
}
