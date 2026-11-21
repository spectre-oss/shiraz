// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import android.os.SystemClock;

public class Interpolator {
    public enum Result {
        NORMAL,
        FREEZE_START,
        FREEZE_END
    }

    private int mValueCount;
    private int mFrameCount;
    private int[] mTimes;
    private boolean[] mDefined;
    private float[][] mValues;
    private float[][] mBlend;
    private float mRepeatCount = 1.0f;
    private boolean mMirror;

    public Interpolator(int valueCount) {
        this(valueCount, 2);
    }

    public Interpolator(int valueCount, int frameCount) {
        reset(valueCount, frameCount);
    }

    public void reset(int valueCount) {
        reset(valueCount, 2);
    }

    public void reset(int valueCount, int frameCount) {
        if (valueCount < 1 || frameCount < 2) {
            throw new IllegalArgumentException("Invalid valueCount or frameCount");
        }
        mValueCount = valueCount;
        mFrameCount = frameCount;
        mTimes = new int[frameCount];
        mDefined = new boolean[frameCount];
        mValues = new float[frameCount][valueCount];
        mBlend = new float[frameCount][];
        mRepeatCount = 1.0f;
        mMirror = false;
    }

    public final int getKeyFrameCount() {
        return mFrameCount;
    }

    public final int getValueCount() {
        return mValueCount;
    }

    public void setKeyFrame(int index, int msec, float[] values) {
        setKeyFrame(index, msec, values, null);
    }

    public void setKeyFrame(int index, int msec, float[] values, float[] blend) {
        if (index < 0 || index >= mFrameCount) {
            throw new IndexOutOfBoundsException("index=" + index + ", frameCount=" + mFrameCount);
        }
        if (values == null || values.length < mValueCount) {
            throw new ArrayStoreException("values length < valueCount");
        }
        if (blend != null && blend.length < 4) {
            throw new ArrayStoreException("blend length < 4");
        }
        mTimes[index] = msec;
        mDefined[index] = true;
        System.arraycopy(values, 0, mValues[index], 0, mValueCount);
        if (blend != null) {
            float[] b = new float[4];
            System.arraycopy(blend, 0, b, 0, 4);
            mBlend[index] = b;
        } else {
            mBlend[index] = null;
        }
    }

    public void setRepeatMirror(float repeatCount, boolean mirror) {
        if (repeatCount >= 0.0f) {
            mRepeatCount = repeatCount;
            mMirror = mirror;
        }
    }

    public Result timeToValues(float[] values) {
        long now;
        try {
            now = SystemClock.uptimeMillis();
        } catch (Throwable ignored) {
            now = System.nanoTime() / 1_000_000L;
        }
        return timeToValues((int) now, values);
    }

    public Result timeToValues(int msec, float[] values) {
        if (values != null && values.length < mValueCount) {
            throw new ArrayStoreException("values array too small");
        }
        int first = -1;
        int last = -1;
        for (int i = 0; i < mFrameCount; i++) {
            if (mDefined[i]) {
                if (first < 0) first = i;
                last = i;
            }
        }
        if (first < 0 || first == last) {
            if (first >= 0 && values != null) {
                System.arraycopy(mValues[first], 0, values, 0, mValueCount);
            }
            return Result.FREEZE_START;
        }

        int startTime = mTimes[first];
        int endTime = mTimes[last];
        int span = endTime - startTime;
        if (msec <= startTime || span <= 0) {
            if (values != null) {
                System.arraycopy(mValues[first], 0, values, 0, mValueCount);
            }
            return Result.FREEZE_START;
        }

        double elapsed = msec - startTime;
        double totalDuration = span * (double) mRepeatCount;
        if (mRepeatCount > 0.0f && elapsed >= totalDuration) {
            int endIdx = (mMirror && (((int) mRepeatCount) & 1) == 0) ? first : last;
            if (values != null) {
                System.arraycopy(mValues[endIdx], 0, values, 0, mValueCount);
            }
            return Result.FREEZE_END;
        }

        double cyclePos = elapsed / span;
        int cycleIndex = (int) Math.floor(cyclePos);
        double frac = cyclePos - cycleIndex;
        if (mMirror && (cycleIndex & 1) == 1) {
            frac = 1.0 - frac;
        }
        double mappedTime = startTime + frac * span;

        int prev = first;
        int next = last;
        for (int i = first + 1; i <= last; i++) {
            if (!mDefined[i]) continue;
            if (mappedTime <= mTimes[i]) {
                next = i;
                break;
            }
            prev = i;
        }

        if (values != null) {
            int segSpan = mTimes[next] - mTimes[prev];
            float t = segSpan <= 0 ? 0.0f : (float) ((mappedTime - mTimes[prev]) / segSpan);
            float[] blend = mBlend[prev];
            if (blend != null) {
                t = evalCubicBezierYForX(t, blend[0], blend[1], blend[2], blend[3]);
            }
            for (int j = 0; j < mValueCount; j++) {
                float v0 = mValues[prev][j];
                float v1 = mValues[next][j];
                values[j] = v0 + (v1 - v0) * t;
            }
        }
        return Result.NORMAL;
    }

    private static float evalCubicBezierYForX(float x, float x1, float y1, float x2, float y2) {
        if (x <= 0.0f) return 0.0f;
        if (x >= 1.0f) return 1.0f;
        float u = x;
        for (int iter = 0; iter < 8; iter++) {
            float inv = 1.0f - u;
            float bx = 3.0f * inv * inv * u * x1 + 3.0f * inv * u * u * x2 + u * u * u;
            float dx = 3.0f * inv * inv * x1 + 6.0f * inv * u * (x2 - x1) + 3.0f * u * u * (1.0f - x2);
            if (Math.abs(dx) < 1e-6f) break;
            u -= (bx - x) / dx;
            if (u < 0.0f) u = 0.0f;
            if (u > 1.0f) u = 1.0f;
        }
        float inv = 1.0f - u;
        return 3.0f * inv * inv * u * y1 + 3.0f * inv * u * u * y2 + u * u * u;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }
}
