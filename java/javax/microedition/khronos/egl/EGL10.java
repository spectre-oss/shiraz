// SPDX-License-Identifier: GPL-3.0-only
package javax.microedition.khronos.egl;
public interface EGL10 extends EGL {
    int EGL_ALPHA_FORMAT = 12424, EGL_ALPHA_MASK_SIZE = 12350, EGL_ALPHA_SIZE = 12321, EGL_BAD_ACCESS = 12290, EGL_BAD_ALLOC = 12291, EGL_BAD_ATTRIBUTE = 12292, EGL_BAD_CONFIG = 12293, EGL_BAD_CONTEXT = 12294, EGL_BAD_CURRENT_SURFACE = 12295, EGL_BAD_DISPLAY = 12296, EGL_BAD_MATCH = 12297, EGL_BAD_NATIVE_PIXMAP = 12298, EGL_BAD_NATIVE_WINDOW = 12299, EGL_BAD_PARAMETER = 12300, EGL_BAD_SURFACE = 12301, EGL_BLUE_SIZE = 12322, EGL_BUFFER_SIZE = 12320, EGL_COLORSPACE = 12423, EGL_COLOR_BUFFER_TYPE = 12351, EGL_CONFIG_CAVEAT = 12327, EGL_CONFIG_ID = 12328, EGL_CORE_NATIVE_ENGINE = 12379, EGL_DEPTH_SIZE = 12325, EGL_DONT_CARE = -1, EGL_DRAW = 12377, EGL_EXTENSIONS = 12373, EGL_GREEN_SIZE = 12323, EGL_HEIGHT = 12374, EGL_HORIZONTAL_RESOLUTION = 12432, EGL_LARGEST_PBUFFER = 12376, EGL_LEVEL = 12329, EGL_LUMINANCE_BUFFER = 12431, EGL_LUMINANCE_SIZE = 12349, EGL_MAX_PBUFFER_HEIGHT = 12330, EGL_MAX_PBUFFER_PIXELS = 12331, EGL_MAX_PBUFFER_WIDTH = 12332, EGL_NATIVE_RENDERABLE = 12333, EGL_NATIVE_VISUAL_ID = 12334, EGL_NATIVE_VISUAL_TYPE = 12335, EGL_NONE = 12344, EGL_NON_CONFORMANT_CONFIG = 12369, EGL_NOT_INITIALIZED = 12289, EGL_PBUFFER_BIT = 1, EGL_PIXEL_ASPECT_RATIO = 12434, EGL_PIXMAP_BIT = 2, EGL_READ = 12378, EGL_RED_SIZE = 12324, EGL_RENDERABLE_TYPE = 12352, EGL_RENDER_BUFFER = 12422, EGL_RGB_BUFFER = 12430, EGL_SAMPLES = 12337, EGL_SAMPLE_BUFFERS = 12338, EGL_SINGLE_BUFFER = 12421, EGL_SLOW_CONFIG = 12368, EGL_STENCIL_SIZE = 12326, EGL_SUCCESS = 12288, EGL_SURFACE_TYPE = 12339, EGL_TRANSPARENT_BLUE_VALUE = 12341, EGL_TRANSPARENT_GREEN_VALUE = 12342, EGL_TRANSPARENT_RED_VALUE = 12343, EGL_TRANSPARENT_RGB = 12370, EGL_TRANSPARENT_TYPE = 12340, EGL_VENDOR = 12371, EGL_VERSION = 12372, EGL_VERTICAL_RESOLUTION = 12433, EGL_WIDTH = 12375, EGL_WINDOW_BIT = 4;
    Object EGL_DEFAULT_DISPLAY = null;
    EGLContext EGL_NO_CONTEXT = new Egl10.NoContext();
    EGLDisplay EGL_NO_DISPLAY = new Egl10.NoDisplay();
    EGLSurface EGL_NO_SURFACE = new Egl10.NoSurface();
    boolean eglChooseConfig(EGLDisplay display, int[] attribs, EGLConfig[] configs, int configSize, int[] numConfig);
    boolean eglCopyBuffers(EGLDisplay display, EGLSurface surface, Object nativePixmap);
    EGLContext eglCreateContext(EGLDisplay display, EGLConfig config, EGLContext share, int[] attribs);
    EGLSurface eglCreatePbufferSurface(EGLDisplay display, EGLConfig config, int[] attribs);
    EGLSurface eglCreatePixmapSurface(EGLDisplay display, EGLConfig config, Object pixmap, int[] attribs);
    EGLSurface eglCreateWindowSurface(EGLDisplay display, EGLConfig config, Object window, int[] attribs);
    boolean eglDestroyContext(EGLDisplay display, EGLContext context);
    boolean eglDestroySurface(EGLDisplay display, EGLSurface surface);
    boolean eglGetConfigAttrib(EGLDisplay display, EGLConfig config, int attribute, int[] value);
    boolean eglGetConfigs(EGLDisplay display, EGLConfig[] configs, int configSize, int[] numConfig);
    EGLContext eglGetCurrentContext();
    EGLDisplay eglGetCurrentDisplay();
    EGLSurface eglGetCurrentSurface(int readdraw);
    EGLDisplay eglGetDisplay(Object nativeDisplay);
    int eglGetError();
    boolean eglInitialize(EGLDisplay display, int[] majorMinor);
    boolean eglMakeCurrent(EGLDisplay display, EGLSurface draw, EGLSurface read, EGLContext context);
    boolean eglQueryContext(EGLDisplay display, EGLContext context, int attribute, int[] value);
    String eglQueryString(EGLDisplay display, int name);
    boolean eglQuerySurface(EGLDisplay display, EGLSurface surface, int attribute, int[] value);
    boolean eglSwapBuffers(EGLDisplay display, EGLSurface surface);
    boolean eglTerminate(EGLDisplay display);
    boolean eglWaitGL();
    boolean eglWaitNative(int engine, Object bindTarget);
}
