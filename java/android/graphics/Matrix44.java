// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import java.util.Arrays;

public class Matrix44 {
    final float[] mBackingArray;

    public Matrix44() {
        mBackingArray = new float[] {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        };
    }

    public Matrix44(Matrix mat) {
        if (mat == null) {
            throw new NullPointerException("mat must not be null");
        }
        float[] m = new float[9];
        mat.getValues(m);
        mBackingArray = new float[] {
            m[0], m[1], 0.0f, m[2],
            m[3], m[4], 0.0f, m[5],
            0.0f, 0.0f, 1.0f, 0.0f,
            m[6], m[7], 0.0f, m[8]
        };
    }

    public void getValues(float[] dst) {
        if (dst == null) {
            throw new NullPointerException("dst must not be null");
        }
        if (dst.length != 16) {
            throw new IllegalArgumentException("Dst array must be of length 16");
        }
        System.arraycopy(mBackingArray, 0, dst, 0, 16);
    }

    public void setValues(float[] src) {
        if (src == null) {
            throw new NullPointerException("src must not be null");
        }
        if (src.length != 16) {
            throw new IllegalArgumentException("Src array must be of length 16");
        }
        System.arraycopy(src, 0, mBackingArray, 0, 16);
    }

    public float get(int row, int col) {
        if (row < 0 || row >= 4 || col < 0 || col >= 4) {
            throw new IllegalArgumentException("row and col must be in [0..3]");
        }
        return mBackingArray[row * 4 + col];
    }

    public void set(int row, int col, float val) {
        if (row < 0 || row >= 4 || col < 0 || col >= 4) {
            throw new IllegalArgumentException("row and col must be in [0..3]");
        }
        mBackingArray[row * 4 + col] = val;
    }

    public void reset() {
        Arrays.fill(mBackingArray, 0.0f);
        mBackingArray[0] = 1.0f;
        mBackingArray[5] = 1.0f;
        mBackingArray[10] = 1.0f;
        mBackingArray[15] = 1.0f;
    }

    public boolean invert() {
        double a00 = mBackingArray[0],  a01 = mBackingArray[1],  a02 = mBackingArray[2],  a03 = mBackingArray[3];
        double a10 = mBackingArray[4],  a11 = mBackingArray[5],  a12 = mBackingArray[6],  a13 = mBackingArray[7];
        double a20 = mBackingArray[8],  a21 = mBackingArray[9],  a22 = mBackingArray[10], a23 = mBackingArray[11];
        double a30 = mBackingArray[12], a31 = mBackingArray[13], a32 = mBackingArray[14], a33 = mBackingArray[15];

        double b00 = a00 * a11 - a01 * a10;
        double b01 = a00 * a12 - a02 * a10;
        double b02 = a00 * a13 - a03 * a10;
        double b03 = a01 * a12 - a02 * a11;
        double b04 = a01 * a13 - a03 * a11;
        double b05 = a02 * a13 - a03 * a12;
        double b06 = a20 * a31 - a21 * a30;
        double b07 = a20 * a32 - a22 * a30;
        double b08 = a20 * a33 - a23 * a30;
        double b09 = a21 * a32 - a22 * a31;
        double b10 = a21 * a33 - a23 * a31;
        double b11 = a22 * a33 - a23 * a32;

        double det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
        if (det == 0.0 || !Double.isFinite(det)) {
            return false;
        }
        double invDet = 1.0 / det;
        if (!Double.isFinite(invDet)) {
            return false;
        }

        mBackingArray[0]  = (float) (( a11 * b11 - a12 * b10 + a13 * b09) * invDet);
        mBackingArray[1]  = (float) ((-a01 * b11 + a02 * b10 - a03 * b09) * invDet);
        mBackingArray[2]  = (float) (( a31 * b05 - a32 * b04 + a33 * b03) * invDet);
        mBackingArray[3]  = (float) ((-a21 * b05 + a22 * b04 - a23 * b03) * invDet);
        mBackingArray[4]  = (float) ((-a10 * b11 + a12 * b08 - a13 * b07) * invDet);
        mBackingArray[5]  = (float) (( a00 * b11 - a02 * b08 + a03 * b07) * invDet);
        mBackingArray[6]  = (float) ((-a30 * b05 + a32 * b02 - a33 * b01) * invDet);
        mBackingArray[7]  = (float) (( a20 * b05 - a22 * b02 + a23 * b01) * invDet);
        mBackingArray[8]  = (float) (( a10 * b10 - a11 * b08 + a13 * b06) * invDet);
        mBackingArray[9]  = (float) ((-a00 * b10 + a01 * b08 - a03 * b06) * invDet);
        mBackingArray[10] = (float) (( a30 * b04 - a31 * b02 + a33 * b00) * invDet);
        mBackingArray[11] = (float) ((-a20 * b04 + a21 * b02 - a23 * b00) * invDet);
        mBackingArray[12] = (float) ((-a10 * b09 + a11 * b07 - a12 * b06) * invDet);
        mBackingArray[13] = (float) (( a00 * b09 - a01 * b07 + a02 * b06) * invDet);
        mBackingArray[14] = (float) ((-a30 * b03 + a31 * b01 - a32 * b00) * invDet);
        mBackingArray[15] = (float) (( a20 * b03 - a21 * b01 + a22 * b00) * invDet);
        return true;
    }

