// SPDX-License-Identifier: GPL-3.0-only
package android.database;

/** Thrown when a closed or deactivated {@link Cursor} is accessed. */
public class StaleDataException extends RuntimeException {
    public StaleDataException() {}

    public StaleDataException(String description) {
        super(description);
    }
}
