// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;

import android.text.TextUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Pattern;

final class TextUtilsFixture {
    // Avoid accidentally relying on String.equals, subSequence or toString.
    private static CharSequence chars(final String text) {
        return new CharSequence() {
            public int length() { return text.length(); }
            public char charAt(int index) { return text.charAt(index); }
            public CharSequence subSequence(int a, int b) { throw new AssertionError("subSequence"); }
            public String toString() { throw new AssertionError("toString"); }
        };
    }
    static void check() {
        if (!TextUtils.isEmpty(null) || !TextUtils.isEmpty(chars("")) || TextUtils.isEmpty(chars("x")))
            throw new AssertionError("Empty text");
        if (!TextUtils.equals(null, null) || !TextUtils.equals(chars("café"), new StringBuilder("café"))
                || TextUtils.equals(null, "") || TextUtils.equals("ab", chars("ac")) || TextUtils.equals("a", "aa"))
            throw new AssertionError("Character equality");
        if (TextUtils.getTrimmedLength(chars("\t a \n")) != 1 || TextUtils.getTrimmedLength(chars(" \t")) != 0
                || TextUtils.getTrimmedLength("\u00a0") != 1) throw new AssertionError("ASCII trimming");
        if (!TextUtils.isDigitsOnly(chars("")) || !TextUtils.isDigitsOnly(chars("12\u0661\ud835\udfce"))
                || TextUtils.isDigitsOnly("1.2") || TextUtils.isDigitsOnly("\ud835"))
            throw new AssertionError("Unicode digits");
        if (!"1|null|x".equals(TextUtils.join("|", Arrays.asList(1, null, "x")))
                || !"".equals(TextUtils.join(",", Collections.emptyList()))
                || !"anullb".equals(TextUtils.join(null, new Object[]{"a", "b"})))
            throw new AssertionError("Text joining");
        if (TextUtils.split("", "[").length != 0 || TextUtils.split("", Pattern.compile(",")).length != 0
                || !Arrays.equals(new String[]{"", "a", "", ""}, TextUtils.split(",a,,", ","))
                || !Arrays.equals(new String[]{"a", ""}, TextUtils.split("a|", Pattern.compile("\\|"))))
            throw new AssertionError("Split trailing empties");
        char[] output = new char[]{'_', '_', '_', '_', '_'};
        TextUtils.getChars(chars("café"), 1, 4, output, 1);
        if (!"_afé_".equals(new String(output)) || !"afé".equals(TextUtils.substring(chars("café"), 1, 4)))
            throw new AssertionError("Character slices");
        try { TextUtils.getChars("abcdef", 0, 6, output, 0); throw new AssertionError("Destination bounds"); }
        catch (IndexOutOfBoundsException expected) { }
        if (!"_afé_".equals(new String(output))) throw new AssertionError("Partial invalid copy");
        try { TextUtils.substring(chars("abc"), 2, 1); throw new AssertionError("Source bounds"); }
        catch (IndexOutOfBoundsException expected) { }
        System.out.println("SHIRAZ_FIXTURE_TEXT_UTILS_OK");
    }
}
