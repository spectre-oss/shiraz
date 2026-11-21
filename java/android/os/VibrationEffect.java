// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Describes a haptic effect (one-shot, waveform, predefined, or primitive composition). */
public abstract class VibrationEffect implements Parcelable {
    public static final int DEFAULT_AMPLITUDE = -1;
    public static final int EFFECT_CLICK = 0;
    public static final int EFFECT_DOUBLE_CLICK = 1;
    public static final int EFFECT_TICK = 2;
    public static final int EFFECT_HEAVY_CLICK = 5;

    private static final int PARCEL_TOKEN_ONE_SHOT = 1;
    private static final int PARCEL_TOKEN_WAVEFORM = 2;
    private static final int PARCEL_TOKEN_PREDEFINED = 3;
    private static final int PARCEL_TOKEN_COMPOSITION = 4;

    public static final Parcelable.Creator<VibrationEffect> CREATOR =
            new Parcelable.Creator<VibrationEffect>() {
                @Override
                public VibrationEffect createFromParcel(Parcel in) {
                    int token = in.readInt();
                    switch (token) {
                        case PARCEL_TOKEN_ONE_SHOT:
                            return new OneShot(in.readLong(), in.readInt());
                        case PARCEL_TOKEN_WAVEFORM:
                            return new Waveform(
                                    in.createLongArray(), in.createIntArray(), in.readInt());
                        case PARCEL_TOKEN_PREDEFINED:
                            return new Predefined(in.readInt());
                        case PARCEL_TOKEN_COMPOSITION: {
                            int count = in.readInt();
                            int[] ids = in.createIntArray();
                            float[] scales = in.createFloatArray();
                            int[] delays = in.createIntArray();
                            return new Composed(count, ids, scales, delays);
                        }
                        default:
                            throw new ParcelFormatException(
                                    "Unexpected VibrationEffect token: " + token);
                    }
                }

                @Override
                public VibrationEffect[] newArray(int size) {
                    return new VibrationEffect[size];
                }
            };

    VibrationEffect() {
    }

    public static VibrationEffect createOneShot(long milliseconds, int amplitude) {
        if (milliseconds <= 0) {
            throw new IllegalArgumentException(
                    "milliseconds must be positive (" + milliseconds + ")");
        }
        if (amplitude < -1 || amplitude == 0 || amplitude > 255) {
            throw new IllegalArgumentException(
                    "amplitude must either be DEFAULT_AMPLITUDE, or between 1 and 255 inclusive (amplitude="
                            + amplitude + ")");
        }
        return new OneShot(milliseconds, amplitude);
    }

    public static VibrationEffect createWaveform(long[] timings, int repeat) {
        if (timings == null) {
            throw new NullPointerException("timings");
        }
        int[] amplitudes = new int[timings.length];
        for (int i = 0; i < timings.length / 2; i++) {
            amplitudes[i * 2 + 1] = DEFAULT_AMPLITUDE;
        }
        return createWaveform(timings, amplitudes, repeat);
    }

    public static VibrationEffect createWaveform(long[] timings, int[] amplitudes, int repeat) {
        if (timings == null || amplitudes == null) {
            throw new NullPointerException("timings and amplitudes must not be null");
        }
        if (timings.length != amplitudes.length) {
            throw new IllegalArgumentException(
                    "timing and amplitude arrays must be of equal length");
        }
        if (timings.length == 0) {
            throw new IllegalArgumentException("timings array must not be empty");
        }
        boolean hasNonZero = false;
        for (int i = 0; i < timings.length; i++) {
            if (timings[i] < 0) {
                throw new IllegalArgumentException("timings must all be >= 0");
            }
            if (timings[i] > 0) {
                hasNonZero = true;
            }
            int amp = amplitudes[i];
            if (amp < -1 || amp > 255) {
                throw new IllegalArgumentException(
                        "amplitudes must all be DEFAULT_AMPLITUDE or between 0 and 255");
            }
        }
        if (!hasNonZero) {
            throw new IllegalArgumentException("timings must have at least one non-zero element");
        }
        if (repeat < -1 || repeat >= timings.length) {
            throw new IllegalArgumentException(
                    "repeat index must be >= -1 and < timings.length (repeat=" + repeat + ")");
        }
        return new Waveform(timings.clone(), amplitudes.clone(), repeat);
    }

    public static VibrationEffect createPredefined(int effectId) {
        switch (effectId) {
            case EFFECT_CLICK:
            case EFFECT_DOUBLE_CLICK:
            case EFFECT_TICK:
            case EFFECT_HEAVY_CLICK:
                return new Predefined(effectId);
            default:
                throw new IllegalArgumentException("Unknown predefined effect id: " + effectId);
        }
    }

