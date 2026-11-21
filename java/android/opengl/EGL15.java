// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;

import java.nio.Buffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Virtual EGL 1.5 sync, image, and platform display tracking compatible with {@link EGL14}. */
public final class EGL15 {
    public static final int EGL_CONTEXT_MAJOR_VERSION = 0x3098;
    public static final int EGL_CONTEXT_MINOR_VERSION = 0x30FB;
    public static final int EGL_CONTEXT_OPENGL_PROFILE_MASK = 0x30FD;
    public static final int EGL_CONTEXT_OPENGL_RESET_NOTIFICATION_STRATEGY = 0x31BD;
    public static final int EGL_NO_RESET_NOTIFICATION = 0x31BE;
    public static final int EGL_LOSE_CONTEXT_ON_RESET = 0x31BF;
    public static final int EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT = 0x00000001;
    public static final int EGL_CONTEXT_OPENGL_COMPATIBILITY_PROFILE_BIT = 0x00000002;
    public static final int EGL_CONTEXT_OPENGL_DEBUG = 0x31B0;
    public static final int EGL_CONTEXT_OPENGL_FORWARD_COMPATIBLE = 0x31B1;
    public static final int EGL_CONTEXT_OPENGL_ROBUST_ACCESS = 0x31B2;
    public static final int EGL_OPENGL_ES3_BIT = 0x00000040;
    public static final int EGL_CL_EVENT_HANDLE = 0x309C;
    public static final int EGL_SYNC_CL_EVENT = 0x30FE;
    public static final int EGL_SYNC_CL_EVENT_COMPLETE = 0x30FF;
    public static final int EGL_SYNC_PRIOR_COMMANDS_COMPLETE = 0x30F0;
    public static final int EGL_SYNC_TYPE = 0x30F7;
    public static final int EGL_SYNC_STATUS = 0x30F1;
    public static final int EGL_SYNC_CONDITION = 0x30F8;
    public static final int EGL_SIGNALED = 0x30F2;
    public static final int EGL_UNSIGNALED = 0x30F3;
    public static final int EGL_SYNC_FLUSH_COMMANDS_BIT = 0x0001;
    public static final long EGL_FOREVER = -1L;
    public static final int EGL_TIMEOUT_EXPIRED = 0x30F5;
    public static final int EGL_CONDITION_SATISFIED = 0x30F6;
    public static final int EGL_SYNC_FENCE = 0x30F9;
    public static final int EGL_GL_COLORSPACE = 0x309D;
    public static final int EGL_GL_COLORSPACE_SRGB = 0x3089;
    public static final int EGL_GL_COLORSPACE_LINEAR = 0x308A;
    public static final int EGL_GL_RENDERBUFFER = 0x30B9;
    public static final int EGL_GL_TEXTURE_2D = 0x30B1;
    public static final int EGL_GL_TEXTURE_LEVEL = 0x30BC;
    public static final int EGL_GL_TEXTURE_3D = 0x30B2;
    public static final int EGL_GL_TEXTURE_ZOFFSET = 0x30BD;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_POSITIVE_X = 0x30B3;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_NEGATIVE_X = 0x30B4;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_POSITIVE_Y = 0x30B5;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_NEGATIVE_Y = 0x30B6;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_POSITIVE_Z = 0x30B7;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_NEGATIVE_Z = 0x30B8;
    public static final int EGL_IMAGE_PRESERVED = 0x30D2;
    public static final int EGL_PLATFORM_ANDROID_KHR = 0x3141;

    public static final EGLContext EGL_NO_CONTEXT = EGL14.EGL_NO_CONTEXT;
    public static final EGLDisplay EGL_NO_DISPLAY = EGL14.EGL_NO_DISPLAY;
    public static final EGLSurface EGL_NO_SURFACE = EGL14.EGL_NO_SURFACE;
    public static final EGLSync EGL_NO_SYNC = new EGLSync(0L);
    public static final EGLImage EGL_NO_IMAGE = new EGLImage(0L);

    private static final AtomicLong NEXT_HANDLE = new AtomicLong(4096L);
    private static final Map<Long, Integer> SYNC_TYPES =
            Collections.synchronizedMap(new HashMap<Long, Integer>());
    private static final Map<Long, Boolean> IMAGES =
            Collections.synchronizedMap(new HashMap<Long, Boolean>());

    private EGL15() {}

