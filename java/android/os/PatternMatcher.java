// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.util.regex.Pattern;

/** Matches strings against literal, prefix, suffix, simple-glob, or advanced-glob patterns. */
public class PatternMatcher implements Parcelable {
    public static final int PATTERN_LITERAL = 0;
    public static final int PATTERN_PREFIX = 1;
    public static final int PATTERN_SIMPLE_GLOB = 2;
    public static final int PATTERN_ADVANCED_GLOB = 3;
    public static final int PATTERN_SUFFIX = 4;

    public static final Parcelable.Creator<PatternMatcher> CREATOR =
            new Parcelable.Creator<PatternMatcher>() {
                @Override
                public PatternMatcher createFromParcel(Parcel source) {
                    return new PatternMatcher(source);
                }

                @Override
                public PatternMatcher[] newArray(int size) {
                    return new PatternMatcher[size];
                }
            };

    private final String pattern;
    private final int type;
    private transient Pattern compiledGlob;

    public PatternMatcher(String pattern, int type) {
        this.pattern = pattern;
        this.type = type;
    }

    public PatternMatcher(Parcel src) {
        this.pattern = src.readString();
        this.type = src.readInt();
    }

    public final String getPath() {
        return pattern;
    }

    public final int getType() {
        return type;
    }

    public boolean match(String match) {
        if (match == null || pattern == null) {
            return false;
        }
        if (type == PATTERN_LITERAL) {
            return pattern.equals(match);
        }
        if (type == PATTERN_PREFIX) {
            return match.startsWith(pattern);
        }
        if (type == PATTERN_SUFFIX) {
            return match.endsWith(pattern);
        }
        if (type == PATTERN_SIMPLE_GLOB) {
            return matchSimpleGlob(pattern, match);
        }
        if (type == PATTERN_ADVANCED_GLOB) {
            Pattern compiled = compiledGlob;
            if (compiled == null) {
                compiled = compileAdvancedGlob(pattern);
                compiledGlob = compiled;
            }
            return compiled.matcher(match).matches();
        }
        return false;
    }

    private static boolean matchSimpleGlob(String pattern, String match) {
        Pattern regex = compileSimpleGlob(pattern);
        return regex.matcher(match).matches();
    }

    private static void appendRegexEscaped(StringBuilder sb, char c) {
        if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
            sb.append('\\');
        }
        sb.append(c);
    }

    private static Pattern compileSimpleGlob(String glob) {
        StringBuilder sb = new StringBuilder(glob.length() * 2 + 4);
        sb.append("\\A");
        int n = glob.length();
        for (int i = 0; i < n; i++) {
            char c = glob.charAt(i);
            if (c == '\\') {
                if (i + 1 < n) {
                    appendRegexEscaped(sb, glob.charAt(++i));
                } else {
                    sb.append("\\\\");
                }
            } else if (c == '.') {
                sb.append('.');
            } else if (c == '*') {
                if (i == 0) {
                    continue;
                }
                sb.append('*');
            } else {
                appendRegexEscaped(sb, c);
            }
        }
        sb.append("\\z");
        return Pattern.compile(sb.toString(), Pattern.DOTALL);
    }

    private static Pattern compileAdvancedGlob(String glob) {
        StringBuilder sb = new StringBuilder(glob.length() * 2 + 4);
        sb.append("\\A");
        int n = glob.length();
        int i = 0;
        while (i < n) {
            char c = glob.charAt(i);
            if (c == '\\') {
                if (i + 1 < n) {
                    appendRegexEscaped(sb, glob.charAt(++i));
                } else {
                    sb.append("\\\\");
                }
                i++;
            } else if (c == '.') {
                sb.append('.');
                i++;
            } else if (c == '*' || c == '+') {
                if (i > 0) {
                    sb.append(c);
                }
                i++;
            } else if (c == '[') {
                int close = findSetEnd(glob, i + 1);
                if (close < 0) {
                    appendRegexEscaped(sb, c);
                    i++;
                } else {
                    sb.append('[');
                    int j = i + 1;
                    if (j < close && glob.charAt(j) == '^') {
                        sb.append('^');
                        j++;
                    }
                    while (j < close) {
                        char sc = glob.charAt(j);
                        if (sc == '\\' && j + 1 < close) {
                            char escaped = glob.charAt(++j);
                            if ("\\[]&-^".indexOf(escaped) >= 0) {
                                sb.append('\\');
                            }
                            sb.append(escaped);
                        } else if (sc == '[' || sc == '&') {
                            sb.append('\\').append(sc);
                        } else {
                            sb.append(sc);
                        }
                        j++;
                    }
                    sb.append(']');
                    i = close + 1;
                }
            } else if (c == '{') {
                int close = glob.indexOf('}', i + 1);
                String quant = close > i + 1 ? glob.substring(i + 1, close) : null;
                if (quant != null && isValidQuantifier(quant)) {
                    sb.append('{').append(quant).append('}');
                    i = close + 1;
                } else {
                    appendRegexEscaped(sb, c);
                    i++;
                }
            } else {
                appendRegexEscaped(sb, c);
                i++;
            }
        }
        sb.append("\\z");
        return Pattern.compile(sb.toString(), Pattern.DOTALL);
    }

    private static int findSetEnd(String s, int start) {
        int n = s.length();
        for (int i = start; i < n; i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == ']' && i > start) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isValidQuantifier(String q) {
        int comma = q.indexOf(',');
        if (comma < 0) {
            return isDigits(q);
        }
        String first = q.substring(0, comma);
        String second = q.substring(comma + 1);
        return isDigits(first) && (second.isEmpty() || isDigits(second));
    }

    private static boolean isDigits(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        String tag = "? ";
        switch (type) {
            case PATTERN_LITERAL:
                tag = "LITERAL: ";
                break;
            case PATTERN_PREFIX:
                tag = "PREFIX: ";
                break;
            case PATTERN_SIMPLE_GLOB:
                tag = "GLOB: ";
                break;
            case PATTERN_ADVANCED_GLOB:
                tag = "ADVANCED: ";
                break;
            case PATTERN_SUFFIX:
                tag = "SUFFIX: ";
                break;
            default:
                break;
        }
        return "PatternMatcher{" + tag + pattern + "}";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(pattern);
        dest.writeInt(type);
    }
}
