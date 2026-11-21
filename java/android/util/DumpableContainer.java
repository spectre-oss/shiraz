// SPDX-License-Identifier: GPL-3.0-only
package android.util;

/** Registry container for {@link Dumpable} diagnostic components. */
public interface DumpableContainer {
    boolean addDumpable(Dumpable dumpable);

    boolean removeDumpable(Dumpable dumpable);
}
