// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import javax.microedition.khronos.egl.EGL;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.opengles.GL;

/** Wraps {@link GL} and {@link EGL} instances with thread-affinity checks and call logging. */
public class GLDebugHelper {
    public static final int CONFIG_CHECK_GL_ERROR = 1;
    public static final int CONFIG_CHECK_THREAD = 2;
    public static final int CONFIG_LOG_ARGUMENT_NAMES = 4;
    public static final int ERROR_WRONG_THREAD = 0x7000;

    public GLDebugHelper() {}

    public static GL wrap(final GL gl, final int configFlags, final Writer log) {
        if (gl == null) {
            return null;
        }
        if (configFlags == 0 && log == null) {
            return gl;
        }
        final Thread boundThread = Thread.currentThread();
        Class<?>[] ifaces = collectInterfaces(gl.getClass(), GL.class);
        return (GL) Proxy.newProxyInstance(
                GL.class.getClassLoader(),
                ifaces,
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ((configFlags & CONFIG_CHECK_THREAD) != 0
                                && Thread.currentThread() != boundThread) {
                            throw new GLException(ERROR_WRONG_THREAD, "GL method called on wrong thread: " + method.getName());
                        }
                        if (log != null) {
                            logCall(log, method, args, (configFlags & CONFIG_LOG_ARGUMENT_NAMES) != 0);
                        }
                        try {
                            return method.invoke(gl, args);
                        } catch (InvocationTargetException e) {
                            throw e.getTargetException();
                        }
                    }
                });
    }

    public static EGL wrap(final EGL egl, final int configFlags, final Writer log) {
        if (egl == null) {
            return null;
        }
        if (configFlags == 0 && log == null) {
            return egl;
        }
        final Thread boundThread = Thread.currentThread();
        Class<?>[] ifaces = collectInterfaces(egl.getClass(), EGL.class);
        return (EGL) Proxy.newProxyInstance(
                EGL.class.getClassLoader(),
                ifaces,
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ((configFlags & CONFIG_CHECK_THREAD) != 0
                                && Thread.currentThread() != boundThread) {
                            throw new GLException(ERROR_WRONG_THREAD, "EGL method called on wrong thread: " + method.getName());
                        }
                        if (log != null) {
                            logCall(log, method, args, (configFlags & CONFIG_LOG_ARGUMENT_NAMES) != 0);
                        }
                        try {
                            Object result = method.invoke(egl, args);
                            if ((configFlags & CONFIG_CHECK_GL_ERROR) != 0
                                    && egl instanceof EGL10
                                    && !"eglGetError".equals(method.getName())) {
                                int err = ((EGL10) egl).eglGetError();
                                if (err != EGL10.EGL_SUCCESS) {
                                    throw new GLException(err, "EGL error after " + method.getName() + ": " + err);
                                }
                            }
                            return result;
                        } catch (InvocationTargetException e) {
                            throw e.getTargetException();
                        }
                    }
                });
    }

    private static Class<?>[] collectInterfaces(Class<?> type, Class<?> fallback) {
        ArrayList<Class<?>> out = new ArrayList<Class<?>>();
        for (Class<?> cur = type; cur != null; cur = cur.getSuperclass()) {
            Class<?>[] declared = cur.getInterfaces();
            for (int i = 0; i < declared.length; i++) {
                if (!out.contains(declared[i])) {
                    out.add(declared[i]);
                }
            }
        }
        if (out.isEmpty()) {
            out.add(fallback);
        }
        return out.toArray(new Class<?>[out.size()]);
    }

    private static void logCall(Writer log, Method method, Object[] args, boolean withNames) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(method.getName()).append('(');
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(", ");
                    if (withNames) sb.append("arg").append(i).append('=');
                    sb.append(String.valueOf(args[i]));
                }
            }
            sb.append(");\n");
            log.write(sb.toString());
            log.flush();
        } catch (IOException ignored) {
        }
    }
}
