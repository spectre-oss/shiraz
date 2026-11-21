// SPDX-License-Identifier: GPL-3.0-only
package android.util;

/** {@link Printer} implementation appending newline-terminated lines to a {@link StringBuilder}. */
public class StringBuilderPrinter implements Printer {
    private final StringBuilder mBuilder;

    public StringBuilderPrinter(StringBuilder builder) {
        if (builder == null) {
            throw new NullPointerException("StringBuilder must not be null");
        }
        mBuilder = builder;
    }

    @Override
    public void println(String x) {
        if (x == null) {
            mBuilder.append("null\n");
            return;
        }
        mBuilder.append(x);
        int len = x.length();
        if (len <= 0 || x.charAt(len - 1) != '\n') {
            mBuilder.append('\n');
        }
    }
}
