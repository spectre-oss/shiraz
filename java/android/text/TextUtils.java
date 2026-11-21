// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import java.util.regex.Pattern;

/** Plain character operations. Styled text, layout and parcel transport are separate APIs. */
public class TextUtils {
    public interface StringSplitter extends Iterable<String> { void setString(String string); }
    public static class SimpleStringSplitter implements StringSplitter, java.util.Iterator<String> {
        private final char delimiter;
        private String string = "";
        private int position;
        public SimpleStringSplitter(char delimiter) { this.delimiter = delimiter; }
        public void setString(String string) { this.string = java.util.Objects.requireNonNull(string); position = 0; }
        public java.util.Iterator<String> iterator() { return this; }
        public boolean hasNext() { return position < string.length(); }
        public String next() {
            if (!hasNext()) throw new java.util.NoSuchElementException();
            int end = string.indexOf(delimiter, position);
            if (end < 0) end = string.length();
            String result = string.substring(position, end);
            position = end + 1;
            return result;
        }
        public void remove() { throw new UnsupportedOperationException(); }
    }
    public static final int CAP_MODE_CHARACTERS=0x1000,CAP_MODE_WORDS=0x2000,CAP_MODE_SENTENCES=0x4000;
    public static int getCapsMode(CharSequence text,int offset,int modes){return CapsMode.getCapsMode(text,offset,modes);}
    public enum TruncateAt {START,MIDDLE,END,MARQUEE,END_SMALL}
    public interface EllipsizeCallback {void ellipsized(int start,int end);}
    public static CharSequence ellipsize(CharSequence text,TextPaint paint,float width,TruncateAt where){return ellipsize(text,paint,width,where,false,null);}
    public static CharSequence ellipsize(CharSequence text,TextPaint paint,float width,TruncateAt where,boolean preserveLength,EllipsizeCallback callback){
        return truncate(text,paint,width,where,preserveLength,callback,false);
    }
    public static String shirazEllipsize(String text,TextPaint paint,float width,TruncateAt where,boolean force){return truncate(text,paint,width,where,false,null,force).toString();}
    public static String shirazEllipsize(String text,TextPaint paint,float width,TruncateAt where,boolean force,EllipsizeCallback callback){return truncate(text,paint,width,where,false,callback,force).toString();}
    private static CharSequence truncate(CharSequence source,TextPaint paint,float width,TruncateAt where,boolean preserve,EllipsizeCallback callback,boolean force){
        if(source==null||paint==null||where==null)throw new NullPointerException("ellipsis arguments");
        if(Float.isNaN(width)||width<0)width=0;
        if(where==TruncateAt.MARQUEE)throw new UnsupportedOperationException("Marquee text is not implemented");
        if(!(source instanceof String||source instanceof StringBuilder||source instanceof StringBuffer))throw new UnsupportedOperationException("Styled ellipsis is not implemented");
        String text=source.toString();if(text.length()>32768)throw new IllegalArgumentException("ellipsis text limit");
        java.util.ArrayList<Integer> ends=new shiraz.runtime.TextGeometry(text,paint).boundaries();
        if(!force&&paint.measureText(text)<=width){if(callback!=null)callback.ellipsized(0,0);return source;}
        String mark=where==TruncateAt.END_SMALL?"\u2025":"\u2026";int n=ends.size()-1,left=0,right=text.length();float budget=width-paint.measureText(mark);
        if(budget>=0){
            if(where==TruncateAt.START){int low=0,high=Math.max(0,n-1);while(low<high){int mid=(low+high+1)/2;if(paint.measureText(mark+text.substring(ends.get(n-mid)))<=width)low=mid;else high=mid-1;}right=ends.get(n-low);}
            else if(where==TruncateAt.END||where==TruncateAt.END_SMALL){int low=0,high=force?n:Math.max(0,n-1);while(low<high){int mid=(low+high+1)/2;if(paint.measureText(text.substring(0,ends.get(mid))+mark)<=width)low=mid;else high=mid-1;}left=ends.get(low);}
            else{int low=0,high=Math.max(0,n-1);while(low<high){int mid=(low+high+1)/2;if(paint.measureText(text.substring(ends.get(n-mid)))<=budget/2)low=mid;else high=mid-1;}int tail=low;right=ends.get(n-tail);low=0;high=Math.max(0,n-tail-1);while(low<high){int mid=(low+high+1)/2;if(paint.measureText(text.substring(0,ends.get(mid))+mark+text.substring(right))<=width)low=mid;else high=mid-1;}left=ends.get(low);}
        }
        if(callback!=null)callback.ellipsized(left,right);
        int remaining=text.length()-(right-left);
        if(preserve){char[] result=text.toCharArray();int at=left;if(remaining>0&&at<right)result[at++]=mark.charAt(0);while(at<right)result[at++]='\ufeff';return new String(result);}
        if(remaining==0&&!force)return "";
        if(budget<0)return "";
        return text.substring(0,left)+mark+text.substring(right);
    }
    private TextUtils() { }
    public static CharSequence concat(CharSequence... parts) {
        if (parts.length == 0) return "";
        if (parts.length == 1) return parts[0];
        boolean styled = false;
        for (CharSequence part : parts) styled |= part instanceof Spanned;
        if (styled) {
            SpannableStringBuilder joined = new SpannableStringBuilder();
            for (CharSequence part : parts) joined.append(part == null ? "null" : part);
            return new SpannedString(joined);
        }
        StringBuilder joined = new StringBuilder();
        for (CharSequence part : parts) joined.append(part);
        return joined.toString();
    }
    public static String htmlEncode(String text) {
        StringBuilder encoded = new StringBuilder();
        for (int i=0; i<text.length(); i++) {
            char value=text.charAt(i);
            switch(value) {
                case '&': encoded.append("&amp;"); break;
                case '<': encoded.append("&lt;"); break;
                case '>': encoded.append("&gt;"); break;
                case '"': encoded.append("&quot;"); break;
                case '\'': encoded.append("&#39;"); break;
                default: encoded.append(value);
            }
        }
        return encoded.toString();
    }
    public static int indexOf(CharSequence text, char value) { return indexOf(text, value, 0); }
    public static int indexOf(CharSequence text, char value, int start) {
        if (text.getClass() == String.class) return ((String) text).indexOf(value, start);
        return indexOf(text, value, start, text.length());
    }
    public static int indexOf(CharSequence text, char value, int start, int end) {
        for (int i = start; i < end; i++) if (text.charAt(i) == value) return i;
        return -1;
    }
    public static int lastIndexOf(CharSequence text, char value) { return lastIndexOf(text, value, text.length()-1); }
    public static int lastIndexOf(CharSequence text, char value, int last) { return lastIndexOf(text, value, 0, last); }
    public static int lastIndexOf(CharSequence text, char value, int start, int last) {
        for (int i = Math.min(last, text.length()-1); i >= start; i--) if (text.charAt(i) == value) return i;
        return -1;
    }
    public static int indexOf(CharSequence text, CharSequence value) { return indexOf(text, value, 0); }
    public static int indexOf(CharSequence text, CharSequence value, int start) { return indexOf(text, value, start, text.length()); }
    public static int indexOf(CharSequence text, CharSequence value, int start, int end) {
        int length = value.length();
        if (length == 0) return start;
        char first = value.charAt(0);
        for (;;) {
            start = indexOf(text, first, start);
            if (start < 0 || start > end - length) return -1;
            if (regionMatches(text, start, value, 0, length)) return start;
            start++;
        }
    }
    public static boolean regionMatches(CharSequence first, int firstStart, CharSequence second, int secondStart, int length) {
        if (firstStart < 0 || secondStart < 0 || length < 0 || firstStart > first.length()-length || secondStart > second.length()-length) throw new IndexOutOfBoundsException();
        for (int i = 0; i < length; i++) if (first.charAt(firstStart+i) != second.charAt(secondStart+i)) return false;
        return true;
    }
    public static int getOffsetAfter(CharSequence text, int offset) {
        if (offset < 0 || offset > text.length()) throw new IndexOutOfBoundsException();
        if (offset == text.length()) return offset;
        int next = offset + Character.charCount(Character.codePointAt(text, offset));
        return outsideReplacement(text, next, true);
    }
    public static int getOffsetBefore(CharSequence text, int offset) {
        if (offset < 0 || offset > text.length()) throw new IndexOutOfBoundsException();
        if (offset == 0) return 0;
        int previous = offset - Character.charCount(Character.codePointBefore(text, offset));
        return outsideReplacement(text, previous, false);
    }
    private static int outsideReplacement(CharSequence text, int offset, boolean forward) {
        if (text instanceof Spanned) {
            Spanned spans = (Spanned) text;
            for (android.text.style.ReplacementSpan span : spans.getSpans(offset, offset, android.text.style.ReplacementSpan.class)) {
                int start = spans.getSpanStart(span), end = spans.getSpanEnd(span);
                if (start < offset && offset < end) offset = forward ? end : start;
            }
        }
        return offset;
    }
    public static int getLayoutDirectionFromLocale(java.util.Locale locale) {
        return locale != null && !java.util.Locale.ROOT.equals(locale) && android.icu.util.ULocale.forLocale(locale).isRightToLeft() ? 1 : 0;
    }

