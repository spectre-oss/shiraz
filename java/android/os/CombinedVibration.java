// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import android.util.SparseArray;
import java.util.Objects;

/** Describes a synchronized vibration across one or more vibrators. */
public abstract class CombinedVibration implements Parcelable {
    private static final int PARCEL_TOKEN_MONO = 1;
    private static final int PARCEL_TOKEN_STEREO = 2;

    public static final Parcelable.Creator<CombinedVibration> CREATOR =
            new Parcelable.Creator<CombinedVibration>() {
                @Override
                public CombinedVibration createFromParcel(Parcel in) {
                    int token = in.readInt();
                    if (token == PARCEL_TOKEN_MONO) {
                        return new Mono(VibrationEffect.CREATOR.createFromParcel(in));
                    }
                    if (token == PARCEL_TOKEN_STEREO) {
                        int count = in.readInt();
                        SparseArray<VibrationEffect> effects = new SparseArray<VibrationEffect>(count);
                        for (int i = 0; i < count; i++) {
                            int id = in.readInt();
                            effects.put(id, VibrationEffect.CREATOR.createFromParcel(in));
                        }
                        return new Stereo(effects);
                    }
                    throw new ParcelFormatException("Unexpected CombinedVibration token: " + token);
                }

                @Override
                public CombinedVibration[] newArray(int size) {
                    return new CombinedVibration[size];
                }
            };

    CombinedVibration() {
    }

    public static CombinedVibration createParallel(VibrationEffect effect) {
        Objects.requireNonNull(effect, "effect");
        return new Mono(effect);
    }

    public static ParallelCombination startParallel() {
        return new ParallelCombination();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final class ParallelCombination {
        private final SparseArray<VibrationEffect> effects = new SparseArray<VibrationEffect>();

        ParallelCombination() {
        }

        public ParallelCombination addVibrator(int vibratorId, VibrationEffect effect) {
            Objects.requireNonNull(effect, "effect");
            effects.put(vibratorId, effect);
            return this;
        }

        public CombinedVibration combine() {
            if (effects.size() == 0) {
                throw new IllegalStateException("Combination must have at least one element");
            }
            return new Stereo(effects);
        }
    }

    private static final class Mono extends CombinedVibration {
        private final VibrationEffect effect;

        Mono(VibrationEffect effect) {
            this.effect = effect;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_MONO);
            effect.writeToParcel(out, flags);
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof Mono) && Objects.equals(effect, ((Mono) o).effect);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(effect);
        }
    }

    private static final class Stereo extends CombinedVibration {
        private final SparseArray<VibrationEffect> effects;

        Stereo(SparseArray<VibrationEffect> effects) {
            this.effects = new SparseArray<VibrationEffect>(effects.size());
            for (int i = 0; i < effects.size(); i++) {
                this.effects.put(effects.keyAt(i), effects.valueAt(i));
            }
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(PARCEL_TOKEN_STEREO);
            out.writeInt(effects.size());
            for (int i = 0; i < effects.size(); i++) {
                out.writeInt(effects.keyAt(i));
                effects.valueAt(i).writeToParcel(out, flags);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Stereo)) {
                return false;
            }
            Stereo other = (Stereo) o;
            if (effects.size() != other.effects.size()) {
                return false;
            }
            for (int i = 0; i < effects.size(); i++) {
                if (effects.keyAt(i) != other.effects.keyAt(i)
                        || !Objects.equals(effects.valueAt(i), other.effects.valueAt(i))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = effects.size();
            for (int i = 0; i < effects.size(); i++) {
                hash = 31 * hash + effects.keyAt(i);
                hash = 31 * hash + Objects.hashCode(effects.valueAt(i));
            }
            return hash;
        }
    }
}
