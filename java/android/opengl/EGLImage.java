// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;

/** Opaque handle to a native EGLImage object. */
public class EGLImage extends EGLObjectHandle {
    EGLImage(long handle) {
        super(handle);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EGLImage)) return false;
        return getNativeHandle() == ((EGLImage) o).getNativeHandle();
    }
}
