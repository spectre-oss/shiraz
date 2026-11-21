// SPDX-License-Identifier: GPL-3.0-only
package android.system;

/** Mutable 64-bit signed integer out-parameter holder for POSIX system calls. */
public class Int64Ref {
    public long value;

    public Int64Ref(long value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "Int64Ref[value=" + value + "]";
    }
}
