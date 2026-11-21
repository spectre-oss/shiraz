// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import java.util.Iterator;

/** A Cursor-like iterator over Entity objects. */
public interface EntityIterator extends Iterator<Entity> {
    void reset();

    void close();
}
