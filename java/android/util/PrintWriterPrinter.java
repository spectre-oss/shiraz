// SPDX-License-Identifier: GPL-3.0-only
package android.util;

import java.io.PrintWriter;

/** {@link Printer} implementation backed by a {@link PrintWriter}. */
public class PrintWriterPrinter implements Printer {
    private final PrintWriter mPW;

    public PrintWriterPrinter(PrintWriter pw) {
        if (pw == null) {
            throw new NullPointerException("PrintWriter must not be null");
        }
        mPW = pw;
    }

    @Override
    public void println(String x) {
        mPW.println(x);
    }
}
