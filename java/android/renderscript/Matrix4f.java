// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

public class Matrix4f {
    final float[] mMat;

    public Matrix4f() {
        mMat = new float[16];
        loadIdentity();
    }

    public Matrix4f(float[] dataArray) {
        mMat = new float[16];
        System.arraycopy(dataArray, 0, mMat, 0, mMat.length);
    }

    public float[] getArray() {
        return mMat;
    }

    public float get(int x, int y) {
        return mMat[x * 4 + y];
    }

    public void set(int x, int y, float v) {
        mMat[x * 4 + y] = v;
    }

    public void loadIdentity() {
        mMat[0] = 1;
        mMat[1] = 0;
        mMat[2] = 0;
        mMat[3] = 0;

        mMat[4] = 0;
        mMat[5] = 1;
        mMat[6] = 0;
        mMat[7] = 0;

        mMat[8] = 0;
        mMat[9] = 0;
        mMat[10] = 1;
        mMat[11] = 0;

        mMat[12] = 0;
        mMat[13] = 0;
        mMat[14] = 0;
        mMat[15] = 1;
    }

    public void load(Matrix4f src) {
        System.arraycopy(src.getArray(), 0, mMat, 0, mMat.length);
    }

    void load(Matrix3f src) {
        float[] s = src.getArray();
        mMat[0] = s[0];
        mMat[1] = s[1];
        mMat[2] = s[2];
        mMat[3] = 0f;

        mMat[4] = s[3];
        mMat[5] = s[4];
        mMat[6] = s[5];
        mMat[7] = 0f;

        mMat[8] = s[6];
        mMat[9] = s[7];
        mMat[10] = s[8];
        mMat[11] = 0f;

        mMat[12] = 0f;
        mMat[13] = 0f;
        mMat[14] = 0f;
        mMat[15] = 1f;
    }

