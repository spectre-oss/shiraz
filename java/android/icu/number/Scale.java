// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

import java.math.BigDecimal;

/** Multiplier applied to numbers prior to formatting in NumberFormatter. */
public class Scale {
    private static final Scale NONE = new Scale(0, null);

    final int mMagnitude;
    final BigDecimal mArbitrary;

    private Scale(int magnitude, BigDecimal arbitrary) {
        mMagnitude = magnitude;
        mArbitrary = arbitrary;
    }

    public static Scale none() {
        return NONE;
    }

    public static Scale powerOfTen(int power) {
        return power == 0 ? NONE : new Scale(power, null);
    }

    public static Scale byBigDecimal(BigDecimal multiplicand) {
        if (multiplicand == null) {
            throw new IllegalArgumentException("Multiplicand must not be null");
        }
        return new Scale(0, multiplicand);
    }

    public static Scale byDouble(double multiplicand) {
        return new Scale(0, BigDecimal.valueOf(multiplicand));
    }

    public static Scale byDoubleAndPowerOfTen(double multiplicand, int power) {
        return new Scale(power, BigDecimal.valueOf(multiplicand));
    }

    BigDecimal applyTo(BigDecimal input) {
        BigDecimal result = input;
        if (mArbitrary != null) {
            result = result.multiply(mArbitrary);
        }
        if (mMagnitude != 0) {
            result = result.scaleByPowerOfTen(mMagnitude);
        }
        return result;
    }
}
