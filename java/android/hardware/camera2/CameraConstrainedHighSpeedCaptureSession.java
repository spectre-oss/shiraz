// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2;

import java.util.List;

public abstract class CameraConstrainedHighSpeedCaptureSession extends CameraCaptureSession {
    public CameraConstrainedHighSpeedCaptureSession() {
    }

    public abstract List<CaptureRequest> createHighSpeedRequestList(CaptureRequest request)
            throws CameraAccessException;
}
