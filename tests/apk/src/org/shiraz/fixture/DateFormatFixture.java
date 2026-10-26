// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.format.DateFormat;
import java.util.*;

final class DateFormatFixture {
    static void check(Context context) {
        Resources resources = context.getResources(); Configuration original = resources.getConfiguration();
        Locale oldLocale = Locale.getDefault(); TimeZone oldZone = TimeZone.getDefault();
        String override = System.getProperty("shiraz.timeFormat");
        try {
            System.clearProperty("shiraz.timeFormat"); Locale.setDefault(Locale.US); TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            Calendar date = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US);
            date.clear(); date.set(2024, Calendar.FEBRUARY, 29, 0, 5, 7); long millis = date.getTimeInMillis();
            if (!"2024-02-29 00:05:07 Thu Thursday T 12 00 00 24/24".contentEquals(DateFormat.format("yyyy-MM-dd HH:mm:ss EEE EEEE EEEEE hh KK kk yy/y", date)))
                throw new AssertionError("Android date field semantics");
            if (!"at 00 o'clock Q S D".contentEquals(DateFormat.format("'at' HH 'o''clock' Q S D", date))
                    || !"'00".contentEquals(DateFormat.format("''HH", date)) || !"unfinished".contentEquals(DateFormat.format("'unfinished", date)))
                throw new AssertionError("Pattern quotes and unknown fields");
            if (!DateFormat.format("yyyy-MM-dd HH:mm", date).equals(DateFormat.format(new StringBuilder("yyyy-MM-dd HH:mm"), millis))
                    || !DateFormat.format("yyyy-MM-dd HH:mm", date).equals(DateFormat.format("yyyy-MM-dd HH:mm", new Date(millis)))
                    || date.getTimeInMillis() != millis) throw new AssertionError("Date/millis/calendar overloads");
            Calendar offset = new GregorianCalendar(TimeZone.getTimeZone("GMT+05:30")); offset.setTimeInMillis(millis);
            if (!"+0530".contentEquals(DateFormat.format("z", offset))) throw new AssertionError("Numeric timezone offset");
            offset.setTimeZone(TimeZone.getTimeZone("GMT-03:30"));
            if (!"-0330".contentEquals(DateFormat.format("z", offset))) throw new AssertionError("Negative timezone offset");
            Locale.setDefault(new Locale("ru", "RU"));
            if (!"февраля февраль".contentEquals(DateFormat.format("MMMM LLLL", date))) throw new AssertionError("Standalone month grammar");
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));
            if (!"٠٥".contentEquals(DateFormat.format("mm", date))) throw new AssertionError("Localized date digits");
            Locale.setDefault(Locale.US);
            Configuration c = new Configuration(original); c.setLocale(Locale.US); resources.updateConfiguration(c, null);
            if (DateFormat.is24HourFormat(context)) throw new AssertionError("US locale clock preference");
            c.setLocale(Locale.FRANCE); resources.updateConfiguration(c, null);
            if (!DateFormat.is24HourFormat(context)) throw new AssertionError("French locale clock preference");
            System.setProperty("shiraz.timeFormat", "12");
            if (DateFormat.is24HourFormat(context)) throw new AssertionError("Explicit 12-hour preference");
            String twelve = ((java.text.SimpleDateFormat)DateFormat.getTimeFormat(context)).toPattern();
            if (twelve.indexOf('h') < 0) throw new AssertionError("12-hour formatter pattern");
            System.setProperty("shiraz.timeFormat", "24");
            String twentyFour = ((java.text.SimpleDateFormat)DateFormat.getTimeFormat(context)).toPattern();
            if (!DateFormat.is24HourFormat(context) || twentyFour.indexOf('H') < 0) throw new AssertionError("24-hour formatter pattern");
            System.setProperty("shiraz.timeFormat", "invalid");
            try { DateFormat.is24HourFormat(context); throw new AssertionError("Invalid clock preference accepted"); }
            catch (IllegalArgumentException expected) { }
            System.clearProperty("shiraz.timeFormat");
            for (java.text.DateFormat formatter : new java.text.DateFormat[]{DateFormat.getDateFormat(context), DateFormat.getMediumDateFormat(context), DateFormat.getLongDateFormat(context)}) {
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                if (!formatter.format(new Date(millis)).contains("2024")) throw new AssertionError("Context date formatter locale");
            }
            String frenchPattern = DateFormat.getBestDateTimePattern(Locale.FRANCE, "yMd");
            if (frenchPattern.indexOf('d') > frenchPattern.indexOf('M')) throw new AssertionError("Locale date field order");
            try { DateFormat.format(new String(new char[1025]).replace('\0', 'H'), date); throw new AssertionError("Unbounded numeric width"); }
            catch (IllegalArgumentException expected) { }
        } finally {
            if (override == null) System.clearProperty("shiraz.timeFormat"); else System.setProperty("shiraz.timeFormat", override);
            Locale.setDefault(oldLocale); TimeZone.setDefault(oldZone); resources.updateConfiguration(original, null);
        }
        System.out.println("SHIRAZ_FIXTURE_DATE_FORMAT_OK");
    }
}
