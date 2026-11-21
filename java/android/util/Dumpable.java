// SPDX-License-Identifier: GPL-3.0-only
package android.util;

import java.io.PrintWriter;

/** Interface for components that can dump diagnostic state to a {@link PrintWriter}. */
public interface Dumpable {
    default String getDumpableName() {
        return getClass().getName();
    }

    void dump(PrintWriter writer, String[] args);
}
