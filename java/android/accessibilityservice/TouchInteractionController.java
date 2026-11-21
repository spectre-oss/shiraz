// SPDX-License-Identifier: GPL-3.0-only
package android.accessibilityservice;

import android.view.MotionEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class TouchInteractionController {
    public static final int STATE_CLEAR = 0;
    public static final int STATE_TOUCH_INTERACTING = 1;
    public static final int STATE_TOUCH_EXPLORING = 2;
    public static final int STATE_DRAGGING = 3;
    public static final int STATE_DELEGATING = 4;

    private static final int MAX_POINTER_COUNT = 32;

    private final int mDisplayId;
    private final Map<Callback, Executor> mCallbacks = new LinkedHashMap<Callback, Executor>();
    private int mState = STATE_CLEAR;

    TouchInteractionController(int displayId) {
        mDisplayId = displayId;
    }

    public void registerCallback(Executor executor, Callback callback) {
        Objects.requireNonNull(callback, "callback");
        synchronized (mCallbacks) {
            mCallbacks.put(callback, executor);
        }
    }

    public boolean unregisterCallback(Callback callback) {
        Objects.requireNonNull(callback, "callback");
        synchronized (mCallbacks) {
            return mCallbacks.remove(callback) != null;
        }
    }

    public void unregisterAllCallbacks() {
        synchronized (mCallbacks) {
            mCallbacks.clear();
        }
    }

    public void requestTouchExploration() {
        validateTransitionRequest();
        mState = STATE_TOUCH_EXPLORING;
    }

    public void requestDragging(int pointerId) {
        validateTransitionRequest();
        if (pointerId < 0 || pointerId >= MAX_POINTER_COUNT) {
            throw new IllegalArgumentException("Invalid pointer id: " + pointerId);
        }
        mState = STATE_DRAGGING;
    }

    public void requestDelegating() {
        validateTransitionRequest();
        mState = STATE_DELEGATING;
    }

    public void performClick() {
    }

    public void performLongClickAndStartDrag() {
    }

    private void validateTransitionRequest() {
        if (mState != STATE_TOUCH_INTERACTING) {
            throw new IllegalStateException(
                    "State transition is not allowed in " + stateToString(mState));
        }
    }

    public int getMaxPointerCount() {
        return MAX_POINTER_COUNT;
    }

    public int getDisplayId() {
        return mDisplayId;
    }

    public int getState() {
        return mState;
    }

    public static String stateToString(int state) {
        switch (state) {
            case STATE_CLEAR:
                return "STATE_CLEAR";
            case STATE_TOUCH_INTERACTING:
                return "STATE_TOUCH_INTERACTING";
            case STATE_TOUCH_EXPLORING:
                return "STATE_TOUCH_EXPLORING";
            case STATE_DRAGGING:
                return "STATE_DRAGGING";
            case STATE_DELEGATING:
                return "STATE_DELEGATING";
            default:
                return "UNKNOWN_STATE: " + state;
        }
    }

    public interface Callback {
        void onMotionEvent(MotionEvent event);

        void onStateChanged(int state);
    }
}
