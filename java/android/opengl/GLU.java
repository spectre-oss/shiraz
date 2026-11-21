// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;

import java.lang.reflect.Method;
import javax.microedition.khronos.opengles.GL10;

/** OpenGL Utility Library (GLU) projection, unprojection, and error string helpers. */
public class GLU {
    private static final int GL_TRUE = 1;
    private static final int GL_FALSE = 0;

    public GLU() {}

    public static String gluErrorString(int error) {
        switch (error) {
            case GLES20.GL_NO_ERROR:
                return "no error";
            case GLES20.GL_INVALID_ENUM:
                return "invalid enum";
            case GLES20.GL_INVALID_VALUE:
                return "invalid value";
            case GLES20.GL_INVALID_OPERATION:
                return "invalid operation";
            case 0x0503: // GL_STACK_OVERFLOW
                return "stack overflow";
            case 0x0504: // GL_STACK_UNDERFLOW
                return "stack underflow";
            case GLES20.GL_OUT_OF_MEMORY:
                return "out of memory";
            default:
                return null;
        }
    }

    public static void gluLookAt(
            GL10 gl,
            float eyeX, float eyeY, float eyeZ,
            float centerX, float centerY, float centerZ,
            float upX, float upY, float upZ) {
        float[] scratch = new float[16];
        Matrix.setLookAtM(scratch, 0, eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ);
        invokeMultMatrixf(gl, scratch, 0);
    }

    public static void gluOrtho2D(GL10 gl, float left, float right, float bottom, float top) {
        gl.glOrthof(left, right, bottom, top, -1.0f, 1.0f);
    }

    public static void gluPerspective(GL10 gl, float fovy, float aspect, float zNear, float zFar) {
        float top = zNear * (float) Math.tan(fovy * (Math.PI / 360.0));
        float bottom = -top;
        float left = bottom * aspect;
        float right = top * aspect;
        invokeFrustumf(gl, left, right, bottom, top, zNear, zFar);
    }

    private static void invokeMultMatrixf(GL10 gl, float[] m, int offset) {
        try {
            Method method = gl.getClass().getMethod("glMultMatrixf", float[].class, int.class);
            method.invoke(gl, m, Integer.valueOf(offset));
        } catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException("GL10 implementation does not support glMultMatrixf", e);
        }
    }

    private static void invokeFrustumf(
            GL10 gl, float left, float right, float bottom, float top, float near, float far) {
        try {
            Method method = gl.getClass().getMethod(
                    "glFrustumf",
                    float.class, float.class, float.class, float.class, float.class, float.class);
            method.invoke(
                    gl,
                    Float.valueOf(left), Float.valueOf(right),
                    Float.valueOf(bottom), Float.valueOf(top),
                    Float.valueOf(near), Float.valueOf(far));
        } catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException("GL10 implementation does not support glFrustumf", e);
        }
    }

    public static int gluProject(
            float objX, float objY, float objZ,
            float[] model, int modelOffset,
            float[] project, int projectOffset,
            int[] view, int viewOffset,
            float[] win, int winOffset) {
        float[] scratch = new float[32];
        Matrix.multiplyMM(scratch, 0, project, projectOffset, model, modelOffset);
        scratch[16] = objX;
        scratch[17] = objY;
        scratch[18] = objZ;
        scratch[19] = 1.0f;
        Matrix.multiplyMV(scratch, 20, scratch, 0, scratch, 16);
        float w = scratch[23];
        if (w == 0.0f) {
            return GL_FALSE;
        }
        float rw = 1.0f / w;
        win[winOffset] = view[viewOffset] + view[viewOffset + 2] * (scratch[20] * rw + 1.0f) * 0.5f;
        win[winOffset + 1] = view[viewOffset + 1] + view[viewOffset + 3] * (scratch[21] * rw + 1.0f) * 0.5f;
        win[winOffset + 2] = (scratch[22] * rw + 1.0f) * 0.5f;
        return GL_TRUE;
    }

    public static int gluUnProject(
            float winX, float winY, float winZ,
            float[] model, int modelOffset,
            float[] project, int projectOffset,
            int[] view, int viewOffset,
            float[] obj, int objOffset) {
        if (view[viewOffset + 2] == 0 || view[viewOffset + 3] == 0) {
            return GL_FALSE;
        }
        float[] scratch = new float[40];
        Matrix.multiplyMM(scratch, 0, project, projectOffset, model, modelOffset);
        if (!Matrix.invertM(scratch, 16, scratch, 0)) {
            return GL_FALSE;
        }
        scratch[32] = 2.0f * (winX - view[viewOffset]) / view[viewOffset + 2] - 1.0f;
        scratch[33] = 2.0f * (winY - view[viewOffset + 1]) / view[viewOffset + 3] - 1.0f;
        scratch[34] = 2.0f * winZ - 1.0f;
        scratch[35] = 1.0f;
        Matrix.multiplyMV(scratch, 36, scratch, 16, scratch, 32);
        if (scratch[39] == 0.0f) {
            return GL_FALSE;
        }
        float rw = 1.0f / scratch[39];
        obj[objOffset] = scratch[36] * rw;
        obj[objOffset + 1] = scratch[37] * rw;
        obj[objOffset + 2] = scratch[38] * rw;
        return GL_TRUE;
    }
}
