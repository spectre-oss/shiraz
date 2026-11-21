// SPDX-License-Identifier: GPL-3.0-only

package android.provider;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import java.util.Locale;

public class UserDictionary {
    public static final String AUTHORITY = "user_dictionary";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    public UserDictionary() {
    }

    public static class Words implements BaseColumns {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/words");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.userword";
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/vnd.google.userword";

        public static final String _ID = BaseColumns._ID;
        public static final String WORD = "word";
        public static final String FREQUENCY = "frequency";
        public static final String LOCALE = "locale";
        public static final String APP_ID = "appid";
        public static final String SHORTCUT = "shortcut";

        @Deprecated
        public static final int LOCALE_TYPE_ALL = 0;
        @Deprecated
        public static final int LOCALE_TYPE_CURRENT = 1;

        public static final String DEFAULT_SORT_ORDER = FREQUENCY + " DESC";

        public Words() {
        }

        @Deprecated
        public static void addWord(
                Context context, String word, int frequency, int localeType) {
            if (localeType != LOCALE_TYPE_ALL && localeType != LOCALE_TYPE_CURRENT) {
                return;
            }
            Locale locale = (localeType == LOCALE_TYPE_CURRENT) ? Locale.getDefault() : null;
            addWord(context, word, frequency, null, locale);
        }

        public static void addWord(
                Context context,
                String word,
                int frequency,
                String shortcut,
                Locale locale) {
            if (context == null || TextUtils.isEmpty(word)) {
                return;
            }
            ContentResolver resolver = context.getContentResolver();
            if (resolver == null) {
                return;
            }
            int clamped = Math.max(0, Math.min(255, frequency));
            ContentValues values = new ContentValues(5);
            values.put(WORD, word);
            values.put(FREQUENCY, Integer.valueOf(clamped));
            values.put(LOCALE, locale == null ? null : locale.toString());
            values.put(APP_ID, Integer.valueOf(0));
            if (!TextUtils.isEmpty(shortcut)) {
                values.put(SHORTCUT, shortcut);
            }
            resolver.insert(CONTENT_URI, values);
        }
    }
}
