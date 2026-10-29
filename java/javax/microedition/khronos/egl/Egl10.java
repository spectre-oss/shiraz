// SPDX-License-Identifier: GPL-3.0-only
package javax.microedition.khronos.egl;
import javax.microedition.khronos.opengles.GL;
/** Khronos EGL 1.0 entry points. This host has no multisample EGL config. */
final class Egl10 implements EGL11 {
    static final Egl10 INSTANCE = new Egl10();
    private int error = EGL_SUCCESS;
    static final class NoContext extends EGLContext { public GL getGL() { return null; } }
    static final class HostContext extends EGLContext {
        private final shiraz.runtime.HostGL10 gl = new shiraz.runtime.HostGL10();
        int version=1;
        long handle;
        public GL getGL() { return gl; }
    }
    static final class NoDisplay extends EGLDisplay {}
    static final class NoSurface extends EGLSurface {}
    static final class HostConfig extends EGLConfig {
        final int id, red, green, blue, alpha;
        HostConfig(int id, int red, int green, int blue, int alpha) { this.id = id; this.red = red; this.green = green; this.blue = blue; this.alpha = alpha; }
        int attribute(int name) {
            switch (name) {
                case EGL_RED_SIZE: return red;
                case EGL_GREEN_SIZE: return green;
                case EGL_BLUE_SIZE: return blue;
                case EGL_ALPHA_SIZE: return alpha;
                case EGL_BUFFER_SIZE: return red + green + blue + alpha;
                case EGL_DEPTH_SIZE: return 24;
                case EGL_STENCIL_SIZE: return 8;
                case EGL_SURFACE_TYPE: return EGL_WINDOW_BIT | EGL_PBUFFER_BIT;
                case EGL_RENDERABLE_TYPE: return 1 | 4 | 64;
                case EGL_CONFIG_ID: return id;
                case EGL_COLOR_BUFFER_TYPE: return EGL_RGB_BUFFER;
                case EGL_CONFIG_CAVEAT: return EGL_NONE;
                case EGL_NATIVE_RENDERABLE: return 1;
                case EGL_LEVEL: case EGL_SAMPLES: case EGL_SAMPLE_BUFFERS: case EGL_TRANSPARENT_TYPE: return name == EGL_TRANSPARENT_TYPE ? EGL_NONE : 0;
                default: return 0;
            }
        }
    }
    static final class WindowSurface extends EGLSurface {
        final android.view.Surface holder; final long window; final int width, height; final boolean owned;
        WindowSurface(android.view.Surface holder, long window, int width, int height, boolean owned) { this.holder = holder; this.window = window; this.width = width; this.height = height; this.owned = owned; }
    }
    private final HostConfig[] hostConfigs = {new HostConfig(1, 8, 8, 8, 8), new HostConfig(2, 8, 8, 8, 0), new HostConfig(3, 5, 6, 5, 0), new HostConfig(4, 4, 4, 4, 4), new HostConfig(5, 5, 5, 5, 1)};
    static final class Display extends EGLDisplay {}
    private final Display display = new Display();
    private final ThreadLocal<EGLContext> currentContext = new ThreadLocal<EGLContext>() { protected EGLContext initialValue() { return EGL_NO_CONTEXT; } };
    private final ThreadLocal<EGLSurface> currentDraw = new ThreadLocal<EGLSurface>() { protected EGLSurface initialValue() { return EGL_NO_SURFACE; } };
    public EGLDisplay eglGetDisplay(Object nativeDisplay) { error = EGL_SUCCESS; return display; }
    public boolean eglInitialize(EGLDisplay target, int[] version) {
        if (target != display) { error = EGL_BAD_DISPLAY; return false; }
        if (version != null && version.length >= 2) { version[0] = 1; version[1] = 4; }
        error = EGL_SUCCESS; return true;
    }
    public boolean eglChooseConfig(EGLDisplay target, int[] attribs, EGLConfig[] configs, int configSize, int[] numConfig) {
        if (target != display || numConfig == null || numConfig.length < 1) { error = EGL_BAD_PARAMETER; return false; }
        int found = 0;
        for (HostConfig candidate : hostConfigs) {
            if (!matches(candidate, attribs)) continue;
            if (configs != null && found < Math.min(configSize, configs.length)) configs[found] = candidate;
            found++;
        }
        numConfig[0] = configs == null ? found : Math.min(found, Math.min(configSize, configs.length));
        error = EGL_SUCCESS; return true;
    }
    private static boolean matches(HostConfig candidate, int[] attribs) {
        if (attribs == null) return true;
        for (int i = 0; i + 1 < attribs.length && attribs[i] != EGL_NONE; i += 2) {
            int name = attribs[i], wanted = attribs[i + 1];
            if (wanted == EGL_DONT_CARE) continue;
            switch (name) {
                case EGL_SURFACE_TYPE: case EGL_RENDERABLE_TYPE:
                    if ((candidate.attribute(name) & wanted) != wanted) return false;
                    break;
                case EGL_CONFIG_ID:
                    if (candidate.id != wanted) return false;
                    break;
                case EGL_RED_SIZE: case EGL_GREEN_SIZE: case EGL_BLUE_SIZE: case EGL_ALPHA_SIZE: case EGL_BUFFER_SIZE:
                case EGL_DEPTH_SIZE: case EGL_STENCIL_SIZE: case EGL_SAMPLES: case EGL_SAMPLE_BUFFERS:
                    if (candidate.attribute(name) < wanted) return false;
                    break;
                default:
                    break;
            }
        }
        return true;
    }
    public boolean eglGetConfigs(EGLDisplay target, EGLConfig[] configs, int configSize, int[] numConfig) { return eglChooseConfig(target, null, configs, configSize, numConfig); }
    public boolean eglGetConfigAttrib(EGLDisplay target, EGLConfig config, int attribute, int[] value) {
        if (!(config instanceof HostConfig) || value == null || value.length < 1) { error = EGL_BAD_CONFIG; return false; }
        value[0] = ((HostConfig) config).attribute(attribute);
        error = EGL_SUCCESS; return true;
    }
    public int eglGetError() { int current = error; error = EGL_SUCCESS; return current; }
    public String eglQueryString(EGLDisplay target, int name) { error = EGL_SUCCESS; return name == EGL_VENDOR ? "Shiraz" : name == EGL_VERSION ? "1.4" : ""; }
    public boolean eglTerminate(EGLDisplay target) { error = target == display ? EGL_SUCCESS : EGL_BAD_DISPLAY; return target == display; }
    public EGLContext eglCreateContext(EGLDisplay target, EGLConfig config, EGLContext share, int[] attribs) {
        if (target != display) { error = EGL_BAD_DISPLAY; return EGL_NO_CONTEXT; }
        HostContext context=new HostContext();
        if(attribs!=null)for(int i=0;i+1<attribs.length&&attribs[i]!=EGL_NONE;i+=2)if(attribs[i]==0x3098)context.version=attribs[i+1];
        if(context.version<1||context.version>3){error=EGL_BAD_ATTRIBUTE;return EGL_NO_CONTEXT;}
        error = EGL_SUCCESS; return context;
    }
    public EGLSurface eglCreatePbufferSurface(EGLDisplay target, EGLConfig config, int[] attribs) {
        if (target != display || !(config instanceof HostConfig)) { error = EGL_BAD_CONFIG; return EGL_NO_SURFACE; }
        int width = 1, height = 1;
        if (attribs != null) {
            for (int i = 0; i + 1 < attribs.length && attribs[i] != EGL_NONE; i += 2) {
                if (attribs[i] == EGL_WIDTH) width = attribs[i + 1];
                else if (attribs[i] == EGL_HEIGHT) height = attribs[i + 1];
            }
        }
        android.view.Surface surface = new android.view.Surface();
        long nativeWindow;
        try { nativeWindow = surface.shirazOffscreenWindow(width, height); }
        catch (RuntimeException failure) { error = EGL_BAD_ALLOC; return EGL_NO_SURFACE; }
        if (nativeWindow == 0) { error = EGL_BAD_ALLOC; return EGL_NO_SURFACE; }
        error = EGL_SUCCESS; return new WindowSurface(surface, nativeWindow, width, height, true);
    }
    public EGLSurface eglCreatePixmapSurface(EGLDisplay target, EGLConfig config, Object pixmap, int[] attribs) { error = EGL_BAD_NATIVE_PIXMAP; return EGL_NO_SURFACE; }
    public EGLSurface eglCreateWindowSurface(EGLDisplay target, EGLConfig config, Object window, int[] attribs) {
        if (target != display) { error = EGL_BAD_DISPLAY; return EGL_NO_SURFACE; }
        if (window instanceof android.view.SurfaceHolder) window = ((android.view.SurfaceHolder) window).getSurface();
        if (!(window instanceof android.view.Surface)) { error = EGL_BAD_NATIVE_WINDOW; return EGL_NO_SURFACE; }
        android.view.Surface surface = (android.view.Surface) window;
        long nativeWindow = surface.shirazNativeWindow();
        if (nativeWindow == 0) { error = EGL_BAD_NATIVE_WINDOW; return EGL_NO_SURFACE; }
        error = EGL_SUCCESS; return new WindowSurface(surface, nativeWindow, surface.shirazWidth(), surface.shirazHeight(), false);
    }
    public boolean eglDestroyContext(EGLDisplay target, EGLContext context) {
        if (!(context instanceof HostContext)) { error = EGL_BAD_CONTEXT; return false; }
        HostContext host = (HostContext) context;
        synchronized (host) { android.view.Surface.shirazEglDestroyContext(host.handle); host.handle = 0; }
        error = EGL_SUCCESS; return true;
    }
    public boolean eglDestroySurface(EGLDisplay target, EGLSurface surface) {
        if (!(surface instanceof WindowSurface)) { error = EGL_BAD_SURFACE; return false; }
        WindowSurface window = (WindowSurface) surface;
        if (currentDraw.get() == surface) { android.view.Surface.shirazEglRelease(); currentDraw.set(EGL_NO_SURFACE); currentContext.set(EGL_NO_CONTEXT); }
        if (window.owned) window.holder.release();
        error = EGL_SUCCESS; return true;
    }
    public EGLContext eglGetCurrentContext() { return currentContext.get(); }
    public EGLDisplay eglGetCurrentDisplay() { return currentContext.get() == EGL_NO_CONTEXT ? EGL_NO_DISPLAY : display; }
    public EGLSurface eglGetCurrentSurface(int readdraw) { return currentDraw.get(); }
    public boolean eglMakeCurrent(EGLDisplay target, EGLSurface draw, EGLSurface read, EGLContext context) {
        if (target != display) { error = EGL_BAD_DISPLAY; return false; }
        boolean release = draw == EGL_NO_SURFACE && read == EGL_NO_SURFACE && context == EGL_NO_CONTEXT;
        boolean window = draw instanceof WindowSurface && read == draw && context instanceof HostContext;
        if (!release && !window) { error = EGL_BAD_MATCH; return false; }
        if (release) {
            if (currentContext.get() != EGL_NO_CONTEXT) android.view.Surface.shirazEglRelease();
        } else {
            HostContext host = (HostContext) context;
            long handle;
            synchronized (host) {
                if (host.handle == 0) host.handle = android.view.Surface.shirazEglCreateContext(host.version);
                handle = host.handle;
            }
            if (handle == 0 || !((WindowSurface) draw).holder.shirazEglMakeCurrent(handle)) { error = EGL_BAD_ACCESS; return false; }
        }
        currentDraw.set(draw); currentContext.set(context); error = EGL_SUCCESS; return true;
    }
    public boolean eglQueryContext(EGLDisplay target, EGLContext context, int attribute, int[] value) { error = EGL_BAD_CONTEXT; return false; }
    public boolean eglQuerySurface(EGLDisplay target, EGLSurface surface, int attribute, int[] value) {
        if (!(surface instanceof WindowSurface) || value == null || value.length < 1) { error = EGL_BAD_SURFACE; return false; }
        WindowSurface window = (WindowSurface) surface;
        if (attribute == EGL_WIDTH) value[0] = window.width;
        else if (attribute == EGL_HEIGHT) value[0] = window.height;
        else { error = EGL_BAD_ATTRIBUTE; return false; }
        error = EGL_SUCCESS; return true;
    }
    public boolean eglSwapBuffers(EGLDisplay target, EGLSurface surface) {
        if (!(surface instanceof WindowSurface) || surface != currentDraw.get()) { error = EGL_BAD_SURFACE; return false; }
        if (!((WindowSurface) surface).holder.shirazEglSwap()) { error = EGL_BAD_SURFACE; return false; }
        error = EGL_SUCCESS; return true;
    }
    public boolean eglCopyBuffers(EGLDisplay target, EGLSurface surface, Object nativePixmap) { error = EGL_BAD_SURFACE; return false; }
    public boolean eglWaitGL() { return true; }
    public boolean eglWaitNative(int engine, Object bindTarget) { return true; }
}
