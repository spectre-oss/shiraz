// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

import android.icu.util.ULocale;
import java.math.BigDecimal;

/** A NumberRangeFormatter bound to a specific locale. */
public class LocalizedNumberRangeFormatter
        extends NumberRangeFormatterSettings<LocalizedNumberRangeFormatter> {
    LocalizedNumberRangeFormatter(NumberRangeFormatterSettings<?> parent, int key, Object value) {
        super(parent, key, value);
    }

    @Override
    LocalizedNumberRangeFormatter create(int key, Object value) {
        return new LocalizedNumberRangeFormatter(this, key, value);
    }

    public FormattedNumberRange formatRange(int first, int second) {
        return formatRange(BigDecimal.valueOf(first), BigDecimal.valueOf(second));
    }

    public FormattedNumberRange formatRange(double first, double second) {
        return formatRange(BigDecimal.valueOf(first), BigDecimal.valueOf(second));
    }

    public FormattedNumberRange formatRange(Number first, Number second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("The numbers must not be null");
        }
        BigDecimal bd1 = first instanceof BigDecimal
                ? (BigDecimal) first
                : new BigDecimal(first.toString());
        BigDecimal bd2 = second instanceof BigDecimal
                ? (BigDecimal) second
                : new BigDecimal(second.toString());

        ULocale uloc = resolveLocale();
        UnlocalizedNumberFormatter both = (UnlocalizedNumberFormatter) resolveSetting(KEY_FORMATTER_BOTH);
        UnlocalizedNumberFormatter f1 = (UnlocalizedNumberFormatter) resolveSetting(KEY_FORMATTER_FIRST);
        UnlocalizedNumberFormatter f2 = (UnlocalizedNumberFormatter) resolveSetting(KEY_FORMATTER_SECOND);
        if (both == null) both = NumberFormatter.with();
        LocalizedNumberFormatter lf1 = (f1 != null ? f1 : both).locale(uloc);
        LocalizedNumberFormatter lf2 = (f2 != null ? f2 : both).locale(uloc);

        FormattedNumber fn1 = lf1.format(bd1);
        FormattedNumber fn2 = lf2.format(bd2);

        NumberRangeFormatter.RangeIdentityResult idResult;
        if (bd1.compareTo(bd2) == 0) {
            idResult = NumberRangeFormatter.RangeIdentityResult.EQUAL_BEFORE_ROUNDING;
        } else if (fn1.toBigDecimal().compareTo(fn2.toBigDecimal()) == 0) {
            idResult = NumberRangeFormatter.RangeIdentityResult.EQUAL_AFTER_ROUNDING;
        } else {
            idResult = NumberRangeFormatter.RangeIdentityResult.NOT_EQUAL;
        }

        NumberRangeFormatter.RangeIdentityFallback fb =
                (NumberRangeFormatter.RangeIdentityFallback) resolveSetting(KEY_IDENTITY_FALLBACK);
        if (fb == null) fb = NumberRangeFormatter.RangeIdentityFallback.APPROXIMATELY;

        String out;
        if (idResult != NumberRangeFormatter.RangeIdentityResult.NOT_EQUAL
                && fb != NumberRangeFormatter.RangeIdentityFallback.RANGE) {
            if (fb == NumberRangeFormatter.RangeIdentityFallback.SINGLE_VALUE
                    || (fb == NumberRangeFormatter.RangeIdentityFallback.APPROXIMATELY_OR_SINGLE_VALUE
                            && idResult == NumberRangeFormatter.RangeIdentityResult.EQUAL_BEFORE_ROUNDING)) {
                out = fn1.toString();
            } else {
                out = "~" + fn1.toString();
            }
        } else {
            out = fn1.toString() + "\u2013" + fn2.toString();
        }

        return new FormattedNumberRange(out, fn1.toBigDecimal(), fn2.toBigDecimal(), idResult);
    }
}
