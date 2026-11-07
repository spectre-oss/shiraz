// SPDX-License-Identifier: GPL-3.0-only
package javax.microedition.khronos.egl;
import javax.microedition.khronos.opengles.GL;
public abstract class EGLContext {
    public static EGL getEGL() { return HostEgl10.INSTANCE; }
    public abstract GL getGL();
}
