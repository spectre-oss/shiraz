// SPDX-License-Identifier: GPL-3.0-only
package android.view.accessibility;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Local capability registry. No Android accessibility service or host AT-SPI bridge is connected. */
public final class AccessibilityManager {
    public static final int FLAG_CONTENT_ICONS = 1;
    public static final int FLAG_CONTENT_TEXT = 2;
    public static final int FLAG_CONTENT_CONTROLS = 4;

    public interface AccessibilityStateChangeListener {
        void onAccessibilityStateChanged(boolean enabled);
    }

    public interface TouchExplorationStateChangeListener {
        void onTouchExplorationStateChanged(boolean enabled);
    }

    public interface AccessibilityServicesStateChangeListener {
        void onAccessibilityServicesStateChanged(AccessibilityManager manager);
    }

    public interface AudioDescriptionRequestedChangeListener {
        void onAudioDescriptionRequestedChanged(boolean enabled);
    }

    public interface HighTextContrastChangeListener {
        void onHighTextContrastStateChanged(boolean enabled);
    }

    private final IdentityHashMap<AccessibilityStateChangeListener, Handler> state =
            new IdentityHashMap<AccessibilityStateChangeListener, Handler>();
    private final IdentityHashMap<TouchExplorationStateChangeListener, Handler> touch =
            new IdentityHashMap<TouchExplorationStateChangeListener, Handler>();
    private final IdentityHashMap<HighTextContrastChangeListener, Handler> contrast =
            new IdentityHashMap<HighTextContrastChangeListener, Handler>();
    private final IdentityHashMap<AccessibilityServicesStateChangeListener, Executor> services =
            new IdentityHashMap<AccessibilityServicesStateChangeListener, Executor>();
    private final IdentityHashMap<AudioDescriptionRequestedChangeListener, Executor> audioDesc =
            new IdentityHashMap<AudioDescriptionRequestedChangeListener, Executor>();
    private final ArrayList<AccessibilityRequestPreparer> preparers =
            new ArrayList<AccessibilityRequestPreparer>();
    private boolean closed;

    private AccessibilityManager() {
    }

    public static AccessibilityManager shirazCreate() {
        return new AccessibilityManager();
    }

    private void live() {
        if (closed) {
            throw new IllegalStateException("Accessibility manager is closed");
        }
    }

    public boolean isEnabled() {
        synchronized (this) {
            live();
            return false;
        }
    }

    public boolean isTouchExplorationEnabled() {
        synchronized (this) {
            live();
            return false;
        }
    }

    public boolean isHighTextContrastEnabled() {
        synchronized (this) {
            live();
            return false;
        }
    }

    @Deprecated
    public List<ServiceInfo> getAccessibilityServiceList() {
        synchronized (this) {
            live();
            return Collections.emptyList();
        }
    }

    public List<AccessibilityServiceInfo> getInstalledAccessibilityServiceList() {
        synchronized (this) {
            live();
            return Collections.emptyList();
        }
    }

    public List<AccessibilityServiceInfo> getEnabledAccessibilityServiceList(
            int feedbackTypeFlags) {
        synchronized (this) {
            live();
            return Collections.emptyList();
        }
    }

    private <T, V> void add(IdentityHashMap<T, V> map, T listener, V dispatcher) {
        live();
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        if (!map.containsKey(listener) && map.size() >= 1024) {
            throw new IllegalStateException("Accessibility listener limit");
        }
        map.put(listener, dispatcher);
    }

    private <T, V> boolean remove(IdentityHashMap<T, V> map, T listener) {
        live();
        boolean present = map.containsKey(listener);
        map.remove(listener);
        return present;
    }

    public boolean addAccessibilityStateChangeListener(
            AccessibilityStateChangeListener listener) {
        synchronized (this) {
            add(state, listener, null);
            return true;
        }
    }

    public void addAccessibilityStateChangeListener(
            AccessibilityStateChangeListener listener, Handler handler) {
        synchronized (this) {
            add(state, listener, handler);
        }
    }

