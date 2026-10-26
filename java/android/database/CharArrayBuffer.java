// SPDX-License-Identifier: GPL-3.0-only
package android.database;
public final class CharArrayBuffer {
    public char[] data;
    public int sizeCopied;
    public CharArrayBuffer(int size) { data = new char[size]; }
    public CharArrayBuffer(char[] data) { this.data = data; }
}
