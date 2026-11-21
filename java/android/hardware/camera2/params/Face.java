// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.camera2.params;

import android.graphics.Point;
import android.graphics.Rect;
import java.util.Objects;

public final class Face {
    public static final int ID_UNSUPPORTED = -1;
    public static final int SCORE_MIN = 1;
    public static final int SCORE_MAX = 100;

    private final Rect mBounds;
    private final int mScore;
    private final int mId;
    private final Point mLeftEye;
    private final Point mRightEye;
    private final Point mMouth;

    public Face(
            Rect bounds,
            int score,
            int id,
            Point leftEyePosition,
            Point rightEyePosition,
            Point mouthPosition) {
        checkNotNull("bounds", bounds);
        if (score < SCORE_MIN || score > SCORE_MAX) {
            throw new IllegalArgumentException("Confidence out of range");
        }
        if (id < 0 && id != ID_UNSUPPORTED) {
            throw new IllegalArgumentException("Id out of range");
        }
        if (id == ID_UNSUPPORTED) {
            checkNull("leftEyePosition", leftEyePosition);
            checkNull("rightEyePosition", rightEyePosition);
            checkNull("mouthPosition", mouthPosition);
        }
        if (leftEyePosition != null || rightEyePosition != null || mouthPosition != null) {
            checkNotNull("leftEyePosition", leftEyePosition);
            checkNotNull("rightEyePosition", rightEyePosition);
            checkNotNull("mouthPosition", mouthPosition);
        }
        mBounds = new Rect(bounds);
        mScore = score;
        mId = id;
        mLeftEye = leftEyePosition == null ? null : new Point(leftEyePosition);
        mRightEye = rightEyePosition == null ? null : new Point(rightEyePosition);
        mMouth = mouthPosition == null ? null : new Point(mouthPosition);
    }

    public Face(Rect bounds, int score) {
        this(bounds, score, ID_UNSUPPORTED, null, null, null);
    }

    private static void checkNotNull(String name, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(name + " was required, but it was null");
        }
    }

    private static void checkNull(String name, Object value) {
        if (value != null) {
            throw new IllegalArgumentException(name + " was required to be null, but it wasn't");
        }
    }

    public Rect getBounds() {
        return new Rect(mBounds);
    }

    public int getScore() {
        return mScore;
    }

    public int getId() {
        return mId;
    }

    public Point getLeftEyePosition() {
        return mLeftEye == null ? null : new Point(mLeftEye);
    }

    public Point getRightEyePosition() {
        return mRightEye == null ? null : new Point(mRightEye);
    }

    public Point getMouthPosition() {
        return mMouth == null ? null : new Point(mMouth);
    }

    @Override
    public String toString() {
        return String.format(
                "{ bounds: %s, score: %s, id: %d, leftEyePosition: %s, "
                        + "rightEyePosition: %s, mouthPosition: %s }",
                mBounds,
                Integer.valueOf(mScore),
                Integer.valueOf(mId),
                mLeftEye,
                mRightEye,
                mMouth);
    }

    public static final class Builder {
        private Rect mBounds;
        private int mScore;
        private int mId = ID_UNSUPPORTED;
        private Point mLeftEye;
        private Point mRightEye;
        private Point mMouth;
        private boolean mBoundsSet;
        private boolean mScoreSet;

        public Builder() {
        }

        public Builder(Face current) {
            Objects.requireNonNull(current, "current");
            mBounds = current.getBounds();
            mScore = current.getScore();
            mId = current.getId();
            mLeftEye = current.getLeftEyePosition();
            mRightEye = current.getRightEyePosition();
            mMouth = current.getMouthPosition();
            mBoundsSet = true;
            mScoreSet = true;
        }

        public Builder setBounds(Rect bounds) {
            checkNotNull("bounds", bounds);
            mBounds = new Rect(bounds);
            mBoundsSet = true;
            return this;
        }

        public Builder setScore(int score) {
            if (score < SCORE_MIN || score > SCORE_MAX) {
                throw new IllegalArgumentException("Confidence out of range");
            }
            mScore = score;
            mScoreSet = true;
            return this;
        }

        public Builder setId(int id) {
            if (id < 0 && id != ID_UNSUPPORTED) {
                throw new IllegalArgumentException("Id out of range");
            }
            mId = id;
            return this;
        }

        public Builder setLeftEyePosition(Point leftEyePosition) {
            checkNotNull("leftEyePosition", leftEyePosition);
            mLeftEye = new Point(leftEyePosition);
            return this;
        }

        public Builder setRightEyePosition(Point rightEyePosition) {
            checkNotNull("rightEyePosition", rightEyePosition);
            mRightEye = new Point(rightEyePosition);
            return this;
        }

        public Builder setMouthPosition(Point mouthPosition) {
            checkNotNull("mouthPosition", mouthPosition);
            mMouth = new Point(mouthPosition);
            return this;
        }

        public Face build() {
            if (!mBoundsSet || !mScoreSet) {
                throw new IllegalStateException("Bounds and score must be set before building");
            }
            return new Face(mBounds, mScore, mId, mLeftEye, mRightEye, mMouth);
        }
    }
}
