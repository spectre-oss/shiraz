// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.Color;
import java.util.Locale;

final class ColorFixture {
    static void check() {
        int[] samples = {0, -1, 0x12345678, 0x8001feff, 0x7fffffff, 0xff000000};
        for (int color : samples) {
            if (Color.argb(Color.alpha(color), Color.red(color), Color.green(color), Color.blue(color)) != color
                    || Color.rgb(Color.red(color), Color.green(color), Color.blue(color)) != (color | 0xff000000))
                throw new AssertionError("Packed color channels");
        }
        if (Color.parseColor("#0123aB") != 0xff0123ab || Color.parseColor("#8012Fe34") != 0x8012fe34
                || Color.parseColor("#00000000") != Color.TRANSPARENT)
            throw new AssertionError("Hex colors");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            if (Color.parseColor("WHITE") != Color.WHITE || Color.parseColor("LightGrey") != Color.LTGRAY
                    || Color.parseColor("aqua") != Color.CYAN || Color.parseColor("fuchsia") != Color.MAGENTA
                    || Color.parseColor("lime") != Color.GREEN || Color.parseColor("maroon") != 0xff800000
                    || Color.parseColor("navy") != 0xff000080 || Color.parseColor("olive") != 0xff808000
                    || Color.parseColor("purple") != 0xff800080 || Color.parseColor("silver") != 0xffc0c0c0
                    || Color.parseColor("teal") != 0xff008080)
                throw new AssertionError("Named colors independent of locale");
        } finally { Locale.setDefault(previous); }
        for (String bad : new String[]{"#fff", "#abcd", "#1234567", "#zzzzzz", "transparent", " red", "red "}) {
            try { Color.parseColor(bad); throw new AssertionError("Invalid color: " + bad); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("SHIRAZ_FIXTURE_COLOR_OK");
    }
}
