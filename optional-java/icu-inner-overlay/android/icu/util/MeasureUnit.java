// SPDX-License-Identifier: GPL-3.0-only
package android.icu.util;

/**
 * Overlay holder providing API 35 {@code MeasureUnit.Complexity} and {@code MeasureUnit.MeasurePrefix}.
 */
public class MeasureUnit {
    protected MeasureUnit() {
    }

    public enum Complexity {
        SINGLE,
        COMPOUND,
        MIXED
    }

    public enum MeasurePrefix {
        YOTTA(10, 24),
        ZETTA(10, 21),
        EXA(10, 18),
        PETA(10, 15),
        TERA(10, 12),
        GIGA(10, 9),
        MEGA(10, 6),
        KILO(10, 3),
        HECTO(10, 2),
        DEKA(10, 1),
        ONE(10, 0),
        DECI(10, -1),
        CENTI(10, -2),
        MILLI(10, -3),
        MICRO(10, -6),
        NANO(10, -9),
        PICO(10, -12),
        FEMTO(10, -15),
        ATTO(10, -18),
        ZEPTO(10, -21),
        YOCTO(10, -24),
        KIBI(1024, 1),
        MEBI(1024, 2),
        GIBI(1024, 3),
        TEBI(1024, 4),
        PEBI(1024, 5),
        EXBI(1024, 6),
        ZEBI(1024, 7),
        YOBI(1024, 8);

        private final int mBase;
        private final int mPower;

        MeasurePrefix(int base, int power) {
            mBase = base;
            mPower = power;
        }

        public int getBase() {
            return mBase;
        }

        public int getPower() {
            return mPower;
        }
    }
}
