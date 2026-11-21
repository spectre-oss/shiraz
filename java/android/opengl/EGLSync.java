// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;

/** Opaque handle to a native EGLSync object. */
public class EGLSync extends EGLObjectHandle {
    EGLSync(long handle) {
        super(handle);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EGLSync)) return false;
        return getNativeHandle() == ((EGLSync) o).getNativeHandle();
    }
}
