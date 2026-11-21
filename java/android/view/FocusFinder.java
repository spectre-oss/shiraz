// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class FocusFinder {
    private static final ThreadLocal<FocusFinder> tlFocusFinder =
            new ThreadLocal<FocusFinder>() {
                @Override
                protected FocusFinder initialValue() {
                    return new FocusFinder();
                }
            };

    public static FocusFinder getInstance() {
        return tlFocusFinder.get();
    }

    private final Rect mFocusedRect = new Rect();
    private final Rect mOtherRect = new Rect();
    private final Rect mBestCandidateRect = new Rect();
    private final ArrayList<View> mTempList = new ArrayList<View>();

    FocusFinder() {
    }

    public final View findNextFocus(ViewGroup root, View focused, int direction) {
        return findNextFocus(root, focused, null, direction);
    }

    public View findNextFocusFromRect(ViewGroup root, Rect focusedRect, int direction) {
        mFocusedRect.set(focusedRect);
        return findNextFocus(root, null, mFocusedRect, direction);
    }

    private View findNextFocus(ViewGroup root, View focused, Rect focusedRect, int direction) {
        if (root == null) {
            return null;
        }
        View effectiveRoot = getEffectiveRoot(root, focused);
        View next = null;
        if (focused != null) {
            next = findNextUserSpecifiedFocus(effectiveRoot, focused, direction);
        }
        if (next != null) {
            return next;
        }
        ArrayList<View> focusables = mTempList;
        try {
            focusables.clear();
            effectiveRoot.addFocusables(focusables, direction);
            if (!focusables.isEmpty()) {
                next = findNextFocus(effectiveRoot, focused, focusedRect, direction, focusables);
            }
        } finally {
            focusables.clear();
        }
        return next;
    }

    private static View getEffectiveRoot(ViewGroup root, View focused) {
        if (focused == null || focused == root) {
            return root;
        }
        View effective = null;
        ViewParent parent = focused.getParent();
        while (parent instanceof ViewGroup) {
            if (parent == root) {
                return effective != null ? effective : root;
            }
            ViewGroup vg = (ViewGroup) parent;
            if (vg.isKeyboardNavigationCluster()) {
                effective = vg;
            }
            parent = parent.getParent();
        }
        return root;
    }

    private View findNextUserSpecifiedFocus(View root, View focused, int direction) {
        int nextId = View.NO_ID;
        switch (direction) {
            case View.FOCUS_LEFT:
                nextId = focused.getNextFocusLeftId();
                break;
            case View.FOCUS_RIGHT:
                nextId = focused.getNextFocusRightId();
                break;
            case View.FOCUS_UP:
                nextId = focused.getNextFocusUpId();
                break;
            case View.FOCUS_DOWN:
                nextId = focused.getNextFocusDownId();
                break;
            case View.FOCUS_FORWARD:
                nextId = focused.getNextFocusForwardId();
                break;
            case View.FOCUS_BACKWARD:
                if (focused.getId() != View.NO_ID) {
                    return findPreviousUserSpecifiedFocus(root, focused, focused.getId());
                }
                return null;
            default:
                return null;
        }
        if (nextId == View.NO_ID) {
            return null;
        }
        View target = root.findViewById(nextId);
        if (target != null && target.getVisibility() == View.VISIBLE && target.isEnabled()
                && target.isFocusable() && (!target.isInTouchMode() || target.isFocusableInTouchMode())) {
            return target;
        }
        return null;
    }

    private View findPreviousUserSpecifiedFocus(View root, View focused, int targetId) {
        ArrayList<View> list = new ArrayList<View>();
        root.addFocusables(list, View.FOCUS_BACKWARD);
        for (int i = 0; i < list.size(); i++) {
            View candidate = list.get(i);
            if (candidate != focused && candidate.getNextFocusForwardId() == targetId) {
                return candidate;
            }
        }
        return null;
    }

    private View findNextFocus(View root, View focused, Rect focusedRect, int direction,
            ArrayList<View> focusables) {
        ViewGroup rootGroup = root instanceof ViewGroup ? (ViewGroup) root : null;
        if (focused != null) {
            if (focusedRect == null) {
                focusedRect = mFocusedRect;
            }
            focused.getFocusedRect(focusedRect);
            if (rootGroup != null && focused != rootGroup) {
                rootGroup.offsetDescendantRectToMyCoords(focused, focusedRect);
            }
        } else if (focusedRect == null) {
            focusedRect = mFocusedRect;
            switch (direction) {
                case View.FOCUS_RIGHT:
                case View.FOCUS_DOWN:
                    setFocusTopLeft(root, focusedRect);
                    break;
                case View.FOCUS_FORWARD:
                    if (root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                        setFocusBottomRight(root, focusedRect);
                    } else {
                        setFocusTopLeft(root, focusedRect);
                    }
                    break;
                case View.FOCUS_LEFT:
                case View.FOCUS_UP:
                    setFocusBottomRight(root, focusedRect);
                    break;
                case View.FOCUS_BACKWARD:
                    if (root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                        setFocusTopLeft(root, focusedRect);
                    } else {
                        setFocusBottomRight(root, focusedRect);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown direction: " + direction);
            }
        }

        switch (direction) {
            case View.FOCUS_FORWARD:
            case View.FOCUS_BACKWARD:
                return findNextFocusInRelativeDirection(focusables, rootGroup, focused, focusedRect, direction);
            case View.FOCUS_UP:
            case View.FOCUS_DOWN:
            case View.FOCUS_LEFT:
            case View.FOCUS_RIGHT:
                return findNextFocusInAbsoluteDirection(focusables, rootGroup, focused, focusedRect, direction);
            default:
                throw new IllegalArgumentException("Unknown direction: " + direction);
        }
    }

    private View findNextFocusInRelativeDirection(final ArrayList<View> focusables,
            final ViewGroup root, View focused, Rect focusedRect, int direction) {
        if (root != null && focusables.size() > 1) {
            focusables.remove(root);
        }
        if (focusables.isEmpty()) {
            return null;
        }
        final boolean isRtl = root != null && root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        Collections.sort(focusables, new Comparator<View>() {
            private final Rect mFirstRect = new Rect();
            private final Rect mSecondRect = new Rect();

            @Override
            public int compare(View first, View second) {
                if (first == second) {
                    return 0;
                }
                getViewRectInRoot(root, first, mFirstRect);
                getViewRectInRoot(root, second, mSecondRect);
                if (mFirstRect.top < mSecondRect.top) {
                    return -1;
                }
                if (mFirstRect.top > mSecondRect.top) {
                    return 1;
                }
                if (mFirstRect.left < mSecondRect.left) {
                    return isRtl ? 1 : -1;
                }
                if (mFirstRect.left > mSecondRect.left) {
                    return isRtl ? -1 : 1;
                }
                if (mFirstRect.bottom < mSecondRect.bottom) {
                    return -1;
                }
                if (mFirstRect.bottom > mSecondRect.bottom) {
                    return 1;
                }
                if (mFirstRect.right < mSecondRect.right) {
                    return isRtl ? 1 : -1;
                }
                if (mFirstRect.right > mSecondRect.right) {
                    return isRtl ? -1 : 1;
                }
                return 0;
            }
        });
        int count = focusables.size();
        if (direction == View.FOCUS_FORWARD) {
            if (focused != null) {
                int position = focusables.lastIndexOf(focused);
                if (position >= 0 && position + 1 < count) {
                    return focusables.get(position + 1);
                }
            }
            return count > 0 ? focusables.get(0) : null;
        } else {
            if (focused != null) {
                int position = focusables.indexOf(focused);
                if (position > 0) {
                    return focusables.get(position - 1);
                }
            }
            return count > 0 ? focusables.get(count - 1) : null;
        }
    }

    private View findNextFocusInAbsoluteDirection(ArrayList<View> focusables, ViewGroup root,
            View focused, Rect focusedRect, int direction) {
        mBestCandidateRect.set(focusedRect);
        switch (direction) {
            case View.FOCUS_LEFT:
                mBestCandidateRect.offset(focusedRect.width() + 1, 0);
                break;
            case View.FOCUS_RIGHT:
                mBestCandidateRect.offset(-(focusedRect.width() + 1), 0);
                break;
            case View.FOCUS_UP:
                mBestCandidateRect.offset(0, focusedRect.height() + 1);
                break;
            case View.FOCUS_DOWN:
                mBestCandidateRect.offset(0, -(focusedRect.height() + 1));
                break;
            default:
                break;
        }

        View closest = null;
        int numFocusables = focusables.size();
        for (int i = 0; i < numFocusables; i++) {
            View focusable = focusables.get(i);
            if (focusable == focused || focusable == root) {
                continue;
            }
            getViewRectInRoot(root, focusable, mOtherRect);
            if (isBetterCandidate(direction, focusedRect, mOtherRect, mBestCandidateRect)) {
                mBestCandidateRect.set(mOtherRect);
                closest = focusable;
            }
        }
        return closest;
    }

    private static void getViewRectInRoot(ViewGroup root, View view, Rect out) {
        view.getDrawingRect(out);
        if (root != null && view != root) {
            root.offsetDescendantRectToMyCoords(view, out);
        }
    }

    private static void setFocusBottomRight(View root, Rect focusedRect) {
        int rootBottom = root.getScrollY() + root.getHeight();
        int rootRight = root.getScrollX() + root.getWidth();
        focusedRect.set(rootRight, rootBottom, rootRight, rootBottom);
    }

    private static void setFocusTopLeft(View root, Rect focusedRect) {
        int rootTop = root.getScrollY();
        int rootLeft = root.getScrollX();
        focusedRect.set(rootLeft, rootTop, rootLeft, rootTop);
    }

    boolean isBetterCandidate(int direction, Rect source, Rect rect1, Rect rect2) {
        if (!isCandidate(source, rect1, direction)) {
            return false;
        }
        if (!isCandidate(source, rect2, direction)) {
            return true;
        }
        if (beamBeats(direction, source, rect1, rect2)) {
            return true;
        }
        if (beamBeats(direction, source, rect2, rect1)) {
            return false;
        }
        return getWeightedDistanceFor(
                majorAxisDistance(direction, source, rect1),
                minorAxisDistance(direction, source, rect1))
                < getWeightedDistanceFor(
                majorAxisDistance(direction, source, rect2),
                minorAxisDistance(direction, source, rect2));
    }

    boolean beamBeats(int direction, Rect source, Rect rect1, Rect rect2) {
        final boolean rect1InSrcBeam = beamsOverlap(direction, source, rect1);
        final boolean rect2InSrcBeam = beamsOverlap(direction, source, rect2);
        if (rect2InSrcBeam || !rect1InSrcBeam) {
            return false;
        }
        if (!isToDirectionOf(direction, source, rect2)) {
            return true;
        }
        if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
            return true;
        }
        return majorAxisDistance(direction, source, rect1)
                < majorAxisDistanceToFarEdge(direction, source, rect2);
    }

    static long getWeightedDistanceFor(int majorAxisDistance, int minorAxisDistance) {
        return 13L * majorAxisDistance * majorAxisDistance
                + (long) minorAxisDistance * minorAxisDistance;
    }

    boolean isCandidate(Rect srcRect, Rect destRect, int direction) {
        switch (direction) {
            case View.FOCUS_LEFT:
                return (srcRect.right > destRect.right || srcRect.left >= destRect.right)
                        && srcRect.left > destRect.left;
            case View.FOCUS_RIGHT:
                return (srcRect.left < destRect.left || srcRect.right <= destRect.left)
                        && srcRect.right < destRect.right;
            case View.FOCUS_UP:
                return (srcRect.bottom > destRect.bottom || srcRect.top >= destRect.bottom)
                        && srcRect.top > destRect.top;
            case View.FOCUS_DOWN:
                return (srcRect.top < destRect.top || srcRect.bottom <= destRect.top)
                        && srcRect.bottom < destRect.bottom;
            default:
                throw new IllegalArgumentException("direction must be one of "
                        + "{FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
        }
    }

    boolean beamsOverlap(int direction, Rect rect1, Rect rect2) {
        switch (direction) {
            case View.FOCUS_LEFT:
            case View.FOCUS_RIGHT:
                return (rect2.bottom > rect1.top) && (rect2.top < rect1.bottom);
            case View.FOCUS_UP:
            case View.FOCUS_DOWN:
                return (rect2.right > rect1.left) && (rect2.left < rect1.right);
            default:
                throw new IllegalArgumentException("direction must be one of "
                        + "{FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
        }
    }

    boolean isToDirectionOf(int direction, Rect src, Rect dest) {
        switch (direction) {
            case View.FOCUS_LEFT:
                return src.left >= dest.right;
            case View.FOCUS_RIGHT:
                return src.right <= dest.left;
            case View.FOCUS_UP:
                return src.top >= dest.bottom;
            case View.FOCUS_DOWN:
                return src.bottom <= dest.top;
            default:
                throw new IllegalArgumentException("direction must be one of "
                        + "{FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
        }
    }

    static int majorAxisDistance(int direction, Rect source, Rect dest) {
        return Math.max(0, majorAxisDistanceRaw(direction, source, dest));
    }

    static int majorAxisDistanceRaw(int direction, Rect source, Rect dest) {
        switch (direction) {
            case View.FOCUS_LEFT:
                return source.left - dest.right;
            case View.FOCUS_RIGHT:
                return dest.left - source.right;
            case View.FOCUS_UP:
                return source.top - dest.bottom;
            case View.FOCUS_DOWN:
                return dest.top - source.bottom;
            default:
                throw new IllegalArgumentException("direction must be one of "
                        + "{FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
        }
    }

    static int majorAxisDistanceToFarEdge(int direction, Rect source, Rect dest) {
        return Math.max(1, majorAxisDistanceToFarEdgeRaw(direction, source, dest));
    }

    static int majorAxisDistanceToFarEdgeRaw(int direction, Rect source, Rect dest) {
        switch (direction) {
            case View.FOCUS_LEFT:
                return source.left - dest.left;
            case View.FOCUS_RIGHT:
                return dest.right - source.right;
            case View.FOCUS_UP:
                return source.top - dest.top;
            case View.FOCUS_DOWN:
                return dest.bottom - source.bottom;
            default:
                throw new IllegalArgumentException("direction must be one of "
                        + "{FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
        }
    }

    static int minorAxisDistance(int direction, Rect source, Rect dest) {
        switch (direction) {
            case View.FOCUS_LEFT:
            case View.FOCUS_RIGHT:
                return Math.abs((source.top + source.height() / 2) - (dest.top + dest.height() / 2));
            case View.FOCUS_UP:
            case View.FOCUS_DOWN:
                return Math.abs((source.left + source.width() / 2) - (dest.left + dest.width() / 2));
            default:
                throw new IllegalArgumentException("direction must be one of "
                        + "{FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
        }
    }

    public View findNextKeyboardNavigationCluster(View root, View currentCluster, int direction) {
        if (root == null) {
            return null;
        }
        return KeyboardClusters.search(root, currentCluster, direction);
    }

    public View findNearestTouchable(ViewGroup root, int x, int y, int direction, int[] deltas) {
        if (root == null) {
            return null;
        }
        ArrayList<View> touchables = root.getTouchables();
        int minDistance = Integer.MAX_VALUE;
        View closest = null;
        int edgeSlop;
        try {
            edgeSlop = ViewConfiguration.get(root.getContext()).getScaledEdgeSlop();
        } catch (RuntimeException unattached) {
            edgeSlop = ViewConfiguration.getEdgeSlop();
        }
        Rect closestBounds = new Rect();
        Rect touchableBounds = mOtherRect;
        for (int i = 0; i < touchables.size(); i++) {
            View touchable = touchables.get(i);
            touchable.getDrawingRect(touchableBounds);
            if (touchable != root) {
                root.offsetDescendantRectToMyCoords(touchable, touchableBounds);
            }
            if (!isTouchCandidate(x, y, touchableBounds, direction)) {
                continue;
            }
            int distance = Integer.MAX_VALUE;
            switch (direction) {
                case View.FOCUS_LEFT:
                    distance = x - touchableBounds.right + 1;
                    break;
                case View.FOCUS_RIGHT:
                    distance = touchableBounds.left;
                    break;
                case View.FOCUS_UP:
                    distance = y - touchableBounds.bottom + 1;
                    break;
                case View.FOCUS_DOWN:
                    distance = touchableBounds.top;
                    break;
                default:
                    break;
            }
            if (distance < edgeSlop) {
                if (closest == null || closestBounds.contains(touchableBounds)
                        || (!touchableBounds.contains(closestBounds) && distance < minDistance)) {
                    minDistance = distance;
                    closest = touchable;
                    closestBounds.set(touchableBounds);
                    if (deltas != null && deltas.length >= 2) {
                        switch (direction) {
                            case View.FOCUS_LEFT:
                                deltas[0] = -distance;
                                deltas[1] = 0;
                                break;
                            case View.FOCUS_RIGHT:
                                deltas[0] = distance;
                                deltas[1] = 0;
                                break;
                            case View.FOCUS_UP:
                                deltas[0] = 0;
                                deltas[1] = -distance;
                                break;
                            case View.FOCUS_DOWN:
                                deltas[0] = 0;
                                deltas[1] = distance;
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        }
        return closest;
    }

    private static boolean isTouchCandidate(int x, int y, Rect destRect, int direction) {
        switch (direction) {
            case View.FOCUS_LEFT:
                return destRect.left <= x && destRect.top <= y && y <= destRect.bottom;
            case View.FOCUS_RIGHT:
                return destRect.left >= x && destRect.top <= y && y <= destRect.bottom;
            case View.FOCUS_UP:
                return destRect.top <= y && destRect.left <= x && x <= destRect.right;
            case View.FOCUS_DOWN:
                return destRect.top >= y && destRect.left <= x && x <= destRect.right;
            default:
                throw new IllegalArgumentException("direction must be one of "
                        + "{FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
        }
    }
}
