// SPDX-License-Identifier: GPL-3.0-only
package android.text.format;

import android.content.Context;
import android.icu.text.DateTimePatternGenerator;
import android.icu.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

/** Plain-text Android date patterns, using the runtime's ICU locale data. */
public class DateFormat {
    public DateFormat() { }
    private static Locale locale(Context context) { return context.getResources().getConfiguration().locale; }
    public static boolean is24HourFormat(Context context) {
        String override = System.getProperty("shiraz.timeFormat");
        if (override != null) {
            if (override.equals("24")) return true;
            if (override.equals("12")) return false;
            throw new IllegalArgumentException("shiraz.timeFormat must be 12 or 24");
        }
        return is24HourLocale(locale(context));
    }
    public static boolean is24HourLocale(Locale locale) {
        java.text.DateFormat format = java.text.DateFormat.getTimeInstance(java.text.DateFormat.LONG, Objects.requireNonNull(locale));
        if (!(format instanceof java.text.SimpleDateFormat)) throw new UnsupportedOperationException("Locale time pattern is unavailable");
        return hasDesignator(((java.text.SimpleDateFormat)format).toPattern(), 'H');
    }
    public static String getBestDateTimePattern(Locale locale, String skeleton) {
        Objects.requireNonNull(locale); Objects.requireNonNull(skeleton);
        if (skeleton.length() > 1024) throw new IllegalArgumentException("Date skeleton too long");
        return DateTimePatternGenerator.getInstance(locale).getBestPattern(skeleton);
    }
    public static String getTimeFormatString(Context context) {
        return getBestDateTimePattern(locale(context), is24HourFormat(context) ? "Hm" : "hm");
    }
    public static java.text.DateFormat getTimeFormat(Context context) { return new java.text.SimpleDateFormat(getTimeFormatString(context), locale(context)); }
    public static java.text.DateFormat getDateFormat(Context context) { return java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, locale(context)); }
    public static java.text.DateFormat getMediumDateFormat(Context context) { return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale(context)); }
    public static java.text.DateFormat getLongDateFormat(Context context) { return java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, locale(context)); }
    public static boolean hasDesignator(CharSequence pattern, char designator) {
        boolean quoted = false;
        for (int i = 0; i < pattern.length(); ++i) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '\'') ++i;
                else quoted = !quoted;
            } else if (!quoted && c == designator) return true;
        }
        return false;
    }
    public static CharSequence format(CharSequence pattern, long millis) {
        Calendar calendar = Calendar.getInstance(); calendar.setTimeInMillis(millis); return format(pattern, calendar);
    }
    public static CharSequence format(CharSequence pattern, Date date) { return format(pattern, Objects.requireNonNull(date).getTime()); }
    private static String number(int value, int width, Locale locale) { return String.format(locale, "%0" + width + "d", value); }
    public static CharSequence format(CharSequence pattern, Calendar calendar) {
        Objects.requireNonNull(pattern); Objects.requireNonNull(calendar);
        if (pattern.length() > 16384) throw new IllegalArgumentException("Date pattern too long");
        Locale locale = Locale.getDefault(); DateFormatSymbols symbols = new DateFormatSymbols(locale);
        StringBuilder result = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < pattern.length();) {
            char c = pattern.charAt(i++);
            if (c == '\'') {
                if (i < pattern.length() && pattern.charAt(i) == '\'') { result.append('\''); ++i; }
                else quoted = !quoted;
                continue;
            }
            if (quoted) { result.append(c); continue; }
            int count = 1;
            while (i < pattern.length() && pattern.charAt(i) == c) { ++i; ++count; }
            if (count > 1024) throw new IllegalArgumentException("Date field too wide");
            int value, style = count == 5 ? DateFormatSymbols.NARROW : count == 4 ? DateFormatSymbols.WIDE : DateFormatSymbols.ABBREVIATED;
            switch (c) {
                case 'a': case 'A': result.append(symbols.getAmPmStrings()[calendar.get(Calendar.AM_PM)]); break;
                case 'd': result.append(number(calendar.get(Calendar.DAY_OF_MONTH), count, locale)); break;
                case 'E': case 'c':
                    result.append(symbols.getWeekdays(c == 'c' ? DateFormatSymbols.STANDALONE : DateFormatSymbols.FORMAT, style)[calendar.get(Calendar.DAY_OF_WEEK)]); break;
                case 'h': case 'K':
                    value = calendar.get(Calendar.HOUR); if (c == 'h' && value == 0) value = 12;
                    result.append(number(value, count, locale)); break;
                case 'H': case 'k': result.append(number(calendar.get(Calendar.HOUR_OF_DAY), count, locale)); break;
                case 'L': case 'M':
                    value = calendar.get(Calendar.MONTH);
                    if (count >= 3 && count <= 5) result.append(symbols.getMonths(c == 'L' ? DateFormatSymbols.STANDALONE : DateFormatSymbols.FORMAT, style)[value]);
                    else result.append(number(value + 1, count, locale));
                    break;
                case 'm': result.append(number(calendar.get(Calendar.MINUTE), count, locale)); break;
                case 's': result.append(number(calendar.get(Calendar.SECOND), count, locale)); break;
                case 'y':
                    value = calendar.get(Calendar.YEAR); result.append(number(count <= 2 ? value % 100 : value, count <= 2 ? 2 : 1, locale)); break;
                case 'z':
                    if (count >= 2) result.append(calendar.getTimeZone().getDisplayName(calendar.get(Calendar.DST_OFFSET) != 0, java.util.TimeZone.SHORT, locale));
                    else {
                        int minutes = (calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) / 60000;
                        result.append(minutes < 0 ? '-' : '+'); minutes = Math.abs(minutes);
                        result.append(number(minutes / 60, 2, locale)).append(number(minutes % 60, 2, locale));
                    }
                    break;
                default: for (int j = 0; j < count; ++j) result.append(c);
            }
        }
        return result.toString();
    }
}
