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
    default void onDescendantInvalidated(View child,View target) {}
    void requestDisallowInterceptTouchEvent(boolean disallow);
    default void requestTransparentRegion(View child) {}
    default void invalidateChild(View child, android.graphics.Rect r) {}
    default ViewParent invalidateChildInParent(int[] location, android.graphics.Rect r) { return null; }
    default void requestChildFocus(View child, View focused) {}
    default void recomputeViewAttributes(View child) {}
    default void clearChildFocus(View child) {}
    default boolean getChildVisibleRect(View child, android.graphics.Rect r, android.graphics.Point offset) { return false; }
    default View focusSearch(View v, int direction) { return null; }
    default View keyboardNavigationClusterSearch(View currentCluster, int direction) { return null; }
    default boolean showContextMenuForChild(View originalView) { return false; }
    default boolean showContextMenuForChild(View originalView, float x, float y) { return false; }
    default void createContextMenu(ContextMenu menu) {}
    default ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback) { return startActionModeForChild(originalView, callback, ActionMode.TYPE_PRIMARY); }
    default ActionMode startActionModeForChild(View originalView, ActionMode.Callback callback, int type) { return getParent() != null ? getParent().startActionModeForChild(originalView, callback, type) : null; }
    default void childHasTransientStateChanged(View child, boolean hasTransientState) {}
    default void requestFitSystemWindows() {}
    default ViewParent getParentForAccessibility() { return getParent(); }
    default void notifySubtreeAccessibilityStateChanged(View child, View source, int changeType) {}
    default boolean canResolveLayoutDirection() { return false; }
    default boolean isLayoutDirectionResolved() { return false; }
    default int getLayoutDirection() { return View.LAYOUT_DIRECTION_LTR; }
    default boolean canResolveTextDirection() { return false; }
    default boolean isTextDirectionResolved() { return false; }
    default int getTextDirection() { return View.TEXT_DIRECTION_INHERIT; }
    default boolean canResolveTextAlignment() { return false; }
    default boolean isTextAlignmentResolved() { return false; }
    default int getTextAlignment() { return View.TEXT_ALIGNMENT_INHERIT; }
    default boolean onNestedPrePerformAccessibilityAction(View target, int action, android.os.Bundle args) { return false; }
    default android.window.OnBackInvokedDispatcher findOnBackInvokedDispatcherForChild(View child, View requester) { return null; }
}
