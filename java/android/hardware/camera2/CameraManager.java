// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2;

import android.content.Context;
import android.hardware.camera2.params.SessionConfiguration;
import android.os.Handler;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public final class CameraManager {
    private final Object mLock = new Object();
    private final Map<AvailabilityCallback, Object> mAvailabilityCallbacks =
            new LinkedHashMap<AvailabilityCallback, Object>();
    private final Map<TorchCallback, Object> mTorchCallbacks =
            new LinkedHashMap<TorchCallback, Object>();

    public CameraManager() {
    }

    public CameraManager(Context context) {
    }

    public String[] getCameraIdList() throws CameraAccessException {
        return new String[0];
    }

    public Set<Set<String>> getConcurrentCameraIds() throws CameraAccessException {
        return Collections.emptySet();
    }

    public boolean isConcurrentSessionConfigurationSupported(
            Map<String, SessionConfiguration> cameraIdAndSessionConfig)
            throws CameraAccessException {
        if (cameraIdAndSessionConfig == null) {
            throw new IllegalArgumentException("cameraIdAndSessionConfig must not be null");
        }
        return false;
    }

    public void registerAvailabilityCallback(AvailabilityCallback callback, Handler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        synchronized (mLock) {
            mAvailabilityCallbacks.put(callback, handler);
        }
    }

    public void registerAvailabilityCallback(Executor executor, AvailabilityCallback callback) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        synchronized (mLock) {
            mAvailabilityCallbacks.put(callback, executor);
        }
    }

    public void unregisterAvailabilityCallback(AvailabilityCallback callback) {
        synchronized (mLock) {
            mAvailabilityCallbacks.remove(callback);
        }
    }

    public void registerTorchCallback(TorchCallback callback, Handler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        synchronized (mLock) {
            mTorchCallbacks.put(callback, handler);
        }
    }

    public void registerTorchCallback(Executor executor, TorchCallback callback) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        synchronized (mLock) {
            mTorchCallbacks.put(callback, executor);
        }
    }

    public void unregisterTorchCallback(TorchCallback callback) {
        synchronized (mLock) {
            mTorchCallbacks.remove(callback);
        }
    }

    private static void requireCameraId(String cameraId) {
        if (cameraId == null) {
            throw new IllegalArgumentException("cameraId was null");
        }
    }

    public CameraCharacteristics getCameraCharacteristics(String cameraId)
            throws CameraAccessException {
        requireCameraId(cameraId);
        throw new IllegalArgumentException("Unknown camera ID " + cameraId);
    }

    public CameraExtensionCharacteristics getCameraExtensionCharacteristics(String cameraId)
            throws CameraAccessException {
        requireCameraId(cameraId);
        throw new IllegalArgumentException("Unknown camera ID " + cameraId);
    }

    public CameraDevice.CameraDeviceSetup getCameraDeviceSetup(String cameraId)
            throws CameraAccessException {
        requireCameraId(cameraId);
        throw new IllegalArgumentException("Unknown camera ID " + cameraId);
    }

    public boolean isCameraDeviceSetupSupported(String cameraId) throws CameraAccessException {
        requireCameraId(cameraId);
        throw new IllegalArgumentException("Unknown camera ID " + cameraId);
    }

    public void openCamera(
            String cameraId,
            CameraDevice.StateCallback callback,
            Handler handler)
            throws CameraAccessException {
        requireCameraId(cameraId);
        if (callback == null) {
            throw new IllegalArgumentException("callback was null");
        }
        throw new IllegalArgumentException("Unknown camera ID " + cameraId);
    }

    public void openCamera(
            String cameraId,
            Executor executor,
            CameraDevice.StateCallback callback)
            throws CameraAccessException {
        requireCameraId(cameraId);
        if (executor == null) {
            throw new IllegalArgumentException("executor was null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback was null");
        }
        throw new IllegalArgumentException("Unknown camera ID " + cameraId);
    }

    public void setTorchMode(String cameraId, boolean enabled) throws CameraAccessException {
        requireCameraId(cameraId);
        throw new IllegalArgumentException("Unknown camera ID " + cameraId);
    }

    public void turnOnTorchWithStrengthLevel(String cameraId, int torchStrength)
            throws CameraAccessException {
        requireCameraId(cameraId);
        throw new IllegalArgumentException("Unknown camera ID " + cameraId);
    }

    public int getTorchStrengthLevel(String cameraId) throws CameraAccessException {
        requireCameraId(cameraId);
        throw new IllegalArgumentException("Unknown camera ID " + cameraId);
    }

    public abstract static class AvailabilityCallback {
        public AvailabilityCallback() {
        }

        public void onCameraAvailable(String cameraId) {
        }

        public void onCameraUnavailable(String cameraId) {
        }

        public void onCameraAccessPrioritiesChanged() {
        }

        public void onPhysicalCameraAvailable(String cameraId, String physicalCameraId) {
        }

        public void onPhysicalCameraUnavailable(String cameraId, String physicalCameraId) {
        }
    }

    public abstract static class TorchCallback {
        public TorchCallback() {
        }

        public void onTorchModeUnavailable(String cameraId) {
        }

        public void onTorchModeChanged(String cameraId, boolean enabled) {
        }

        public void onTorchStrengthLevelChanged(String cameraId, int newStrengthLevel) {
        }
    }
}
