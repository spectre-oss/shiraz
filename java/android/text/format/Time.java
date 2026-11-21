// SPDX-License-Identifier: GPL-3.0-only
package android.text.format;

import android.util.TimeFormatException;
import java.lang.reflect.Constructor;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/** Legacy mutable calendar/time representation with RFC 2445, RFC 3339, Julian day, and strftime formatting. */
@Deprecated
public class Time {
    public static final String TIMEZONE_UTC = "UTC";
    public static final int EPOCH_JULIAN_DAY = 2440588;
    public static final int MONDAY_BEFORE_JULIAN_EPOCH = 2440585;

    public static final int SECOND = 1;
    public static final int MINUTE = 2;
    public static final int HOUR = 3;
    public static final int MONTH_DAY = 4;
    public static final int MONTH = 5;
    public static final int YEAR = 6;
    public static final int WEEK_DAY = 7;
    public static final int YEAR_DAY = 8;
    public static final int WEEK_NUM = 9;

    public static final int SUNDAY = 0;
    public static final int MONDAY = 1;
    public static final int TUESDAY = 2;
    public static final int WEDNESDAY = 3;
    public static final int THURSDAY = 4;
    public static final int FRIDAY = 5;
    public static final int SATURDAY = 6;

    private static final int[] DAYS_PER_MONTH = {
        31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
    };

    public boolean allDay;
    public int second;
    public int minute;
    public int hour;
    public int monthDay;
    public int month;
    public int year;
    public int weekDay;
    public int yearDay;
    public int isDst;
    public long gmtoff;
    public String timezone;

    public Time(String timezoneId) {
        if (timezoneId == null) {
            throw new NullPointerException("timezoneId is null!");
        }
        initialize(timezoneId);
    }

    public Time() {
        initialize(getCurrentTimezone());
    }

    public Time(Time other) {
        initialize(other.timezone);
        set(other);
    }

    private void initialize(String timezoneId) {
        this.timezone = timezoneId;
        this.year = 1970;
        this.monthDay = 1;
        this.isDst = -1;
    }

    private static RuntimeException timeFormatError(String message) {
        try {
            Constructor<TimeFormatException> c =
                    TimeFormatException.class.getDeclaredConstructor(String.class);
            c.setAccessible(true);
            return c.newInstance(message);
        } catch (Exception ignored) {
            return new IllegalArgumentException(message);
        }
    }

    private static TimeZone resolveTimeZone(String id) {
        if (id == null || id.isEmpty()) {
            return TimeZone.getDefault();
        }
        TimeZone tz = TimeZone.getTimeZone(id);
        if (!"GMT".equalsIgnoreCase(id)
                && !"UTC".equalsIgnoreCase(id)
                && !id.startsWith("GMT")
                && "GMT".equals(tz.getID())) {
            try {
                android.icu.util.TimeZone icuTz = android.icu.util.TimeZone.getTimeZone(id);
                if (icuTz != null && !"GMT".equals(icuTz.getID())) {
                    int rawOffset = icuTz.getRawOffset();
                    return new java.util.SimpleTimeZone(rawOffset, id);
                }
            } catch (Throwable ignored) {
            }
        }
        return tz;
    }

    private static int getZoneOffsetMillis(String id, long utcMillis, boolean[] dstOut) {
        if (id == null || id.isEmpty()) {
            id = getCurrentTimezone();
        }
        TimeZone tz = TimeZone.getTimeZone(id);
        boolean fallbackIcu = !"GMT".equalsIgnoreCase(id)
                && !"UTC".equalsIgnoreCase(id)
                && !id.startsWith("GMT")
                && "GMT".equals(tz.getID());
        if (fallbackIcu) {
            try {
                android.icu.util.TimeZone icuTz = android.icu.util.TimeZone.getTimeZone(id);
                if (icuTz != null) {
                    int[] offsets = new int[2];
                    icuTz.getOffset(utcMillis, false, offsets);
                    if (dstOut != null && dstOut.length > 0) {
                        dstOut[0] = offsets[1] != 0;
                    }
                    return offsets[0] + offsets[1];
                }
            } catch (Throwable ignored) {
            }
        }
        int off = tz.getOffset(utcMillis);
        if (dstOut != null && dstOut.length > 0) {
            dstOut[0] = tz.inDaylightTime(new java.util.Date(utcMillis));
        }
        return off;
    }

