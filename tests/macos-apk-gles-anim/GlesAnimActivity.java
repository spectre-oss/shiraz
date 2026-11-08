// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.macosglesanim;

import android.app.Activity;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * M4b continuous animated fixture (build/macos/m4b/design/DESIGN-v2.md section 7.2).
 * One full-window ES 2 GLSurfaceView, RENDERMODE_CONTINUOUSLY, that never reads
 * pixels back. Frame n (1-based swap count) fills the window with
 * R = n & 0xff, G = (n >> 8) & 0xff, B = 0x40 | (popcount(n) & 0x3f), A = 255,
 * except an asymmetric corner pattern: a 64x64 px white block at the top left,
 * a 32x96 px magenta block at the bottom right and a 1 px cyan row at the top.
 * Each frame logs SHIRAZ_ZC_PRODUCED id=n t_ns=System.nanoTime().
 * SHIRAZ_TEST_ANIM_MODE: paced (default, one frame per 16.67 ms) or free.
 */
public class GlesAnimActivity extends Activity {
    static final String MODE = System.getenv("SHIRAZ_TEST_ANIM_MODE") == null ? "paced" : System.getenv("SHIRAZ_TEST_ANIM_MODE");

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!MODE.equals("paced") && !MODE.equals("free"))
            throw new IllegalArgumentException("SHIRAZ_TEST_ANIM_MODE must be paced or free (alpha, noclear, twopass: not implemented)");
        System.out.println("MACOS_GLES_ANIM_MODE " + MODE);
        GLSurfaceView view = new GLSurfaceView(this);
        view.setEGLContextClientVersion(2);
        view.setRenderer(new Renderer());
        view.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setContentView(view);
    }

    static final class Renderer implements GLSurfaceView.Renderer {
        int width, height, n;
        long next;

        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            System.out.println("MACOS_GLES_ANIM_SURFACE_CREATED");
        }

        public void onSurfaceChanged(GL10 gl, int width, int height) {
            this.width = width;
            this.height = height;
            GLES20.glViewport(0, 0, width, height);
            System.out.println("MACOS_GLES_ANIM_SIZE width=" + width + " height=" + height);
        }

        static void fill(int x, int y, int w, int h, int rgb) {
            GLES20.glScissor(x, y, w, h);
            GLES20.glClearColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        }

        public void onDrawFrame(GL10 gl) {
            if (MODE.equals("paced")) {
                long now = System.nanoTime();
                if (next == 0) next = now;
                if (next > now) {
                    try { Thread.sleep((next - now) / 1000000, (int) ((next - now) % 1000000)); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
                }
                next += 16666667L;
            }
            n++;
            int rgb = (n & 0xff) << 16 | ((n >> 8) & 0xff) << 8 | (0x40 | (Integer.bitCount(n) & 0x3f));
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
            GLES20.glClearColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            // GL window coordinates: origin at the bottom left.
            fill(0, height - 64, 64, 64, 0xffffff);
            fill(width - 32, 0, 32, 96, 0xff00ff);
            fill(0, height - 1, width, 1, 0x00ffff);
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
            System.out.println("SHIRAZ_ZC_PRODUCED id=" + n + " t_ns=" + System.nanoTime());
        }
    }
}
