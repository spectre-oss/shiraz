// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.macosgles;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.pm.ConfigurationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.opengles.GL10;

/**
 * OpenGL ES through GLSurfaceView beside ordinary View content, in a 360x640 dp
 * frame. Geometry and colours match the gles profile of
 * scripts/macos_runtime_smoke.py.
 *
 * <p>An ES 2 GLSurfaceView (setEGLContextClientVersion(2), renderer on its own
 * GL thread as on Android) clears, draws a shader triangle from a VBO bound
 * through a GLES30 vertex array object, and checks glReadPixels exactly. A
 * second GLSurfaceView with the default (ES 1) context clears and draws a
 * GLUtils-uploaded texture through the javax GL10 interface; Shiraz has no
 * android.opengl.GLES10 class, so GL10 is the GLES1 entry point it exposes.
 * A coloured View and a TextView exercise the Canvas path next to them.
 *
 * <p>Every stage prints a marker. A renderer failure prints MACOS_GLES_FAIL and
 * is rethrown on the UI thread by the status View, so the run exits nonzero
 * (Shiraz's GLSurfaceView only logs GL thread failures). No lambdas or method
 * references: the darwin ART interpreter does not run invoke-custom.
 */
public final class GlesActivity extends Activity {
    static final int BACKGROUND = 0xff101820;
    static final int TEXT_COLOR = 0xffffffff;
    static final int VIEW_COLOR = 0xff7b1fa2;
    /** Red-dominant clear and green-dominant triangle: an RGBA/BGRA swizzle changes both. */
    static final int GLES2_CLEAR = 0xffc02040;
    static final int GLES2_TRIANGLE = 0xff30d050;
    static final int GLES1_CLEAR = 0xff2050e0;
    static final int GLES1_TEXTURE = 0xfff0c020;
    static final String TEXT = "Shiraz GLES";
    /** Triangle in normalized device coordinates, apex up. */
    static final float[] TRIANGLE = {-0.6f, -0.6f, 0.6f, -0.6f, 0f, 0.6f};
    /** Textured quad in the top-left quadrant of the GLES1 surface. */
    static final float[] QUAD = {-0.75f, 0f, 0f, 0f, 0f, 0.75f, -0.75f, 0.75f};
    static final float[] QUAD_UV = {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f};
    /** (x, y) in normalized device coordinates and the colour expected there. */
    static final float[][] GLES2_PROBES = {
        {0f, 0f, 1}, {0.4f, -0.45f, 1}, {0.4f, 0.45f, 0}, {0f, 0.8f, 0}, {0f, -0.8f, 0},
        {-0.95f, -0.95f, 0}, {0.95f, 0.95f, 0}};
    static final float[][] GLES1_PROBES = {
        {-0.375f, 0.375f, 1}, {-0.375f, -0.375f, 0}, {0.375f, 0.375f, 0}, {-0.95f, -0.95f, 0}, {0.95f, 0.95f, 0}};
    static final int GL_VENDOR = 0x1f00, GL_RENDERER = 0x1f01, GL_VERSION = 0x1f02;
    static final int GL_TEXTURE_2D = 0x0de1, GL_NEAREST = 0x2600;
    static final int GL_TEXTURE_MAG_FILTER = 0x2800, GL_TEXTURE_MIN_FILTER = 0x2801;
    static final int GL_VERTEX_ARRAY = 0x8074, GL_TEXTURE_COORD_ARRAY = 0x8078;
    static final int GL_MODELVIEW = 0x1700, GL_PROJECTION = 0x1701;

