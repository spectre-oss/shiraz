// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

class ProbeChecks {
  static final class Skip extends Exception {
    Skip(String message) {
      super(message);
    }
  }
  static void check(boolean value) {
    if (!value)
      throw new AssertionError("Expected invariant was false");
  }
  static void eq(Object actual, Object expected) {
    if (!java.util.Objects.equals(actual, expected))
      throw new AssertionError("Expected " + expected + "; observed " + actual);
  }
  static String run(Context context, String id) throws Exception {
    return ProbeDispatch.execute(context, id);
  }
  static Bitmap bitmap() {
    return Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
  }
  static android.database.sqlite.SQLiteDatabase database(Context c) {
    File file = new File(c.getCacheDir(), "probe.db");
    android.database.sqlite.SQLiteDatabase.deleteDatabase(file);
    return android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null);
  }
  static String read(InputStream in) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int n;
    while ((n = in.read(buffer)) != -1) {
      check(bytes.size() + n <= 65536);
      bytes.write(buffer, 0, n);
    }
    return bytes.toString("UTF-8");
  }
  interface UiCheck {
    void run() throws Exception;
  }
  static void ui(Context c, UiCheck body) throws Exception {
    FutureTask<Void> task = new FutureTask<>(() -> {
      body.run();
      return null;
    });
    check(new android.os.Handler(android.os.Looper.getMainLooper()).post(task));
    try {
      task.get(2, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Error)
        throw (Error) cause;
      if (cause instanceof Exception)
        throw (Exception) cause;
      throw e;
    }
  }
  static void http(boolean unused) throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(2000);
      FutureTask<Void> serve = new FutureTask<>(() -> {
        try (Socket peer = server.accept()) {
          peer.setSoTimeout(2000);
          BufferedReader reader =
              new BufferedReader(new InputStreamReader(peer.getInputStream(), "US-ASCII"));
          String line;
          int length = 0;
          while ((line = reader.readLine()) != null && !line.isEmpty()) {
            length += line.length();
            check(length < 8192);
          }
          peer.getOutputStream().write(
              "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nprobe".getBytes(
                  "US-ASCII"));
        }
        return null;
      });
      Thread t = new Thread(serve, "probe-http");
      t.setDaemon(true);
      t.start();
      HttpURLConnection connection =
          (HttpURLConnection) new URL("http://127.0.0.1:" + server.getLocalPort() + "/probe")
              .openConnection();
      connection.setConnectTimeout(2000);
      connection.setReadTimeout(2000);
      try {
        check(connection.getResponseCode() == 200);
        try (InputStream in = connection.getInputStream()) {
          eq(read(in), "probe");
        }
        serve.get(3, TimeUnit.SECONDS);
      } finally {
        connection.disconnect();
      }
    }
  }
  static void egl() throws Exception {
    egl("clear");
  }
  static void egl(String mode) throws Exception {
    EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
    check(display != EGL14.EGL_NO_DISPLAY);
    int[] version = new int[2];
    check(EGL14.eglInitialize(display, version, 0, version, 1));
    EGLContext context = EGL14.EGL_NO_CONTEXT;
    EGLSurface surface = EGL14.EGL_NO_SURFACE;
    try {
      EGLConfig[] configs = new EGLConfig[1];
      int[] count = new int[1];
      check(EGL14.eglChooseConfig(display,
                new int[] {EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RENDERABLE_TYPE,
                    EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE},
                0, configs, 0, 1, count, 0)
          && count[0] > 0);
      context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
          new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
      check(context != EGL14.EGL_NO_CONTEXT);
      surface = EGL14.eglCreatePbufferSurface(display, configs[0],
          new int[] {EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE}, 0);
      check(surface != EGL14.EGL_NO_SURFACE
          && EGL14.eglMakeCurrent(display, surface, surface, context));
      GlChecks.run(mode);
    } finally {
      EGL14.eglMakeCurrent(
          display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
      if (surface != EGL14.EGL_NO_SURFACE)
        EGL14.eglDestroySurface(display, surface);
      if (context != EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroyContext(display, context);
      EGL14.eglTerminate(display);
    }
  }
}
