// SPDX-License-Identifier: GPL-3.0-only
package android.companion.virtual;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

public final class VirtualDeviceManager {
    public interface VirtualDeviceListener {
        default void onVirtualDeviceCreated(int deviceId) {
        }

        default void onVirtualDeviceClosed(int deviceId) {
        }
    }

    private final List<VirtualDeviceListener> mListeners = new CopyOnWriteArrayList<>();

    public VirtualDeviceManager() {
    }

    public List<VirtualDevice> getVirtualDevices() {
        return Collections.emptyList();
    }

    public VirtualDevice getVirtualDevice(int deviceId) {
        return null;
    }

    public void registerVirtualDeviceListener(Executor executor, VirtualDeviceListener listener) {
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        mListeners.add(listener);
    }

    public void unregisterVirtualDeviceListener(VirtualDeviceListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        mListeners.remove(listener);
    }
}
