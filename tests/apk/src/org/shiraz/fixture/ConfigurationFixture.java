// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;
import android.util.DisplayMetrics;
import java.util.Locale;

final class ConfigurationFixture {
    static void check(Resources resources) {
        Configuration original = resources.getConfiguration(); DisplayMetrics display = resources.getDisplayMetrics();
        if (original.locale == null || original.getLocales().size() != 1 || !original.locale.equals(original.getLocales().get(0))
                || original.orientation != Configuration.ORIENTATION_PORTRAIT || original.screenWidthDp != display.widthPixels
                || original.screenHeightDp != display.heightPixels || original.densityDpi != 160 || display.density != 1 || display.scaledDensity != 1)
            throw new AssertionError("Initial resource configuration/metrics");
        Locale[] input = {Locale.FRENCH, Locale.ENGLISH, Locale.FRENCH};
        LocaleList list = new LocaleList(input); input[0] = Locale.GERMAN;
        if (list.size() != 2 || !list.get(0).equals(Locale.FRENCH) || list.get(-1) != null || list.get(5) != null
                || list.indexOf(Locale.ENGLISH) != 1 || list.indexOf(Locale.GERMAN) != -1 || !"fr,en".equals(list.toLanguageTags())
                || !list.equals(LocaleList.forLanguageTags("fr,en")) || !LocaleList.getEmptyLocaleList().isEmpty())
            throw new AssertionError("Immutable locale list");
        try { new LocaleList((Locale)null); throw new AssertionError("Null locale accepted"); } catch (NullPointerException expected) { }
        Configuration copy = new Configuration(original); copy.locale = Locale.FRENCH;
        if (!original.equals(new Configuration(original)) || original.hashCode() != new Configuration(original).hashCode())
            throw new AssertionError("Configuration copy equality");
        if (!copy.getLocales().get(0).equals(Locale.FRENCH) || !resources.getConfiguration().locale.equals(original.locale))
            throw new AssertionError("Legacy locale synchronization / configuration snapshot");
        DisplayMetrics detached = resources.getDisplayMetrics(); detached.density = 99;
        if (resources.getDisplayMetrics().density != 1) throw new AssertionError("Metrics snapshot alias");
        try {
            resources.updateConfiguration(copy, display);
            if (!"Langue française".equals(resources.getString(R.string.localized)) || !"1,5".equals(resources.getString(R.string.decimal_probe, 1.5)))
                throw new AssertionError("Native locale selection / formatted locale");
            Configuration alternate = resources.getConfiguration(); alternate.setLocale(Locale.ENGLISH);
            alternate.orientation = Configuration.ORIENTATION_LANDSCAPE; alternate.uiMode = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES;
            alternate.densityDpi = 320; alternate.fontScale = 1.25f;
            alternate.screenWidthDp = Math.max(1, display.widthPixels / 2); alternate.screenHeightDp = Math.max(1, display.heightPixels / 2);
            alternate.smallestScreenWidthDp = Math.min(alternate.screenWidthDp, alternate.screenHeightDp);
            resources.updateConfiguration(alternate, null);
            if (!"Default language".equals(resources.getString(R.string.localized)) || !"1.5".equals(resources.getString(R.string.decimal_probe, 1.5))
                    || !"landscape-selected".equals(resources.getString(R.string.orientation_probe)) || !"night-selected".equals(resources.getString(R.string.night_probe))
                    || resources.getDisplayMetrics().density != 2 || resources.getDisplayMetrics().scaledDensity != 2.5f)
                throw new AssertionError("Configuration qualifiers / density scaling");
            Configuration rtl = resources.getConfiguration(); rtl.setLocale(Locale.forLanguageTag("ar"));
            if (rtl.getLayoutDirection() != 1 || android.text.TextUtils.getLayoutDirectionFromLocale(Locale.forLanguageTag("ar-Latn")) != 0
                    || android.text.TextUtils.getLayoutDirectionFromLocale(Locale.FRENCH) != 0 || android.text.TextUtils.getLayoutDirectionFromLocale(null) != 0)
                throw new AssertionError("ICU script layout direction");
            resources.updateConfiguration(rtl, null);
            if (resources.getConfiguration().getLayoutDirection() != 1) throw new AssertionError("RTL configuration");
            Configuration invalid = resources.getConfiguration(); invalid.fontScale = Float.NaN;
            try { resources.updateConfiguration(invalid, null); throw new AssertionError("Invalid font scale accepted"); }
            catch (IllegalArgumentException expected) { }
            invalid = resources.getConfiguration(); invalid.setLocales(list);
            try { resources.updateConfiguration(invalid, null); throw new AssertionError("Unsupported fallback list accepted"); }
            catch (UnsupportedOperationException expected) { }
            DisplayMetrics resize = resources.getDisplayMetrics(); resize.widthPixels++;
            try { resources.updateConfiguration(null, resize); throw new AssertionError("Window resize through Resources accepted"); }
            catch (UnsupportedOperationException expected) { }
            if (!resources.getConfiguration().locale.getLanguage().equals("ar") || resources.getDisplayMetrics().density != 2)
                throw new AssertionError("Rejected update partially changed state");
        } finally { resources.updateConfiguration(original, display); }
        if (!resources.getConfiguration().locale.equals(original.locale) || resources.getDisplayMetrics().density != 1
                || !"portrait-default".equals(resources.getString(R.string.orientation_probe)) || !"day-default".equals(resources.getString(R.string.night_probe)))
            throw new AssertionError("Configuration restoration / cache invalidation");
        System.out.println("SHIRAZ_FIXTURE_CONFIGURATION_OK");
    }
}
