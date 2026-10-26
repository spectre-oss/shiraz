// SPDX-License-Identifier: GPL-3.0-only
package android.view;
public final class WindowSmoke {
    public static void main(String[] args) {
        Surface surface = new Surface();
        if (surface.shirazNativeWindow() != 0) throw new AssertionError("unconfigured window");
        surface.shirazConfigure(4, 3, android.graphics.PixelFormat.RGBA_8888);
        long first = surface.shirazNativeWindow();
        long second = surface.shirazNativeWindow();
        if (first == 0 || first != second) throw new AssertionError("native window identity");
        int[] posted = new int[]{0xff112233, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        if (!surface.shirazPostArgb(posted)) throw new AssertionError("window post");
        android.graphics.Bitmap shown = android.graphics.Bitmap.createBitmap(4, 3, android.graphics.Bitmap.Config.ARGB_8888);
        surface.shirazDraw(new android.graphics.Canvas(shown), 4, 3);
        if (shown.getPixel(0, 0) != 0xff112233) throw new AssertionError("posted pixel was not presented");
        shown.recycle();
        surface.release();
        if (surface.shirazNativeWindow() != 0) throw new AssertionError("released window");
        javax.microedition.khronos.egl.EGL10 egl = (javax.microedition.khronos.egl.EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();
        javax.microedition.khronos.egl.EGLDisplay display = egl.eglGetDisplay(javax.microedition.khronos.egl.EGL10.EGL_DEFAULT_DISPLAY);
        if (!egl.eglInitialize(display, null)) throw new AssertionError("EGL init");
        int[] count = new int[1];
        javax.microedition.khronos.egl.EGLConfig[] configs = new javax.microedition.khronos.egl.EGLConfig[1];
        if (!egl.eglChooseConfig(display, null, configs, 1, count) || count[0] != 1 || configs[0] == null) throw new AssertionError("config");
        if (!egl.eglGetConfigAttrib(display, configs[0], javax.microedition.khronos.egl.EGL10.EGL_DEPTH_SIZE, count) || count[0] != 24) throw new AssertionError("depth");
        if (egl.eglChooseConfig(display, new int[]{javax.microedition.khronos.egl.EGL10.EGL_SAMPLES, 4, javax.microedition.khronos.egl.EGL10.EGL_NONE}, null, 0, count) && count[0] != 0) throw new AssertionError("samples");
        surface = new Surface();
        if (egl.eglCreateWindowSurface(display, null, surface, null) != javax.microedition.khronos.egl.EGL10.EGL_NO_SURFACE) throw new AssertionError("unconfigured window surface");
        surface.shirazConfigure(4, 3, android.graphics.PixelFormat.RGBA_8888);
        javax.microedition.khronos.egl.EGLSurface window = egl.eglCreateWindowSurface(display, null, surface, null);
        javax.microedition.khronos.egl.EGLContext context = egl.eglCreateContext(display, null, javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT, null);
        if (window == javax.microedition.khronos.egl.EGL10.EGL_NO_SURFACE || context == javax.microedition.khronos.egl.EGL10.EGL_NO_CONTEXT) throw new AssertionError("window surface");
        int[] size = new int[1];
        if (!egl.eglQuerySurface(display, window, javax.microedition.khronos.egl.EGL10.EGL_WIDTH, size) || size[0] != 4) throw new AssertionError("window width");
        if (!egl.eglQuerySurface(display, window, javax.microedition.khronos.egl.EGL10.EGL_HEIGHT, size) || size[0] != 3) throw new AssertionError("window height");
        javax.microedition.khronos.egl.EGLSurface pbuffer = egl.eglCreatePbufferSurface(display, configs[0], new int[]{javax.microedition.khronos.egl.EGL10.EGL_WIDTH, 2, javax.microedition.khronos.egl.EGL10.EGL_HEIGHT, 2, javax.microedition.khronos.egl.EGL10.EGL_NONE});
        if (pbuffer == javax.microedition.khronos.egl.EGL10.EGL_NO_SURFACE) throw new AssertionError("pbuffer");
        if (!egl.eglQuerySurface(display, pbuffer, javax.microedition.khronos.egl.EGL10.EGL_WIDTH, size) || size[0] != 2) throw new AssertionError("pbuffer width");
        if (!egl.eglMakeCurrent(display, pbuffer, pbuffer, context) || egl.eglGetCurrentContext().getGL() == null) throw new AssertionError("pbuffer current");
        if (!egl.eglDestroySurface(display, pbuffer)) throw new AssertionError("pbuffer destroy");
        if (!egl.eglMakeCurrent(display, window, window, context) || egl.eglGetCurrentSurface(javax.microedition.khronos.egl.EGL10.EGL_DRAW) != window) throw new AssertionError("current window");
        if (!egl.eglSwapBuffers(display, window)) throw new AssertionError("swap");
        surface.release();
        System.out.println("NATIVE_WINDOW_OK");
    }
}