    public long normalize(boolean ignoreDst) {
        long millis = toMillis(ignoreDst);
        set(millis);
        return millis;
    }

    public void switchTimezone(String timezone) {
        long ms = toMillis(true);
        this.timezone = timezone;
        set(ms);
    }

    public int getActualMaximum(int field) {
        switch (field) {
            case SECOND:
                return 59;
            case MINUTE:
                return 59;
            case HOUR:
                return 23;
            case MONTH_DAY: {
                int n = DAYS_PER_MONTH[this.month];
                if (n != 28) {
                    return n;
                }
                int y = this.year;
                return ((y % 4) == 0 && ((y % 100) != 0 || (y % 400) == 0)) ? 29 : 28;
            }
            case MONTH:
                return 11;
            case YEAR:
                return 2037;
            case WEEK_DAY:
                return 6;
            case YEAR_DAY: {
                int y = this.year;
                return ((y % 4) == 0 && ((y % 100) != 0 || (y % 400) == 0)) ? 365 : 364;
            }
            case WEEK_NUM:
                throw new RuntimeException("WEEK_NUM not implemented");
            default:
                throw new RuntimeException("bad field=" + field);
        }
    }

    public void clear(String timezoneId) {
        if (timezoneId == null) {
            throw new NullPointerException("timezone is null!");
        }
        this.timezone = timezoneId;
        this.allDay = false;
        this.second = 0;
        this.minute = 0;
        this.hour = 0;
        this.monthDay = 0;
        this.month = 0;
        this.year = 0;
        this.weekDay = 0;
        this.yearDay = 0;
        this.gmtoff = 0;
        this.isDst = -1;
    }

    public static int compare(Time a, Time b) {
        if (a == null) throw new NullPointerException("a == null");
        if (b == null) throw new NullPointerException("b == null");
        return Long.compare(a.toMillis(false), b.toMillis(false));
    }