    public void loadRotate(float rot, float x, float y, float z) {
        mMat[3] = 0;
        mMat[7] = 0;
        mMat[11] = 0;
        mMat[12] = 0;
        mMat[13] = 0;
        mMat[14] = 0;
        mMat[15] = 1;
        rot *= (float) (Math.PI / 180.0f);
        float c = (float) Math.cos(rot);
        float s = (float) Math.sin(rot);
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len != 1.0f && len > 0.0f) {
            float recipLen = 1.0f / len;
            x *= recipLen;
            y *= recipLen;
            z *= recipLen;
        }
        float nc = 1.0f - c;
        float xy = x * y;
        float yz = y * z;
        float zx = z * x;
        float xs = x * s;
        float ys = y * s;
        float zs = z * s;
        mMat[0] = x * x * nc + c;
        mMat[4] = xy * nc - zs;
        mMat[8] = zx * nc + ys;
        mMat[1] = xy * nc + zs;
        mMat[5] = y * y * nc + c;
        mMat[9] = yz * nc - xs;
        mMat[2] = zx * nc - ys;
        mMat[6] = yz * nc + xs;
        mMat[10] = z * z * nc + c;
    }

    public void loadScale(float x, float y, float z) {
        loadIdentity();
        mMat[0] = x;
        mMat[5] = y;
        mMat[10] = z;
    }

    public void loadTranslate(float x, float y, float z) {
        loadIdentity();
        mMat[12] = x;
        mMat[13] = y;
        mMat[14] = z;
    }

    public void loadMultiply(Matrix4f lhs, Matrix4f rhs) {
        float[] out = new float[16];
        for (int i = 0; i < 4; i++) {
            float ri0 = 0;
            float ri1 = 0;
            float ri2 = 0;
            float ri3 = 0;
            for (int j = 0; j < 4; j++) {
                float rhs_ij = rhs.get(i, j);
                ri0 += lhs.get(j, 0) * rhs_ij;
                ri1 += lhs.get(j, 1) * rhs_ij;
                ri2 += lhs.get(j, 2) * rhs_ij;
                ri3 += lhs.get(j, 3) * rhs_ij;
            }
            out[i * 4] = ri0;
            out[i * 4 + 1] = ri1;
            out[i * 4 + 2] = ri2;
            out[i * 4 + 3] = ri3;
        }
        System.arraycopy(out, 0, mMat, 0, 16);
    }

    public void loadOrtho(float l, float r, float b, float t, float n, float f) {
        loadIdentity();
        mMat[0] = 2.0f / (r - l);
        mMat[5] = 2.0f / (t - b);
        mMat[10] = -2.0f / (f - n);
        mMat[12] = -(r + l) / (r - l);
        mMat[13] = -(t + b) / (t - b);
        mMat[14] = -(f + n) / (f - n);
    }

    public void loadOrthoWindow(int w, int h) {
        loadOrtho(0, w, h, 0, -1, 1);
    }

    public void loadFrustum(float l, float r, float b, float t, float n, float f) {
        loadIdentity();
        mMat[0] = 2.0f * n / (r - l);
        mMat[5] = 2.0f * n / (t - b);
        mMat[8] = (r + l) / (r - l);
        mMat[9] = (t + b) / (t - b);
        mMat[10] = -(f + n) / (f - n);
        mMat[11] = -1.0f;
        mMat[14] = -2.0f * f * n / (f - n);
        mMat[15] = 0.0f;
    }

    public void loadPerspective(float fovy, float aspect, float near, float far) {
        float top = near * (float) Math.tan((float) (fovy * Math.PI / 360.0f));
        float bottom = -top;
        float left = bottom * aspect;
        float right = top * aspect;
        loadFrustum(left, right, bottom, top, near, far);
    }

    public void loadProjectionNormalized(int w, int h) {
        Matrix4f m1 = new Matrix4f();
        Matrix4f m2 = new Matrix4f();
        if (w > h) {
            float aspect = ((float) w) / h;
            m1.loadFrustum(-aspect, aspect, -1, 1, 1, 100);
        } else {
            float aspect = ((float) h) / w;
            m1.loadFrustum(-1, 1, -aspect, aspect, 1, 100);
        }
        m2.loadRotate(180, 0, 1, 0);
        m1.loadMultiply(m1, m2);
        m2.loadScale(-2, 2, 1);
        m1.loadMultiply(m1, m2);
        m2.loadTranslate(0, 0, 2);
        m1.loadMultiply(m1, m2);
        load(m1);
    }

    public void multiply(Matrix4f rhs) {
        Matrix4f tmp = new Matrix4f();
        tmp.loadMultiply(this, rhs);
        load(tmp);
    }

    public void rotate(float rot, float x, float y, float z) {
        Matrix4f tmp = new Matrix4f();
        tmp.loadRotate(rot, x, y, z);
        multiply(tmp);
    }

    public void scale(float x, float y, float z) {
        Matrix4f tmp = new Matrix4f();
        tmp.loadScale(x, y, z);
        multiply(tmp);
    }

    public void translate(float x, float y, float z) {
        Matrix4f tmp = new Matrix4f();
        tmp.loadTranslate(x, y, z);
        multiply(tmp);
    }

    private float computeCofactor(int col, int row) {
        int c0 = col == 0 ? 1 : 0;
        int c1 = (col <= 1) ? 2 : 1;
        int c2 = (col == 3) ? 2 : 3;

        int r0 = row == 0 ? 1 : 0;
        int r1 = (row <= 1) ? 2 : 1;
        int r2 = (row == 3) ? 2 : 3;

        float a00 = mMat[c0 * 4 + r0];
        float a01 = mMat[c1 * 4 + r0];
        float a02 = mMat[c2 * 4 + r0];

        float a10 = mMat[c0 * 4 + r1];
        float a11 = mMat[c1 * 4 + r1];
        float a12 = mMat[c2 * 4 + r1];

        float a20 = mMat[c0 * 4 + r2];
        float a21 = mMat[c1 * 4 + r2];
        float a22 = mMat[c2 * 4 + r2];

        float minor = a00 * (a11 * a22 - a12 * a21)
                - a01 * (a10 * a22 - a12 * a20)
                + a02 * (a10 * a21 - a11 * a20);
        return ((col + row) & 1) == 0 ? minor : -minor;
    }

    public boolean inverse() {
        float[] cofactors = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                cofactors[c * 4 + r] = computeCofactor(c, r);
            }
        }
        float det = mMat[0] * cofactors[0]
                + mMat[1] * cofactors[1]
                + mMat[2] * cofactors[2]
                + mMat[3] * cofactors[3];
        if (Math.abs(det) < 1e-30f) {
            return false;
        }
        float invDet = 1.0f / det;
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                mMat[c * 4 + r] = cofactors[r * 4 + c] * invDet;
            }
        }
        return true;
    }

    public boolean inverseTranspose() {
        float[] cofactors = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                cofactors[c * 4 + r] = computeCofactor(c, r);
            }
        }
        float det = mMat[0] * cofactors[0]
                + mMat[1] * cofactors[1]
                + mMat[2] * cofactors[2]
                + mMat[3] * cofactors[3];
        if (Math.abs(det) < 1e-30f) {
            return false;
        }
        float invDet = 1.0f / det;
        for (int i = 0; i < 16; i++) {
            mMat[i] = cofactors[i] * invDet;
        }
        return true;
    }

    public void transpose() {
        for (int i = 0; i < 3; i++) {
            for (int j = i + 1; j < 4; j++) {
                float temp = mMat[i * 4 + j];
                mMat[i * 4 + j] = mMat[j * 4 + i];
                mMat[j * 4 + i] = temp;
            }
        }
    }
}
