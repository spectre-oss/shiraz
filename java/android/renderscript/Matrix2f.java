// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

public class Matrix2f {
    final float[] mMat;

    public Matrix2f() {
        mMat = new float[4];
        loadIdentity();
    }

    public Matrix2f(float[] dataArray) {
        mMat = new float[4];
        System.arraycopy(dataArray, 0, mMat, 0, mMat.length);
    }

    public float[] getArray() {
        return mMat;
    }

    public float get(int x, int y) {
        return mMat[x * 2 + y];
    }

    public void set(int x, int y, float v) {
        mMat[x * 2 + y] = v;
    }

    public void loadIdentity() {
        mMat[0] = 1;
        mMat[1] = 0;
        mMat[2] = 0;
        mMat[3] = 1;
    }

    public void load(Matrix2f src) {
        System.arraycopy(src.getArray(), 0, mMat, 0, mMat.length);
    }

    public void loadRotate(float rot) {
        rot *= (float) (Math.PI / 180.0f);
        float c = (float) Math.cos(rot);
        float s = (float) Math.sin(rot);
        mMat[0] = c;
        mMat[1] = -s;
        mMat[2] = s;
        mMat[3] = c;
    }

    public void loadScale(float x, float y) {
        loadIdentity();
        mMat[0] = x;
        mMat[3] = y;
    }

    public void loadMultiply(Matrix2f lhs, Matrix2f rhs) {
        float[] out = new float[4];
        for (int i = 0; i < 2; i++) {
            float ri0 = 0;
            float ri1 = 0;
            for (int j = 0; j < 2; j++) {
                float rhs_ij = rhs.get(i, j);
                ri0 += lhs.get(j, 0) * rhs_ij;
                ri1 += lhs.get(j, 1) * rhs_ij;
            }
            out[i * 2] = ri0;
            out[i * 2 + 1] = ri1;
        }
        System.arraycopy(out, 0, mMat, 0, 4);
    }

    public void multiply(Matrix2f rhs) {
        Matrix2f tmp = new Matrix2f();
        tmp.loadMultiply(this, rhs);
        load(tmp);
    }

    public void rotate(float rot) {
        Matrix2f tmp = new Matrix2f();
        tmp.loadRotate(rot);
        multiply(tmp);
    }

    public void scale(float x, float y) {
        Matrix2f tmp = new Matrix2f();
        tmp.loadScale(x, y);
        multiply(tmp);
    }

    public void transpose() {
        float temp = mMat[1];
        mMat[1] = mMat[2];
        mMat[2] = temp;
    }
}
