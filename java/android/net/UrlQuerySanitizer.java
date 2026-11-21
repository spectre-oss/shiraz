// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Sanitizes URL query strings using configurable per-parameter character masks. */
public class UrlQuerySanitizer {

    /** Non-static inner pair holding a sanitized parameter name and value. */
    public class ParameterValuePair {
        public String mParameter;
        public String mValue;

        public ParameterValuePair(String parameter, String value) {
            this.mParameter = parameter;
            this.mValue = value;
        }
    }

    /** Strategy interface for sanitizing an individual parameter value. */
    public interface ValueSanitizer {
        String sanitize(String value);
    }

    /** Bitmask-driven character sanitizer matching Android's flag semantics. */
    public static class IllegalCharacterValueSanitizer implements ValueSanitizer {
        public static final int SPACE_OK = 1;
        public static final int OTHER_WHITESPACE_OK = 1 << 1;
        public static final int NON_7_BIT_ASCII_OK = 1 << 2;
        public static final int DQUOTE_OK = 1 << 3;
        public static final int SQUOTE_OK = 1 << 4;
        public static final int LT_OK = 1 << 5;
        public static final int GT_OK = 1 << 6;
        public static final int AMP_OK = 1 << 7;
        public static final int PCT_OK = 1 << 8;
        public static final int NUL_OK = 1 << 9;
        public static final int SCRIPT_URL_OK = 1 << 10;

        public static final int ALL_OK = 0x7ff;
        public static final int ALL_WHITESPACE_OK = SPACE_OK | OTHER_WHITESPACE_OK;

        public static final int ALL_ILLEGAL = 0;
        public static final int ALL_BUT_NUL_LEGAL = 1535;
        public static final int ALL_BUT_WHITESPACE_LEGAL = 1532;
        public static final int URL_LEGAL = 404;
        public static final int URL_AND_SPACE_LEGAL = 405;
        public static final int AMP_LEGAL = 128;
        public static final int AMP_AND_SPACE_LEGAL = 129;
        public static final int SPACE_LEGAL = 1;
        public static final int ALL_BUT_NUL_AND_ANGLE_BRACKETS_LEGAL = 1439;

        private static final String JAVASCRIPT_PREFIX = "javascript:";
        private static final String VBSCRIPT_PREFIX = "vbscript:";

        private final int mFlags;

        public IllegalCharacterValueSanitizer(int flags) {
            this.mFlags = flags;
        }

        @Override
        public String sanitize(String value) {
            if (value == null) {
                return null;
            }
            int length = value.length();
            if ((mFlags & SCRIPT_URL_OK) == 0 && length >= VBSCRIPT_PREFIX.length()) {
                String asLower = value.toLowerCase(Locale.ROOT);
                if (asLower.startsWith(JAVASCRIPT_PREFIX) || asLower.startsWith(VBSCRIPT_PREFIX)) {
                    return "";
                }
            }
            if ((mFlags & ALL_WHITESPACE_OK) == 0) {
                value = trimWhitespace(value);
                length = value.length();
            }
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                char c = value.charAt(i);
                if (!characterIsLegal(c)) {
                    c = (mFlags & SPACE_OK) != 0 ? ' ' : '_';
                }
                sb.append(c);
            }
            return sb.toString();
        }

        private String trimWhitespace(String value) {
            int start = 0;
            int last = value.length() - 1;
            int end = last;
            while (start <= end && isWhitespace(value.charAt(start))) {
                start++;
            }
            while (end >= start && isWhitespace(value.charAt(end))) {
                end--;
            }
            if (start == 0 && end == last) {
                return value;
            }
            return value.substring(start, end + 1);
        }

        private boolean isWhitespace(char c) {
            switch (c) {
                case ' ':
                case '\t':
                case '\f':
                case '\n':
                case '\r':
                case 11:
                    return true;
                default:
                    return false;
            }
        }