    public String format(String format) {
        if (format == null) {
            format = "%c";
        }
        Time copy = new Time(this);
        copy.normalize(false);
        DateFormatSymbols syms = DateFormatSymbols.getInstance(Locale.getDefault());
        String[] months = syms.getMonths();
        String[] shortMonths = syms.getShortMonths();
        String[] weekdays = syms.getWeekdays();
        String[] shortWeekdays = syms.getShortWeekdays();
        String[] amPm = syms.getAmPmStrings();

        StringBuilder out = new StringBuilder();
        int len = format.length();
        for (int i = 0; i < len; i++) {
            char ch = format.charAt(i);
            if (ch != '%' || i + 1 >= len) {
                out.append(ch);
                continue;
            }
            char spec = format.charAt(++i);
            switch (spec) {
                case '%':
                    out.append('%');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'Y':
                    out.append(String.format(Locale.US, "%04d", Integer.valueOf(copy.year)));
                    break;
                case 'y':
                    out.append(String.format(Locale.US, "%02d", Integer.valueOf(Math.abs(copy.year) % 100)));
                    break;
                case 'C':
                    out.append(String.format(Locale.US, "%02d", Integer.valueOf(copy.year / 100)));
                    break;
                case 'm':
                    out.append(String.format(Locale.US, "%02d", Integer.valueOf(copy.month + 1)));
                    break;
                case 'd':
                    out.append(String.format(Locale.US, "%02d", Integer.valueOf(copy.monthDay)));
                    break;
                case 'e':
                    out.append(String.format(Locale.US, "%2d", Integer.valueOf(copy.monthDay)));
                    break;
                case 'H':
                    out.append(String.format(Locale.US, "%02d", Integer.valueOf(copy.hour)));
                    break;
                case 'k':
                    out.append(String.format(Locale.US, "%2d", Integer.valueOf(copy.hour)));
                    break;
                case 'I': {
                    int h12 = copy.hour % 12;
                    if (h12 == 0) h12 = 12;
                    out.append(String.format(Locale.US, "%02d", Integer.valueOf(h12)));
                    break;
                }
                case 'l': {
                    int h12 = copy.hour % 12;
                    if (h12 == 0) h12 = 12;
                    out.append(String.format(Locale.US, "%2d", Integer.valueOf(h12)));
                    break;
                }
                case 'M':
                    out.append(String.format(Locale.US, "%02d", Integer.valueOf(copy.minute)));
                    break;
                case 'S':
                    out.append(String.format(Locale.US, "%02d", Integer.valueOf(copy.second)));
                    break;
                case 's':
                    out.append(Long.toString(copy.toMillis(false) / 1000L));
                    break;
                case 'p':
                    out.append(copy.hour < 12 ? amPm[0] : amPm[1]);
                    break;
                case 'P':
                    out.append((copy.hour < 12 ? amPm[0] : amPm[1]).toLowerCase(Locale.getDefault()));
                    break;
                case 'A':
                    out.append(weekdays[copy.weekDay + 1]);
                    break;
                case 'a':
                    out.append(shortWeekdays[copy.weekDay + 1]);
                    break;
                case 'B':
                    out.append(months[copy.month]);
                    break;
                case 'b':
                case 'h':
                    out.append(shortMonths[copy.month]);
                    break;
                case 'j':
                    out.append(String.format(Locale.US, "%03d", Integer.valueOf(copy.yearDay + 1)));
                    break;
                case 'w':
                    out.append(Integer.toString(copy.weekDay));
                    break;
                case 'u':
                    out.append(Integer.toString(copy.weekDay == 0 ? 7 : copy.weekDay));
                    break;
                case 'V':
                    out.append(String.format(Locale.US, "%02d", Integer.valueOf(copy.getWeekNumber())));
                    break;
                case 'F':
                    out.append(String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            Integer.valueOf(copy.year),
                            Integer.valueOf(copy.month + 1),
                            Integer.valueOf(copy.monthDay)));
                    break;
                case 'T':
                case 'X':
                    out.append(String.format(
                            Locale.US,
                            "%02d:%02d:%02d",
                            Integer.valueOf(copy.hour),
                            Integer.valueOf(copy.minute),
                            Integer.valueOf(copy.second)));
                    break;
                case 'R':
                    out.append(String.format(
                            Locale.US,
                            "%02d:%02d",
                            Integer.valueOf(copy.hour),
                            Integer.valueOf(copy.minute)));
                    break;
                case 'D':
                case 'x':
                    out.append(String.format(
                            Locale.US,
                            "%02d/%02d/%02d",
                            Integer.valueOf(copy.month + 1),
                            Integer.valueOf(copy.monthDay),
                            Integer.valueOf(Math.abs(copy.year) % 100)));
                    break;
                case 'r': {
                    int h12 = copy.hour % 12;
                    if (h12 == 0) h12 = 12;
                    out.append(String.format(
                            Locale.US,
                            "%02d:%02d:%02d %s",
                            Integer.valueOf(h12),
                            Integer.valueOf(copy.minute),
                            Integer.valueOf(copy.second),
                            copy.hour < 12 ? amPm[0] : amPm[1]));
                    break;
                }
                case 'c':
                    out.append(String.format(
                            Locale.US,
                            "%s %s %2d %02d:%02d:%02d %04d",
                            shortWeekdays[copy.weekDay + 1],
                            shortMonths[copy.month],
                            Integer.valueOf(copy.monthDay),
                            Integer.valueOf(copy.hour),
                            Integer.valueOf(copy.minute),
                            Integer.valueOf(copy.second),
                            Integer.valueOf(copy.year)));
                    break;
                case 'z': {
                    long totalMinutes = copy.gmtoff / 60L;
                    char sign = totalMinutes < 0 ? '-' : '+';
                    long absMin = Math.abs(totalMinutes);
                    out.append(String.format(
                            Locale.US,
                            "%c%02d%02d",
                            Character.valueOf(sign),
                            Long.valueOf(absMin / 60L),
                            Long.valueOf(absMin % 60L)));
                    break;
                }
                case 'Z':
                    out.append(copy.timezone != null ? copy.timezone : "");
                    break;
                default:
                    out.append(spec);
                    break;
            }
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return String.format(
                Locale.US,
                "%04d%02d%02dT%02d%02d%02d%s(%d,%d,%d,%d,%d)",
                Integer.valueOf(year),
                Integer.valueOf(month + 1),
                Integer.valueOf(monthDay),
                Integer.valueOf(hour),
                Integer.valueOf(minute),
                Integer.valueOf(second),
                timezone != null ? timezone : "",
                Integer.valueOf(weekDay),
                Integer.valueOf(yearDay),
                Long.valueOf(gmtoff),
                Integer.valueOf(isDst),
                Long.valueOf(toMillis(false) / 1000L));
    }

