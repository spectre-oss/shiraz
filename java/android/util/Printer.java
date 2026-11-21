// SPDX-License-Identifier: GPL-3.0-only
package android.util;

/** Simple line-oriented text output sink used by Looper, dumps, and debuggers. */
public interface Printer {
    void println(String x);
}
