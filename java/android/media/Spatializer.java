// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class Spatializer {
    public static final int SPATIALIZER_IMMERSIVE_LEVEL_OTHER = -1;
    public static final int SPATIALIZER_IMMERSIVE_LEVEL_NONE = 0;
    public static final int SPATIALIZER_IMMERSIVE_LEVEL_MULTICHANNEL = 1;

    private final Map<OnSpatializerStateChangedListener, Executor> mStateListeners =
            new ConcurrentHashMap<OnSpatializerStateChangedListener, Executor>();
    private final Map<OnHeadTrackerAvailableListener, Executor> mHeadTrackerListeners =
            new ConcurrentHashMap<OnHeadTrackerAvailableListener, Executor>();

    Spatializer() {}

    public interface OnSpatializerStateChangedListener {
        void onSpatializerEnabledChanged(Spatializer spat, boolean enabled);

        void onSpatializerAvailableChanged(Spatializer spat, boolean available);
    }

    public interface OnHeadTrackerAvailableListener {
        void onHeadTrackerAvailableChanged(Spatializer spatializer, boolean available);
    }

    public boolean isEnabled() {
        return false;
    }

    public boolean isAvailable() {
        return false;
    }

    public boolean isHeadTrackerAvailable() {
        return false;
    }

    public void addOnHeadTrackerAvailableListener(
            Executor executor, OnHeadTrackerAvailableListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        mHeadTrackerListeners.put(listener, executor);
    }

    public void removeOnHeadTrackerAvailableListener(OnHeadTrackerAvailableListener listener) {
        Objects.requireNonNull(listener, "listener");
        mHeadTrackerListeners.remove(listener);
    }

    public int getImmersiveAudioLevel() {
        return SPATIALIZER_IMMERSIVE_LEVEL_NONE;
    }

    public boolean canBeSpatialized(AudioAttributes attributes, AudioFormat format) {
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(format, "format");
        return false;
    }

    public void addOnSpatializerStateChangedListener(
            Executor executor, OnSpatializerStateChangedListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        mStateListeners.put(listener, executor);
    }

    public void removeOnSpatializerStateChangedListener(OnSpatializerStateChangedListener listener) {
        Objects.requireNonNull(listener, "listener");
        mStateListeners.remove(listener);
    }
}
