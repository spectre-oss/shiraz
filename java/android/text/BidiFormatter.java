// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import java.util.Locale;

/** Wraps text whose first strong character runs against the context direction in Unicode embeddings. */
public final class BidiFormatter {
    private static final TextDirectionHeuristic DEFAULT_TEXT_DIRECTION_HEURISTIC =
            TextDirectionHeuristics.FIRSTSTRONG_LTR;
    private static final BidiFormatter INSTANCE =
            new BidiFormatter(false, true, DEFAULT_TEXT_DIRECTION_HEURISTIC);
    private static final BidiFormatter RTL_INSTANCE =
            new BidiFormatter(true, true, DEFAULT_TEXT_DIRECTION_HEURISTIC);

    public static final class Builder {
        private boolean mIsRtlContext;
        private boolean mStereoReset;
        private TextDirectionHeuristic mTextDirectionHeuristic;

        public Builder() {
            initialize(TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == 1);
        }

        public Builder(boolean rtlContext) {
            initialize(rtlContext);
        }

        public Builder(Locale locale) {
            if (locale == null) {
                initialize(TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == 1);
            } else {
                initialize(TextUtils.getLayoutDirectionFromLocale(locale) == 1);
            }
        }

        private void initialize(boolean isRtlContext) {
            mIsRtlContext = isRtlContext;
            mTextDirectionHeuristic = DEFAULT_TEXT_DIRECTION_HEURISTIC;
            mStereoReset = true;
        }

        public Builder stereoReset(boolean stereoReset) {
            mStereoReset = stereoReset;
            return this;
        }

        public Builder setTextDirectionHeuristic(TextDirectionHeuristic heuristic) {
            mTextDirectionHeuristic = heuristic;
            return this;
        }

        public BidiFormatter build() {
            if (mStereoReset && mTextDirectionHeuristic == DEFAULT_TEXT_DIRECTION_HEURISTIC) {
                return mIsRtlContext ? RTL_INSTANCE : INSTANCE;
            }
            return new BidiFormatter(mIsRtlContext, mStereoReset, mTextDirectionHeuristic);
        }
    }

    private final boolean contextRtl;
    private final boolean stereoReset;
    private final TextDirectionHeuristic defaultHeuristic;

    private BidiFormatter(
            boolean contextRtl, boolean stereoReset, TextDirectionHeuristic heuristic) {
        this.contextRtl = contextRtl;
        this.stereoReset = stereoReset;
        this.defaultHeuristic = heuristic != null ? heuristic : DEFAULT_TEXT_DIRECTION_HEURISTIC;
    }

    public static BidiFormatter getInstance() {
        return new Builder().build();
    }

    public static BidiFormatter getInstance(boolean rtlContext) {
        return rtlContext ? RTL_INSTANCE : INSTANCE;
    }

    public static BidiFormatter getInstance(Locale locale) {
        if (locale == null) {
            throw new NullPointerException("locale");
        }
        return getInstance(TextUtils.getLayoutDirectionFromLocale(locale) == 1);
    }

    public boolean isRtlContext() {
        return contextRtl;
    }

    public boolean getStereoReset() {
        return stereoReset;
    }

    public boolean isRtl(String str) {
        return isRtl((CharSequence) str);
    }

    public boolean isRtl(CharSequence str) {
        if (str == null) {
            throw new NullPointerException("str");
        }
        return defaultHeuristic.isRtl(str, 0, str.length());
    }

    private static int firstStrongDirection(CharSequence text) {
        int direction = 0;
        for (int i = 0; i < text.length() && direction == 0;) {
            int cp = Character.codePointAt(text, i);
            i += Character.charCount(cp);
            byte d = Character.getDirectionality(cp);
            if (d == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                direction = 1;
            } else if (d == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
                direction = -1;
            }
        }
        return direction;
    }

    public String unicodeWrap(String str, TextDirectionHeuristic heuristic, boolean isolate) {
        if (str == null) {
            return null;
        }
        return unicodeWrap((CharSequence) str, heuristic, isolate).toString();
    }

    public CharSequence unicodeWrap(
            CharSequence str, TextDirectionHeuristic heuristic, boolean isolate) {
        if (str == null) {
            return null;
        }
        TextDirectionHeuristic h = heuristic != null ? heuristic : defaultHeuristic;
        int direction;
        if (h == DEFAULT_TEXT_DIRECTION_HEURISTIC) {
            direction = firstStrongDirection(str);
            if (direction == 0 || (direction == -1) == contextRtl) {
                return str;
            }
        } else {
            boolean isRtl = h.isRtl(str, 0, str.length());
            direction = isRtl ? -1 : 1;
            if (isRtl == contextRtl) {
                return str;
            }
        }
        SpannableStringBuilder result = new SpannableStringBuilder();
        result.append(direction == -1 ? '\u202B' : '\u202A');
        result.append(str);
        result.append('\u202C');
        if (isolate) {
            result.append(contextRtl ? '\u200F' : '\u200E');
        }
        return result;
    }

    public String unicodeWrap(String str, TextDirectionHeuristic heuristic) {
        return unicodeWrap(str, heuristic, stereoReset);
    }

    public CharSequence unicodeWrap(CharSequence str, TextDirectionHeuristic heuristic) {
        return unicodeWrap(str, heuristic, stereoReset);
    }

    public String unicodeWrap(String str, boolean isolate) {
        return unicodeWrap(str, defaultHeuristic, isolate);
    }

    public CharSequence unicodeWrap(CharSequence str, boolean isolate) {
        return unicodeWrap(str, defaultHeuristic, isolate);
    }

    public String unicodeWrap(String text) {
        if (text == null) {
            return null;
        }
        if (defaultHeuristic == DEFAULT_TEXT_DIRECTION_HEURISTIC && stereoReset) {
            int direction = firstStrongDirection(text);
            if (direction == 0 || (direction == -1) == contextRtl) {
                return text;
            }
            return (direction == -1 ? "\u202B" : "\u202A")
                    + text + "\u202C" + (contextRtl ? "\u200F" : "\u200E");
        }
        return unicodeWrap(text, defaultHeuristic, stereoReset);
    }

    public CharSequence unicodeWrap(CharSequence str) {
        return unicodeWrap(str, defaultHeuristic, stereoReset);
    }
}
