// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;

import android.text.InputType;
import java.util.HashMap;
import java.util.Locale;

/** KeyListener for combined date and time input filtering. */
public class DateTimeKeyListener extends NumberKeyListener {
    public static final char[] CHARACTERS = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'a', 'm', 'p', ':', '/', '-', ' '
    };

    private static final HashMap<Locale, DateTimeKeyListener> sInstanceCache =
            new HashMap<Locale, DateTimeKeyListener>();

    private final char[] mCharacters;

    @Deprecated
    public DateTimeKeyListener() {
        this(null);
    }

    public DateTimeKeyListener(Locale locale) {
        mCharacters = CHARACTERS;
    }

    @Override
    public int getInputType() {
        return InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_NORMAL;
    }

    @Override
    protected char[] getAcceptedChars() {
        return mCharacters;
    }

    @Deprecated
    public static DateTimeKeyListener getInstance() {
        return getInstance(null);
    }

    public static DateTimeKeyListener getInstance(Locale locale) {
        synchronized (sInstanceCache) {
            DateTimeKeyListener instance = sInstanceCache.get(locale);
            if (instance == null) {
                instance = new DateTimeKeyListener(locale);
                sInstanceCache.put(locale, instance);
            }
            return instance;
        }
    }
}
