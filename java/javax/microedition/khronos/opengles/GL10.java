// SPDX-License-Identifier: GPL-3.0-only
package javax.microedition.khronos.opengles;
public interface GL10 extends GL {
    void glFlush();
    void glFinish();
    void glReadPixels(int x, int y, int width, int height, int format, int type, java.nio.Buffer pixels);
}
