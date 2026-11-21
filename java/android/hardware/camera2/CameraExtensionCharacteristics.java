// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2;

import android.util.Range;
import android.util.Size;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class CameraExtensionCharacteristics {
    public static final int EXTENSION_AUTOMATIC = 0;
    public static final int EXTENSION_FACE_RETOUCH = 1;
    public static final int EXTENSION_BEAUTY = 1;
    public static final int EXTENSION_BOKEH = 2;
    public static final int EXTENSION_HDR = 3;
    public static final int EXTENSION_NIGHT = 4;

    private final String mCameraId;

    public CameraExtensionCharacteristics(String cameraId) {
        mCameraId = cameraId;
    }

    public List<Integer> getSupportedExtensions() {
        return Collections.emptyList();
    }

    public <T> T get(int extension, CameraCharacteristics.Key<T> key) {
        if (key == null) {
            throw new NullPointerException("key must not be null");
        }
        return null;
    }

    @SuppressWarnings("rawtypes")
    public Set<CameraCharacteristics.Key> getKeys(int extension) {
        return Collections.emptySet();
    }

    public boolean isPostviewAvailable(int extension) {
        return false;
    }

    public List<Size> getPostviewSupportedSizes(
            int extension, Size captureSize, int format) {
        if (captureSize == null) {
            throw new NullPointerException("captureSize must not be null");
        }
        return Collections.emptyList();
    }

    public <T> List<Size> getExtensionSupportedSizes(int extension, Class<T> klass) {
        if (klass == null) {
            throw new NullPointerException("klass must not be null");
        }
        return Collections.emptyList();
    }

    public List<Size> getExtensionSupportedSizes(int extension, int format) {
        return Collections.emptyList();
    }

    public Range<Long> getEstimatedCaptureLatencyRangeMillis(
            int extension, Size captureOutputSize, int format) {
        if (captureOutputSize == null) {
            throw new NullPointerException("captureOutputSize must not be null");
        }
        return null;
    }

    public boolean isCaptureProcessProgressAvailable(int extension) {
        return false;
    }

    @SuppressWarnings("rawtypes")
    public Set<CaptureRequest.Key> getAvailableCaptureRequestKeys(int extension) {
        return Collections.emptySet();
    }

    @SuppressWarnings("rawtypes")
    public Set<CaptureResult.Key> getAvailableCaptureResultKeys(int extension) {
        return Collections.emptySet();
    }

    String getCameraId() {
        return mCameraId;
    }
}