    private static boolean validDisplay(EGLDisplay dpy) {
        return dpy != null && !EGL_NO_DISPLAY.equals(dpy);
    }

    public static EGLSync eglCreateSync(
            EGLDisplay dpy, int type, long[] attrib_list, int offset) {
        if (!validDisplay(dpy)) {
            return EGL_NO_SYNC;
        }
        if (attrib_list != null && (offset < 0 || offset >= attrib_list.length)) {
            throw new IllegalArgumentException("attrib_list offset out of bounds");
        }
        long id = NEXT_HANDLE.getAndIncrement();
        SYNC_TYPES.put(Long.valueOf(id), Integer.valueOf(type));
        return new EGLSync(id);
    }

    public static boolean eglGetSyncAttrib(
            EGLDisplay dpy, EGLSync sync, int attribute, long[] value, int offset) {
        if (!validDisplay(dpy) || sync == null || !SYNC_TYPES.containsKey(Long.valueOf(sync.getNativeHandle()))) {
            return false;
        }
        if (value == null || offset < 0 || offset >= value.length) {
            throw new IllegalArgumentException("value array out of bounds");
        }
        Integer type = SYNC_TYPES.get(Long.valueOf(sync.getNativeHandle()));
        if (attribute == EGL_SYNC_TYPE) {
            value[offset] = type != null ? type.intValue() : EGL_SYNC_FENCE;
            return true;
        } else if (attribute == EGL_SYNC_STATUS) {
            value[offset] = EGL_SIGNALED;
            return true;
        } else if (attribute == EGL_SYNC_CONDITION) {
            value[offset] = EGL_SYNC_PRIOR_COMMANDS_COMPLETE;
            return true;
        }
        return false;
    }

    public static boolean eglDestroySync(EGLDisplay dpy, EGLSync sync) {
        if (!validDisplay(dpy) || sync == null) {
            return false;
        }
        return SYNC_TYPES.remove(Long.valueOf(sync.getNativeHandle())) != null;
    }

    public static int eglClientWaitSync(
            EGLDisplay dpy, EGLSync sync, int flags, long timeout) {
        if (!validDisplay(dpy) || sync == null || !SYNC_TYPES.containsKey(Long.valueOf(sync.getNativeHandle()))) {
            return 0;
        }
        return EGL_CONDITION_SATISFIED;
    }

    public static EGLDisplay eglGetPlatformDisplay(
            int platform, long native_display, long[] attrib_list, int offset) {
        if (attrib_list != null && (offset < 0 || offset >= attrib_list.length)) {
            throw new IllegalArgumentException("attrib_list offset out of bounds");
        }
        return EGL14.eglGetDisplay((int) native_display);
    }

    public static EGLSurface eglCreatePlatformWindowSurface(
            EGLDisplay dpy, EGLConfig config, Buffer native_window, long[] attrib_list, int offset) {
        return EGL14.eglCreateWindowSurface(dpy, config, native_window, null, 0);
    }

    public static EGLSurface eglCreatePlatformPixmapSurface(
            EGLDisplay dpy, EGLConfig config, Buffer native_pixmap, long[] attrib_list, int offset) {
        if (!validDisplay(dpy)) {
            return EGL_NO_SURFACE;
        }
        return EGL14.eglCreatePbufferSurface(dpy, config, null, 0);
    }

    public static boolean eglWaitSync(EGLDisplay dpy, EGLSync sync, int flags) {
        return validDisplay(dpy) && sync != null && SYNC_TYPES.containsKey(Long.valueOf(sync.getNativeHandle()));
    }

    public static EGLImage eglCreateImage(
            EGLDisplay dpy, EGLContext ctx, int target, long buffer, long[] attrib_list, int offset) {
        if (!validDisplay(dpy)) {
            return EGL_NO_IMAGE;
        }
        if (attrib_list != null && (offset < 0 || offset >= attrib_list.length)) {
            throw new IllegalArgumentException("attrib_list offset out of bounds");
        }
        long id = NEXT_HANDLE.getAndIncrement();
        IMAGES.put(Long.valueOf(id), Boolean.TRUE);
        return new EGLImage(id);
    }

    public static boolean eglDestroyImage(EGLDisplay dpy, EGLImage image) {
        if (!validDisplay(dpy) || image == null) {
            return false;
        }
        return IMAGES.remove(Long.valueOf(image.getNativeHandle())) != null;
    }
}
