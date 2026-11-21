// SPDX-License-Identifier: GPL-3.0-only
package android.util;

import java.io.PrintStream;

/** {@link Printer} implementation backed by a {@link PrintStream}. */
public class PrintStreamPrinter implements Printer {
    private final PrintStream mPS;

    public PrintStreamPrinter(PrintStream pw) {
        if (pw == null) {
            throw new NullPointerException("PrintStream must not be null");
        }
        mPS = pw;
    }

    @Override
    public void println(String x) {
        mPS.println(x);
    }
}