    public static Composition startComposition() {
        return new Composition();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final class Composition {
        public static final int PRIMITIVE_CLICK = 1;
        public static final int PRIMITIVE_THUD = 2;
        public static final int PRIMITIVE_SPIN = 3;
        public static final int PRIMITIVE_QUICK_RISE = 4;
        public static final int PRIMITIVE_SLOW_RISE = 5;
        public static final int PRIMITIVE_QUICK_FALL = 6;
        public static final int PRIMITIVE_TICK = 7;
        public static final int PRIMITIVE_LOW_TICK = 8;

        private final List<Integer> primitiveIds = new ArrayList<Integer>();
        private final List<Float> scales = new ArrayList<Float>();
        private final List<Integer> delays = new ArrayList<Integer>();

        Composition() {
        }

        public Composition addPrimitive(int primitiveId) {
            return addPrimitive(primitiveId, 1.0f, 0);
        }

        public Composition addPrimitive(int primitiveId, float scale) {
            return addPrimitive(primitiveId, scale, 0);
        }

        public Composition addPrimitive(int primitiveId, float scale, int delay) {
            if (primitiveId < PRIMITIVE_CLICK || primitiveId > PRIMITIVE_LOW_TICK) {
                throw new IllegalArgumentException("Unknown primitiveId: " + primitiveId);
            }
            if (scale < 0.0f || scale > 1.0f || Float.isNaN(scale)) {
                throw new IllegalArgumentException("scale must be between 0 and 1: " + scale);
            }
            if (delay < 0) {
                throw new IllegalArgumentException("delay must be >= 0: " + delay);
            }
            primitiveIds.add(Integer.valueOf(primitiveId));
            scales.add(Float.valueOf(scale));
            delays.add(Integer.valueOf(delay));
            return this;
        }

        public VibrationEffect compose() {
            if (primitiveIds.isEmpty()) {
                throw new IllegalStateException("Composition must have at least one element");
            }
            int n = primitiveIds.size();
            int[] ids = new int[n];
            float[] sc = new float[n];
            int[] dl = new int[n];
            for (int i = 0; i < n; i++) {
                ids[i] = primitiveIds.get(i).intValue();
                sc[i] = scales.get(i).floatValue();
                dl[i] = delays.get(i).intValue();
            }
            return new Composed(n, ids, sc, dl);
        }
    }

    private static final class OneShot extends VibrationEffect {
        private final long duration;
        private final int amplitude;

        OneShot(long duration, int amplitude) {
            this.duration = duration;
            this.amplitude = amplitude;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_ONE_SHOT);
            out.writeLong(duration);
            out.writeInt(amplitude);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof OneShot)) {
                return false;
            }
            OneShot other = (OneShot) o;
            return duration == other.duration && amplitude == other.amplitude;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Long.valueOf(duration), Integer.valueOf(amplitude));
        }
    }

    private static final class Waveform extends VibrationEffect {
        private final long[] timings;
        private final int[] amplitudes;
        private final int repeat;

        Waveform(long[] timings, int[] amplitudes, int repeat) {
            this.timings = timings;
            this.amplitudes = amplitudes;
            this.repeat = repeat;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_WAVEFORM);
            out.writeLongArray(timings);
            out.writeIntArray(amplitudes);
            out.writeInt(repeat);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Waveform)) {
                return false;
            }
            Waveform other = (Waveform) o;
            return repeat == other.repeat
                    && Arrays.equals(timings, other.timings)
                    && Arrays.equals(amplitudes, other.amplitudes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Integer.valueOf(Arrays.hashCode(timings)),
                    Integer.valueOf(Arrays.hashCode(amplitudes)),
                    Integer.valueOf(repeat));
        }
    }

    private static final class Predefined extends VibrationEffect {
        private final int effectId;

        Predefined(int effectId) {
            this.effectId = effectId;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_PREDEFINED);
            out.writeInt(effectId);
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Predefined) && effectId == ((Predefined) o).effectId;
        }

        @Override
        public int hashCode() {
            return effectId;
        }
    }

    private static final class Composed extends VibrationEffect {
        private final int count;
        private final int[] ids;
        private final float[] scales;
        private final int[] delays;

        Composed(int count, int[] ids, float[] scales, int[] delays) {
            this.count = count;
            this.ids = ids;
            this.scales = scales;
            this.delays = delays;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_COMPOSITION);
            out.writeInt(count);
            out.writeIntArray(ids);
            out.writeFloatArray(scales);
            out.writeIntArray(delays);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Composed)) {
                return false;
            }
            Composed other = (Composed) o;
            return count == other.count
                    && Arrays.equals(ids, other.ids)
                    && Arrays.equals(scales, other.scales)
                    && Arrays.equals(delays, other.delays);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Integer.valueOf(Arrays.hashCode(ids)),
                    Integer.valueOf(Arrays.hashCode(scales)),
                    Integer.valueOf(Arrays.hashCode(delays)));
        }
    }
}
