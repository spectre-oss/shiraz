// SPDX-License-Identifier: GPL-3.0-only
package android.database;
public class CursorIndexOutOfBoundsException extends IndexOutOfBoundsException {
    public CursorIndexOutOfBoundsException(int index, int size) { super("Index " + index + " requested, size " + size); }
    public CursorIndexOutOfBoundsException(String message) { super(message); }
}
