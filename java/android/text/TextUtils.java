// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import java.util.regex.Pattern;

/** Plain character operations. Styled text, layout and parcel transport are separate APIs. */
public class TextUtils {
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
}
