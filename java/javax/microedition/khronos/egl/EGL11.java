// SPDX-License-Identifier: GPL-3.0-only
package javax.microedition.khronos.egl;
/** Android's EGL 1.1 marker adds the context-loss error to the EGL10 API. */
public interface EGL11 extends EGL10 {
    int EGL_CONTEXT_LOST = 0x300e;
}