    public boolean isIdentity() {
        for (int i = 0; i < 16; i++) {
            float expected = (i % 5 == 0) ? 1.0f : 0.0f;
            if (mBackingArray[i] != expected) {
                return false;
            }
        }
        return true;
    }

    public float[] map(float x, float y, float z, float w) {
        float[] dst = new float[4];
        map(x, y, z, w, dst);
        return dst;
    }

    public void map(float x, float y, float z, float w, float[] dst) {
        if (dst == null) {
            throw new NullPointerException("dst must not be null");
        }
        if (dst.length != 4) {
            throw new IllegalArgumentException("Dst array must be of length 4");
        }
        float rx = x * mBackingArray[0]  + y * mBackingArray[1]  + z * mBackingArray[2]  + w * mBackingArray[3];
        float ry = x * mBackingArray[4]  + y * mBackingArray[5]  + z * mBackingArray[6]  + w * mBackingArray[7];
        float rz = x * mBackingArray[8]  + y * mBackingArray[9]  + z * mBackingArray[10] + w * mBackingArray[11];
        float rw = x * mBackingArray[12] + y * mBackingArray[13] + z * mBackingArray[14] + w * mBackingArray[15];
        dst[0] = rx;
        dst[1] = ry;
        dst[2] = rz;
        dst[3] = rw;
    }

    public Matrix44 concat(Matrix44 b) {
        if (b == null) {
            throw new NullPointerException("b must not be null");
        }
        multiplyInPlace(b.mBackingArray);
        return this;
    }

    private void multiplyInPlace(float[] rhs) {
        float[] out = new float[16];
        for (int r = 0; r < 4; r++) {
            int rowBase = r * 4;
            float a0 = mBackingArray[rowBase];
            float a1 = mBackingArray[rowBase + 1];
            float a2 = mBackingArray[rowBase + 2];
            float a3 = mBackingArray[rowBase + 3];
            for (int c = 0; c < 4; c++) {
                out[rowBase + c] = a0 * rhs[c]
                        + a1 * rhs[4 + c]
                        + a2 * rhs[8 + c]
                        + a3 * rhs[12 + c];
            }
        }
        System.arraycopy(out, 0, mBackingArray, 0, 16);
    }

    public Matrix44 rotate(float deg, float xComp, float yComp, float zComp) {
        double len = Math.sqrt((double) xComp * xComp + (double) yComp * yComp + (double) zComp * zComp);
        if (len == 0.0) {
            return this;
        }
        double nx = xComp / len;
        double ny = yComp / len;
        double nz = zComp / len;
        double rad = Math.toRadians(deg);
        double c = Math.cos(rad);
        double s = Math.sin(rad);
        double omc = 1.0 - c;

        float[] rot = new float[] {
            (float) (c + nx * nx * omc),        (float) (nx * ny * omc - nz * s), (float) (nx * nz * omc + ny * s), 0.0f,
            (float) (ny * nx * omc + nz * s),   (float) (c + ny * ny * omc),      (float) (ny * nz * omc - nx * s), 0.0f,
            (float) (nz * nx * omc - ny * s),   (float) (nz * ny * omc + nx * s), (float) (c + nz * nz * omc),      0.0f,
            0.0f,                               0.0f,                             0.0f,                             1.0f
        };
        multiplyInPlace(rot);
        return this;
    }

    public Matrix44 scale(float x, float y, float z) {
        for (int r = 0; r < 4; r++) {
            int rowBase = r * 4;
            mBackingArray[rowBase] *= x;
            mBackingArray[rowBase + 1] *= y;
            mBackingArray[rowBase + 2] *= z;
        }
        return this;
    }

    public Matrix44 translate(float x, float y, float z) {
        for (int r = 0; r < 4; r++) {
            int rowBase = r * 4;
            mBackingArray[rowBase + 3] += mBackingArray[rowBase] * x
                    + mBackingArray[rowBase + 1] * y
                    + mBackingArray[rowBase + 2] * z;
        }
        return this;
    }

    @Override
    public String toString() {
        return String.format(
                "| %.4f %.4f %.4f %.4f |\n| %.4f %.4f %.4f %.4f |\n| %.4f %.4f %.4f %.4f |\n| %.4f %.4f %.4f %.4f |\n",
                mBackingArray[0], mBackingArray[1], mBackingArray[2], mBackingArray[3],
                mBackingArray[4], mBackingArray[5], mBackingArray[6], mBackingArray[7],
                mBackingArray[8], mBackingArray[9], mBackingArray[10], mBackingArray[11],
                mBackingArray[12], mBackingArray[13], mBackingArray[14], mBackingArray[15]);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Matrix44)) return false;
        return Arrays.equals(mBackingArray, ((Matrix44) obj).mBackingArray);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mBackingArray);
    }
}