    private static int parseDigits(String s, int start, int count) {
        if (start + count > s.length()) {
            throw timeFormatError("Unexpected end of time string: " + s);
        }
        int val = 0;
        for (int i = 0; i < count; i++) {
            char c = s.charAt(start + i);
            if (c < '0' || c > '9') {
                throw timeFormatError("Invalid digit in time string: " + s);
            }
            val = val * 10 + (c - '0');
        }
        return val;
    }

    public boolean parse(String s) {
        if (s == null) {
            throw new NullPointerException("time string is null");
        }
        int len = s.length();
        if (len < 8) {
            throw timeFormatError("Time string too short: " + s);
        }
        boolean inUtc = false;
        year = parseDigits(s, 0, 4);
        month = parseDigits(s, 4, 2) - 1;
        monthDay = parseDigits(s, 6, 2);
        if (len > 8) {
            if (len < 15 || s.charAt(8) != 'T') {
                throw timeFormatError("Malformed RFC 2445 time string: " + s);
            }
            allDay = false;
            hour = parseDigits(s, 9, 2);
            minute = parseDigits(s, 11, 2);
            second = parseDigits(s, 13, 2);
            if (len > 15) {
                if (len != 16 || s.charAt(15) != 'Z') {
                    throw timeFormatError("Invalid trailing characters in time string: " + s);
                }
                inUtc = true;
            }
        } else {
            allDay = true;
            hour = 0;
            minute = 0;
            second = 0;
        }
        weekDay = 0;
        yearDay = 0;
        isDst = -1;
        gmtoff = 0;
        if (inUtc) {
            timezone = TIMEZONE_UTC;
        }
        return inUtc;
    }

    public boolean parse3339(String s) {
        if (s == null) {
            throw new NullPointerException("time string is null");
        }
        int len = s.length();
        if (len < 10 || s.charAt(4) != '-' || s.charAt(7) != '-') {
            throw timeFormatError("Time string too short or malformed: " + s);
        }
        year = parseDigits(s, 0, 4);
        month = parseDigits(s, 5, 2) - 1;
        monthDay = parseDigits(s, 8, 2);
        boolean inUtc = false;

        if (len > 10) {
            char sep = s.charAt(10);
            if (len < 19 || (sep != 'T' && sep != 't' && sep != ' ')
                    || s.charAt(13) != ':' || s.charAt(16) != ':') {
                throw timeFormatError("Malformed RFC 3339 time part: " + s);
            }
            allDay = false;
            hour = parseDigits(s, 11, 2);
            minute = parseDigits(s, 14, 2);
            second = parseDigits(s, 17, 2);

            int tzIndex = 19;
            if (tzIndex < len && s.charAt(tzIndex) == '.') {
                tzIndex++;
                while (tzIndex < len && Character.isDigit(s.charAt(tzIndex))) {
                    tzIndex++;
                }
            }
            int offsetMinutes = 0;
            if (tzIndex < len) {
                char z = s.charAt(tzIndex);
                if (z == 'Z' || z == 'z') {
                    if (tzIndex + 1 != len) {
                        throw timeFormatError("Extra characters after Z: " + s);
                    }
                    inUtc = true;
                } else if (z == '+' || z == '-') {
                    if (tzIndex + 6 != len || s.charAt(tzIndex + 3) != ':') {
                        throw timeFormatError("Malformed RFC 3339 offset: " + s);
                    }
                    int oh = parseDigits(s, tzIndex + 1, 2);
                    int om = parseDigits(s, tzIndex + 4, 2);
                    int sign = (z == '-') ? 1 : -1;
                    offsetMinutes = sign * (oh * 60 + om);
                    inUtc = true;
                } else {
                    throw timeFormatError("Unexpected timezone character: " + s);
                }
            }
            if (inUtc) {
                timezone = TIMEZONE_UTC;
                minute += offsetMinutes;
                normalize(true);
            } else {
                isDst = -1;
                gmtoff = 0;
            }
        } else {
            allDay = true;
            hour = 0;
            minute = 0;
            second = 0;
            weekDay = 0;
            yearDay = 0;
            isDst = -1;
            gmtoff = 0;
        }
        return inUtc;
    }

