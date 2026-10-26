// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;

final class ColorResourceFixture {
    static void check(Context context) {
        Resources resources = context.getResources(); context.setTheme(R.style.FixtureBase);
        if (context.getColor(R.color.accent) != 0xff123456 || resources.getColor(R.color.color_alias) != 0xff123456
                || resources.getColor(R.color.short_rgb, null) != 0xff112233 || context.getColor(R.color.theme_color) != 0xff112233
                || context.getColor(R.integer.not_string) != 42)
            throw new AssertionError("Compiled literal/reference/theme color values");
        if (context.getColor(R.color.selector_alpha_rounding) != 0xb3ffffff) throw new AssertionError("Android float alpha rounding");
        ColorStateList list = context.getColorStateList(R.color.selector_colors);
        if (!list.isStateful() || list.isOpaque() || list.getDefaultColor() != 0xff123456
                || list.getColorForState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed}, 0) != 0xff112233
                || list.getColorForState(new int[]{android.R.attr.state_enabled}, 0) != 0xff123456
                || list.getColorForState(new int[0], 0) != 0x20ff0000 || list.getColorForState(null, 0) != 0x20ff0000
                || context.getColor(R.color.selector_colors) != 0xff123456)
            throw new AssertionError("Selector order/default/positive-negative states/alpha");
        ColorStateList opaque = list.withAlpha(255);
        if (!opaque.isOpaque() || opaque.getColorForState(new int[0], 0) != 0xffff0000 || list.getColorForState(new int[0], 0) != 0x20ff0000)
            throw new AssertionError("Independent alpha replacement");
        if (context.getColorStateList(R.color.selector_nested).getColorForState(new int[]{android.R.attr.state_checked}, 0) != 0x80123456)
            throw new AssertionError("Nested selector default color");
        ContextThemeWrapper other = new ContextThemeWrapper(context, R.style.FixtureOverlay);
        if (other.getColor(R.color.theme_color) != 0xff445566 || other.getColorStateList(R.color.selector_colors)
                .getColorForState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed}, 0) != 0xff445566
                || context.getColor(R.color.theme_color) != 0xff112233)
            throw new AssertionError("Colors use the calling wrapper theme");
        Configuration original = resources.getConfiguration();
        try {
            Configuration night = new Configuration(original); night.uiMode = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES;
            resources.updateConfiguration(night, null);
            if (context.getColor(R.color.accent) != 0xffeeddcc || context.getColor(R.color.selector_colors) != 0xffeeddcc)
                throw new AssertionError("Configuration refresh in color references");
        } finally { resources.updateConfiguration(original, null); }
        if (context.getColor(R.color.primary_text_light) != 0xff010203 || context.getColor(android.R.color.primary_text_light) != 0xff000000)
            throw new AssertionError("Color XML loaded from wrong APK cookie");
        for (int invalid : new int[]{0, 0x7fffffff, R.string.welcome, R.color.selector_cycle_a}) {
            try { context.getColor(invalid); throw new AssertionError("Invalid/non-color/cyclic resource accepted"); }
            catch (Resources.NotFoundException expected) { }
        }
        try { context.getColorStateList(R.integer.not_string); throw new AssertionError("Non-color integer state list accepted"); }
        catch (Resources.NotFoundException expected) { }
        try { resources.getColor(R.color.theme_color, null); throw new AssertionError("Missing color theme accepted"); }
        catch (Resources.NotFoundException expected) { }
        int[][] states = {{android.R.attr.state_pressed}, {}}; int[] colors = {0xff001122, 0xff334455};
        ColorStateList created = new ColorStateList(states, colors); states[0][0] = android.R.attr.state_checked; colors[1] = 0;
        if (created.getDefaultColor() != 0xff334455 || created.getColorForState(new int[]{android.R.attr.state_pressed, 0}, 0) != 0xff001122)
            throw new AssertionError("State list constructor snapshot");
        ColorStateList single = ColorStateList.valueOf(0x80010203);
        if (single.isStateful() || single.isOpaque() || single.getDefaultColor() != 0x80010203
                || single.getColorForState(null, 0) != 0x80010203) throw new AssertionError("Literal color state list");
        ColorStateList onlyPressed = new ColorStateList(new int[][]{{android.R.attr.state_pressed}}, new int[]{0xff010203});
        if (onlyPressed.getColorForState(new int[0], 17) != 17) throw new AssertionError("State list fallback");
        System.out.println("SHIRAZ_FIXTURE_COLOR_RESOURCES_OK");
    }
}
