// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;

import android.text.InputType;
import java.util.HashMap;
import java.util.Locale;

/** KeyListener for date input filtering. */
public class DateKeyListener extends NumberKeyListener {
    @Deprecated
    public static final char[] CHARACTERS = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '/', '-', '.'
    };

    private static final HashMap<Locale, DateKeyListener> sInstanceCache =
            new HashMap<Locale, DateKeyListener>();

    private final char[] mCharacters;

    @Deprecated
    public DateKeyListener() {
        this(null);
    }

    public DateKeyListener(Locale locale) {
        mCharacters = CHARACTERS;
    }

    @Override
    public int getInputType() {
        return InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE;
    }

    @Override
    protected char[] getAcceptedChars() {
        return mCharacters;
    }

    @Deprecated
    public static DateKeyListener getInstance() {
        return getInstance(null);
    }

    public static DateKeyListener getInstance(Locale locale) {
        synchronized (sInstanceCache) {
            DateKeyListener instance = sInstanceCache.get(locale);
            if (instance == null) {
                instance = new DateKeyListener(locale);
                sInstanceCache.put(locale, instance);
            }
            return instance;
        }
    }
}
