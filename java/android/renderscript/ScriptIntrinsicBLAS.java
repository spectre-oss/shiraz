// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ScriptIntrinsicBLAS extends ScriptIntrinsic {
    public static final int NO_TRANSPOSE = 111;
    public static final int TRANSPOSE = 112;
    public static final int CONJ_TRANSPOSE = 113;

    public static final int UPPER = 121;
    public static final int LOWER = 122;

    public static final int NON_UNIT = 131;
    public static final int UNIT = 132;

    public static final int LEFT = 141;
    public static final int RIGHT = 142;

    private ScriptIntrinsicBLAS(long id, RenderScript rs) {
        super(id, rs);
    }

    public static ScriptIntrinsicBLAS create(RenderScript rs) {
        rs.validate();
        if (rs.mBLAS == null) {
            rs.mBLAS = new ScriptIntrinsicBLAS(0, rs);
        }
        return rs.mBLAS;
    }

    private void validateTranspose(int trans, boolean allowConj) {
        if (trans != NO_TRANSPOSE && trans != TRANSPOSE
                && (!allowConj || trans != CONJ_TRANSPOSE)) {
            if (trans == CONJ_TRANSPOSE) {
                return; // Real BLAS treats CONJ_TRANSPOSE identically to TRANSPOSE
            }
            throw new RSIllegalArgumentException("Invalid transpose argument: " + trans);
        }
    }

    private void validateUplo(int uplo) {
        if (uplo != UPPER && uplo != LOWER) {
            throw new RSIllegalArgumentException("Invalid uplo argument: " + uplo);
        }
    }

    private void validateDiag(int diag) {
        if (diag != NON_UNIT && diag != UNIT) {
            throw new RSIllegalArgumentException("Invalid diag argument: " + diag);
        }
    }

    private void validateSide(int side) {
        if (side != LEFT && side != RIGHT) {
            throw new RSIllegalArgumentException("Invalid side argument: " + side);
        }
    }

    private void validateInc(int inc) {
        if (inc <= 0) {
            throw new RSIllegalArgumentException("Invalid stride increment: " + inc);
        }
    }

    private void checkAlloc(Allocation a, Element expected) {
        if (a == null) {
            throw new RSIllegalArgumentException("Allocation cannot be null.");
        }
        mRS.validateObject(a);
        if (!a.getElement().isCompatible(expected)) {
            throw new RSIllegalArgumentException("Element type mismatch.");
        }
    }

    private static int rows(Allocation a) {
        return Math.max(1, a.getType().getY());
    }

    private static int cols(Allocation a) {
        return a.getType().getX();
    }

    private static ByteBuffer buf(Allocation a) {
        return ByteBuffer.wrap(a.rawBytes()).order(ByteOrder.LITTLE_ENDIAN);
    }

    // Complex interleaved array [re0, im0, re1, im1, ...] helpers
    private static double[] readComplexMatrix(Allocation a, boolean isDouble) {
        int count = rows(a) * cols(a);
        double[] out = new double[count * 2];
        ByteBuffer b = buf(a);
        if (isDouble) {
            for (int i = 0; i < count; i++) {
                out[2 * i] = b.getDouble(i * 16);
                out[2 * i + 1] = b.getDouble(i * 16 + 8);
            }
        } else {
            for (int i = 0; i < count; i++) {
                out[2 * i] = b.getFloat(i * 8);
                out[2 * i + 1] = b.getFloat(i * 8 + 4);
            }
        }
        return out;
    }

    private static void writeComplexMatrix(Allocation a, double[] data, boolean isDouble) {
        int count = rows(a) * cols(a);
        ByteBuffer b = buf(a);
        if (isDouble) {
            for (int i = 0; i < count; i++) {
                b.putDouble(i * 16, data[2 * i]);
                b.putDouble(i * 16 + 8, data[2 * i + 1]);
            }
        } else {
            for (int i = 0; i < count; i++) {
                b.putFloat(i * 8, (float) data[2 * i]);
                b.putFloat(i * 8 + 4, (float) data[2 * i + 1]);
            }
        }
    }

    private static double[] readRealMatrix(Allocation a, boolean isDouble) {
        int count = rows(a) * cols(a);
        double[] out = new double[count];
        ByteBuffer b = buf(a);
        if (isDouble) {
            for (int i = 0; i < count; i++) {
                out[i] = b.getDouble(i * 8);
            }
        } else {
            for (int i = 0; i < count; i++) {
                out[i] = b.getFloat(i * 4);
            }
        }
        return out;
    }

    private static void writeRealMatrix(Allocation a, double[] data, boolean isDouble) {
        int count = rows(a) * cols(a);
        ByteBuffer b = buf(a);
        if (isDouble) {
            for (int i = 0; i < count; i++) {
                b.putDouble(i * 8, data[i]);
            }
        } else {
            for (int i = 0; i < count; i++) {
                b.putFloat(i * 4, (float) data[i]);
            }
        }
    }

    private static double getOpReal(double[] mat, int matRows, int matCols, int trans, int r, int c) {
        if (trans == NO_TRANSPOSE) {
            return mat[r * matCols + c];
        }
        return mat[c * matCols + r];
    }

    private static void getOpComplex(double[] mat, int matCols, int trans, int r, int c, double[] out) {
        if (trans == NO_TRANSPOSE) {
            int idx = (r * matCols + c) * 2;
            out[0] = mat[idx];
            out[1] = mat[idx + 1];
        } else {
            int idx = (c * matCols + r) * 2;
            out[0] = mat[idx];
            out[1] = (trans == CONJ_TRANSPOSE) ? -mat[idx + 1] : mat[idx + 1];
        }
    }

    // ==================== Level 2 Real & Complex GEMV ====================

    private void gemvReal(int transA, double alpha, Allocation A, Allocation X, int incX,
                          double beta, Allocation Y, int incY, boolean isDouble) {
        mRS.validate();
        validateTranspose(transA, false);
        validateInc(incX);
        validateInc(incY);
        Element el = isDouble ? Element.F64(mRS) : Element.F32(mRS);
        checkAlloc(A, el);
        checkAlloc(X, el);
        checkAlloc(Y, el);

        int aRows = rows(A);
        int aCols = cols(A);
        int m = (transA == NO_TRANSPOSE) ? aRows : aCols;
        int n = (transA == NO_TRANSPOSE) ? aCols : aRows;

        double[] aData = readRealMatrix(A, isDouble);
        double[] xData = readRealMatrix(X, isDouble);
        double[] yData = readRealMatrix(Y, isDouble);

        if (xData.length < 1 + (n - 1) * incX || yData.length < 1 + (m - 1) * incY) {
            throw new RSIllegalArgumentException("Vector dimension too small for GEMV.");
        }

        for (int i = 0; i < m; i++) {
            double sum = 0.0;
            for (int j = 0; j < n; j++) {
                sum += getOpReal(aData, aRows, aCols, transA, i, j) * xData[j * incX];
            }
            int yi = i * incY;
            yData[yi] = alpha * sum + (beta == 0.0 ? 0.0 : beta * yData[yi]);
        }
        writeRealMatrix(Y, yData, isDouble);
    }

    private void gemvComplex(int transA, double alphaRe, double alphaIm, Allocation A,
                             Allocation X, int incX, double betaRe, double betaIm,
                             Allocation Y, int incY, boolean isDouble) {
        mRS.validate();
        validateTranspose(transA, true);
        validateInc(incX);
        validateInc(incY);
        Element el = isDouble ? Element.F64_2(mRS) : Element.F32_2(mRS);
        checkAlloc(A, el);
        checkAlloc(X, el);
        checkAlloc(Y, el);

        int aRows = rows(A);
        int aCols = cols(A);
        int m = (transA == NO_TRANSPOSE) ? aRows : aCols;
        int n = (transA == NO_TRANSPOSE) ? aCols : aRows;

        double[] aData = readComplexMatrix(A, isDouble);
        double[] xData = readComplexMatrix(X, isDouble);
        double[] yData = readComplexMatrix(Y, isDouble);
        double[] aij = new double[2];

        for (int i = 0; i < m; i++) {
            double sumRe = 0.0, sumIm = 0.0;
            for (int j = 0; j < n; j++) {
                getOpComplex(aData, aCols, transA, i, j, aij);
                int xIdx = j * incX * 2;
                double xRe = xData[xIdx], xIm = xData[xIdx + 1];
                sumRe += aij[0] * xRe - aij[1] * xIm;
                sumIm += aij[0] * xIm + aij[1] * xRe;
            }
            double asRe = alphaRe * sumRe - alphaIm * sumIm;
            double asIm = alphaRe * sumIm + alphaIm * sumRe;
            int yIdx = i * incY * 2;
            if (betaRe == 0.0 && betaIm == 0.0) {
                yData[yIdx] = asRe;
                yData[yIdx + 1] = asIm;
            } else {
                double yRe = yData[yIdx], yIm = yData[yIdx + 1];
                yData[yIdx] = asRe + (betaRe * yRe - betaIm * yIm);
                yData[yIdx + 1] = asIm + (betaRe * yIm + betaIm * yRe);
            }
        }
        writeComplexMatrix(Y, yData, isDouble);
    }

    public void SGEMV(int TransA, float alpha, Allocation A, Allocation X, int incX, float beta, Allocation Y, int incY) {
        gemvReal(TransA, alpha, A, X, incX, beta, Y, incY, false);
    }

    public void DGEMV(int TransA, double alpha, Allocation A, Allocation X, int incX, double beta, Allocation Y, int incY) {
        gemvReal(TransA, alpha, A, X, incX, beta, Y, incY, true);
    }

    public void CGEMV(int TransA, Float2 alpha, Allocation A, Allocation X, int incX, Float2 beta, Allocation Y, int incY) {
        gemvComplex(TransA, alpha.x, alpha.y, A, X, incX, beta.x, beta.y, Y, incY, false);
    }

    public void ZGEMV(int TransA, Double2 alpha, Allocation A, Allocation X, int incX, Double2 beta, Allocation Y, int incY) {
        gemvComplex(TransA, alpha.x, alpha.y, A, X, incX, beta.x, beta.y, Y, incY, true);
    }

    // ==================== Banded & Packed Level 2 ====================

    private void validateBanded(int kl, int ku) {
        if (kl < 0 || ku < 0) {
            throw new RSIllegalArgumentException("Invalid band width.");
        }
    }

    public void SGBMV(int TransA, int KL, int KU, float alpha, Allocation A, Allocation X, int incX, float beta, Allocation Y, int incY) {
        validateBanded(KL, KU);
        SGEMV(TransA, alpha, A, X, incX, beta, Y, incY);
    }

    public void DGBMV(int TransA, int KL, int KU, double alpha, Allocation A, Allocation X, int incX, double beta, Allocation Y, int incY) {
        validateBanded(KL, KU);
        DGEMV(TransA, alpha, A, X, incX, beta, Y, incY);
    }

    public void CGBMV(int TransA, int KL, int KU, Float2 alpha, Allocation A, Allocation X, int incX, Float2 beta, Allocation Y, int incY) {
        validateBanded(KL, KU);
        CGEMV(TransA, alpha, A, X, incX, beta, Y, incY);
    }

    public void ZGBMV(int TransA, int KL, int KU, Double2 alpha, Allocation A, Allocation X, int incX, Double2 beta, Allocation Y, int incY) {
        validateBanded(KL, KU);
        ZGEMV(TransA, alpha, A, X, incX, beta, Y, incY);
    }

    private void trmvReal(int uplo, int transA, int diag, Allocation A, Allocation X, int incX, boolean solve, boolean isDouble) {
        mRS.validate();
        validateUplo(uplo);
        validateTranspose(transA, false);
        validateDiag(diag);
        validateInc(incX);
        Element el = isDouble ? Element.F64(mRS) : Element.F32(mRS);
        checkAlloc(A, el);
        checkAlloc(X, el);

        int n = rows(A);
        if (cols(A) != n) {
            throw new RSIllegalArgumentException("Triangular matrix A must be square.");
        }
        double[] aData = readRealMatrix(A, isDouble);
        double[] xData = readRealMatrix(X, isDouble);
        double[] out = xData.clone();

        boolean effectiveUpper = (transA == NO_TRANSPOSE) ? (uplo == UPPER) : (uplo == LOWER);
        if (!solve) {
            for (int i = 0; i < n; i++) {
                double sum = 0.0;
                for (int j = 0; j < n; j++) {
                    if (effectiveUpper && j < i) continue;
                    if (!effectiveUpper && j > i) continue;
                    double aVal = (i == j && diag == UNIT) ? 1.0 : getOpReal(aData, n, n, transA, i, j);
                    sum += aVal * xData[j * incX];
                }
                out[i * incX] = sum;
            }
        } else {
            if (effectiveUpper) {
                for (int i = n - 1; i >= 0; i--) {
                    double sum = out[i * incX];
                    for (int j = i + 1; j < n; j++) {
                        sum -= getOpReal(aData, n, n, transA, i, j) * out[j * incX];
                    }
                    if (diag == NON_UNIT) {
                        sum /= getOpReal(aData, n, n, transA, i, i);
                    }
                    out[i * incX] = sum;
                }
            } else {
                for (int i = 0; i < n; i++) {
                    double sum = out[i * incX];
                    for (int j = 0; j < i; j++) {
                        sum -= getOpReal(aData, n, n, transA, i, j) * out[j * incX];
                    }
                    if (diag == NON_UNIT) {
                        sum /= getOpReal(aData, n, n, transA, i, i);
                    }
                    out[i * incX] = sum;
                }
            }
        }
        writeRealMatrix(X, out, isDouble);
    }

    private void trmvComplex(int uplo, int transA, int diag, Allocation A, Allocation X, int incX, boolean solve, boolean isDouble) {
        mRS.validate();
        validateUplo(uplo);
        validateTranspose(transA, true);
        validateDiag(diag);
        validateInc(incX);
        Element el = isDouble ? Element.F64_2(mRS) : Element.F32_2(mRS);
        checkAlloc(A, el);
        checkAlloc(X, el);

        int n = rows(A);
        double[] aData = readComplexMatrix(A, isDouble);
        double[] xData = readComplexMatrix(X, isDouble);
        double[] out = xData.clone();
        double[] aij = new double[2];

        boolean effectiveUpper = (transA == NO_TRANSPOSE) ? (uplo == UPPER) : (uplo == LOWER);
        if (!solve) {
            for (int i = 0; i < n; i++) {
                double sumRe = 0.0, sumIm = 0.0;
                for (int j = 0; j < n; j++) {
                    if (effectiveUpper && j < i) continue;
                    if (!effectiveUpper && j > i) continue;
                    if (i == j && diag == UNIT) {
                        aij[0] = 1.0;
                        aij[1] = 0.0;
                    } else {
                        getOpComplex(aData, n, transA, i, j, aij);
                    }
                    int xIdx = j * incX * 2;
                    sumRe += aij[0] * xData[xIdx] - aij[1] * xData[xIdx + 1];
                    sumIm += aij[0] * xData[xIdx + 1] + aij[1] * xData[xIdx];
                }
                out[i * incX * 2] = sumRe;
                out[i * incX * 2 + 1] = sumIm;
            }
        } else {
            for (int step = 0; step < n; step++) {
                int i = effectiveUpper ? (n - 1 - step) : step;
                double sumRe = out[i * incX * 2];
                double sumIm = out[i * incX * 2 + 1];
                int jStart = effectiveUpper ? (i + 1) : 0;
                int jEnd = effectiveUpper ? n : i;
                for (int j = jStart; j < jEnd; j++) {
                    getOpComplex(aData, n, transA, i, j, aij);
                    double xRe = out[j * incX * 2], xIm = out[j * incX * 2 + 1];
                    sumRe -= (aij[0] * xRe - aij[1] * xIm);
                    sumIm -= (aij[0] * xIm + aij[1] * xRe);
                }
                if (diag == NON_UNIT) {
                    getOpComplex(aData, n, transA, i, i, aij);
                    double denom = aij[0] * aij[0] + aij[1] * aij[1];
                    double qRe = (sumRe * aij[0] + sumIm * aij[1]) / denom;
                    double qIm = (sumIm * aij[0] - sumRe * aij[1]) / denom;
                    sumRe = qRe;
                    sumIm = qIm;
                }
                out[i * incX * 2] = sumRe;
                out[i * incX * 2 + 1] = sumIm;
            }
        }
        writeComplexMatrix(X, out, isDouble);
    }

    public void STRMV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        trmvReal(Uplo, TransA, Diag, A, X, incX, false, false);
    }

    public void DTRMV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        trmvReal(Uplo, TransA, Diag, A, X, incX, false, true);
    }

    public void CTRMV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        trmvComplex(Uplo, TransA, Diag, A, X, incX, false, false);
    }

    public void ZTRMV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        trmvComplex(Uplo, TransA, Diag, A, X, incX, false, true);
    }

    public void STBMV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        if (K < 0) throw new RSIllegalArgumentException("Invalid K");
        mRS.validate();
        validateUplo(Uplo);
        validateTranspose(TransA, false);
        validateDiag(Diag);
        validateInc(incX);
        checkAlloc(A, Element.F32(mRS));
        checkAlloc(X, Element.F32(mRS));
    }

    public void DTBMV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        if (K < 0) throw new RSIllegalArgumentException("Invalid K");
        mRS.validate();
        validateUplo(Uplo);
        validateTranspose(TransA, false);
        validateDiag(Diag);
        validateInc(incX);
        checkAlloc(A, Element.F64(mRS));
        checkAlloc(X, Element.F64(mRS));
    }

    public void CTBMV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        if (K < 0) throw new RSIllegalArgumentException("Invalid K");
        mRS.validate();
        validateUplo(Uplo);
        validateTranspose(TransA, true);
        validateDiag(Diag);
        validateInc(incX);
        checkAlloc(A, Element.F32_2(mRS));
        checkAlloc(X, Element.F32_2(mRS));
    }

    public void ZTBMV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        if (K < 0) throw new RSIllegalArgumentException("Invalid K");
        mRS.validate();
        validateUplo(Uplo);
        validateTranspose(TransA, true);
        validateDiag(Diag);
        validateInc(incX);
        checkAlloc(A, Element.F64_2(mRS));
        checkAlloc(X, Element.F64_2(mRS));
    }

    public void STPMV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        mRS.validate();
        validateUplo(Uplo);
        validateTranspose(TransA, false);
        validateDiag(Diag);
        validateInc(incX);
        checkAlloc(Ap, Element.F32(mRS));
        checkAlloc(X, Element.F32(mRS));
    }

    public void DTPMV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        mRS.validate();
        validateUplo(Uplo);
        validateTranspose(TransA, false);
        validateDiag(Diag);
        validateInc(incX);
        checkAlloc(Ap, Element.F64(mRS));
        checkAlloc(X, Element.F64(mRS));
    }

    public void CTPMV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        mRS.validate();
        validateUplo(Uplo);
        validateTranspose(TransA, true);
        validateDiag(Diag);
        validateInc(incX);
        checkAlloc(Ap, Element.F32_2(mRS));
        checkAlloc(X, Element.F32_2(mRS));
    }

    public void ZTPMV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        mRS.validate();
        validateUplo(Uplo);
        validateTranspose(TransA, true);
        validateDiag(Diag);
        validateInc(incX);
        checkAlloc(Ap, Element.F64_2(mRS));
        checkAlloc(X, Element.F64_2(mRS));
    }

    public void STRSV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        trmvReal(Uplo, TransA, Diag, A, X, incX, true, false);
    }

    public void DTRSV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        trmvReal(Uplo, TransA, Diag, A, X, incX, true, true);
    }

    public void CTRSV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        trmvComplex(Uplo, TransA, Diag, A, X, incX, true, false);
    }

    public void ZTRSV(int Uplo, int TransA, int Diag, Allocation A, Allocation X, int incX) {
        trmvComplex(Uplo, TransA, Diag, A, X, incX, true, true);
    }

    public void STBSV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        STBMV(Uplo, TransA, Diag, K, A, X, incX);
    }

    public void DTBSV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        DTBMV(Uplo, TransA, Diag, K, A, X, incX);
    }

    public void CTBSV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        CTBMV(Uplo, TransA, Diag, K, A, X, incX);
    }

    public void ZTBSV(int Uplo, int TransA, int Diag, int K, Allocation A, Allocation X, int incX) {
        ZTBMV(Uplo, TransA, Diag, K, A, X, incX);
    }

    public void STPSV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        STPMV(Uplo, TransA, Diag, Ap, X, incX);
    }

    public void DTPSV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        DTPMV(Uplo, TransA, Diag, Ap, X, incX);
    }

    public void CTPSV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        CTPMV(Uplo, TransA, Diag, Ap, X, incX);
    }

    public void ZTPSV(int Uplo, int TransA, int Diag, Allocation Ap, Allocation X, int incX) {
        ZTPMV(Uplo, TransA, Diag, Ap, X, incX);
    }

    // ==================== Symmetric / Rank-1 / Rank-2 Level 2 ====================

    private void symvReal(int uplo, double alpha, Allocation A, Allocation X, int incX,
                          double beta, Allocation Y, int incY, boolean isDouble) {
        mRS.validate();
        validateUplo(uplo);
        validateInc(incX);
        validateInc(incY);
        Element el = isDouble ? Element.F64(mRS) : Element.F32(mRS);
        checkAlloc(A, el);
        checkAlloc(X, el);
        checkAlloc(Y, el);

        int n = rows(A);
        double[] aData = readRealMatrix(A, isDouble);
        double[] xData = readRealMatrix(X, isDouble);
        double[] yData = readRealMatrix(Y, isDouble);

        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int j = 0; j < n; j++) {
                double aij = (uplo == UPPER)
                        ? (i <= j ? aData[i * n + j] : aData[j * n + i])
                        : (i >= j ? aData[i * n + j] : aData[j * n + i]);
                sum += aij * xData[j * incX];
            }
            int yi = i * incY;
            yData[yi] = alpha * sum + (beta == 0.0 ? 0.0 : beta * yData[yi]);
        }
        writeRealMatrix(Y, yData, isDouble);
    }

    private void gerReal(double alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A, boolean isDouble) {
        mRS.validate();
        validateInc(incX);
        validateInc(incY);
        Element el = isDouble ? Element.F64(mRS) : Element.F32(mRS);
        checkAlloc(X, el);
        checkAlloc(Y, el);
        checkAlloc(A, el);

        int m = rows(A);
        int n = cols(A);
        double[] xData = readRealMatrix(X, isDouble);
        double[] yData = readRealMatrix(Y, isDouble);
        double[] aData = readRealMatrix(A, isDouble);

        for (int i = 0; i < m; i++) {
            double xi = alpha * xData[i * incX];
            for (int j = 0; j < n; j++) {
                aData[i * n + j] += xi * yData[j * incY];
            }
        }
        writeRealMatrix(A, aData, isDouble);
    }

    private void syrReal(int uplo, double alpha, Allocation X, int incX, Allocation A, boolean isDouble) {
        mRS.validate();
        validateUplo(uplo);
        validateInc(incX);
        Element el = isDouble ? Element.F64(mRS) : Element.F32(mRS);
        checkAlloc(X, el);
        checkAlloc(A, el);

        int n = rows(A);
        double[] xData = readRealMatrix(X, isDouble);
        double[] aData = readRealMatrix(A, isDouble);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((uplo == UPPER && i <= j) || (uplo == LOWER && i >= j)) {
                    aData[i * n + j] += alpha * xData[i * incX] * xData[j * incX];
                }
            }
        }
        writeRealMatrix(A, aData, isDouble);
    }

    private void syr2Real(int uplo, double alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A, boolean isDouble) {
        mRS.validate();
        validateUplo(uplo);
        validateInc(incX);
        validateInc(incY);
        Element el = isDouble ? Element.F64(mRS) : Element.F32(mRS);
        checkAlloc(X, el);
        checkAlloc(Y, el);
        checkAlloc(A, el);

        int n = rows(A);
        double[] xData = readRealMatrix(X, isDouble);
        double[] yData = readRealMatrix(Y, isDouble);
        double[] aData = readRealMatrix(A, isDouble);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((uplo == UPPER && i <= j) || (uplo == LOWER && i >= j)) {
                    aData[i * n + j] += alpha * (xData[i * incX] * yData[j * incY] + yData[i * incY] * xData[j * incX]);
                }
            }
        }
        writeRealMatrix(A, aData, isDouble);
    }

    public void SSYMV(int Uplo, float alpha, Allocation A, Allocation X, int incX, float beta, Allocation Y, int incY) {
        symvReal(Uplo, alpha, A, X, incX, beta, Y, incY, false);
    }

    public void SSBMV(int Uplo, int K, float alpha, Allocation A, Allocation X, int incX, float beta, Allocation Y, int incY) {
        if (K < 0) throw new RSIllegalArgumentException("Invalid K");
        validateUplo(Uplo);
        validateInc(incX);
        validateInc(incY);
        checkAlloc(A, Element.F32(mRS));
        checkAlloc(X, Element.F32(mRS));
        checkAlloc(Y, Element.F32(mRS));
    }

    public void SSPMV(int Uplo, float alpha, Allocation Ap, Allocation X, int incX, float beta, Allocation Y, int incY) {
        validateUplo(Uplo);
        validateInc(incX);
        validateInc(incY);
        checkAlloc(Ap, Element.F32(mRS));
        checkAlloc(X, Element.F32(mRS));
        checkAlloc(Y, Element.F32(mRS));
    }

    public void SGER(float alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        gerReal(alpha, X, incX, Y, incY, A, false);
    }

    public void SSYR(int Uplo, float alpha, Allocation X, int incX, Allocation A) {
        syrReal(Uplo, alpha, X, incX, A, false);
    }

    public void SSPR(int Uplo, float alpha, Allocation X, int incX, Allocation Ap) {
        validateUplo(Uplo);
        validateInc(incX);
        checkAlloc(X, Element.F32(mRS));
        checkAlloc(Ap, Element.F32(mRS));
    }

    public void SSYR2(int Uplo, float alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        syr2Real(Uplo, alpha, X, incX, Y, incY, A, false);
    }

    public void SSPR2(int Uplo, float alpha, Allocation X, int incX, Allocation Y, int incY, Allocation Ap) {
        validateUplo(Uplo);
        validateInc(incX);
        validateInc(incY);
        checkAlloc(X, Element.F32(mRS));
        checkAlloc(Y, Element.F32(mRS));
        checkAlloc(Ap, Element.F32(mRS));
    }

    public void DSYMV(int Uplo, double alpha, Allocation A, Allocation X, int incX, double beta, Allocation Y, int incY) {
        symvReal(Uplo, alpha, A, X, incX, beta, Y, incY, true);
    }

    public void DSBMV(int Uplo, int K, double alpha, Allocation A, Allocation X, int incX, double beta, Allocation Y, int incY) {
        if (K < 0) throw new RSIllegalArgumentException("Invalid K");
        validateUplo(Uplo);
        validateInc(incX);
        validateInc(incY);
        checkAlloc(A, Element.F64(mRS));
        checkAlloc(X, Element.F64(mRS));
        checkAlloc(Y, Element.F64(mRS));
    }

    public void DSPMV(int Uplo, double alpha, Allocation Ap, Allocation X, int incX, double beta, Allocation Y, int incY) {
        validateUplo(Uplo);
        validateInc(incX);
        validateInc(incY);
        checkAlloc(Ap, Element.F64(mRS));
        checkAlloc(X, Element.F64(mRS));
        checkAlloc(Y, Element.F64(mRS));
    }

    public void DGER(double alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        gerReal(alpha, X, incX, Y, incY, A, true);
    }

    public void DSYR(int Uplo, double alpha, Allocation X, int incX, Allocation A) {
        syrReal(Uplo, alpha, X, incX, A, true);
    }

    public void DSPR(int Uplo, double alpha, Allocation X, int incX, Allocation Ap) {
        validateUplo(Uplo);
        validateInc(incX);
        checkAlloc(X, Element.F64(mRS));
        checkAlloc(Ap, Element.F64(mRS));
    }

    public void DSYR2(int Uplo, double alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        syr2Real(Uplo, alpha, X, incX, Y, incY, A, true);
    }

    public void DSPR2(int Uplo, double alpha, Allocation X, int incX, Allocation Y, int incY, Allocation Ap) {
        validateUplo(Uplo);
        validateInc(incX);
        validateInc(incY);
        checkAlloc(X, Element.F64(mRS));
        checkAlloc(Y, Element.F64(mRS));
        checkAlloc(Ap, Element.F64(mRS));
    }

    // ==================== Hermitian Level 2 ====================

    private void gerComplex(double aRe, double aIm, Allocation X, int incX, Allocation Y, int incY,
                            Allocation A, boolean conjY, boolean isDouble) {
        mRS.validate();
        validateInc(incX);
        validateInc(incY);
        Element el = isDouble ? Element.F64_2(mRS) : Element.F32_2(mRS);
        checkAlloc(X, el);
        checkAlloc(Y, el);
        checkAlloc(A, el);

        int m = rows(A);
        int n = cols(A);
        double[] xData = readComplexMatrix(X, isDouble);
        double[] yData = readComplexMatrix(Y, isDouble);
        double[] aData = readComplexMatrix(A, isDouble);

        for (int i = 0; i < m; i++) {
            double xRe = xData[i * incX * 2];
            double xIm = xData[i * incX * 2 + 1];
            double axRe = aRe * xRe - aIm * xIm;
            double axIm = aRe * xIm + aIm * xRe;
            for (int j = 0; j < n; j++) {
                double yRe = yData[j * incY * 2];
                double yIm = conjY ? -yData[j * incY * 2 + 1] : yData[j * incY * 2 + 1];
                int idx = (i * n + j) * 2;
                aData[idx] += axRe * yRe - axIm * yIm;
                aData[idx + 1] += axRe * yIm + axIm * yRe;
            }
        }
        writeComplexMatrix(A, aData, isDouble);
    }

    public void CHEMV(int Uplo, Float2 alpha, Allocation A, Allocation X, int incX, Float2 beta, Allocation Y, int incY) {
        validateUplo(Uplo);
        CGEMV(NO_TRANSPOSE, alpha, A, X, incX, beta, Y, incY);
    }

    public void CHBMV(int Uplo, int K, Float2 alpha, Allocation A, Allocation X, int incX, Float2 beta, Allocation Y, int incY) {
        if (K < 0) throw new RSIllegalArgumentException("Invalid K");
        validateUplo(Uplo);
        validateInc(incX);
        validateInc(incY);
        checkAlloc(A, Element.F32_2(mRS));
        checkAlloc(X, Element.F32_2(mRS));
        checkAlloc(Y, Element.F32_2(mRS));
    }

    public void CHPMV(int Uplo, Float2 alpha, Allocation Ap, Allocation X, int incX, Float2 beta, Allocation Y, int incY) {
        validateUplo(Uplo);
        validateInc(incX);
        validateInc(incY);
        checkAlloc(Ap, Element.F32_2(mRS));
        checkAlloc(X, Element.F32_2(mRS));
        checkAlloc(Y, Element.F32_2(mRS));
    }

    public void CGERU(Float2 alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        gerComplex(alpha.x, alpha.y, X, incX, Y, incY, A, false, false);
    }

    public void CGERC(Float2 alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        gerComplex(alpha.x, alpha.y, X, incX, Y, incY, A, true, false);
    }

    public void CHER(int Uplo, float alpha, Allocation X, int incX, Allocation A) {
        validateUplo(Uplo);
        gerComplex(alpha, 0.0, X, incX, X, incX, A, true, false);
    }

    public void CHPR(int Uplo, float alpha, Allocation X, int incX, Allocation Ap) {
        validateUplo(Uplo);
        validateInc(incX);
        checkAlloc(X, Element.F32_2(mRS));
        checkAlloc(Ap, Element.F32_2(mRS));
    }

    public void CHER2(int Uplo, Float2 alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        validateUplo(Uplo);
        gerComplex(alpha.x, alpha.y, X, incX, Y, incY, A, true, false);
    }

    public void CHPR2(int Uplo, Float2 alpha, Allocation X, int incX, Allocation Y, int incY, Allocation Ap) {
        validateUplo(Uplo);
        validateInc(incX);
        validateInc(incY);
        checkAlloc(X, Element.F32_2(mRS));
        checkAlloc(Y, Element.F32_2(mRS));
        checkAlloc(Ap, Element.F32_2(mRS));
    }

    public void ZHEMV(int Uplo, Double2 alpha, Allocation A, Allocation X, int incX, Double2 beta, Allocation Y, int incY) {
        validateUplo(Uplo);
        ZGEMV(NO_TRANSPOSE, alpha, A, X, incX, beta, Y, incY);
    }

    public void ZHBMV(int Uplo, int K, Double2 alpha, Allocation A, Allocation X, int incX, Double2 beta, Allocation Y, int incY) {
        if (K < 0) throw new RSIllegalArgumentException("Invalid K");
        validateUplo(Uplo);
        validateInc(incX);
        validateInc(incY);
        checkAlloc(A, Element.F64_2(mRS));
        checkAlloc(X, Element.F64_2(mRS));
        checkAlloc(Y, Element.F64_2(mRS));
    }

    public void ZHPMV(int Uplo, Double2 alpha, Allocation Ap, Allocation X, int incX, Double2 beta, Allocation Y, int incY) {
        validateUplo(Uplo);
        validateInc(incX);
        validateInc(incY);
        checkAlloc(Ap, Element.F64_2(mRS));
        checkAlloc(X, Element.F64_2(mRS));
        checkAlloc(Y, Element.F64_2(mRS));
    }

    public void ZGERU(Double2 alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        gerComplex(alpha.x, alpha.y, X, incX, Y, incY, A, false, true);
    }

    public void ZGERC(Double2 alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        gerComplex(alpha.x, alpha.y, X, incX, Y, incY, A, true, true);
    }

    public void ZHER(int Uplo, double alpha, Allocation X, int incX, Allocation A) {
        validateUplo(Uplo);
        gerComplex(alpha, 0.0, X, incX, X, incX, A, true, true);
    }

    public void ZHPR(int Uplo, double alpha, Allocation X, int incX, Allocation Ap) {
        validateUplo(Uplo);
        validateInc(incX);
        checkAlloc(X, Element.F64_2(mRS));
        checkAlloc(Ap, Element.F64_2(mRS));
    }

    public void ZHER2(int Uplo, Double2 alpha, Allocation X, int incX, Allocation Y, int incY, Allocation A) {
        validateUplo(Uplo);
        gerComplex(alpha.x, alpha.y, X, incX, Y, incY, A, true, true);
    }

    public void ZHPR2(int Uplo, Double2 alpha, Allocation X, int incX, Allocation Y, int incY, Allocation Ap) {
        validateUplo(Uplo);
        validateInc(incX);
        validateInc(incY);
        checkAlloc(X, Element.F64_2(mRS));
        checkAlloc(Y, Element.F64_2(mRS));
        checkAlloc(Ap, Element.F64_2(mRS));
    }

    // ==================== Level 3 GEMM ====================

    private void gemmReal(int transA, int transB, double alpha, Allocation A, Allocation B,
                          double beta, Allocation C, boolean isDouble) {
        mRS.validate();
        validateTranspose(transA, false);
        validateTranspose(transB, false);
        Element el = isDouble ? Element.F64(mRS) : Element.F32(mRS);
        checkAlloc(A, el);
        checkAlloc(B, el);
        checkAlloc(C, el);

        int aRows = rows(A), aCols = cols(A);
        int bRows = rows(B), bCols = cols(B);
        int m = rows(C), n = cols(C);
        int opARows = (transA == NO_TRANSPOSE) ? aRows : aCols;
        int k = (transA == NO_TRANSPOSE) ? aCols : aRows;
        int opBRows = (transB == NO_TRANSPOSE) ? bRows : bCols;
        int opBCols = (transB == NO_TRANSPOSE) ? bCols : bRows;

        if (opARows != m || opBCols != n || opBRows != k) {
            throw new RSIllegalArgumentException("Dimension mismatch in GEMM.");
        }

        double[] aData = readRealMatrix(A, isDouble);
        double[] bData = readRealMatrix(B, isDouble);
        double[] cData = readRealMatrix(C, isDouble);

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double sum = 0.0;
                for (int p = 0; p < k; p++) {
                    double aip = getOpReal(aData, aRows, aCols, transA, i, p);
                    double bpj = getOpReal(bData, bRows, bCols, transB, p, j);
                    sum += aip * bpj;
                }
                int idx = i * n + j;
                cData[idx] = alpha * sum + (beta == 0.0 ? 0.0 : beta * cData[idx]);
            }
        }
        writeRealMatrix(C, cData, isDouble);
    }

    private void gemmComplex(int transA, int transB, double alphaRe, double alphaIm,
                             Allocation A, Allocation B, double betaRe, double betaIm,
                             Allocation C, boolean isDouble) {
        mRS.validate();
        validateTranspose(transA, true);
        validateTranspose(transB, true);
        Element el = isDouble ? Element.F64_2(mRS) : Element.F32_2(mRS);
        checkAlloc(A, el);
        checkAlloc(B, el);
        checkAlloc(C, el);

        int aRows = rows(A), aCols = cols(A);
        int bRows = rows(B), bCols = cols(B);
        int m = rows(C), n = cols(C);
        int opARows = (transA == NO_TRANSPOSE) ? aRows : aCols;
        int k = (transA == NO_TRANSPOSE) ? aCols : aRows;
        int opBRows = (transB == NO_TRANSPOSE) ? bRows : bCols;
        int opBCols = (transB == NO_TRANSPOSE) ? bCols : bRows;

        if (opARows != m || opBCols != n || opBRows != k) {
            throw new RSIllegalArgumentException("Dimension mismatch in CGEMM/ZGEMM.");
        }

        double[] aData = readComplexMatrix(A, isDouble);
        double[] bData = readComplexMatrix(B, isDouble);
        double[] cData = readComplexMatrix(C, isDouble);
        double[] aip = new double[2];
        double[] bpj = new double[2];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double sumRe = 0.0, sumIm = 0.0;
                for (int p = 0; p < k; p++) {
                    getOpComplex(aData, aCols, transA, i, p, aip);
                    getOpComplex(bData, bCols, transB, p, j, bpj);
                    sumRe += aip[0] * bpj[0] - aip[1] * bpj[1];
                    sumIm += aip[0] * bpj[1] + aip[1] * bpj[0];
                }
                double asRe = alphaRe * sumRe - alphaIm * sumIm;
                double asIm = alphaRe * sumIm + alphaIm * sumRe;
                int idx = (i * n + j) * 2;
                if (betaRe == 0.0 && betaIm == 0.0) {
                    cData[idx] = asRe;
                    cData[idx + 1] = asIm;
                } else {
                    double cRe = cData[idx], cIm = cData[idx + 1];
                    cData[idx] = asRe + (betaRe * cRe - betaIm * cIm);
                    cData[idx + 1] = asIm + (betaRe * cIm + betaIm * cRe);
                }
            }
        }
        writeComplexMatrix(C, cData, isDouble);
    }

    public void SGEMM(int TransA, int TransB, float alpha, Allocation A, Allocation B, float beta, Allocation C) {
        gemmReal(TransA, TransB, alpha, A, B, beta, C, false);
    }

    public void DGEMM(int TransA, int TransB, double alpha, Allocation A, Allocation B, double beta, Allocation C) {
        gemmReal(TransA, TransB, alpha, A, B, beta, C, true);
    }

    public void CGEMM(int TransA, int TransB, Float2 alpha, Allocation A, Allocation B, Float2 beta, Allocation C) {
        gemmComplex(TransA, TransB, alpha.x, alpha.y, A, B, beta.x, beta.y, C, false);
    }

    public void ZGEMM(int TransA, int TransB, Double2 alpha, Allocation A, Allocation B, Double2 beta, Allocation C) {
        gemmComplex(TransA, TransB, alpha.x, alpha.y, A, B, beta.x, beta.y, C, true);
    }

    // ==================== Level 3 SYMM / SYRK / SYR2K / TRMM / TRSM / HEMM / HERK / HER2K ====================

    private void symmReal(int side, int uplo, double alpha, Allocation A, Allocation B,
                          double beta, Allocation C, boolean isDouble) {
        mRS.validate();
        validateSide(side);
        validateUplo(uplo);
        Element el = isDouble ? Element.F64(mRS) : Element.F32(mRS);
        checkAlloc(A, el);
        checkAlloc(B, el);
        checkAlloc(C, el);

        int m = rows(C), n = cols(C);
        int k = (side == LEFT) ? m : n;
        if (rows(A) != k || cols(A) != k || rows(B) != m || cols(B) != n) {
            throw new RSIllegalArgumentException("Dimension mismatch in SYMM.");
        }
        double[] aData = readRealMatrix(A, isDouble);
        double[] bData = readRealMatrix(B, isDouble);
        double[] cData = readRealMatrix(C, isDouble);

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                double sum = 0.0;
                for (int p = 0; p < k; p++) {
                    if (side == LEFT) {
                        double aip = (uplo == UPPER)
                                ? (i <= p ? aData[i * k + p] : aData[p * k + i])
                                : (i >= p ? aData[i * k + p] : aData[p * k + i]);
                        sum += aip * bData[p * n + j];
                    } else {
                        double apj = (uplo == UPPER)
                                ? (p <= j ? aData[p * k + j] : aData[j * k + p])
                                : (p >= j ? aData[p * k + j] : aData[j * k + p]);
                        sum += bData[i * n + p] * apj;
                    }
                }
                int idx = i * n + j;
                cData[idx] = alpha * sum + (beta == 0.0 ? 0.0 : beta * cData[idx]);
            }
        }
        writeRealMatrix(C, cData, isDouble);
    }

    public void SSYMM(int Side, int Uplo, float alpha, Allocation A, Allocation B, float beta, Allocation C) {
        symmReal(Side, Uplo, alpha, A, B, beta, C, false);
    }

    public void DSYMM(int Side, int Uplo, double alpha, Allocation A, Allocation B, double beta, Allocation C) {
        symmReal(Side, Uplo, alpha, A, B, beta, C, true);
    }

    public void CSYMM(int Side, int Uplo, Float2 alpha, Allocation A, Allocation B, Float2 beta, Allocation C) {
        validateSide(Side);
        validateUplo(Uplo);
        if (Side == LEFT) {
            CGEMM(NO_TRANSPOSE, NO_TRANSPOSE, alpha, A, B, beta, C);
        } else {
            CGEMM(NO_TRANSPOSE, NO_TRANSPOSE, alpha, B, A, beta, C);
        }
    }

    public void ZSYMM(int Side, int Uplo, Double2 alpha, Allocation A, Allocation B, Double2 beta, Allocation C) {
        validateSide(Side);
        validateUplo(Uplo);
        if (Side == LEFT) {
            ZGEMM(NO_TRANSPOSE, NO_TRANSPOSE, alpha, A, B, beta, C);
        } else {
            ZGEMM(NO_TRANSPOSE, NO_TRANSPOSE, alpha, B, A, beta, C);
        }
    }

    private void syrkReal(int uplo, int trans, double alpha, Allocation A, double beta, Allocation C, boolean isDouble) {
        mRS.validate();
        validateUplo(uplo);
        validateTranspose(trans, false);
        Element el = isDouble ? Element.F64(mRS) : Element.F32(mRS);
        checkAlloc(A, el);
        checkAlloc(C, el);

        int n = rows(C);
        int aRows = rows(A), aCols = cols(A);
        int k = (trans == NO_TRANSPOSE) ? aCols : aRows;
        double[] aData = readRealMatrix(A, isDouble);
        double[] cData = readRealMatrix(C, isDouble);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((uplo == UPPER && i <= j) || (uplo == LOWER && i >= j)) {
                    double sum = 0.0;
                    for (int p = 0; p < k; p++) {
                        sum += getOpReal(aData, aRows, aCols, trans, i, p)
                                * getOpReal(aData, aRows, aCols, trans, j, p);
                    }
                    int idx = i * n + j;
                    cData[idx] = alpha * sum + (beta == 0.0 ? 0.0 : beta * cData[idx]);
                }
            }
        }
        writeRealMatrix(C, cData, isDouble);
    }

    public void SSYRK(int Uplo, int Trans, float alpha, Allocation A, float beta, Allocation C) {
        syrkReal(Uplo, Trans, alpha, A, beta, C, false);
    }

    public void DSYRK(int Uplo, int Trans, double alpha, Allocation A, double beta, Allocation C) {
        syrkReal(Uplo, Trans, alpha, A, beta, C, true);
    }

    public void CSYRK(int Uplo, int Trans, Float2 alpha, Allocation A, Float2 beta, Allocation C) {
        validateUplo(Uplo);
        validateTranspose(Trans, false);
        int opp = (Trans == NO_TRANSPOSE) ? TRANSPOSE : NO_TRANSPOSE;
        CGEMM(Trans, opp, alpha, A, A, beta, C);
    }

    public void ZSYRK(int Uplo, int Trans, Double2 alpha, Allocation A, Double2 beta, Allocation C) {
        validateUplo(Uplo);
        validateTranspose(Trans, false);
        int opp = (Trans == NO_TRANSPOSE) ? TRANSPOSE : NO_TRANSPOSE;
        ZGEMM(Trans, opp, alpha, A, A, beta, C);
    }

    private void syr2kReal(int uplo, int trans, double alpha, Allocation A, Allocation B,
                           double beta, Allocation C, boolean isDouble) {
        mRS.validate();
        validateUplo(uplo);
        validateTranspose(trans, false);
        Element el = isDouble ? Element.F64(mRS) : Element.F32(mRS);
        checkAlloc(A, el);
        checkAlloc(B, el);
        checkAlloc(C, el);

        int n = rows(C);
        int aRows = rows(A), aCols = cols(A);
        int k = (trans == NO_TRANSPOSE) ? aCols : aRows;
        double[] aData = readRealMatrix(A, isDouble);
        double[] bData = readRealMatrix(B, isDouble);
        double[] cData = readRealMatrix(C, isDouble);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((uplo == UPPER && i <= j) || (uplo == LOWER && i >= j)) {
                    double sum = 0.0;
                    for (int p = 0; p < k; p++) {
                        sum += getOpReal(aData, aRows, aCols, trans, i, p) * getOpReal(bData, aRows, aCols, trans, j, p)
                                + getOpReal(bData, aRows, aCols, trans, i, p) * getOpReal(aData, aRows, aCols, trans, j, p);
                    }
                    int idx = i * n + j;
                    cData[idx] = alpha * sum + (beta == 0.0 ? 0.0 : beta * cData[idx]);
                }
            }
        }
        writeRealMatrix(C, cData, isDouble);
    }

    public void SSYR2K(int Uplo, int Trans, float alpha, Allocation A, Allocation B, float beta, Allocation C) {
        syr2kReal(Uplo, Trans, alpha, A, B, beta, C, false);
    }

    public void DSYR2K(int Uplo, int Trans, double alpha, Allocation A, Allocation B, double beta, Allocation C) {
        syr2kReal(Uplo, Trans, alpha, A, B, beta, C, true);
    }

    public void CSYR2K(int Uplo, int Trans, Float2 alpha, Allocation A, Allocation B, Float2 beta, Allocation C) {
        validateUplo(Uplo);
        validateTranspose(Trans, false);
        int opp = (Trans == NO_TRANSPOSE) ? TRANSPOSE : NO_TRANSPOSE;
        CGEMM(Trans, opp, alpha, A, B, beta, C);
        CGEMM(Trans, opp, alpha, B, A, new Float2(1.0f, 0.0f), C);
    }

    public void ZSYR2K(int Uplo, int Trans, Double2 alpha, Allocation A, Allocation B, Double2 beta, Allocation C) {
        validateUplo(Uplo);
        validateTranspose(Trans, false);
        int opp = (Trans == NO_TRANSPOSE) ? TRANSPOSE : NO_TRANSPOSE;
        ZGEMM(Trans, opp, alpha, A, B, beta, C);
        ZGEMM(Trans, opp, alpha, B, A, new Double2(1.0, 0.0), C);
    }

    private void trmmReal(int side, int uplo, int transA, int diag, double alpha,
                          Allocation A, Allocation B, boolean solve, boolean isDouble) {
        mRS.validate();
        validateSide(side);
        validateUplo(uplo);
        validateTranspose(transA, false);
        validateDiag(diag);
        Element el = isDouble ? Element.F64(mRS) : Element.F32(mRS);
        checkAlloc(A, el);
        checkAlloc(B, el);

        int m = rows(B), n = cols(B);
        int k = (side == LEFT) ? m : n;
        if (rows(A) != k || cols(A) != k) {
            throw new RSIllegalArgumentException("Triangular matrix A dimension mismatch.");
        }
        double[] aData = readRealMatrix(A, isDouble);
        double[] bData = readRealMatrix(B, isDouble);
        double[] out = new double[m * n];

        boolean effectiveUpper = (transA == NO_TRANSPOSE) ? (uplo == UPPER) : (uplo == LOWER);
        if (!solve) {
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    double sum = 0.0;
                    for (int p = 0; p < k; p++) {
                        if (side == LEFT) {
                            if ((effectiveUpper && p < i) || (!effectiveUpper && p > i)) continue;
                            double aVal = (i == p && diag == UNIT) ? 1.0 : getOpReal(aData, k, k, transA, i, p);
                            sum += aVal * bData[p * n + j];
                        } else {
                            if ((effectiveUpper && j < p) || (!effectiveUpper && j > p)) continue;
                            double aVal = (p == j && diag == UNIT) ? 1.0 : getOpReal(aData, k, k, transA, p, j);
                            sum += bData[i * n + p] * aVal;
                        }
                    }
                    out[i * n + j] = alpha * sum;
                }
            }
        } else {
            for (int i = 0; i < m * n; i++) {
                out[i] = alpha * bData[i];
            }
            if (side == LEFT) {
                for (int j = 0; j < n; j++) {
                    for (int step = 0; step < m; step++) {
                        int i = effectiveUpper ? (m - 1 - step) : step;
                        double sum = out[i * n + j];
                        int pStart = effectiveUpper ? (i + 1) : 0;
                        int pEnd = effectiveUpper ? m : i;
                        for (int p = pStart; p < pEnd; p++) {
                            sum -= getOpReal(aData, k, k, transA, i, p) * out[p * n + j];
                        }
                        if (diag == NON_UNIT) {
                            sum /= getOpReal(aData, k, k, transA, i, i);
                        }
                        out[i * n + j] = sum;
                    }
                }
            } else {
                for (int i = 0; i < m; i++) {
                    for (int step = 0; step < n; step++) {
                        int j = effectiveUpper ? step : (n - 1 - step);
                        double sum = out[i * n + j];
                        int pStart = effectiveUpper ? 0 : (j + 1);
                        int pEnd = effectiveUpper ? j : n;
                        for (int p = pStart; p < pEnd; p++) {
                            sum -= out[i * n + p] * getOpReal(aData, k, k, transA, p, j);
                        }
                        if (diag == NON_UNIT) {
                            sum /= getOpReal(aData, k, k, transA, j, j);
                        }
                        out[i * n + j] = sum;
                    }
                }
            }
        }
        writeRealMatrix(B, out, isDouble);
    }

    public void STRMM(int Side, int Uplo, int TransA, int Diag, float alpha, Allocation A, Allocation B) {
        trmmReal(Side, Uplo, TransA, Diag, alpha, A, B, false, false);
    }

    public void DTRMM(int Side, int Uplo, int TransA, int Diag, double alpha, Allocation A, Allocation B) {
        trmmReal(Side, Uplo, TransA, Diag, alpha, A, B, false, true);
    }

    public void CTRMM(int Side, int Uplo, int TransA, int Diag, Float2 alpha, Allocation A, Allocation B) {
        mRS.validate();
        validateSide(Side);
        validateUplo(Uplo);
        validateTranspose(TransA, true);
        validateDiag(Diag);
        checkAlloc(A, Element.F32_2(mRS));
        checkAlloc(B, Element.F32_2(mRS));
        Allocation tmp = Allocation.createTyped(mRS, B.getType());
        tmp.copyFrom(B);
        if (Side == LEFT) {
            CGEMM(TransA, NO_TRANSPOSE, alpha, A, tmp, new Float2(0f, 0f), B);
        } else {
            CGEMM(NO_TRANSPOSE, TransA, alpha, tmp, A, new Float2(0f, 0f), B);
        }
        tmp.destroy();
    }

    public void ZTRMM(int Side, int Uplo, int TransA, int Diag, Double2 alpha, Allocation A, Allocation B) {
        mRS.validate();
        validateSide(Side);
        validateUplo(Uplo);
        validateTranspose(TransA, true);
        validateDiag(Diag);
        checkAlloc(A, Element.F64_2(mRS));
        checkAlloc(B, Element.F64_2(mRS));
        Allocation tmp = Allocation.createTyped(mRS, B.getType());
        tmp.copyFrom(B);
        if (Side == LEFT) {
            ZGEMM(TransA, NO_TRANSPOSE, alpha, A, tmp, new Double2(0.0, 0.0), B);
        } else {
            ZGEMM(NO_TRANSPOSE, TransA, alpha, tmp, A, new Double2(0.0, 0.0), B);
        }
        tmp.destroy();
    }

    public void STRSM(int Side, int Uplo, int TransA, int Diag, float alpha, Allocation A, Allocation B) {
        trmmReal(Side, Uplo, TransA, Diag, alpha, A, B, true, false);
    }

    public void DTRSM(int Side, int Uplo, int TransA, int Diag, double alpha, Allocation A, Allocation B) {
        trmmReal(Side, Uplo, TransA, Diag, alpha, A, B, true, true);
    }

    public void CTRSM(int Side, int Uplo, int TransA, int Diag, Float2 alpha, Allocation A, Allocation B) {
        mRS.validate();
        validateSide(Side);
        validateUplo(Uplo);
        validateTranspose(TransA, true);
        validateDiag(Diag);
        checkAlloc(A, Element.F32_2(mRS));
        checkAlloc(B, Element.F32_2(mRS));
    }

    public void ZTRSM(int Side, int Uplo, int TransA, int Diag, Double2 alpha, Allocation A, Allocation B) {
        mRS.validate();
        validateSide(Side);
        validateUplo(Uplo);
        validateTranspose(TransA, true);
        validateDiag(Diag);
        checkAlloc(A, Element.F64_2(mRS));
        checkAlloc(B, Element.F64_2(mRS));
    }

    public void CHEMM(int Side, int Uplo, Float2 alpha, Allocation A, Allocation B, Float2 beta, Allocation C) {
        CSYMM(Side, Uplo, alpha, A, B, beta, C);
    }

    public void ZHEMM(int Side, int Uplo, Double2 alpha, Allocation A, Allocation B, Double2 beta, Allocation C) {
        ZSYMM(Side, Uplo, alpha, A, B, beta, C);
    }

    public void CHERK(int Uplo, int Trans, float alpha, Allocation A, float beta, Allocation C) {
        validateUplo(Uplo);
        validateTranspose(Trans, true);
        int opp = (Trans == NO_TRANSPOSE) ? CONJ_TRANSPOSE : NO_TRANSPOSE;
        CGEMM(Trans, opp, new Float2(alpha, 0f), A, A, new Float2(beta, 0f), C);
    }

    public void ZHERK(int Uplo, int Trans, double alpha, Allocation A, double beta, Allocation C) {
        validateUplo(Uplo);
        validateTranspose(Trans, true);
        int opp = (Trans == NO_TRANSPOSE) ? CONJ_TRANSPOSE : NO_TRANSPOSE;
        ZGEMM(Trans, opp, new Double2(alpha, 0.0), A, A, new Double2(beta, 0.0), C);
    }

    public void CHER2K(int Uplo, int Trans, Float2 alpha, Allocation A, Allocation B, float beta, Allocation C) {
        validateUplo(Uplo);
        validateTranspose(Trans, true);
        int opp = (Trans == NO_TRANSPOSE) ? CONJ_TRANSPOSE : NO_TRANSPOSE;
        CGEMM(Trans, opp, alpha, A, B, new Float2(beta, 0f), C);
        CGEMM(Trans, opp, new Float2(alpha.x, -alpha.y), B, A, new Float2(1f, 0f), C);
    }

    public void ZHER2K(int Uplo, int Trans, Double2 alpha, Allocation A, Allocation B, double beta, Allocation C) {
        validateUplo(Uplo);
        validateTranspose(Trans, true);
        int opp = (Trans == NO_TRANSPOSE) ? CONJ_TRANSPOSE : NO_TRANSPOSE;
        ZGEMM(Trans, opp, alpha, A, B, new Double2(beta, 0.0), C);
        ZGEMM(Trans, opp, new Double2(alpha.x, -alpha.y), B, A, new Double2(1.0, 0.0), C);
    }

    // ==================== 8-Bit Quantized GEMM (BNNM) ====================

    public void BNNM(Allocation A, int a_offset, Allocation B, int b_offset, Allocation C, int c_offset, int c_mult) {
        mRS.validate();
        checkAlloc(A, Element.U8(mRS));
        checkAlloc(B, Element.U8(mRS));
        checkAlloc(C, Element.U8(mRS));

        int k = cols(A);
        int m = rows(A);
        int n = rows(B);
        if (cols(B) != k || rows(C) != m || cols(C) != n) {
            throw new RSIllegalArgumentException("Dimension mismatch in BNNM.");
        }

        byte[] aBytes = A.rawBytes();
        byte[] bBytes = B.rawBytes();
        byte[] cBytes = C.rawBytes();

        for (int i = 0; i < m; i++) {
            int aRow = i * k;
            int cRow = i * n;
            for (int j = 0; j < n; j++) {
                int bRow = j * k;
                long acc = 0L;
                for (int p = 0; p < k; p++) {
                    int av = (aBytes[aRow + p] & 0xFF) - a_offset;
                    int bv = (bBytes[bRow + p] & 0xFF) - b_offset;
                    acc += (long) av * (long) bv;
                }
                long scaled = ((acc + c_offset) * c_mult + (1L << 20)) >> 21;
                if (scaled < 0L) scaled = 0L;
                if (scaled > 255L) scaled = 255L;
                cBytes[cRow + j] = (byte) scaled;
            }
        }
    }
}
