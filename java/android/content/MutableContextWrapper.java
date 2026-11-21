// SPDX-License-Identifier: GPL-3.0-only
package android.content;

/** A ContextWrapper that allows the base context to be modified after creation. */
public class MutableContextWrapper extends ContextWrapper {
    public MutableContextWrapper(Context base) {
        super(base);
    }

    public void setBaseContext(Context base) {
        this.base = base;
    }
}