    public static boolean isEmpty(CharSequence value) {
        return value == null || value.length() == 0;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null || a.length() != b.length()) return false;
        for (int i = 0; i < a.length(); ++i) if (a.charAt(i) != b.charAt(i)) return false;
        return true;
    }

    public static int getTrimmedLength(CharSequence value) {
        int start = 0, end = value.length();
        while (start < end && value.charAt(start) <= ' ') ++start;
        while (end > start && value.charAt(end - 1) <= ' ') --end;
        return end - start;
    }

    public static boolean isDigitsOnly(CharSequence value) {
        for (int i = 0; i < value.length();) {
            int point = Character.codePointAt(value, i);
            if (!Character.isDigit(point)) return false;
            i += Character.charCount(point);
        }
        return true;
    }

    public static String join(CharSequence delimiter, Iterable tokens) {
        StringBuilder result = new StringBuilder();
        boolean separator = false;
        for (Object token : tokens) {
            if (separator) result.append(delimiter);
            result.append(token);
            separator = true;
        }
        return result.toString();
    }

    public static String join(CharSequence delimiter, Object[] tokens) {
        return join(delimiter, java.util.Arrays.asList(tokens));
    }

    public static String[] split(String text, String expression) {
        return text.isEmpty() ? new String[0] : text.split(expression, -1);
    }

    public static String[] split(String text, Pattern expression) {
        return text.isEmpty() ? new String[0] : expression.split(text, -1);
    }

    public static void getChars(CharSequence value, int start, int end, char[] dest, int offset) {
        if (start < 0 || end < start || end > value.length()) throw new IndexOutOfBoundsException();
        if (offset < 0 || offset > dest.length - (end - start)) throw new IndexOutOfBoundsException();
        if (value instanceof String) ((String) value).getChars(start, end, dest, offset);
        else if (value instanceof StringBuilder) ((StringBuilder) value).getChars(start, end, dest, offset);
        else if (value instanceof StringBuffer) ((StringBuffer) value).getChars(start, end, dest, offset);
        else if (value instanceof GetChars) ((GetChars)value).getChars(start,end,dest,offset);
        else for (int i = start; i < end; ++i) dest[offset++] = value.charAt(i);
    }

    public static String substring(CharSequence value, int start, int end) {
        if (start < 0 || end < start || end > value.length()) throw new IndexOutOfBoundsException();
        char[] copy = new char[end - start];
        getChars(value, start, end, copy, 0);
        return new String(copy);
    }

    public static void writeToParcel(CharSequence cs, android.os.Parcel p, int parcelableFlags) {
        if (cs == null) {
            p.writeInt(1);
            p.writeString(null);
        } else {
            p.writeInt(1);
            p.writeString(cs.toString());
        }
    }

    public static final android.os.Parcelable.Creator<CharSequence> CHAR_SEQUENCE_CREATOR =
            new android.os.Parcelable.Creator<CharSequence>() {
                @Override
                public CharSequence createFromParcel(android.os.Parcel p) {
                    int kind = p.readInt();
                    String str = p.readString();
                    if (str == null) return null;
                    if (kind == 1) return str;
                    return new SpannableString(str);
                }

                @Override
                public CharSequence[] newArray(int size) {
                    return new CharSequence[size];
                }
            };

    public static final int SAFE_STRING_FLAG_TRIM = 0x1;
    public static final int SAFE_STRING_FLAG_SINGLE_LINE = 0x2;
    public static final int SAFE_STRING_FLAG_FIRST_LINE = 0x4;

    public static void copySpansFrom(
            Spanned source, int start, int end, Class kind, Spannable dest, int destoff) {
        if (kind == null) {
            kind = Object.class;
        }
        Object[] spans = source.getSpans(start, end, kind);
        for (int i = 0; i < spans.length; i++) {
            int st = source.getSpanStart(spans[i]);
            int en = source.getSpanEnd(spans[i]);
            int fl = source.getSpanFlags(spans[i]);
            if (st < start) st = start;
            if (en > end) en = end;
            dest.setSpan(spans[i], st - start + destoff, en - start + destoff, fl);
        }
    }

    public static CharSequence stringOrSpannedString(CharSequence source) {
        if (source == null) return null;
        if (source instanceof SpannedString) return source;
        if (source instanceof Spanned) return new SpannedString(source);
        return source.toString();
    }

    @Deprecated
    public static boolean isGraphic(CharSequence str) {
        final int len = str.length();
        for (int cp, i = 0; i < len; i += Character.charCount(cp)) {
            cp = Character.codePointAt(str, i);
            int gc = Character.getType(cp);
            if (gc != Character.CONTROL
                    && gc != Character.FORMAT
                    && gc != Character.SURROGATE
                    && gc != Character.UNASSIGNED
                    && gc != Character.LINE_SEPARATOR
                    && gc != Character.PARAGRAPH_SEPARATOR
                    && gc != Character.SPACE_SEPARATOR) {
                return true;
            }
        }
        return false;
    }

    @Deprecated
    public static boolean isGraphic(char c) {
        int gc = Character.getType(c);
        return gc != Character.CONTROL
                && gc != Character.FORMAT
                && gc != Character.SURROGATE
                && gc != Character.UNASSIGNED
                && gc != Character.LINE_SEPARATOR
                && gc != Character.PARAGRAPH_SEPARATOR
                && gc != Character.SPACE_SEPARATOR;
    }

    public static void dumpSpans(CharSequence cs, android.util.Printer printer, String prefix) {
        if (cs instanceof Spanned) {
            Spanned sp = (Spanned) cs;
            Object[] os = sp.getSpans(0, cs.length(), Object.class);
            for (int i = 0; i < os.length; i++) {
                Object o = os[i];
                printer.println(prefix + cs.subSequence(sp.getSpanStart(o), sp.getSpanEnd(o))
                        + ": " + Integer.toHexString(System.identityHashCode(o))
                        + " " + o.getClass().getCanonicalName()
                        + " (" + sp.getSpanStart(o) + "-" + sp.getSpanEnd(o)
                        + ") fl=#" + sp.getSpanFlags(o));
            }
        } else {
            printer.println(prefix + cs + ": (no spans)");
        }
    }

    public static CharSequence replace(
            CharSequence template, String[] sources, CharSequence[] destinations) {
        SpannableStringBuilder tb = new SpannableStringBuilder(template);
        for (int i = 0; i < sources.length; i++) {
            int where = indexOf(tb, sources[i]);
            if (where >= 0) {
                tb.setSpan(sources[i], where, where + sources[i].length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        for (int i = 0; i < sources.length; i++) {
            int start = tb.getSpanStart(sources[i]);
            int end = tb.getSpanEnd(sources[i]);
            if (start >= 0) {
                tb.replace(start, end, destinations[i]);
            }
        }
        return tb;
    }

    public static CharSequence expandTemplate(CharSequence template, CharSequence... values) {
        if (values.length > 9) {
            throw new IllegalArgumentException("max of 9 values are supported");
        }
        SpannableStringBuilder ssb = new SpannableStringBuilder(template);
        int i = 0;
        while (i < ssb.length()) {
            if (ssb.charAt(i) == '^') {
                char next = i + 1 < ssb.length() ? ssb.charAt(i + 1) : '\0';
                if (next == '^') {
                    ssb.delete(i + 1, i + 2);
                    i++;
                    continue;
                } else if (Character.isDigit(next)) {
                    int which = Character.getNumericValue(next) - 1;
                    if (which < 0) {
                        throw new IllegalArgumentException("template requests value ^0");
                    }
                    if (which >= values.length) {
                        throw new IllegalArgumentException(
                                "template requests value ^" + (which + 1)
                                        + "; only " + values.length + " provided");
                    }
                    ssb.replace(i, i + 2, values[which]);
                    i += values[which].length();
                    continue;
                }
            }
            i++;
        }
        return ssb;
    }

    @Deprecated
    public static CharSequence getReverse(CharSequence source, int start, int end) {
        StringBuilder sb = new StringBuilder(end - start);
        for (int i = end - 1; i >= start; i--) {
            sb.append(source.charAt(i));
        }
        return sb.toString();
    }

    public static CharSequence commaEllipsize(
            CharSequence text, TextPaint p, float avail, String oneMore, String more) {
        if (text == null) return "";
        if (p.measureText(text.toString()) <= avail) return text;
        String[] parts = text.toString().split(",");
        StringBuilder fit = new StringBuilder();
        int kept = 0;
        for (int i = 0; i < parts.length; i++) {
            String candidate = (kept == 0 ? "" : ", ") + parts[i].trim();
            int remaining = parts.length - (i + 1);
            String suffix = remaining == 0 ? "" : ", " + (remaining == 1 ? oneMore : String.format(more, Integer.valueOf(remaining)));
            if (p.measureText(fit.toString() + candidate + suffix) <= avail) {
                fit.append(candidate);
                kept++;
            } else {
                int rem = parts.length - kept;
                if (kept > 0 && rem > 0) {
                    fit.append(", ").append(rem == 1 ? oneMore : String.format(more, Integer.valueOf(rem)));
                }
                break;
            }
        }
        return fit.toString();
    }

    public static CharSequence listEllipsize(
            android.content.Context context,
            java.util.List<CharSequence> elements,
            String separator,
            TextPaint paint,
            float avail,
            int moreId) {
        if (elements == null || elements.isEmpty()) return "";
        String sep = separator == null ? ", " : separator;
        String joined = join(sep, elements);
        if (paint.measureText(joined) <= avail) return joined;
        return ellipsize(joined, paint, avail, TruncateAt.END);
    }

    public static CharSequence makeSafeForPresentation(
            String unclean, int maxCharactersToConsider, float ellipsizeDip, int flags) {
        if (unclean == null) throw new NullPointerException("unclean");
        String s = maxCharactersToConsider > 0 && unclean.length() > maxCharactersToConsider
                ? unclean.substring(0, maxCharactersToConsider)
                : unclean;
        if ((flags & SAFE_STRING_FLAG_FIRST_LINE) != 0) {
            int nl = s.indexOf('\n');
            int cr = s.indexOf('\r');
            int cut = nl >= 0 && cr >= 0 ? Math.min(nl, cr) : Math.max(nl, cr);
            if (cut >= 0) s = s.substring(0, cut);
        } else if ((flags & SAFE_STRING_FLAG_SINGLE_LINE) != 0) {
            s = s.replace('\n', ' ').replace('\r', ' ');
        }
        if ((flags & SAFE_STRING_FLAG_TRIM) != 0) {
            s = s.trim();
        }
        return s;
    }
}