        private boolean characterIsLegal(char c) {
            switch (c) {
                case ' ':
                    return (mFlags & SPACE_OK) != 0;
                case '\t':
                case '\f':
                case '\n':
                case '\r':
                case 11:
                    return (mFlags & OTHER_WHITESPACE_OK) != 0;
                case '\"':
                    return (mFlags & DQUOTE_OK) != 0;
                case '\'':
                    return (mFlags & SQUOTE_OK) != 0;
                case '<':
                    return (mFlags & LT_OK) != 0;
                case '>':
                    return (mFlags & GT_OK) != 0;
                case '&':
                    return (mFlags & AMP_OK) != 0;
                case '%':
                    return (mFlags & PCT_OK) != 0;
                case '\0':
                    return (mFlags & NUL_OK) != 0;
                default:
                    return (c >= 32 && c < 127) || (c >= 128 && (mFlags & NON_7_BIT_ASCII_OK) != 0);
            }
        }
    }

    private static final ValueSanitizer sAllIllegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.ALL_ILLEGAL);
    private static final ValueSanitizer sAllButNulLegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.ALL_BUT_NUL_LEGAL);
    private static final ValueSanitizer sAllButWhitespaceLegal =
            new IllegalCharacterValueSanitizer(
                    IllegalCharacterValueSanitizer.ALL_BUT_WHITESPACE_LEGAL);
    private static final ValueSanitizer sUrlLegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.URL_LEGAL);
    private static final ValueSanitizer sUrlAndSpaceLegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.URL_AND_SPACE_LEGAL);
    private static final ValueSanitizer sAmpLegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.AMP_LEGAL);
    private static final ValueSanitizer sAmpAndSpaceLegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.AMP_AND_SPACE_LEGAL);
    private static final ValueSanitizer sSpaceLegal =
            new IllegalCharacterValueSanitizer(IllegalCharacterValueSanitizer.SPACE_LEGAL);
    private static final ValueSanitizer sAllButNulAndAngleBracketsLegal =
            new IllegalCharacterValueSanitizer(
                    IllegalCharacterValueSanitizer.ALL_BUT_NUL_AND_ANGLE_BRACKETS_LEGAL);

    private final HashMap<String, ValueSanitizer> mSanitizers =
            new HashMap<String, ValueSanitizer>();
    private final HashMap<String, String> mEntries = new HashMap<String, String>();
    private final ArrayList<ParameterValuePair> mEntriesList = new ArrayList<ParameterValuePair>();
    private boolean mAllowUnregisteredParamaters;
    private boolean mPreferFirstRepeatedParameter;
    private ValueSanitizer mUnregisteredParameterValueSanitizer = getAllIllegal();

    public UrlQuerySanitizer() {
    }

    public UrlQuerySanitizer(String url) {
        setAllowUnregisteredParamaters(true);
        parseUrl(url);
    }

    public ValueSanitizer getUnregisteredParameterValueSanitizer() {
        return mUnregisteredParameterValueSanitizer;
    }

    public void setUnregisteredParameterValueSanitizer(ValueSanitizer sanitizer) {
        mUnregisteredParameterValueSanitizer = sanitizer;
    }

    public static final ValueSanitizer getAllIllegal() {
        return sAllIllegal;
    }

    public static final ValueSanitizer getAllButNulLegal() {
        return sAllButNulLegal;
    }

    public static final ValueSanitizer getAllButWhitespaceLegal() {
        return sAllButWhitespaceLegal;
    }

    public static final ValueSanitizer getUrlLegal() {
        return sUrlLegal;
    }

    public static final ValueSanitizer getUrlAndSpaceLegal() {
        return sUrlAndSpaceLegal;
    }

    public static final ValueSanitizer getAmpLegal() {
        return sAmpLegal;
    }

    public static final ValueSanitizer getAmpAndSpaceLegal() {
        return sAmpAndSpaceLegal;
    }

    public static final ValueSanitizer getSpaceLegal() {
        return sSpaceLegal;
    }

    public static final ValueSanitizer getAllButNulAndAngleBracketsLegal() {
        return sAllButNulAndAngleBracketsLegal;
    }

    public void parseUrl(String url) {
        if (url == null) {
            clear();
            return;
        }
        int queryIndex = url.indexOf('?');
        String query;
        if (queryIndex >= 0) {
            query = url.substring(queryIndex + 1);
            int fragIndex = query.indexOf('#');
            if (fragIndex >= 0) {
                query = query.substring(0, fragIndex);
            }
        } else {
            query = "";
        }
        parseQuery(query);
    }

    public void parseQuery(String query) {
        clear();
        if (query == null || query.isEmpty()) {
            return;
        }
        int start = 0;
        int len = query.length();
        while (start <= len) {
            int amp = query.indexOf('&', start);
            int end = amp < 0 ? len : amp;
            if (end > start) {
                int eq = query.indexOf('=', start);
                if (eq >= start && eq < end) {
                    parseEntry(query.substring(start, eq), query.substring(eq + 1, end));
                } else {
                    parseEntry(query.substring(start, end), "");
                }
            }
            if (amp < 0) {
                break;
            }
            start = amp + 1;
        }
    }

    public Set<String> getParameterSet() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(mEntries.keySet()));
    }

    public List<ParameterValuePair> getParameterList() {
        return Collections.unmodifiableList(new ArrayList<ParameterValuePair>(mEntriesList));
    }

    public boolean hasParameter(String parameter) {
        return mEntries.containsKey(parameter);
    }

    public String getValue(String parameter) {
        return mEntries.get(parameter);
    }

    public void registerParameter(String parameter, ValueSanitizer valueSanitizer) {
        if (valueSanitizer == null) {
            mSanitizers.remove(parameter);
        } else {
            mSanitizers.put(parameter, valueSanitizer);
        }
    }

    public void registerParameters(String[] parameters, ValueSanitizer valueSanitizer) {
        if (parameters == null) {
            return;
        }
        for (String p : parameters) {
            registerParameter(p, valueSanitizer);
        }
    }

    public void setAllowUnregisteredParamaters(boolean allowUnregisteredParamaters) {
        mAllowUnregisteredParamaters = allowUnregisteredParamaters;
    }

    public boolean getAllowUnregisteredParamaters() {
        return mAllowUnregisteredParamaters;
    }

    public void setPreferFirstRepeatedParameter(boolean preferFirstRepeatedParameter) {
        mPreferFirstRepeatedParameter = preferFirstRepeatedParameter;
    }

    public boolean getPreferFirstRepeatedParameter() {
        return mPreferFirstRepeatedParameter;
    }

    protected void parseEntry(String parameter, String value) {
        String unescapedParameter = unescape(parameter);
        ValueSanitizer valueSanitizer = getEffectiveValueSanitizer(unescapedParameter);
        if (valueSanitizer == null) {
            return;
        }
        String unescapedValue = unescape(value);
        String sanitizedValue = valueSanitizer.sanitize(unescapedValue);
        addSanitizedEntry(unescapedParameter, sanitizedValue);
    }

    protected void addSanitizedEntry(String parameter, String value) {
        mEntriesList.add(new ParameterValuePair(parameter, value));
        if (mPreferFirstRepeatedParameter && mEntries.containsKey(parameter)) {
            return;
        }
        mEntries.put(parameter, value);
    }

    public ValueSanitizer getValueSanitizer(String parameter) {
        return mSanitizers.get(parameter);
    }

    public ValueSanitizer getEffectiveValueSanitizer(String parameter) {
        ValueSanitizer sanitizer = getValueSanitizer(parameter);
        if (sanitizer == null && mAllowUnregisteredParamaters) {
            sanitizer = getUnregisteredParameterValueSanitizer();
        }
        return sanitizer;
    }

    public String unescape(String string) {
        if (string == null) {
            return null;
        }
        int firstEscape = string.indexOf('%');
        if (firstEscape < 0) {
            firstEscape = string.indexOf('+');
            if (firstEscape < 0) {
                return string;
            }
        }
        int length = string.length();
        StringBuilder sb = new StringBuilder(length);
        sb.append(string, 0, firstEscape);
        for (int i = firstEscape; i < length; i++) {
            char c = string.charAt(i);
            if (c == '+') {
                c = ' ';
            } else if (c == '%' && i + 2 < length) {
                char c1 = string.charAt(i + 1);
                char c2 = string.charAt(i + 2);
                if (isHexDigit(c1) && isHexDigit(c2)) {
                    c = (char) (decodeHexDigit(c1) * 16 + decodeHexDigit(c2));
                    i += 2;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    protected boolean isHexDigit(char c) {
        return decodeHexDigit(c) >= 0;
    }

    protected int decodeHexDigit(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        return -1;
    }

    protected void clear() {
        mEntries.clear();
        mEntriesList.clear();
    }
}
