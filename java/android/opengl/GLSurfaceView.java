// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.util.ArrayList;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL;
import javax.microedition.khronos.opengles.GL10;
/**
 * Surface whose renderer runs on a dedicated GL thread. Like Android, the thread asks the configured chooser for a
 * config, the context factory for a context and the window-surface factory for a surface, then calls the renderer.
 */
public class GLSurfaceView extends SurfaceView implements SurfaceHolder.Callback2 {
    private static final String TAG = "GLSurfaceView";
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098, EGL_OPENGL_ES2_BIT = 4;
    public static final int RENDERMODE_WHEN_DIRTY = 0, RENDERMODE_CONTINUOUSLY = 1;
    public static final int DEBUG_CHECK_GL_ERROR = 1, DEBUG_LOG_GL_CALLS = 2;
    public interface Renderer {
        void onSurfaceCreated(GL10 gl, EGLConfig config);
        void onSurfaceChanged(GL10 gl, int width, int height);
        void onDrawFrame(GL10 gl);
    }
    public interface EGLConfigChooser { EGLConfig chooseConfig(EGL10 egl, EGLDisplay display); }
    public interface EGLContextFactory {
        EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config);
        void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context);
    }
    public interface EGLWindowSurfaceFactory {
        EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display, EGLConfig config, Object nativeWindow);
        void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface);
    }
    public interface GLWrapper { GL wrap(GL gl); }
    private final class ComponentChooser implements EGLConfigChooser {
        private final int[] spec;
        ComponentChooser(int red, int green, int blue, int alpha, int depth, int stencil) {
            spec = new int[]{EGL10.EGL_RED_SIZE, red, EGL10.EGL_GREEN_SIZE, green, EGL10.EGL_BLUE_SIZE, blue, EGL10.EGL_ALPHA_SIZE, alpha,
                EGL10.EGL_DEPTH_SIZE, depth, EGL10.EGL_STENCIL_SIZE, stencil, EGL10.EGL_RENDERABLE_TYPE, clientVersion >= 2 ? EGL_OPENGL_ES2_BIT : 1, EGL10.EGL_NONE};
        }
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[] count = new int[1];
            if (!egl.eglChooseConfig(display, spec, null, 0, count) || count[0] < 1) throw new IllegalArgumentException("No configs match configSpec");
            EGLConfig[] configs = new EGLConfig[count[0]];
            if (!egl.eglChooseConfig(display, spec, configs, configs.length, count)) throw new IllegalArgumentException("eglChooseConfig#2 failed");
            return configs[0];
        }
    }
    private final class DefaultContextFactory implements EGLContextFactory {
        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            int[] attributes = clientVersion != 0 ? new int[]{EGL_CONTEXT_CLIENT_VERSION, clientVersion, EGL10.EGL_NONE} : null;
            return egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attributes);
        }
        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) { egl.eglDestroyContext(display, context); }
    }
    private static final class DefaultWindowSurfaceFactory implements EGLWindowSurfaceFactory {
        public EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display, EGLConfig config, Object nativeWindow) {
            return egl.eglCreateWindowSurface(display, config, nativeWindow, null);
        }
        public void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface) { egl.eglDestroySurface(display, surface); }
    }
    private Renderer renderer;
    private EGLConfigChooser configChooser;
    private EGLContextFactory contextFactory;
    private EGLWindowSurfaceFactory windowSurfaceFactory;
    private GLWrapper wrapper;
    private int renderMode = RENDERMODE_CONTINUOUSLY, debugFlags, clientVersion;
    private boolean preserveContext;
    private final Object lock = new Object();
    private final ArrayList<Runnable> events = new ArrayList<Runnable>();
    private Thread glThread;
    private boolean paused, dirty = true, exit;
    private int width, height;
    public GLSurfaceView(Context context) { this(context, null); }
    public GLSurfaceView(Context context, AttributeSet attrs) { super(context, attrs); getHolder().addCallback(this); }
    public void setGLWrapper(GLWrapper value) { wrapper = value; }
    public void setDebugFlags(int flags) { debugFlags = flags; }
    public int getDebugFlags() { return debugFlags; }
    public void setPreserveEGLContextOnPause(boolean preserve) { preserveContext = preserve; }
    public boolean getPreserveEGLContextOnPause() { return preserveContext; }
    public void setRenderer(Renderer value) {
        if (value == null) throw new IllegalArgumentException("renderer");
        checkRenderThreadState();
        if (configChooser == null) configChooser = new ComponentChooser(8, 8, 8, 0, 16, 0);
        if (contextFactory == null) contextFactory = new DefaultContextFactory();
        if (windowSurfaceFactory == null) windowSurfaceFactory = new DefaultWindowSurfaceFactory();
        renderer = value;
    }
    public void setEGLContextFactory(EGLContextFactory factory) { checkRenderThreadState(); contextFactory = factory; }
    public void setEGLWindowSurfaceFactory(EGLWindowSurfaceFactory factory) { checkRenderThreadState(); windowSurfaceFactory = factory; }
    public void setEGLConfigChooser(EGLConfigChooser chooser) { checkRenderThreadState(); configChooser = chooser; }
    public void setEGLConfigChooser(boolean needDepth) { setEGLConfigChooser(new ComponentChooser(8, 8, 8, 0, needDepth ? 16 : 0, 0)); }
    public void setEGLConfigChooser(int red, int green, int blue, int alpha, int depth, int stencil) { setEGLConfigChooser(new ComponentChooser(red, green, blue, alpha, depth, stencil)); }
    public void setEGLContextClientVersion(int version) { checkRenderThreadState(); if (version < 0 || version > 3) throw new IllegalArgumentException("client version"); clientVersion = version; }
    private void checkRenderThreadState() { if (renderer != null) throw new IllegalStateException("setRenderer has already been called for this instance."); }
    public void setRenderMode(int mode) {
        if (mode != RENDERMODE_WHEN_DIRTY && mode != RENDERMODE_CONTINUOUSLY) throw new IllegalArgumentException("render mode");
        synchronized (lock) { renderMode = mode; lock.notifyAll(); }
    }
    public int getRenderMode() { synchronized (lock) { return renderMode; } }
    public void requestRender() { synchronized (lock) { dirty = true; lock.notifyAll(); } }
    public void surfaceCreated(SurfaceHolder holder) {}
    public void surfaceDestroyed(SurfaceHolder holder) { stopThread(); }
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (renderer == null || w < 1 || h < 1) return;
        synchronized (lock) {
            width = w; height = h; dirty = true;
            if (glThread == null) {
                exit = false;
                glThread = new Thread(new Runnable() { public void run() { guardedRun(); } }, "GLThread");
                glThread.setDaemon(true);
                glThread.start();
            }
            lock.notifyAll();
        }
    }
    public void surfaceRedrawNeededAsync(SurfaceHolder holder, Runnable finished) { requestRender(); if (finished != null) finished.run(); }
    public void surfaceRedrawNeeded(SurfaceHolder holder) { requestRender(); }
    public void onPause() { synchronized (lock) { paused = true; lock.notifyAll(); } }
    public void onResume() { synchronized (lock) { paused = false; dirty = true; lock.notifyAll(); } }
    public void queueEvent(Runnable runnable) {
        if (runnable == null) throw new IllegalArgumentException("event");
        synchronized (lock) { events.add(runnable); lock.notifyAll(); }
    }
    @Override protected void onDetachedFromWindow() { stopThread(); super.onDetachedFromWindow(); }
    private void stopThread() {
        Thread thread;
        synchronized (lock) { thread = glThread; glThread = null; exit = true; lock.notifyAll(); }
        if (thread != null && thread != Thread.currentThread()) {
            try { thread.join(2000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
    }
    private void guardedRun() {
        try { run(); }
        catch (Throwable failure) { Log.e(TAG, "GL thread failed", failure); }
    }
    private void run() throws InterruptedException {
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (display == EGL10.EGL_NO_DISPLAY) throw new RuntimeException("eglGetDisplay failed");
        if (!egl.eglInitialize(display, new int[2])) throw new RuntimeException("eglInitialize failed");
        EGLConfig config = configChooser.chooseConfig(egl, display);
        EGLContext context = contextFactory.createContext(egl, display, config);
        if (context == null || context == EGL10.EGL_NO_CONTEXT) throw new RuntimeException("createContext failed: " + egl.eglGetError());
        EGLSurface surface = windowSurfaceFactory.createWindowSurface(egl, display, config, getHolder());
        if (surface == null || surface == EGL10.EGL_NO_SURFACE) {
            Log.w(TAG, "createWindowSurface failed: " + egl.eglGetError());
            contextFactory.destroyContext(egl, display, context);
            return;
        }
        if (!egl.eglMakeCurrent(display, surface, surface, context)) Log.w(TAG, "eglMakeCurrent failed: " + egl.eglGetError());
        GL gl = context.getGL();
        if (wrapper != null) gl = wrapper.wrap(gl);
        GL10 gl10 = (GL10) gl;
        Renderer current = renderer;
        current.onSurfaceCreated(gl10, config);
        int appliedWidth = 0, appliedHeight = 0;
        try {
            while (true) {
                Runnable event = null;
                boolean draw = false;
                int w, h;
                synchronized (lock) {
                    while (true) {
                        if (exit) return;
                        if (!events.isEmpty()) { event = events.remove(0); break; }
                        if (!paused && width > 0 && (appliedWidth != width || appliedHeight != height || dirty || renderMode == RENDERMODE_CONTINUOUSLY)) {
                            draw = true; dirty = false; break;
                        }
                        lock.wait(renderMode == RENDERMODE_CONTINUOUSLY && !paused ? 16 : 0);
                    }
                    w = width; h = height;
                }
                if (event != null) { event.run(); continue; }
                if (!draw) continue;
                if (w != appliedWidth || h != appliedHeight) {
                    appliedWidth = w; appliedHeight = h;
                    current.onSurfaceChanged(gl10, w, h);
                }
                current.onDrawFrame(gl10);
                egl.eglSwapBuffers(display, surface);
                if (renderMode == RENDERMODE_CONTINUOUSLY) Thread.sleep(16);
            }
        } finally {
            egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            windowSurfaceFactory.destroySurface(egl, display, surface);
            contextFactory.destroyContext(egl, display, context);
        }
    }
    Renderer shirazRenderer() { return renderer; }
    int shirazClientVersion() { return clientVersion; }
}
