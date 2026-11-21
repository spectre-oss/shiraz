// SPDX-License-Identifier: GPL-3.0-only
package android.window;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/** Concrete priority-ordered predictive back dispatcher for Shiraz windows and dialogs. */
public class WindowOnBackInvokedDispatcher implements OnBackInvokedDispatcher {
    private final TreeMap<Integer, ArrayList<OnBackInvokedCallback>> mCallbacks =
            new TreeMap<Integer, ArrayList<OnBackInvokedCallback>>();
    private final ArrayList<OnBackInvokedCallback> mObserverCallbacks =
            new ArrayList<OnBackInvokedCallback>();
    private OnBackAnimationCallback mActiveAnimationCallback;

    @Override
    public synchronized void registerOnBackInvokedCallback(
            int priority, OnBackInvokedCallback callback) {
        if (callback == null) {
            throw new NullPointerException("callback");
        }
        if (priority < 0 && priority != PRIORITY_SYSTEM_NAVIGATION_OBSERVER) {
            throw new IllegalArgumentException(
                    "Priority cannot be negative except PRIORITY_SYSTEM_NAVIGATION_OBSERVER: "
                            + priority);
        }
        unregisterLocked(callback);
        if (priority == PRIORITY_SYSTEM_NAVIGATION_OBSERVER) {
            mObserverCallbacks.add(callback);
            return;
        }
        ArrayList<OnBackInvokedCallback> bucket = mCallbacks.get(Integer.valueOf(priority));
        if (bucket == null) {
            bucket = new ArrayList<OnBackInvokedCallback>();
            mCallbacks.put(Integer.valueOf(priority), bucket);
        }
        bucket.add(callback);
    }

    @Override
    public synchronized void unregisterOnBackInvokedCallback(OnBackInvokedCallback callback) {
        if (callback == null) {
            return;
        }
        unregisterLocked(callback);
    }

    private void unregisterLocked(OnBackInvokedCallback callback) {
        mObserverCallbacks.remove(callback);
        java.util.Iterator<Map.Entry<Integer, ArrayList<OnBackInvokedCallback>>> it =
                mCallbacks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ArrayList<OnBackInvokedCallback>> entry = it.next();
            ArrayList<OnBackInvokedCallback> bucket = entry.getValue();
            bucket.remove(callback);
            if (bucket.isEmpty()) {
                it.remove();
            }
        }
        if (mActiveAnimationCallback == callback) {
            mActiveAnimationCallback.onBackCancelled();
            mActiveAnimationCallback = null;
        }
    }

    public synchronized OnBackInvokedCallback getTopCallback() {
        if (mCallbacks.isEmpty()) {
            return null;
        }
        ArrayList<OnBackInvokedCallback> highest = mCallbacks.lastEntry().getValue();
        return highest.isEmpty() ? null : highest.get(highest.size() - 1);
    }

    public synchronized boolean hasCallbacks() {
        return getTopCallback() != null;
    }

    public void dispatchOnBackStarted(BackEvent event) {
        OnBackInvokedCallback top = getTopCallback();
        if (top instanceof OnBackAnimationCallback) {
            synchronized (this) {
                mActiveAnimationCallback = (OnBackAnimationCallback) top;
            }
            ((OnBackAnimationCallback) top).onBackStarted(event);
        }
    }

    public void dispatchOnBackProgressed(BackEvent event) {
        OnBackAnimationCallback active;
        synchronized (this) {
            active = mActiveAnimationCallback;
        }
        if (active != null) {
            active.onBackProgressed(event);
        }
    }

    public void dispatchOnBackCancelled() {
        OnBackAnimationCallback active;
        synchronized (this) {
            active = mActiveAnimationCallback;
            mActiveAnimationCallback = null;
        }
        if (active != null) {
            active.onBackCancelled();
        }
    }

    public boolean dispatchOnBackInvoked() {
        ArrayList<OnBackInvokedCallback> observers;
        OnBackInvokedCallback top;
        synchronized (this) {
            observers = new ArrayList<OnBackInvokedCallback>(mObserverCallbacks);
            top = getTopCallback();
            mActiveAnimationCallback = null;
        }
        for (int i = 0; i < observers.size(); i++) {
            observers.get(i).onBackInvoked();
        }
        if (top != null) {
            top.onBackInvoked();
            return true;
        }
        return false;
    }
}
