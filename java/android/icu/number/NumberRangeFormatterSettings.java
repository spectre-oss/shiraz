// SPDX-License-Identifier: GPL-3.0-only
package android.icu.number;

import android.icu.util.ULocale;
import java.util.Objects;

/** Immutable fluent configuration chain for NumberRangeFormatter. */
public abstract class NumberRangeFormatterSettings<T extends NumberRangeFormatterSettings<?>> {
    static final int KEY_LOCALE = 1;
    static final int KEY_FORMATTER_BOTH = 2;
    static final int KEY_FORMATTER_FIRST = 3;
    static final int KEY_FORMATTER_SECOND = 4;
    static final int KEY_COLLAPSE = 5;
    static final int KEY_IDENTITY_FALLBACK = 6;

    final NumberRangeFormatterSettings<?> mParent;
    final int mKey;
    final Object mValue;

    NumberRangeFormatterSettings(NumberRangeFormatterSettings<?> parent, int key, Object value) {
        mParent = parent;
        mKey = key;
        mValue = value;
    }

    abstract T create(int key, Object value);

    public T numberFormatterBoth(UnlocalizedNumberFormatter formatter) {
        return create(KEY_FORMATTER_BOTH, formatter);
    }

    public T numberFormatterFirst(UnlocalizedNumberFormatter formatterFirst) {
        return create(KEY_FORMATTER_FIRST, formatterFirst);
    }

    public T numberFormatterSecond(UnlocalizedNumberFormatter formatterSecond) {
        return create(KEY_FORMATTER_SECOND, formatterSecond);
    }

    public T collapse(NumberRangeFormatter.RangeCollapse collapse) {
        return create(KEY_COLLAPSE, collapse);
    }

    public T identityFallback(NumberRangeFormatter.RangeIdentityFallback identityFallback) {
        return create(KEY_IDENTITY_FALLBACK, identityFallback);
    }

    Object resolveSetting(int targetKey) {
        for (NumberRangeFormatterSettings<?> cur = this; cur != null; cur = cur.mParent) {
            if (cur.mKey == targetKey) return cur.mValue;
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
        for (int k = 1; k <= 6; k++) {
            h = 31 * h + Objects.hashCode(resolveSetting(k));
        }
        return h;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NumberRangeFormatterSettings)) return false;
        NumberRangeFormatterSettings<?> that = (NumberRangeFormatterSettings<?>) other;
        for (int k = 1; k <= 6; k++) {
            if (!Objects.equals(resolveSetting(k), that.resolveSetting(k))) return false;
        }
        return true;
    }
}
