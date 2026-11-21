// SPDX-License-Identifier: GPL-3.0-only
package android.text.method;

import android.text.InputType;
import java.util.HashMap;
import java.util.Locale;

/** KeyListener for time-of-day input filtering. */
public class TimeKeyListener extends NumberKeyListener {
    public static final char[] CHARACTERS = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'm', 'p', ':'
    };

    private static final HashMap<Locale, TimeKeyListener> sInstanceCache =
            new HashMap<Locale, TimeKeyListener>();

    private final char[] mCharacters;

    @Deprecated
    public TimeKeyListener() {
        this(null);
    }

    public TimeKeyListener(Locale locale) {
        mCharacters = CHARACTERS;
    }

    @Override
    public int getInputType() {
        return InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME;
    }

    @Override
    protected char[] getAcceptedChars() {
        return mCharacters;
    }

    @Deprecated
    public static TimeKeyListener getInstance() {
        return getInstance(null);
    }

    public static TimeKeyListener getInstance(Locale locale) {
        synchronized (sInstanceCache) {
            TimeKeyListener instance = sInstanceCache.get(locale);
            if (instance == null) {
                instance = new TimeKeyListener(locale);
                sInstanceCache.put(locale, instance);
            }
            return instance;
        }
    }
}
