// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

public final class VolumeShaper implements AutoCloseable {
    private static final int MAX_CURVE_POINTS = 16;

    interface VolumeSink {
        void onShaperVolumeChanged(float volume);
    }

    private Configuration mConfiguration;
    private final VolumeSink mSink;
    private boolean mClosed;
    private boolean mStarted;
    private boolean mReversed;
    private long mAnchorNanoTime;
    private float mCurrentVolume = 1.0f;

    VolumeShaper(Configuration configuration, VolumeSink sink) {
        mConfiguration = Objects.requireNonNull(configuration, "configuration");
        mSink = sink;
        float[] vols = mConfiguration.getVolumes();
        if (vols.length > 0) {
            mCurrentVolume = vols[0];
        }
    }

    public void apply(Operation operation) {
        Objects.requireNonNull(operation, "operation");
        if (mClosed) {
            throw new IllegalStateException("VolumeShaper is closed");
        }
        mReversed = (operation.mFlags & Operation.FLAG_REVERSE) != 0;
        mAnchorNanoTime = System.nanoTime();
        mStarted = true;
        float v = evaluateVolume();
        mCurrentVolume = v;
        if (mSink != null) {
            mSink.onShaperVolumeChanged(v);
        }
    }

    public void replace(Configuration configuration, Operation operation, boolean join) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(operation, "operation");
        if (mClosed) {
            throw new IllegalStateException("VolumeShaper is closed");
        }
        float current = getVolume();
        if (join) {
            mConfiguration = new Configuration.Builder(configuration)
                    .scaleToStartVolume(current)
                    .build();
        } else {
            mConfiguration = configuration;
        }
        apply(operation);
    }

    public float getVolume() {
        if (mClosed) {
            throw new IllegalStateException("VolumeShaper is closed");
        }
        if (!mStarted) {
            float[] vols = mConfiguration.getVolumes();
            return vols.length > 0 ? vols[0] : 1.0f;
        }
        mCurrentVolume = evaluateVolume();
        return mCurrentVolume;
    }

    private float evaluateVolume() {
        long durationMs = mConfiguration.getDuration();
        float[] times = mConfiguration.getTimes();
        float[] volumes = mConfiguration.getVolumes();
        if (times.length == 0) return 1.0f;
        if (durationMs <= 0) {
            return mReversed ? volumes[0] : volumes[volumes.length - 1];
        }
        long elapsedNs = Math.max(0L, System.nanoTime() - mAnchorNanoTime);
        float progress = Math.min(1.0f, (float) elapsedNs / (durationMs * 1_000_000.0f));
        float t = mReversed ? (1.0f - progress) : progress;
        if (t <= times[0]) return volumes[0];
        if (t >= times[times.length - 1]) return volumes[volumes.length - 1];
        int idx = 0;
        while (idx + 1 < times.length && times[idx + 1] < t) {
            idx++;
        }
        float t0 = times[idx];
        float t1 = times[idx + 1];
        float v0 = volumes[idx];
        float v1 = volumes[idx + 1];
        if (t1 <= t0) return v1;
        float u = (t - t0) / (t1 - t0);
        int interp = mConfiguration.getInterpolatorType();
        if (interp == Configuration.INTERPOLATOR_TYPE_STEP) {
            return u < 1.0f ? v0 : v1;
        } else if (interp == Configuration.INTERPOLATOR_TYPE_CUBIC
                || interp == Configuration.INTERPOLATOR_TYPE_CUBIC_MONOTONIC) {
            float smooth = u * u * (3.0f - 2.0f * u);
            return clamp01(v0 + (v1 - v0) * smooth);
        }
        return clamp01(v0 + (v1 - v0) * u);
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    @Override
    public void close() {
        mClosed = true;
    }

    @Override
    protected void finalize() {
        close();
    }

    public static final class Configuration implements Parcelable {
        public static final int INTERPOLATOR_TYPE_STEP = 0;
        public static final int INTERPOLATOR_TYPE_LINEAR = 1;
        public static final int INTERPOLATOR_TYPE_CUBIC = 2;
        public static final int INTERPOLATOR_TYPE_CUBIC_MONOTONIC = 3;

        public static final Configuration LINEAR_RAMP = new Builder()
                .setInterpolatorType(INTERPOLATOR_TYPE_LINEAR)
                .setCurve(new float[]{0.f, 1.f}, new float[]{0.f, 1.f})
                .setDuration(1000)
                .build();

        public static final Configuration CUBIC_RAMP = new Builder()
                .setInterpolatorType(INTERPOLATOR_TYPE_CUBIC)
                .setCurve(new float[]{0.f, 1.f}, new float[]{0.f, 1.f})
                .setDuration(1000)
                .build();

        public static final Configuration SINE_RAMP;
        public static final Configuration SCURVE_RAMP;

        static {
            float[] times = new float[MAX_CURVE_POINTS];
            float[] sine = new float[MAX_CURVE_POINTS];
            float[] scurve = new float[MAX_CURVE_POINTS];
            for (int i = 0; i < MAX_CURVE_POINTS; i++) {
                float x = (float) i / (float) (MAX_CURVE_POINTS - 1);
                times[i] = x;
                sine[i] = (float) Math.sin(x * Math.PI * 0.5);
                scurve[i] = (float) (0.5 * (1.0 - Math.cos(x * Math.PI)));
            }
            times[0] = 0.0f;
            times[MAX_CURVE_POINTS - 1] = 1.0f;
            sine[0] = 0.0f;
            sine[MAX_CURVE_POINTS - 1] = 1.0f;
            scurve[0] = 0.0f;
            scurve[MAX_CURVE_POINTS - 1] = 1.0f;

            SINE_RAMP = new Builder()
                    .setInterpolatorType(INTERPOLATOR_TYPE_CUBIC_MONOTONIC)
                    .setCurve(times, sine)
                    .setDuration(1000)
                    .build();
            SCURVE_RAMP = new Builder()
                    .setInterpolatorType(INTERPOLATOR_TYPE_CUBIC_MONOTONIC)
                    .setCurve(times, scurve)
                    .setDuration(1000)
                    .build();
        }

        private final int mInterpolatorType;
        private final double mDurationMs;
        private final float[] mTimes;
        private final float[] mVolumes;

        Configuration(int interpolatorType, double durationMs, float[] times, float[] volumes) {
            mInterpolatorType = interpolatorType;
            mDurationMs = durationMs;
            mTimes = times.clone();
            mVolumes = volumes.clone();
        }

        private Configuration(Parcel in) {
            mInterpolatorType = in.readInt();
            mDurationMs = in.readDouble();
            float[] t = in.createFloatArray();
            float[] v = in.createFloatArray();
            mTimes = t != null ? t : new float[]{0.f, 1.f};
            mVolumes = v != null ? v : new float[]{0.f, 1.f};
        }

        public static int getMaximumCurvePoints() {
            return MAX_CURVE_POINTS;
        }

        public int getInterpolatorType() {
            return mInterpolatorType;
        }

        public long getDuration() {
            return (long) mDurationMs;
        }

        public float[] getTimes() {
            return mTimes.clone();
        }

        public float[] getVolumes() {
            return mVolumes.clone();
        }

        @Override
        public String toString() {
            return "VolumeShaper.Configuration{interpolatorType=" + mInterpolatorType
                    + ", durationMs=" + mDurationMs
                    + ", times=" + Arrays.toString(mTimes)
                    + ", volumes=" + Arrays.toString(mVolumes) + "}";
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mInterpolatorType,
                    Double.valueOf(mDurationMs),
                    Integer.valueOf(Arrays.hashCode(mTimes)),
                    Integer.valueOf(Arrays.hashCode(mVolumes)));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Configuration)) return false;
            Configuration other = (Configuration) o;
            return mInterpolatorType == other.mInterpolatorType
                    && Double.compare(mDurationMs, other.mDurationMs) == 0
                    && Arrays.equals(mTimes, other.mTimes)
                    && Arrays.equals(mVolumes, other.mVolumes);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mInterpolatorType);
            dest.writeDouble(mDurationMs);
            dest.writeFloatArray(mTimes);
            dest.writeFloatArray(mVolumes);
        }

        public static final Parcelable.Creator<Configuration> CREATOR =
                new Parcelable.Creator<Configuration>() {
                    @Override
                    public Configuration createFromParcel(Parcel in) {
                        return new Configuration(in);
                    }

                    @Override
                    public Configuration[] newArray(int size) {
                        return new Configuration[size];
                    }
                };

        private static void checkCurveForErrors(float[] times, float[] volumes, boolean log) {
            if (times == null) throw new NullPointerException("times array must be non-null");
            if (volumes == null) throw new NullPointerException("volumes array must be non-null");
            if (times.length != volumes.length) {
                throw new IllegalArgumentException("array length must match");
            }
            if (times.length < 2) {
                throw new IllegalArgumentException("curve length must be >= 2");
            }
            if (times.length > MAX_CURVE_POINTS) {
                throw new IllegalArgumentException("curve length must be <= " + MAX_CURVE_POINTS);
            }
            if (times[0] != 0.0f) {
                throw new IllegalArgumentException("times must start at 0.f");
            }
            if (times[times.length - 1] != 1.0f) {
                throw new IllegalArgumentException("times must end at 1.f");
            }
            for (int i = 1; i < times.length; i++) {
                if (!(times[i] > times[i - 1])) {
                    throw new IllegalArgumentException("times not monotonic increasing at " + i);
                }
            }
            for (int i = 0; i < volumes.length; i++) {
                if (Float.isNaN(volumes[i])) {
                    throw new IllegalArgumentException("volumes cannot be NaN");
                }
                if (log) {
                    if (volumes[i] > 0.0f) {
                        throw new IllegalArgumentException("log volumes must be <= 0.f");
                    }
                } else {
                    if (volumes[i] < 0.0f || volumes[i] > 1.0f) {
                        throw new IllegalArgumentException("volumes must be in [0.f, 1.f]");
                    }
                }
            }
        }

        public static final class Builder {
            private int mInterpolatorType = INTERPOLATOR_TYPE_CUBIC;
            private double mDurationMs = 1000.0;
            private float[] mTimes;
            private float[] mVolumes;

            public Builder() {}

            public Builder(Configuration configuration) {
                Objects.requireNonNull(configuration, "configuration");
                mInterpolatorType = configuration.mInterpolatorType;
                mDurationMs = configuration.mDurationMs;
                mTimes = configuration.mTimes.clone();
                mVolumes = configuration.mVolumes.clone();
            }

            public Builder setInterpolatorType(int interpolatorType) {
                if (interpolatorType < INTERPOLATOR_TYPE_STEP
                        || interpolatorType > INTERPOLATOR_TYPE_CUBIC_MONOTONIC) {
                    throw new IllegalArgumentException("invalid interpolatorType: " + interpolatorType);
                }
                mInterpolatorType = interpolatorType;
                return this;
            }

            public Builder setDuration(long durationMillis) {
                if (durationMillis <= 0) {
                    throw new IllegalArgumentException("durationMillis must be > 0: " + durationMillis);
                }
                mDurationMs = durationMillis;
                return this;
            }

            public Builder setCurve(float[] times, float[] volumes) {
                checkCurveForErrors(times, volumes, false);
                mTimes = times.clone();
                mVolumes = volumes.clone();
                return this;
            }

            public Builder reflectTimes() {
                ensureCurveSet();
                int n = mTimes.length;
                for (int i = 0; i < n / 2; i++) {
                    float tmpT = mTimes[i];
                    mTimes[i] = 1.0f - mTimes[n - 1 - i];
                    mTimes[n - 1 - i] = 1.0f - tmpT;
                    float tmpV = mVolumes[i];
                    mVolumes[i] = mVolumes[n - 1 - i];
                    mVolumes[n - 1 - i] = tmpV;
                }
                if ((n & 1) != 0) {
                    mTimes[n / 2] = 1.0f - mTimes[n / 2];
                }
                return this;
            }

            public Builder invertVolumes() {
                ensureCurveSet();
                float min = mVolumes[0];
                float max = mVolumes[0];
                for (int i = 1; i < mVolumes.length; i++) {
                    if (mVolumes[i] < min) min = mVolumes[i];
                    if (mVolumes[i] > max) max = mVolumes[i];
                }
                float span = max + min;
                for (int i = 0; i < mVolumes.length; i++) {
                    mVolumes[i] = clamp01(span - mVolumes[i]);
                }
                return this;
            }

            public Builder scaleToEndVolume(float volume) {
                checkVolumeArg(volume);
                ensureCurveSet();
                float startVol = mVolumes[0];
                float endVol = mVolumes[mVolumes.length - 1];
                if (endVol == startVol) {
                    float offset = volume - startVol;
                    for (int i = 0; i < mVolumes.length; i++) {
                        mVolumes[i] = clamp01(mVolumes[i] + offset * mTimes[i]);
                    }
                } else {
                    float scale = (volume - startVol) / (endVol - startVol);
                    for (int i = 0; i < mVolumes.length; i++) {
                        mVolumes[i] = clamp01(scale * (mVolumes[i] - startVol) + startVol);
                    }
                }
                return this;
            }

            public Builder scaleToStartVolume(float volume) {
                checkVolumeArg(volume);
                ensureCurveSet();
                float startVol = mVolumes[0];
                float endVol = mVolumes[mVolumes.length - 1];
                if (endVol == startVol) {
                    float offset = volume - endVol;
                    for (int i = 0; i < mVolumes.length; i++) {
                        mVolumes[i] = clamp01(mVolumes[i] + offset * (1.0f - mTimes[i]));
                    }
                } else {
                    float scale = (volume - endVol) / (startVol - endVol);
                    for (int i = 0; i < mVolumes.length; i++) {
                        mVolumes[i] = clamp01(scale * (mVolumes[i] - endVol) + endVol);
                    }
                }
                return this;
            }

            public Configuration build() {
                ensureCurveSet();
                return new Configuration(mInterpolatorType, mDurationMs, mTimes, mVolumes);
            }

            private void ensureCurveSet() {
                if (mTimes == null || mVolumes == null) {
                    throw new IllegalStateException("curve is not set");
                }
            }

            private static void checkVolumeArg(float volume) {
                if (Float.isNaN(volume) || volume < 0.0f || volume > 1.0f) {
                    throw new IllegalArgumentException("volume must be in [0.f, 1.f]: " + volume);
                }
            }
        }
    }

    public static final class Operation implements Parcelable {
        static final int FLAG_NONE = 0;
        static final int FLAG_REVERSE = 1;

        public static final Operation PLAY = new Operation(FLAG_NONE);
        public static final Operation REVERSE = new Operation(FLAG_REVERSE);

        final int mFlags;

        Operation(int flags) {
            mFlags = flags;
        }

        private Operation(Parcel in) {
            mFlags = in.readInt();
        }

        @Override
        public String toString() {
            return "VolumeShaper.Operation{flags=0x" + Integer.toHexString(mFlags) + "}";
        }

        @Override
        public int hashCode() {
            return mFlags;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Operation)) return false;
            return mFlags == ((Operation) o).mFlags;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mFlags);
        }

        public static final Parcelable.Creator<Operation> CREATOR =
                new Parcelable.Creator<Operation>() {
                    @Override
                    public Operation createFromParcel(Parcel in) {
                        return new Operation(in);
                    }

                    @Override
                    public Operation[] newArray(int size) {
                        return new Operation[size];
                    }
                };
    }
}
