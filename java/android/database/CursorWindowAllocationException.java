// SPDX-License-Identifier: GPL-3.0-only
package android.database;

/** Thrown when a {@link CursorWindow} cannot allocate native or heap backing storage. */
public class CursorWindowAllocationException extends RuntimeException {
    public CursorWindowAllocationException(String description) {
        super(description);
    }
}
