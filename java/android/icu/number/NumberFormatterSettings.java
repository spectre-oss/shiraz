// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

import android.icu.text.DisplayOptions;
import android.icu.text.DecimalFormatSymbols;
import android.icu.text.NumberingSystem;
import android.icu.util.MeasureUnit;
import android.icu.util.ULocale;
import java.math.RoundingMode;
import java.util.Objects;

/** Immutable fluent configuration chain for NumberFormatter. */
public abstract class NumberFormatterSettings<T extends NumberFormatterSettings<?>> {
    final NumberFormatterSettings<?> mParent;
    final int mKey;
    final Object mValue;

    static final int KEY_LOCALE = 1;
    static final int KEY_NOTATION = 2;
    static final int KEY_UNIT = 3;
    static final int KEY_PER_UNIT = 4;
    static final int KEY_PRECISION = 5;
    static final int KEY_ROUNDING_MODE = 6;
    static final int KEY_GROUPING = 7;
    static final int KEY_INTEGER_WIDTH = 8;
    static final int KEY_SYMBOLS = 9;
    static final int KEY_UNIT_WIDTH = 10;
    static final int KEY_SIGN = 11;
    static final int KEY_DECIMAL = 12;
    static final int KEY_SCALE = 13;
    static final int KEY_USAGE = 14;
    static final int KEY_DISPLAY_OPTIONS = 15;

    NumberFormatterSettings(NumberFormatterSettings<?> parent, int key, Object value) {
        mParent = parent;
        mKey = key;
        mValue = value;
    }

    abstract T create(int key, Object value);

    public T notation(Notation notation) {
        return create(KEY_NOTATION, notation);
    }

    public T unit(MeasureUnit unit) {
        return create(KEY_UNIT, unit);
    }

    public T perUnit(MeasureUnit perUnit) {
        return create(KEY_PER_UNIT, perUnit);
    }

    public T precision(Precision precision) {
        return create(KEY_PRECISION, precision);
    }

    public T roundingMode(RoundingMode roundingMode) {
        return create(KEY_ROUNDING_MODE, roundingMode);
    }

    public T grouping(NumberFormatter.GroupingStrategy strategy) {
        return create(KEY_GROUPING, strategy);
    }

    public T integerWidth(IntegerWidth style) {
        return create(KEY_INTEGER_WIDTH, style);
    }

    public T symbols(DecimalFormatSymbols symbols) {
        return create(KEY_SYMBOLS, symbols != null ? symbols.clone() : null);
    }

    public T symbols(NumberingSystem ns) {
        return create(KEY_SYMBOLS, ns);
    }

    public T unitWidth(NumberFormatter.UnitWidth style) {
        return create(KEY_UNIT_WIDTH, style);
    }

    public T sign(NumberFormatter.SignDisplay style) {
        return create(KEY_SIGN, style);
    }

    public T decimal(NumberFormatter.DecimalSeparatorDisplay style) {
        return create(KEY_DECIMAL, style);
    }

    public T scale(Scale scale) {
        return create(KEY_SCALE, scale);
    }

    public T usage(String usage) {
        return create(KEY_USAGE, usage);
    }

    public T displayOptions(DisplayOptions displayOptions) {
        return create(KEY_DISPLAY_OPTIONS, displayOptions);
    }

    Object resolveSetting(int targetKey) {
        for (NumberFormatterSettings<?> cur = this; cur != null; cur = cur.mParent) {
            if (cur.mKey == targetKey) {
                return cur.mValue;
            }
        }
        return null;
    }

    ULocale resolveLocale() {
        Object loc = resolveSetting(KEY_LOCALE);
        return loc instanceof ULocale ? (ULocale) loc : ULocale.getDefault();
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (int k = 1; k <= 15; k++) {
            h = 31 * h + Objects.hashCode(resolveSetting(k));
        }
        return h;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NumberFormatterSettings)) return false;
        NumberFormatterSettings<?> that = (NumberFormatterSettings<?>) other;
        for (int k = 1; k <= 15; k++) {
            if (!Objects.equals(resolveSetting(k), that.resolveSetting(k))) {
                return false;
            }
        }
        return true;
    }
}
