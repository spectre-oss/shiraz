// SPDX-License-Identifier: GPL-3.0-only
package android.util;

/**
 * Primitive {@code int}-specialized {@link Property} that avoids boxing when animated via
 * {@link #setValue(Object, int)}.
 */
public abstract class IntProperty<T> extends Property<T, Integer> {
    public IntProperty(String name) {
        super(Integer.class, name);
    }

    public abstract void setValue(T object, int value);

    @Override
    public final void set(T object, Integer value) {
        setValue(object, value.intValue());
    }
}
