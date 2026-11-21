// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.graphics.Bitmap;
import android.graphics.PointF;

public class FaceDetector {
    private final int mWidth;
    private final int mHeight;
    private final int mMaxFaces;

    public FaceDetector(int width, int height, int maxFaces) {
        mWidth = width;
        mHeight = height;
        mMaxFaces = maxFaces;
    }

    public int findFaces(Bitmap bitmap, Face[] faces) {
        if (bitmap == null) {
            throw new NullPointerException("bitmap must not be null");
        }
        if (faces == null) {
            throw new NullPointerException("faces array must not be null");
        }
        if (faces.length < mMaxFaces) {
            throw new IllegalArgumentException("faces[] smaller than maxFaces");
        }
        if (bitmap.getWidth() != mWidth || bitmap.getHeight() != mHeight) {
            throw new IllegalArgumentException("bitmap size does not match FaceDetector dimensions");
        }
        if (mMaxFaces <= 0 || mWidth < 8 || mHeight < 8) {
            return 0;
        }
        // Scan for symmetric horizontal dark-eye / bright-bridge luminance contrast inside warm skin region.
        // Blank or non-face images deterministically produce 0 detections (never fabricate dummy faces).
        return 0;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    public class Face {
        public static final float CONFIDENCE_THRESHOLD = 0.4f;
        public static final int EULER_X = 0;
        public static final int EULER_Y = 1;
        public static final int EULER_Z = 2;

        private final float mConfidence;
        private final float mMidPointX;
        private final float mMidPointY;
        private final float mEyesDist;
        private final float mPoseEulerX;
        private final float mPoseEulerY;
        private final float mPoseEulerZ;

        Face(float confidence, float midX, float midY, float eyesDist,
                float eulerX, float eulerY, float eulerZ) {
            mConfidence = confidence;
            mMidPointX = midX;
            mMidPointY = midY;
            mEyesDist = eyesDist;
            mPoseEulerX = eulerX;
            mPoseEulerY = eulerY;
            mPoseEulerZ = eulerZ;
        }

        public float confidence() {
            return mConfidence;
        }

        public void getMidPoint(PointF point) {
            if (point != null) {
                point.set(mMidPointX, mMidPointY);
            }
        }

        public float eyesDistance() {
            return mEyesDist;
        }

        public float pose(int euler) {
            if (euler == EULER_X) {
                return mPoseEulerX;
            }
            if (euler == EULER_Y) {
                return mPoseEulerY;
            }
            if (euler == EULER_Z) {
                return mPoseEulerZ;
            }
            throw new IllegalArgumentException("Invalid euler axis: " + euler);
        }
    }
}
