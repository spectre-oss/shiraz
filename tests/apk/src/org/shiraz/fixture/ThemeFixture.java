// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import java.util.Locale;

final class ThemeFixture {
    private static int value(Resources.Theme theme, int attr) {
        TypedValue out = new TypedValue();
        if (!theme.resolveAttribute(attr, out, true)) throw new AssertionError("Missing theme value");
        return out.data;
    }
    static void check(Context context) {
        Resources resources = context.getResources();
        Resources.Theme base = resources.newTheme(), copy = resources.newTheme();
        TypedValue out = new TypedValue(); out.data = 123;
        if (base.resolveAttribute(R.attr.themeAccent, out, true) || out.data != 123) throw new AssertionError("Empty theme/output mutation");
        base.applyStyle(R.style.FixtureBase, true); copy.setTo(base);
        if (value(base, R.attr.themeAccent) != 0xff112233 || value(base, R.attr.themeAlias) != 0xff112233) throw new AssertionError("Style colors/attribute alias");
        base.applyStyle(R.style.FixtureOverlay, false);
        if (value(base, R.attr.themeAccent) != 0xff112233 || value(base, R.attr.themeCount) != 7) throw new AssertionError("Non-force style merge");
        base.applyStyle(R.style.FixtureOverlay, true);
        if (value(base, R.attr.themeAccent) != 0xff445566 || value(copy, R.attr.themeAccent) != 0xff112233) throw new AssertionError("Force merge / independent theme copy");
        if (!copy.resolveAttribute(R.attr.themeLabel, out, false) || out.type != TypedValue.TYPE_REFERENCE || out.data != R.string.localized)
            throw new AssertionError("Unresolved theme reference");
        if (!copy.resolveAttribute(R.attr.themeLabel, out, true) || out.type != TypedValue.TYPE_STRING || out.resourceId != R.string.localized
                || !resources.getString(R.string.localized).contentEquals(out.string) || out.assetCookie <= 0 || !out.string.equals(out.coerceToString()))
            throw new AssertionError("Resolved string/resource ID/cookie");
        for (int invalid : new int[]{R.string.app_name, 0x7fffffff}) {
            try { copy.applyStyle(invalid, true); throw new AssertionError("Unavailable or invalid style accepted"); }
            catch (Resources.NotFoundException expected) { }
        }
        Resources.Theme platform = resources.newTheme(); platform.applyStyle(R.style.FixtureMissingParent, true);
        if (!platform.resolveAttribute(android.R.attr.windowNoTitle, out, true) || out.type != TypedValue.TYPE_INT_BOOLEAN)
            throw new AssertionError("AOSP framework parent style");
        if (resources.getIdentifier("Theme.Material.Light.NoActionBar", "style", "android") != 0x01030241)
            throw new AssertionError("Stable framework resource ID");
        if (value(copy, R.attr.themeAccent) != 0xff112233) throw new AssertionError("Failed style changed theme");
        // More independent histories than the native snapshot retention budget.
        Resources.Theme[] independent = new Resources.Theme[80];
        for (int i=0;i<independent.length;i++) {
            Resources.Theme theme=resources.newTheme(); independent[i]=theme;
            for(int j=0;j<=i;j++)theme.applyStyle(R.style.FixtureBase,true);
            theme.applyStyle(R.style.FixtureOverlay,(i&1)==0);
            if(value(theme,R.attr.themeAccent)!=((i&1)==0?0xff445566:0xff112233))throw new AssertionError("Independent style history");
        }
        for(int i=independent.length-1;i>=0;i--)
            if(value(independent[i],R.attr.themeAccent)!=((i&1)==0?0xff445566:0xff112233))throw new AssertionError("Theme history after eviction");
        if(value(copy,R.attr.themeAccent)!=0xff112233)throw new AssertionError("Original theme after eviction");
        Configuration original = resources.getConfiguration();
        try {
            Configuration c = new Configuration(original); c.setLocale(Locale.FRENCH); resources.updateConfiguration(c, null);
            if (!copy.resolveAttribute(R.attr.themeLabel, out, true) || !"Langue française".contentEquals(out.string)) throw new AssertionError("Theme resource locale refresh");
            c.uiMode = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES; resources.updateConfiguration(c, null); copy.rebase();
            if (value(copy, R.attr.themeAccent) != 0xffabcdef) throw new AssertionError("Theme night-qualified style refresh");
        } finally { resources.updateConfiguration(original, null); }
        if (value(copy, R.attr.themeAccent) != 0xff112233) throw new AssertionError("Theme configuration restoration");
        context.setTheme(R.style.FixtureBase);
        if (context.getTheme() != context.getTheme() || value(context.getTheme(), R.attr.themeFlag) == 0) throw new AssertionError("Context theme state");
        ContextThemeWrapper wrapper = new ContextThemeWrapper(context, R.style.FixtureChild);
        if (value(wrapper.getTheme(), R.attr.themeFlag) != 0 || value(wrapper.getTheme(), R.attr.themeAccent) != 0xff112233
                || value(context.getTheme(), R.attr.themeFlag) == 0) throw new AssertionError("Wrapper theme inheritance/isolation");
        wrapper.setTheme(R.style.FixtureOverlay);
        if (value(wrapper.getTheme(), R.attr.themeAccent) != 0xff445566 || value(context.getTheme(), R.attr.themeAccent) != 0xff112233)
            throw new AssertionError("Wrapper style override isolation");
        if (new ContextThemeWrapper(context, copy).getTheme() != copy) throw new AssertionError("Provided theme instance");
        System.out.println("SHIRAZ_FIXTURE_THEME_OK");
    }
}
