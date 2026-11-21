// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TotalCaptureResult extends CaptureResult {
    private final List<CaptureResult> mPartialResults;
    private final Map<String, CaptureResult> mPhysicalCaptureResults;
    private final Map<String, TotalCaptureResult> mPhysicalTotalResults;

    public TotalCaptureResult(
            String cameraId,
            CaptureRequest request,
            int sequenceId,
            long frameNumber,
            List<CaptureResult> partials) {
        super(cameraId, request, sequenceId, frameNumber);
        if (partials == null) {
            mPartialResults = Collections.emptyList();
        } else {
            mPartialResults = Collections.unmodifiableList(new ArrayList<CaptureResult>(partials));
        }
        mPhysicalCaptureResults = Collections.emptyMap();
        mPhysicalTotalResults = Collections.emptyMap();
    }

    public List<CaptureResult> getPartialResults() {
        return mPartialResults;
    }

    public Map<String, CaptureResult> getPhysicalCameraResults() {
        return new LinkedHashMap<String, CaptureResult>(mPhysicalCaptureResults);
    }

    public Map<String, TotalCaptureResult> getPhysicalCameraTotalResults() {
        return Collections.unmodifiableMap(mPhysicalTotalResults);
    }
}
