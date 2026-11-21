// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.datatypes;

import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Length;
import android.health.connect.datatypes.units.Mass;
import android.health.connect.datatypes.units.Power;
import android.health.connect.datatypes.units.Pressure;
import android.health.connect.datatypes.units.TemperatureDelta;
import android.health.connect.datatypes.units.Velocity;
import android.health.connect.datatypes.units.Volume;

public final class AggregationType<T> {
    static final int OP_SUM = 1;
    static final int OP_MIN = 2;
    static final int OP_MAX = 3;
    static final int OP_AVG = 4;
    static final int OP_COUNT = 5;

    static final int KIND_LONG = 1;
    static final int KIND_DOUBLE = 2;
    static final int KIND_ENERGY = 3;
    static final int KIND_LENGTH = 4;
    static final int KIND_MASS = 5;
    static final int KIND_POWER = 6;
    static final int KIND_PRESSURE = 7;
    static final int KIND_TEMP_DELTA = 8;
    static final int KIND_VELOCITY = 9;
    static final int KIND_VOLUME = 10;

    private final Class<? extends Record> mRecordClass;
    private final String mMetric;
    private final int mOp;
    private final int mKind;

    AggregationType(Class<? extends Record> recordClass, String metric, int op, int kind) {
        mRecordClass = recordClass;
        mMetric = metric;
        mOp = op;
        mKind = kind;
    }

    public Class<? extends Record> shirazGetRecordClass() {
        return mRecordClass;
    }

    public String shirazGetMetric() {
        return mMetric;
    }

    public static Object shirazCreateAccumulator() {
        return new Accumulator();
    }

    public void shirazFeed(Record record, Object accumulator) {
        if (mRecordClass.isInstance(record) && accumulator instanceof Accumulator) {
            record.shirazCollectSamples(mMetric, (Accumulator) accumulator);
        }
    }

    public T shirazFinish(Object accumulator) {
        if (accumulator instanceof Accumulator) {
            return shirazCompute((Accumulator) accumulator);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public T shirazCompute(Accumulator acc) {
        if (acc.count == 0) {
            return null;
        }
        double val;
        switch (mOp) {
            case OP_SUM:
                val = acc.sum;
                break;
            case OP_MIN:
                val = acc.min;
                break;
            case OP_MAX:
                val = acc.max;
                break;
            case OP_AVG:
                val = acc.sum / acc.count;
                break;
            case OP_COUNT:
                val = acc.count;
                break;
            default:
                return null;
        }
        switch (mKind) {
            case KIND_LONG:
                if (mOp == OP_SUM) return (T) Long.valueOf(acc.longSum);
                if (mOp == OP_MIN) return (T) Long.valueOf(acc.longMin);
                if (mOp == OP_MAX) return (T) Long.valueOf(acc.longMax);
                if (mOp == OP_COUNT) return (T) Long.valueOf(acc.count);
                return (T) Long.valueOf(Math.round(val));
            case KIND_DOUBLE:
                return (T) Double.valueOf(val);
            case KIND_ENERGY:
                return (T) Energy.fromCalories(val);
            case KIND_LENGTH:
                return (T) Length.fromMeters(val);
            case KIND_MASS:
                return (T) Mass.fromGrams(val);
            case KIND_POWER:
                return (T) Power.fromWatts(val);
            case KIND_PRESSURE:
                return (T) Pressure.fromMillimetersOfMercury(val);
            case KIND_TEMP_DELTA:
                return (T) TemperatureDelta.fromCelsius(val);
            case KIND_VELOCITY:
                return (T) Velocity.fromMetersPerSecond(val);
            case KIND_VOLUME:
                return (T) Volume.fromLiters(val);
            default:
                return null;
        }
    }

    static final class Accumulator {
        int count;
        double sum;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        long longSum;
        long longMin = Long.MAX_VALUE;
        long longMax = Long.MIN_VALUE;

        void addDouble(double v) {
            count++;
            sum += v;
            if (v < min) min = v;
            if (v > max) max = v;
        }

        void addLong(long v) {
            count++;
            longSum += v;
            sum += v;
            if (v < longMin) longMin = v;
            if (v > longMax) longMax = v;
            if (v < min) min = v;
            if (v > max) max = v;
        }
    }
}
