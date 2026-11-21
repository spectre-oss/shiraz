// SPDX-License-Identifier: GPL-3.0-only

package android.hardware.camera2;

public class CaptureFailure {
    public static final int REASON_ERROR = 0;
    public static final int REASON_FLUSHED = 1;

    private final CaptureRequest mRequest;
    private final int mReason;
    private final boolean mDropped;
    private final int mSequenceId;
    private final long mFrameNumber;
    private final String mErrorPhysicalCameraId;

    public CaptureFailure(
            CaptureRequest request,
            int reason,
            boolean wasImageCaptured,
            int sequenceId,
            long frameNumber,
            String errorPhysicalCameraId) {
        mRequest = request;
        mReason = reason;
        mDropped = !wasImageCaptured;
        mSequenceId = sequenceId;
        mFrameNumber = frameNumber;
        mErrorPhysicalCameraId = errorPhysicalCameraId;
    }

    public CaptureRequest getRequest() {
        return mRequest;
    }

    public long getFrameNumber() {
        return mFrameNumber;
    }

    public int getReason() {
        return mReason;
    }

    public boolean wasImageCaptured() {
        return !mDropped;
    }

    public int getSequenceId() {
        return mSequenceId;
    }

    public String getPhysicalCameraId() {
        return mErrorPhysicalCameraId;
    }
}
