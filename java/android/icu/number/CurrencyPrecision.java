// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

import android.icu.util.Currency;

/** Precision subclass for currency-based rounding strategies. */
public abstract class CurrencyPrecision extends Precision {
    CurrencyPrecision() {
    }

    public Precision withCurrency(Currency currency) {
        if (currency == null) {
            throw new IllegalArgumentException("Currency must not be null");
        }
        int digits = currency.getDefaultFractionDigits();
        PrecisionImpl p = new PrecisionImpl();
        p.mMinFrac = Math.max(0, digits);
        p.mMaxFrac = Math.max(0, digits);
        return p;
    }
}
