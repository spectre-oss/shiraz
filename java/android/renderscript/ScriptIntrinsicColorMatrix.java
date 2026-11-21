// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ScriptIntrinsicColorMatrix extends ScriptIntrinsic {
    private final Matrix4f mMatrix = new Matrix4f();
    private final Float4 mAdd = new Float4();

    private ScriptIntrinsicColorMatrix(long id, RenderScript rs) {
        super(id, rs);
    }

    @Deprecated
    public static ScriptIntrinsicColorMatrix create(RenderScript rs, Element e) {
        return create(rs);
    }

    public static ScriptIntrinsicColorMatrix create(RenderScript rs) {
        rs.validate();
        return new ScriptIntrinsicColorMatrix(0, rs);
    }

    public void setColorMatrix(Matrix4f m) {
        mRS.validate();
        mMatrix.load(m);
    }

    public void setColorMatrix(Matrix3f m) {
        mRS.validate();
        mMatrix.load(m);
    }

    public void setAdd(Float4 f) {
        mRS.validate();
        mAdd.x = f.x;
        mAdd.y = f.y;
        mAdd.z = f.z;
        mAdd.w = f.w;
    }

    public void setAdd(float r, float g, float b, float a) {
        mRS.validate();
        mAdd.x = r;
        mAdd.y = g;
        mAdd.z = b;
        mAdd.w = a;
    }

    public void setGreyscale() {
        mRS.validate();
        mMatrix.loadIdentity();
        mMatrix.set(0, 0, 0.299f);
        mMatrix.set(1, 0, 0.587f);
        mMatrix.set(2, 0, 0.114f);
        mMatrix.set(0, 1, 0.299f);
        mMatrix.set(1, 1, 0.587f);
        mMatrix.set(2, 1, 0.114f);
        mMatrix.set(0, 2, 0.299f);
        mMatrix.set(1, 2, 0.587f);
        mMatrix.set(2, 2, 0.114f);
    }

    public void setYUVtoRGB() {
        mRS.validate();
        mMatrix.loadIdentity();
        mMatrix.set(0, 0, 1.0f);
        mMatrix.set(1, 0, 0.0f);
        mMatrix.set(2, 0, 1.13983f);
        mMatrix.set(0, 1, 1.0f);
        mMatrix.set(1, 1, -0.39465f);
        mMatrix.set(2, 1, -0.5806f);
        mMatrix.set(0, 2, 1.0f);
        mMatrix.set(1, 2, 2.03211f);
        mMatrix.set(2, 2, 0.0f);
    }

    public void setRGBtoYUV() {
        mRS.validate();
        mMatrix.loadIdentity();
        mMatrix.set(0, 0, 0.299f);
        mMatrix.set(1, 0, 0.587f);
        mMatrix.set(2, 0, 0.114f);
        mMatrix.set(0, 1, -0.14713f);
        mMatrix.set(1, 1, -0.28886f);
        mMatrix.set(2, 1, 0.436f);
        mMatrix.set(0, 2, 0.615f);
        mMatrix.set(1, 2, -0.51499f);
        mMatrix.set(2, 2, -0.10001f);
    }

    public void forEach(Allocation ain, Allocation aout) {
        forEach(ain, aout, null);
    }

    public void forEach(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        mRS.validate();
        if (ain == null || aout == null) {
            throw new RSIllegalArgumentException("Input and output allocations must be non-null.");
        }
        validateElement(ain.getElement());
        validateElement(aout.getElement());
        forEach(0, ain, aout, null, opt);
    }

    private void validateElement(Element e) {
        if (!e.isCompatible(Element.U8(mRS))
                && !e.isCompatible(Element.U8_2(mRS))
                && !e.isCompatible(Element.U8_3(mRS))
                && !e.isCompatible(Element.U8_4(mRS))
                && !e.isCompatible(Element.F32(mRS))
                && !e.isCompatible(Element.F32_2(mRS))
                && !e.isCompatible(Element.F32_3(mRS))
                && !e.isCompatible(Element.F32_4(mRS))) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
    }

    void executeKernel(int slot, Allocation[] ains, Allocation aout, FieldPacker v, LaunchOptions sc) {
        if (ains == null || ains.length < 1 || ains[0] == null || aout == null) {
            throw new RSIllegalArgumentException("Input and output allocations are required.");
        }
        Allocation ain = ains[0];
        validateElement(ain.getElement());
        validateElement(aout.getElement());

        Type inType = ain.getType();
        Type outType = aout.getType();
        if (inType.getX() != outType.getX()
                || inType.getY() != outType.getY()
                || inType.getZ() != outType.getZ()) {
            throw new RSIllegalArgumentException("Input and output dimensions must match.");
        }
        if (sc != null) {
            sc.validateForType(outType);
        }

        int dimX = outType.getX();
        int dimY = Math.max(1, outType.getY());
        int dimZ = Math.max(1, outType.getZ());

        int xStart = (sc != null && sc.getXEnd() > 0) ? sc.getXStart() : 0;
        int xEnd = (sc != null && sc.getXEnd() > 0) ? sc.getXEnd() : dimX;
        int yStart = (sc != null && sc.getYEnd() > 0) ? sc.getYStart() : 0;
        int yEnd = (sc != null && sc.getYEnd() > 0) ? sc.getYEnd() : dimY;
        int zStart = (sc != null && sc.getZEnd() > 0) ? sc.getZStart() : 0;
        int zEnd = (sc != null && sc.getZEnd() > 0) ? sc.getZEnd() : dimZ;

        Element inElem = ain.getElement();
        Element outElem = aout.getElement();
        boolean inFloat = inElem.mType == Element.DataType.FLOAT_32;
        boolean outFloat = outElem.mType == Element.DataType.FLOAT_32;
        int inVec = inElem.getVectorSize();
        int outVec = outElem.getVectorSize();
        int inStride = inElem.getBytesSize();
        int outStride = outElem.getBytesSize();

        byte[] src = ain.rawBytes();
        if (ain == aout) {
            src = src.clone();
        }
        byte[] dst = aout.rawBytes();
        ByteBuffer srcBuf = inFloat ? ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN) : null;
        ByteBuffer dstBuf = outFloat ? ByteBuffer.wrap(dst).order(ByteOrder.LITTLE_ENDIAN) : null;

        float[] m = mMatrix.getArray();
        float m00 = m[0], m01 = m[1], m02 = m[2], m03 = m[3];
        float m10 = m[4], m11 = m[5], m12 = m[6], m13 = m[7];
        float m20 = m[8], m21 = m[9], m22 = m[10], m23 = m[11];
        float m30 = m[12], m31 = m[13], m32 = m[14], m33 = m[15];
        float ax = mAdd.x, ay = mAdd.y, az = mAdd.z, aw = mAdd.w;
        final float inv255 = 1.0f / 255.0f;

        for (int z = zStart; z < zEnd; z++) {
            for (int y = yStart; y < yEnd; y++) {
                int rowIndex = (z * dimY + y) * dimX;
                for (int x = xStart; x < xEnd; x++) {
                    int idx = rowIndex + x;
                    int sOff = idx * inStride;
                    int dOff = idx * outStride;

                    float r = 0.0f, g = 0.0f, b = 0.0f, a = 1.0f;
                    if (inFloat) {
                        r = srcBuf.getFloat(sOff);
                        if (inVec >= 2) g = srcBuf.getFloat(sOff + 4);
                        if (inVec >= 3) b = srcBuf.getFloat(sOff + 8);
                        if (inVec >= 4) a = srcBuf.getFloat(sOff + 12);
                    } else {
                        r = (src[sOff] & 0xFF) * inv255;
                        if (inVec >= 2) g = (src[sOff + 1] & 0xFF) * inv255;
                        if (inVec >= 3) b = (src[sOff + 2] & 0xFF) * inv255;
                        if (inVec >= 4) a = (src[sOff + 3] & 0xFF) * inv255;
                    }

                    // Matrix4f is column-major: col 0 is [m00,m01,m02,m03], col 1 is [m10..m13], etc.
                    float or = m00 * r + m10 * g + m20 * b + m30 * a + ax;
                    float og = m01 * r + m11 * g + m21 * b + m31 * a + ay;
                    float ob = m02 * r + m12 * g + m22 * b + m32 * a + az;
                    float oa = m03 * r + m13 * g + m23 * b + m33 * a + aw;

                    if (outFloat) {
                        dstBuf.putFloat(dOff, or);
                        if (outVec >= 2) dstBuf.putFloat(dOff + 4, og);
                        if (outVec >= 3) dstBuf.putFloat(dOff + 8, ob);
                        if (outVec >= 4) dstBuf.putFloat(dOff + 12, oa);
                    } else {
                        dst[dOff] = clampNormByte(or);
                        if (outVec >= 2) dst[dOff + 1] = clampNormByte(og);
                        if (outVec >= 3) dst[dOff + 2] = clampNormByte(ob);
                        if (outVec >= 4) dst[dOff + 3] = clampNormByte(oa);
                    }
                }
            }
        }
    }

    private static byte clampNormByte(float v) {
        if (v <= 0.0f) {
            return 0;
        }
        if (v >= 1.0f) {
            return (byte) 255;
        }
        return (byte) (int) (v * 255.0f + 0.5f);
    }

    public Script.KernelID getKernelID() {
        return createKernelID(0, 3, null, null);
    }
}
