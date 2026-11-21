// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import java.util.ArrayList;

public class Camera {
    private static final float CAMERA_UNIT_PIXELS = 72.0f;

    private float[] mMatrix = identity4x4();
    private final ArrayList<float[]> mStack = new ArrayList<float[]>();
    private float mLocX = 0.0f;
    private float mLocY = 0.0f;
    private float mLocZ = -8.0f;

    public Camera() {}

    private static float[] identity4x4() {
        return new float[] {
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        };
    }

    private void preConcat(float[] rhs) {
        float[] out = new float[16];
        for (int r = 0; r < 4; r++) {
            int rb = r * 4;
            for (int c = 0; c < 4; c++) {
                out[rb + c] = mMatrix[rb] * rhs[c]
                        + mMatrix[rb + 1] * rhs[4 + c]
                        + mMatrix[rb + 2] * rhs[8 + c]
                        + mMatrix[rb + 3] * rhs[12 + c];
            }
        }
        mMatrix = out;
    }

    public void save() {
        mStack.add(mMatrix.clone());
    }

    public void restore() {
        if (mStack.isEmpty()) {
            throw new IllegalStateException("Camera restore underflow");
        }
        mMatrix = mStack.remove(mStack.size() - 1);
    }

    public void translate(float x, float y, float z) {
        // Sk3DView: +Y is up in 3D camera space, while +Y is down in 2D canvas space.
        preConcat(new float[] {
            1f, 0f, 0f, x,
            0f, 1f, 0f, -y,
            0f, 0f, 1f, z,
            0f, 0f, 0f, 1f
        });
    }

    public void rotateX(float deg) {
        double rad = Math.toRadians(deg);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        preConcat(new float[] {
            1f, 0f,  0f, 0f,
            0f, c,  -s,  0f,
            0f, s,   c,  0f,
            0f, 0f,  0f, 1f
        });
    }

    public void rotateY(float deg) {
        double rad = Math.toRadians(deg);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        preConcat(new float[] {
             c, 0f, s, 0f,
            0f, 1f, 0f, 0f,
            -s, 0f, c, 0f,
            0f, 0f, 0f, 1f
        });
    }

    public void rotateZ(float deg) {
        double rad = Math.toRadians(-deg);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        preConcat(new float[] {
            c, -s, 0f, 0f,
            s,  c, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        });
    }

    public void rotate(float x, float y, float z) {
        rotateX(x);
        rotateY(y);
        rotateZ(z);
    }

    public float getLocationX() {
        return mLocX;
    }

    public float getLocationY() {
        return mLocY;
    }

    public float getLocationZ() {
        return mLocZ;
    }

    public void setLocation(float x, float y, float z) {
        mLocX = x;
        mLocY = y;
        mLocZ = z;
    }

    public void getMatrix(Matrix matrix) {
        if (matrix == null) {
            throw new NullPointerException("matrix must not be null");
        }
        float ex = mLocX * CAMERA_UNIT_PIXELS;
        float ey = -mLocY * CAMERA_UNIT_PIXELS;
        float ez = mLocZ * CAMERA_UNIT_PIXELS;
        float invEz = ez == 0.0f ? -1.0f / 576.0f : 1.0f / ez;

        // Perspective projection from 3D plane z=0 toward eye (ex, ey, ez):
        // x' = (x * (-ez) + z * ex) / (-ez + z) = (x - z * (ex / ez)) / (1 - z / ez)
        // y' = (y - z * (ey / ez)) / (1 - z / ez)
        float m00 = mMatrix[0] - mMatrix[8] * (ex * invEz);
        float m01 = mMatrix[1] - mMatrix[9] * (ex * invEz);
        float m02 = mMatrix[3] - mMatrix[11] * (ex * invEz);

        float m10 = mMatrix[4] - mMatrix[8] * (ey * invEz);
        float m11 = mMatrix[5] - mMatrix[9] * (ey * invEz);
        float m12 = mMatrix[7] - mMatrix[11] * (ey * invEz);

        float m20 = -mMatrix[8] * invEz;
        float m21 = -mMatrix[9] * invEz;
        float m22 = 1.0f - mMatrix[11] * invEz;

        if (m22 != 0.0f && m22 != 1.0f) {
            float invW = 1.0f / m22;
            m00 *= invW; m01 *= invW; m02 *= invW;
            m10 *= invW; m11 *= invW; m12 *= invW;
            m20 *= invW; m21 *= invW; m22 = 1.0f;
        }
        if (Math.abs(m20) < 1e-7f) m20 = 0.0f;
        if (Math.abs(m21) < 1e-7f) m21 = 0.0f;

        matrix.setValues(new float[] {
            m00, m01, m02,
            m10, m11, m12,
            m20, m21, m22
        });
    }

    public void applyToCanvas(Canvas canvas) {
        if (canvas == null) {
            throw new NullPointerException("canvas must not be null");
        }
        Matrix m = new Matrix();
        getMatrix(m);
        if (!m.isAffine()) {
            float[] v = new float[9];
            m.getValues(v);
            float w = v[8] == 0.0f ? 1.0f : v[8];
            v[0] /= w; v[1] /= w; v[2] /= w;
            v[3] /= w; v[4] /= w; v[5] /= w;
            v[6] = 0.0f; v[7] = 0.0f; v[8] = 1.0f;
            m.setValues(v);
        }
        canvas.concat(m);
    }

    public float dotWithNormal(float dx, float dy, float dz) {
        // Transform normal by upper-left 3x3 and dot with camera forward (0, 0, -1) -> -z'
        float nx = mMatrix[0] * dx + mMatrix[1] * (-dy) + mMatrix[2] * dz;
        float ny = mMatrix[4] * dx + mMatrix[5] * (-dy) + mMatrix[6] * dz;
        float nz = mMatrix[8] * dx + mMatrix[9] * (-dy) + mMatrix[10] * dz;
        float ex = mLocX * CAMERA_UNIT_PIXELS - mMatrix[3];
        float ey = -mLocY * CAMERA_UNIT_PIXELS - mMatrix[7];
        float ez = mLocZ * CAMERA_UNIT_PIXELS - mMatrix[11];
        float len = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (len == 0.0f) {
            return -nz;
        }
        return (nx * ex + ny * ey + nz * ez) / len;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }
}
