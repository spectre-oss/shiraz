package android.text.format;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Locale-aware date helpers covering the commonly used flags. */
public class DateUtils {
    public static final long SECOND_IN_MILLIS = 1000L, MINUTE_IN_MILLIS = 60000L, HOUR_IN_MILLIS = 3600000L, DAY_IN_MILLIS = 86400000L, WEEK_IN_MILLIS = 604800000L, YEAR_IN_MILLIS = 31449600000L;
    public static final int FORMAT_SHOW_TIME = 0x1, FORMAT_SHOW_WEEKDAY = 0x2, FORMAT_SHOW_YEAR = 0x4, FORMAT_NO_YEAR = 0x8, FORMAT_SHOW_DATE = 0x10, FORMAT_NO_MONTH_DAY = 0x20,
        FORMAT_12HOUR = 0x40, FORMAT_24HOUR = 0x80, FORMAT_ABBREV_TIME = 0x4000, FORMAT_ABBREV_WEEKDAY = 0x8000, FORMAT_ABBREV_MONTH = 0x10000, FORMAT_NUMERIC_DATE = 0x20000,
        FORMAT_ABBREV_RELATIVE = 0x40000, FORMAT_ABBREV_ALL = 0x80000;

    public static CharSequence getRelativeTimeSpanString(long time){return getRelativeTimeSpanString(time,System.currentTimeMillis(),MINUTE_IN_MILLIS,0);}
    public static CharSequence getRelativeTimeSpanString(long time,long now,long minimum){return getRelativeTimeSpanString(time,now,minimum,0);}
    public static CharSequence getRelativeTimeSpanString(long time,long now,long minimum,int flags){
        double distance=Math.abs((double)now-time);long unit;String name;
        if(distance<MINUTE_IN_MILLIS&&minimum<MINUTE_IN_MILLIS){unit=SECOND_IN_MILLIS;name="second";}
        else if(distance<HOUR_IN_MILLIS&&minimum<HOUR_IN_MILLIS){unit=MINUTE_IN_MILLIS;name="minute";}
        else if(distance<DAY_IN_MILLIS&&minimum<DAY_IN_MILLIS){unit=HOUR_IN_MILLIS;name="hour";}
        else if(distance<WEEK_IN_MILLIS&&minimum<WEEK_IN_MILLIS){unit=DAY_IN_MILLIS;name="day";}
        else return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(new Date(time));
        long count=(long)(distance/unit);
        String value=java.text.NumberFormat.getIntegerInstance().format(count)+" "+name+(count==1?"":"s");
        return time<=now?value+" ago":"in "+value;
    }
    public static String formatElapsedTime(long elapsedSeconds) {
        long seconds = Math.max(0, elapsedSeconds);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long rest = seconds % 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, rest);
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, rest);
    }

    public static boolean isToday(long millis) {
        Calendar now = Calendar.getInstance();
        Calendar other = Calendar.getInstance();
        other.setTimeInMillis(millis);
        return now.get(Calendar.YEAR) == other.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR);
    }

    public static String formatDateTime(Context context, long millis, int flags) {
        Locale locale = Locale.getDefault();
        StringBuilder pattern = new StringBuilder();
        boolean abbreviateAll = (flags & FORMAT_ABBREV_ALL) != 0;
        if ((flags & FORMAT_SHOW_WEEKDAY) != 0) pattern.append((flags & FORMAT_ABBREV_WEEKDAY) != 0 || abbreviateAll ? "EEE" : "EEEE");
        if ((flags & FORMAT_SHOW_DATE) != 0) {
            if (pattern.length() > 0) pattern.append(", ");
            boolean year = (flags & FORMAT_SHOW_YEAR) != 0 || ((flags & FORMAT_NO_YEAR) == 0 && !sameYear(millis));
            if ((flags & FORMAT_NUMERIC_DATE) != 0) pattern.append(year ? "yyyy-MM-dd" : "MM-dd");
            else {
                pattern.append((flags & FORMAT_ABBREV_MONTH) != 0 || abbreviateAll ? "MMM" : "MMMM");
                if ((flags & FORMAT_NO_MONTH_DAY) == 0) pattern.append(" d");
                if (year) pattern.append((flags & FORMAT_NO_MONTH_DAY) == 0 ? ", yyyy" : " yyyy");
            }
        }
        if ((flags & FORMAT_SHOW_TIME) != 0) {
            if (pattern.length() > 0) pattern.append(", ");
            boolean twentyFour = (flags & FORMAT_24HOUR) != 0 || ((flags & FORMAT_12HOUR) == 0 && DateFormat.is24HourFormat(context));
            pattern.append(twentyFour ? "HH:mm" : "h:mm a");
        }
        if (pattern.length() == 0) pattern.append("MMMM d");
        return new SimpleDateFormat(pattern.toString(), locale).format(new Date(millis));
    }

    private static boolean sameYear(long millis) {
        Calendar now = Calendar.getInstance();
        Calendar other = Calendar.getInstance();
        other.setTimeInMillis(millis);
        return now.get(Calendar.YEAR) == other.get(Calendar.YEAR);
    }
}
