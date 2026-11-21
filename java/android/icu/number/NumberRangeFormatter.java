// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

import android.icu.util.ULocale;
import java.util.Locale;

/** Modern fluent API for localized number range formatting in ICU. */
public abstract class NumberRangeFormatter {
    public enum RangeCollapse {
        AUTO,
        NONE,
        UNIT,
        ALL
    }

    public enum RangeIdentityFallback {
        SINGLE_VALUE,
        APPROXIMATELY_OR_SINGLE_VALUE,
        APPROXIMATELY,
        RANGE
    }

    public enum RangeIdentityResult {
        EQUAL_BEFORE_ROUNDING,
        EQUAL_AFTER_ROUNDING,
        NOT_EQUAL
    }

    private static final UnlocalizedNumberRangeFormatter BASE =
            new UnlocalizedNumberRangeFormatter();

    private NumberRangeFormatter() {
    }

    public static UnlocalizedNumberRangeFormatter with() {
        return BASE;
    }

    public static LocalizedNumberRangeFormatter withLocale(Locale locale) {
        return BASE.locale(locale);
    }

    public static LocalizedNumberRangeFormatter withLocale(ULocale locale) {
        return BASE.locale(locale);
    }
}
