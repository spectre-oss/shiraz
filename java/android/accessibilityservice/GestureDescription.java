// SPDX-License-Identifier: GPL-3.0-only
package android.accessibilityservice;

import android.graphics.Path;
import android.graphics.PathMeasure;
import android.view.Display;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class GestureDescription {
    private static final int MAX_STROKE_COUNT = 20;
    private static final long MAX_GESTURE_DURATION_MS = 60L * 1000L;

    private final List<StrokeDescription> mStrokes;
    private final int mDisplayId;

    private GestureDescription(List<StrokeDescription> strokes, int displayId) {
        mStrokes = new ArrayList<StrokeDescription>(strokes);
        mDisplayId = displayId;
    }

    public static int getMaxStrokeCount() {
        return MAX_STROKE_COUNT;
    }

    public static long getMaxGestureDuration() {
        return MAX_GESTURE_DURATION_MS;
    }

    public int getStrokeCount() {
        return mStrokes.size();
    }

    public StrokeDescription getStroke(int index) {
        return mStrokes.get(index);
    }

    public int getDisplayId() {
        return mDisplayId;
    }

    public static class Builder {
        private final List<StrokeDescription> mStrokes = new ArrayList<StrokeDescription>();
        private int mDisplayId = Display.DEFAULT_DISPLAY;

        public Builder() {
        }

        public Builder addStroke(StrokeDescription strokeDescription) {
            Objects.requireNonNull(strokeDescription, "strokeDescription");
            if (mStrokes.size() >= MAX_STROKE_COUNT) {
                throw new IllegalStateException(
                        "Attempting to add too many strokes to a gesture. Maximum is "
                                + MAX_STROKE_COUNT);
            }
            mStrokes.add(strokeDescription);
            if (getTotalDuration(mStrokes) > MAX_GESTURE_DURATION_MS) {
                mStrokes.remove(mStrokes.size() - 1);
                throw new IllegalStateException(
                        "Gesture would exceed maximum duration with new stroke. Maximum duration is "
                                + MAX_GESTURE_DURATION_MS);
            }
            return this;
        }

        public Builder setDisplayId(int displayId) {
            mDisplayId = displayId;
            return this;
        }

        public GestureDescription build() {
            if (mStrokes.isEmpty()) {
                throw new IllegalStateException("Gestures must have at least one stroke");
            }
            return new GestureDescription(mStrokes, mDisplayId);
        }

        private static long getTotalDuration(List<StrokeDescription> strokes) {
            long latestEnd = Long.MIN_VALUE;
            for (int i = 0; i < strokes.size(); i++) {
                StrokeDescription s = strokes.get(i);
                latestEnd = Math.max(latestEnd, s.mEndTime);
            }
            return Math.max(latestEnd, 0L);
        }
    }

    public static class StrokeDescription {
        private static final int INVALID_STROKE_ID = -1;
        private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

        final Path mPath;
        final long mStartTime;
        final long mEndTime;
        final boolean mContinued;
        final int mId;
        final int mContinuedStrokeId;

        public StrokeDescription(Path path, long startTime, long duration) {
            this(path, startTime, duration, false);
        }

        public StrokeDescription(
                Path path, long startTime, long duration, boolean willContinue) {
            this(path, startTime, duration, willContinue, NEXT_ID.getAndIncrement(), INVALID_STROKE_ID);
        }

        private StrokeDescription(
                Path path,
                long startTime,
                long duration,
                boolean willContinue,
                int id,
                int continuedStrokeId) {
            if (duration <= 0) {
                throw new IllegalArgumentException("Duration must be positive");
            }
            if (startTime < 0) {
                throw new IllegalArgumentException("Start time must not be negative");
            }
            if (duration > MAX_GESTURE_DURATION_MS - startTime) {
                throw new IllegalArgumentException("Stroke exceeds max gesture duration");
            }
            validatePath(path);
            mPath = new Path(path);
            mStartTime = startTime;
            mEndTime = startTime + duration;
            mContinued = willContinue;
            mId = id;
            mContinuedStrokeId = continuedStrokeId;
        }

        private static void validatePath(Path path) {
            if (path == null) {
                throw new IllegalArgumentException("Path cannot be null");
            }
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Path cannot be empty");
            }
            double[] pts = path.shirazAnimationPoints();
            for (int i = 0; i < pts.length; i += 3) {
                if (pts[i + 1] < 0.0 || pts[i + 2] < 0.0) {
                    throw new IllegalArgumentException("Path bounds must not be negative");
                }
            }
            PathMeasure measure = new PathMeasure(path, false);
            if (measure.getLength() > 0f && measure.nextContour()) {
                throw new IllegalArgumentException("Path has more than one contour");
            }
        }

        public Path getPath() {
            return new Path(mPath);
        }

        public long getStartTime() {
            return mStartTime;
        }

        public long getDuration() {
            return mEndTime - mStartTime;
        }

        public StrokeDescription continueStroke(
                Path path, long startTime, long duration, boolean willContinue) {
            if (!mContinued) {
                throw new IllegalStateException(
                        "Only strokes marked willContinue can be continued");
            }
            return new StrokeDescription(
                    path, startTime, duration, willContinue, NEXT_ID.getAndIncrement(), mId);
        }

        public boolean willContinue() {
            return mContinued;
        }
    }
}