    public boolean removeAccessibilityStateChangeListener(
            AccessibilityStateChangeListener listener) {
        synchronized (this) {
            return remove(state, listener);
        }
    }

    public boolean addTouchExplorationStateChangeListener(
            TouchExplorationStateChangeListener listener) {
        synchronized (this) {
            add(touch, listener, null);
            return true;
        }
    }

    public void addTouchExplorationStateChangeListener(
            TouchExplorationStateChangeListener listener, Handler handler) {
        synchronized (this) {
            add(touch, listener, handler);
        }
    }

    public boolean removeTouchExplorationStateChangeListener(
            TouchExplorationStateChangeListener listener) {
        synchronized (this) {
            return remove(touch, listener);
        }
    }

    public void addAccessibilityServicesStateChangeListener(
            Executor executor, AccessibilityServicesStateChangeListener listener) {
        Objects.requireNonNull(executor, "executor");
        synchronized (this) {
            add(services, listener, executor);
        }
    }

    public void addAccessibilityServicesStateChangeListener(
            AccessibilityServicesStateChangeListener listener) {
        synchronized (this) {
            add(services, listener, null);
        }
    }

    public boolean removeAccessibilityServicesStateChangeListener(
            AccessibilityServicesStateChangeListener listener) {
        synchronized (this) {
            return remove(services, listener);
        }
    }

    public void addHighTextContrastStateChangeListener(
            HighTextContrastChangeListener listener, Handler handler) {
        synchronized (this) {
            add(contrast, listener, handler);
        }
    }

    public void removeHighTextContrastStateChangeListener(
            HighTextContrastChangeListener listener) {
        synchronized (this) {
            remove(contrast, listener);
        }
    }

    public boolean isRequestFromAccessibilityTool() {
        synchronized (this) {
            live();
            return false;
        }
    }

    public void addAccessibilityRequestPreparer(AccessibilityRequestPreparer preparer) {
        Objects.requireNonNull(preparer, "preparer");
        synchronized (this) {
            live();
            if (!preparers.contains(preparer)) {
                preparers.add(preparer);
            }
        }
    }

    public void removeAccessibilityRequestPreparer(AccessibilityRequestPreparer preparer) {
        Objects.requireNonNull(preparer, "preparer");
        synchronized (this) {
            live();
            preparers.remove(preparer);
        }
    }

    public int getRecommendedTimeoutMillis(int originalTimeout, int uiContentFlags) {
        synchronized (this) {
            live();
            return Math.max(0, originalTimeout);
        }
    }

    public int getAccessibilityFocusStrokeWidth() {
        synchronized (this) {
            live();
            return 0;
        }
    }

    public int getAccessibilityFocusColor() {
        synchronized (this) {
            live();
            return 0;
        }
    }

    public void addAudioDescriptionRequestedChangeListener(
            Executor executor, AudioDescriptionRequestedChangeListener listener) {
        Objects.requireNonNull(executor, "executor");
        synchronized (this) {
            add(audioDesc, listener, executor);
        }
    }

    public boolean removeAudioDescriptionRequestedChangeListener(
            AudioDescriptionRequestedChangeListener listener) {
        synchronized (this) {
            return remove(audioDesc, listener);
        }
    }

    public boolean isAudioDescriptionRequested() {
        synchronized (this) {
            live();
            return false;
        }
    }

    public static boolean isAccessibilityButtonSupported() {
        return false;
    }

    public void sendAccessibilityEvent(AccessibilityEvent event) {
        synchronized (this) {
            live();
            if (event == null) {
                throw new NullPointerException("event");
            }
            throw new UnsupportedOperationException(
                    "Accessibility event delivery requires a host accessibility bridge");
        }
    }

    public void interrupt() {
        synchronized (this) {
            live();
            throw new UnsupportedOperationException(
                    "Accessibility feedback requires a host accessibility bridge");
        }
    }

    public void shirazClose() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            state.clear();
            touch.clear();
            contrast.clear();
            services.clear();
            audioDesc.clear();
            preparers.clear();
        }
    }
}