    /** First renderer failure; rethrown by StatusView on the UI thread. */
    static volatile Throwable failure;
    StatusView status;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        float density = getResources().getDisplayMetrics().density;
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ConfigurationInfo info = manager.getDeviceConfigurationInfo();
        System.out.println("MACOS_GLES_DEVICE_CONFIG req_gl_es_version=0x" + Integer.toHexString(info.reqGlEsVersion)
                + " gl_es_version=" + info.getGlEsVersion());

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BACKGROUND);
        TextView text = new TextView(this);
        text.setText(TEXT);
        text.setTextColor(TEXT_COLOR);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28 * density);
        text.setIncludeFontPadding(false);
        root.addView(text, place(density, 16, 16, -1, -1));
        status = new StatusView(this);
        status.setBackgroundColor(VIEW_COLOR);
        root.addView(status, place(density, 16, 64, 328, 56));

        GLSurfaceView es2 = new GLSurfaceView(this);
        es2.setEGLContextClientVersion(2);
        es2.setRenderer(new Es2Renderer(status));
        es2.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        root.addView(es2, place(density, 40, 136, 280, 280));

        // Concurrency diagnostics only (build/macos/m4/race): SHIRAZ_TEST_GLES_LOWER=none
        // drops the lower view, =es2 makes it a second ES 2 view. The smoke
        // profile's frame and marker checks assume the default ES 1 view.
        String lower = System.getenv("SHIRAZ_TEST_GLES_LOWER");
        System.out.println("MACOS_GLES_LOWER_VIEW " + (lower == null ? "es1" : lower));
        if ("es2".equals(lower)) {
            GLSurfaceView second = new GLSurfaceView(this);
            second.setEGLContextClientVersion(2);
            second.setRenderer(new Es2Renderer(status));
            second.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            root.addView(second, place(density, 40, 440, 280, 160));
        } else if (!"none".equals(lower)) {
            // Default client version: an ES 1 context, as on Android.
            GLSurfaceView es1 = new GLSurfaceView(this);
            es1.setRenderer(new Es1Renderer(status));
            es1.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            root.addView(es1, place(density, 40, 440, 280, 160));
        }
        setContentView(root);
        System.out.println("MACOS_GLES_CONTENT_SET density=" + density);
    }

    /** Layout params at dp offsets; a negative size wraps the content. */
    static FrameLayout.LayoutParams place(float density, int left, int top, int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                width < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : Math.round(width * density),
                height < 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : Math.round(height * density));
        params.leftMargin = Math.round(left * density);
        params.topMargin = Math.round(top * density);
        return params;
    }

    static void fail(String stage, Throwable error, View status) {
        if (failure == null) failure = error;
        System.out.println("MACOS_GLES_FAIL stage=" + stage + " error=" + error);
        status.postInvalidate();
    }

    static String hex(int value) {
        return "0x" + Integer.toHexString(value);
    }

    static FloatBuffer floats(float[] values) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }

    static void requireCurrentContext(String stage) {
        EGL10 egl = (EGL10) EGLContext.getEGL();
        if (egl.eglGetCurrentContext() == EGL10.EGL_NO_CONTEXT)
            throw new IllegalStateException(stage + ": no current EGL context (eglMakeCurrent failed)");
    }

    /** GL window coordinates (origin bottom-left) of an NDC point on a WIDTH x HEIGHT surface. */
    static int[] window(float x, float y, int width, int height) {
        return new int[]{(int) ((x + 1) / 2 * width), (int) ((y + 1) / 2 * height)};
    }

    /** 0xAARRGGBB of one pixel read back as RGBA unsigned bytes. */
    static int argb(ByteBuffer pixel) {
        return (pixel.get(3) & 255) << 24 | (pixel.get(0) & 255) << 16 | (pixel.get(1) & 255) << 8 | (pixel.get(2) & 255);
    }

    static void checkProbes(String name, float[][] probes, int width, int height, int inside, int outside, ByteBuffer pixel,
                            GL10 gl) {
        for (float[] probe : probes) {
            int[] at = window(probe[0], probe[1], width, height);
            pixel.clear();
            if (gl == null) GLES20.glReadPixels(at[0], at[1], 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel);
            else gl.glReadPixels(at[0], at[1], 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel);
            int expected = probe[2] != 0 ? inside : outside, actual = argb(pixel);
            if (actual != expected)
                throw new IllegalStateException(name + " readback at " + at[0] + "," + at[1] + " (ndc " + probe[0] + ","
                        + probe[1] + ") " + hex(actual) + " != " + hex(expected));
        }
    }

    /** ES 2 renderer: shader triangle from a VBO recorded in a GLES30 vertex array object. */
    static final class Es2Renderer implements GLSurfaceView.Renderer {
        final View status;
        final ByteBuffer pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        int program, vao, width, height;
        boolean checked;

        Es2Renderer(View status) {
            this.status = status;
        }

        static int shader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = {0};
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] != 1) throw new IllegalStateException("shader compile: " + GLES20.glGetShaderInfoLog(shader));
            return shader;
        }

        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            try {
                requireCurrentContext("gles2");
                String renderer = GLES20.glGetString(GL_RENDERER), version = GLES20.glGetString(GL_VERSION);
                System.out.println("MACOS_GLES2_VENDOR " + GLES20.glGetString(GL_VENDOR));
                System.out.println("MACOS_GLES2_RENDERER " + renderer);
                System.out.println("MACOS_GLES2_VERSION " + version);
                // ANGLE and Android drivers return a backwards-compatible ES 3 context for client version 2;
                // the GLES30 calls below need it.
                if (version == null || !version.startsWith("OpenGL ES 3."))
                    throw new IllegalStateException("GLES30 calls need an OpenGL ES 3 context, got " + version);
                int vertex = shader(GLES20.GL_VERTEX_SHADER,
                        "attribute vec2 position;void main(){gl_Position=vec4(position,0.0,1.0);}");
                int fragment = shader(GLES20.GL_FRAGMENT_SHADER,
                        "precision mediump float;uniform vec4 color;void main(){gl_FragColor=color;}");
                program = GLES20.glCreateProgram();
                GLES20.glAttachShader(program, vertex);
                GLES20.glAttachShader(program, fragment);
                GLES20.glBindAttribLocation(program, 0, "position");
                GLES20.glLinkProgram(program);
                int[] linked = {0};
                GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
                if (linked[0] != 1) throw new IllegalStateException("program link: " + GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteShader(vertex);
                GLES20.glDeleteShader(fragment);
                int[] names = {0};
                GLES30.glGenVertexArrays(1, names, 0);
                vao = names[0];
                GLES30.glBindVertexArray(vao);
                if (vao == 0 || !GLES30.glIsVertexArray(vao)) throw new IllegalStateException("vertex array object " + vao);
                GLES20.glGenBuffers(1, names, 0);
                if (names[0] == 0) throw new IllegalStateException("vertex buffer");
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, names[0]);
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, TRIANGLE.length * 4, floats(TRIANGLE), GLES20.GL_STATIC_DRAW);
                GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, 0);
                GLES20.glEnableVertexAttribArray(0);
                GLES30.glBindVertexArray(0);
                GLES20.glUseProgram(program);
                GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "color"), ((GLES2_TRIANGLE >> 16) & 255) / 255f,
                        ((GLES2_TRIANGLE >> 8) & 255) / 255f, (GLES2_TRIANGLE & 255) / 255f, 1f);
                int error = GLES20.glGetError();
                if (error != 0) throw new IllegalStateException("GL error " + hex(error) + " during setup");
                System.out.println("MACOS_GLES2_SURFACE_CREATED vao=" + vao);
            } catch (RuntimeException error) {
                fail("gles2-create", error, status);
                throw error;
            }
        }

        public void onSurfaceChanged(GL10 gl, int width, int height) {
            this.width = width;
            this.height = height;
            GLES20.glViewport(0, 0, width, height);
        }

        public void onDrawFrame(GL10 gl) {
            try {
                if (program == 0) throw new IllegalStateException("draw without a program");
                GLES20.glClearColor(((GLES2_CLEAR >> 16) & 255) / 255f, ((GLES2_CLEAR >> 8) & 255) / 255f,
                        (GLES2_CLEAR & 255) / 255f, 1f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                GLES30.glBindVertexArray(vao);
                int[] binding = {-1};
                GLES20.glGetIntegerv(GLES30.GL_VERTEX_ARRAY_BINDING, binding, 0);
                if (binding[0] != vao) throw new IllegalStateException("GL_VERTEX_ARRAY_BINDING " + binding[0] + " != " + vao);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
                GLES30.glBindVertexArray(0);
                if (!checked) {
                    checked = true;
                    checkProbes("gles2", GLES2_PROBES, width, height, GLES2_TRIANGLE, GLES2_CLEAR, pixel, null);
                    int error = GLES20.glGetError();
                    if (error != 0) throw new IllegalStateException("GL error " + hex(error) + " after draw");
                    System.out.println("MACOS_GLES2_FRAME_OK width=" + width + " height=" + height + " clear="
                            + hex(GLES2_CLEAR & 0xffffff) + " triangle=" + hex(GLES2_TRIANGLE & 0xffffff) + " vao=" + vao);
                }
            } catch (RuntimeException error) {
                fail("gles2-draw", error, status);
                throw error;
            }
        }
    }

    /** ES 1 renderer: fixed-function textured quad, texture uploaded by GLUtils. */
    static final class Es1Renderer implements GLSurfaceView.Renderer {
        final View status;
        final ByteBuffer pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        final FloatBuffer quad = floats(QUAD), uv = floats(QUAD_UV);
        final ByteBuffer indices = ByteBuffer.allocateDirect(6);
        int width, height;
        boolean checked;

        Es1Renderer(View status) {
            this.status = status;
            indices.put(new byte[]{0, 1, 2, 0, 2, 3}).position(0);
        }

        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            try {
                requireCurrentContext("gles1");
                String version = gl.glGetString(GL_VERSION);
                System.out.println("MACOS_GLES1_RENDERER " + gl.glGetString(GL_RENDERER));
                System.out.println("MACOS_GLES1_VERSION " + version);
                if (version == null || !version.startsWith("OpenGL ES-CM 1.") && !version.startsWith("OpenGL ES 1."))
                    throw new IllegalStateException("expected an OpenGL ES 1 context, got " + version);
                Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(GLES1_TEXTURE);
                int[] textures = {0};
                gl.glGenTextures(1, textures, 0);
                if (textures[0] == 0) throw new IllegalStateException("texture name");
                gl.glBindTexture(GL_TEXTURE_2D, textures[0]);
                gl.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                gl.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0);
                bitmap.recycle();
                System.out.println("MACOS_GLES1_SURFACE_CREATED texture=" + textures[0]);
            } catch (RuntimeException error) {
                fail("gles1-create", error, status);
                throw error;
            }
        }

        public void onSurfaceChanged(GL10 gl, int width, int height) {
            this.width = width;
            this.height = height;
            gl.glViewport(0, 0, width, height);
        }

        public void onDrawFrame(GL10 gl) {
            try {
                gl.glClearColor(((GLES1_CLEAR >> 16) & 255) / 255f, ((GLES1_CLEAR >> 8) & 255) / 255f,
                        (GLES1_CLEAR & 255) / 255f, 1f);
                gl.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                gl.glMatrixMode(GL_PROJECTION);
                gl.glLoadIdentity();
                gl.glOrthof(-1, 1, -1, 1, -1, 1);
                gl.glMatrixMode(GL_MODELVIEW);
                gl.glLoadIdentity();
                gl.glEnable(GL_TEXTURE_2D);
                gl.glEnableClientState(GL_VERTEX_ARRAY);
                gl.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
                gl.glColor4f(1, 1, 1, 1);
                gl.glVertexPointer(2, GLES20.GL_FLOAT, 0, quad);
                gl.glTexCoordPointer(2, GLES20.GL_FLOAT, 0, uv);
                gl.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_BYTE, indices);
                if (!checked) {
                    checked = true;
                    checkProbes("gles1", GLES1_PROBES, width, height, GLES1_TEXTURE, GLES1_CLEAR, pixel, gl);
                    System.out.println("MACOS_GLES1_FRAME_OK width=" + width + " height=" + height + " clear="
                            + hex(GLES1_CLEAR & 0xffffff) + " texture=" + hex(GLES1_TEXTURE & 0xffffff));
                }
            } catch (RuntimeException error) {
                fail("gles1-draw", error, status);
                throw error;
            }
        }
    }

    /** The coloured View; rethrows a GL thread failure on the UI thread. */
    static final class StatusView extends View {
        boolean reported;

        StatusView(Activity context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Throwable error = failure;
            if (error != null) throw new IllegalStateException("GLES renderer failed", error);
            if (!reported) {
                reported = true;
                System.out.println("MACOS_GLES_VIEW_DRAWN width=" + getWidth() + " height=" + getHeight());
            }
        }
    }
}
