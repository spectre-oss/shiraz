// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

import android.icu.util.ULocale;
import java.util.Locale;

/** Modern fluent API for localized number, currency, and unit formatting in ICU. */
public final class NumberFormatter {
    public enum UnitWidth {
        NARROW,
        SHORT,
        FULL_NAME,
        ISO_CODE,
        FORMAL,
        VARIANT,
        HIDDEN
    }

    public enum GroupingStrategy {
        OFF,
        MIN2,
        AUTO,
        ON_ALIGNED,
        THOUSANDS
    }

    public enum SignDisplay {
        AUTO,
        ALWAYS,
        NEVER,
        ACCOUNTING,
        ACCOUNTING_ALWAYS,
        EXCEPT_ZERO,
        ACCOUNTING_EXCEPT_ZERO,
        NEGATIVE,
        ACCOUNTING_NEGATIVE
    }

    public enum DecimalSeparatorDisplay {
        AUTO,
        ALWAYS
    }

    public enum TrailingZeroDisplay {
        AUTO,
        HIDE_IF_WHOLE
    }

    public enum RoundingPriority {
        RELAXED,
        STRICT
    }

    private static final UnlocalizedNumberFormatter BASE = new UnlocalizedNumberFormatter();

    private NumberFormatter() {
    }

    public static UnlocalizedNumberFormatter with() {
        return BASE;
    }

    public static LocalizedNumberFormatter withLocale(Locale locale) {
        return BASE.locale(locale);
    }

    public static LocalizedNumberFormatter withLocale(ULocale locale) {
        return BASE.locale(locale);
    }
}