    public static String getCurrentTimezone() {
        return TimeZone.getDefault().getID();
    }

    public void setToNow() {
        set(System.currentTimeMillis());
    }

    public long toMillis(boolean ignoreDst) {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
        cal.clear();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, monthDay);
        if (!allDay) {
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, second);
        }
        long localAsUtc = cal.getTimeInMillis();
        if (allDay || TIMEZONE_UTC.equalsIgnoreCase(timezone)) {
            return localAsUtc;
        }
        TimeZone tz = resolveTimeZone(timezone);
        int rawOffset = tz.getRawOffset();
        long approxUtc = localAsUtc - rawOffset;
        boolean[] dst = new boolean[1];
        int totalOffset = getZoneOffsetMillis(timezone, approxUtc, dst);
        long refinedUtc = localAsUtc - totalOffset;
        int totalOffset2 = getZoneOffsetMillis(timezone, refinedUtc, dst);
        if (!ignoreDst && isDst >= 0) {
            int dstSavings = tz.getDSTSavings();
            if (dstSavings == 0) dstSavings = 3600000;
            int offset = rawOffset + (isDst > 0 ? dstSavings : 0);
            return localAsUtc - offset;
        }
        return localAsUtc - totalOffset2;
    }

    public void set(long millis) {
        allDay = false;
        boolean[] dst = new boolean[1];
        int offsetMs = TIMEZONE_UTC.equalsIgnoreCase(timezone)
                ? 0
                : getZoneOffsetMillis(timezone, millis, dst);
        gmtoff = offsetMs / 1000L;
        isDst = TIMEZONE_UTC.equalsIgnoreCase(timezone) ? 0 : (dst[0] ? 1 : 0);

        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
        cal.setTimeInMillis(millis + offsetMs);
        year = cal.get(Calendar.YEAR);
        month = cal.get(Calendar.MONTH);
        monthDay = cal.get(Calendar.DAY_OF_MONTH);
        hour = cal.get(Calendar.HOUR_OF_DAY);
        minute = cal.get(Calendar.MINUTE);
        second = cal.get(Calendar.SECOND);
        weekDay = cal.get(Calendar.DAY_OF_WEEK) - 1;
        yearDay = cal.get(Calendar.DAY_OF_YEAR) - 1;
    }

    public String format2445() {
        if (allDay) {
            return String.format(
                    Locale.US,
                    "%04d%02d%02d",
                    Integer.valueOf(year),
                    Integer.valueOf(month + 1),
                    Integer.valueOf(monthDay));
        }
        boolean utc = TIMEZONE_UTC.equals(timezone);
        return String.format(
                Locale.US,
                utc ? "%04d%02d%02dT%02d%02d%02dZ" : "%04d%02d%02dT%02d%02d%02d",
                Integer.valueOf(year),
                Integer.valueOf(month + 1),
                Integer.valueOf(monthDay),
                Integer.valueOf(hour),
                Integer.valueOf(minute),
                Integer.valueOf(second));
    }

    public void set(Time that) {
        this.timezone = that.timezone;
        this.allDay = that.allDay;
        this.second = that.second;
        this.minute = that.minute;
        this.hour = that.hour;
        this.monthDay = that.monthDay;
        this.month = that.month;
        this.year = that.year;
        this.weekDay = that.weekDay;
        this.yearDay = that.yearDay;
        this.isDst = that.isDst;
        this.gmtoff = that.gmtoff;
    }

    public void set(int second, int minute, int hour, int monthDay, int month, int year) {
        this.allDay = false;
        this.second = second;
        this.minute = minute;
        this.hour = hour;
        this.monthDay = monthDay;
        this.month = month;
        this.year = year;
        this.weekDay = 0;
        this.yearDay = 0;
        this.isDst = -1;
        this.gmtoff = 0;
    }

    public void set(int monthDay, int month, int year) {
        this.allDay = true;
        this.second = 0;
        this.minute = 0;
        this.hour = 0;
        this.monthDay = monthDay;
        this.month = month;
        this.year = year;
        this.weekDay = 0;
        this.yearDay = 0;
        this.isDst = -1;
        this.gmtoff = 0;
    }

    public boolean before(Time that) {
        return compare(this, that) < 0;
    }

    public boolean after(Time that) {
        return compare(this, that) > 0;
    }

    public int getWeekNumber() {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.setMinimalDaysInFirstWeek(4);
        cal.set(year, month, monthDay, 0, 0, 0);
        return cal.get(Calendar.WEEK_OF_YEAR);
    }

    public String format3339(boolean allDay) {
        if (allDay) {
            return String.format(
                    Locale.US,
                    "%04d-%02d-%02d",
                    Integer.valueOf(year),
                    Integer.valueOf(month + 1),
                    Integer.valueOf(monthDay));
        }
        if (TIMEZONE_UTC.equals(timezone)) {
            return String.format(
                    Locale.US,
                    "%04d-%02d-%02dT%02d:%02d:%02d.000Z",
                    Integer.valueOf(year),
                    Integer.valueOf(month + 1),
                    Integer.valueOf(monthDay),
                    Integer.valueOf(hour),
                    Integer.valueOf(minute),
                    Integer.valueOf(second));
        }
        long offsetSecs = gmtoff;
        char sign = offsetSecs < 0 ? '-' : '+';
        long absSecs = Math.abs(offsetSecs);
        int minutes = (int) ((absSecs % 3600L) / 60L);
        int hours = (int) (absSecs / 3600L);
        return String.format(
                Locale.US,
                "%04d-%02d-%02dT%02d:%02d:%02d.000%c%02d:%02d",
                Integer.valueOf(year),
                Integer.valueOf(month + 1),
                Integer.valueOf(monthDay),
                Integer.valueOf(hour),
                Integer.valueOf(minute),
                Integer.valueOf(second),
                Character.valueOf(sign),
                Integer.valueOf(hours),
                Integer.valueOf(minutes));
    }

    public static boolean isEpoch(Time time) {
        long millis = time.toMillis(true);
        return getJulianDay(millis, 0) == EPOCH_JULIAN_DAY;
    }

    public static int getJulianDay(long millis, long gmtoffSeconds) {
        long offsetMillis = gmtoffSeconds * 1000L;
        long julianDay = (millis + offsetMillis) / 86400000L;
        if ((millis + offsetMillis) < 0 && (millis + offsetMillis) % 86400000L != 0) {
            julianDay--;
        }
        return (int) julianDay + EPOCH_JULIAN_DAY;
    }

    public long setJulianDay(int julianDay) {
        long millis = (julianDay - EPOCH_JULIAN_DAY) * 86400000L;
        set(millis);
        int currentDay = getJulianDay(millis, gmtoff);
        monthDay += julianDay - currentDay;
        hour = 0;
        minute = 0;
        second = 0;
        return normalize(true);
    }

    public static int getWeeksSinceEpochFromJulianDay(int julianDay, int firstDayOfWeek) {
        int diff = THURSDAY - firstDayOfWeek;
        if (diff < 0) {
            diff += 7;
        }
        int refDay = EPOCH_JULIAN_DAY - diff;
        int delta = julianDay - refDay;
        return delta >= 0 ? delta / 7 : (delta - 6) / 7;
    }

    public static int getJulianMondayFromWeeksSinceEpoch(int week) {
        return MONDAY_BEFORE_JULIAN_EPOCH + week * 7;
    }
}
