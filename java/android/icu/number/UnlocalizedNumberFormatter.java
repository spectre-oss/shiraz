// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

import android.icu.util.ULocale;
import java.util.Locale;

/** A NumberFormatter that does not yet have a locale associated with it. */
public class UnlocalizedNumberFormatter extends NumberFormatterSettings<UnlocalizedNumberFormatter> {
    UnlocalizedNumberFormatter() {
        super(null, 0, null);
    }

    UnlocalizedNumberFormatter(NumberFormatterSettings<?> parent, int key, Object value) {
        super(parent, key, value);
    }

    @Override
    UnlocalizedNumberFormatter create(int key, Object value) {
        return new UnlocalizedNumberFormatter(this, key, value);
    }

    public LocalizedNumberFormatter locale(Locale locale) {
        return new LocalizedNumberFormatter(this, KEY_LOCALE, ULocale.forLocale(locale));
    }

    public LocalizedNumberFormatter locale(ULocale locale) {
        return new LocalizedNumberFormatter(this, KEY_LOCALE, locale);
    }
}
