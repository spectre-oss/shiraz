// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ScriptIntrinsicHistogram extends ScriptIntrinsic {
    private Allocation mOut;
    private float mDotR = 0.299f;
    private float mDotG = 0.587f;
    private float mDotB = 0.114f;
    private float mDotA = 0.0f;

    private ScriptIntrinsicHistogram(long id, RenderScript rs) {
        super(id, rs);
    }

    public static ScriptIntrinsicHistogram create(RenderScript rs, Element e) {
        rs.validate();
        if (!e.isCompatible(Element.U8_4(rs))
                && !e.isCompatible(Element.U8_3(rs))
                && !e.isCompatible(Element.U8_2(rs))
                && !e.isCompatible(Element.U8(rs))) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
        return new ScriptIntrinsicHistogram(0, rs);
    }

    public void forEach(Allocation ain) {
        forEach(ain, null);
    }

    public void forEach(Allocation ain, Script.LaunchOptions opt) {
        mRS.validate();
        if (ain == null) {
            throw new RSIllegalArgumentException("Input cannot be null.");
        }
        if (mOut == null) {
            throw new RSIllegalArgumentException("Output is null.");
        }
        if (ain.getType().getElement().getVectorSize()
                < mOut.getType().getElement().getVectorSize()) {
            throw new RSIllegalArgumentException(
                    "Input vector size must be >= output vector size.");
        }
        if (!ain.getType().getElement().isCompatible(Element.U8(mRS))
                && !ain.getType().getElement().isCompatible(Element.U8_2(mRS))
                && !ain.getType().getElement().isCompatible(Element.U8_3(mRS))
                && !ain.getType().getElement().isCompatible(Element.U8_4(mRS))) {
            throw new RSIllegalArgumentException("Input type must be U8, U8_1, U8_2 or U8_4.");
        }
        forEach(0, ain, null, null, opt);
    }

    public void setDotCoefficients(float r, float g, float b, float a) {
        mRS.validate();
        if (r < 0.0f || g < 0.0f || b < 0.0f || a < 0.0f) {
            throw new RSIllegalArgumentException("Coefficient may not be negative.");
        }
        if ((r + g + b + a) > 1.0f) {
            throw new RSIllegalArgumentException("Sum of coefficients must be 1.0 or less.");
        }
        mDotR = r;
        mDotG = g;
        mDotB = b;
        mDotA = a;
    }

    public void setOutput(Allocation aout) {
        mRS.validate();
        if (aout == null) {
            throw new RSIllegalArgumentException("Output cannot be null.");
        }
        if (aout.getType().getX() != 256
                || aout.getType().getY() != 0
                || aout.getType().hasMipmaps()
                || aout.getType().getYuv() != 0) {
            throw new RSIllegalArgumentException("Output must be 1D, 256 elements.");
        }
        Element.DataType dt = aout.getType().getElement().mType;
        if (dt != Element.DataType.UNSIGNED_32 && dt != Element.DataType.SIGNED_32) {
            throw new RSIllegalArgumentException("Output type must be U32 or I32.");
        }
        mOut = aout;
    }

    public void forEach_Dot(Allocation ain) {
        forEach_Dot(ain, null);
    }

    public void forEach_Dot(Allocation ain, Script.LaunchOptions opt) {
        mRS.validate();
        if (ain == null) {
            throw new RSIllegalArgumentException("Input cannot be null.");
        }
        if (mOut == null) {
            throw new RSIllegalArgumentException("Output is null.");
        }
        if (mOut.getType().getElement().getVectorSize() != 1) {
            throw new RSIllegalArgumentException("Output vector size must be 1.");
        }
        if (!ain.getType().getElement().isCompatible(Element.U8(mRS))
                && !ain.getType().getElement().isCompatible(Element.U8_2(mRS))
                && !ain.getType().getElement().isCompatible(Element.U8_3(mRS))
                && !ain.getType().getElement().isCompatible(Element.U8_4(mRS))) {
            throw new RSIllegalArgumentException("Input type must be U8, U8_1, U8_2 or U8_4.");
        }
        forEach(1, ain, null, null, opt);
    }

    void setGlobalField(int slot, Object value) {
        if (slot == 1 && value instanceof Allocation) {
            setOutput((Allocation) value);
        } else {
            super.setGlobalField(slot, value);
        }
    }

    void executeKernel(int slot, Allocation[] ains, Allocation aout, FieldPacker v, LaunchOptions sc) {
        if (ains == null || ains.length < 1 || ains[0] == null) {
            throw new RSIllegalArgumentException("Input allocation is required.");
        }
        Allocation out = mOut != null ? mOut : aout;
        if (out == null) {
            throw new RSInvalidStateException("Output allocation is not set.");
        }
        Allocation ain = ains[0];
        Type inType = ain.getType();
        if (sc != null) {
            sc.validateForType(inType);
        }

        int dimX = inType.getX();
        int dimY = Math.max(1, inType.getY());
        int dimZ = Math.max(1, inType.getZ());

        int xStart = (sc != null && sc.getXEnd() > 0) ? sc.getXStart() : 0;
        int xEnd = (sc != null && sc.getXEnd() > 0) ? sc.getXEnd() : dimX;
        int yStart = (sc != null && sc.getYEnd() > 0) ? sc.getYStart() : 0;
        int yEnd = (sc != null && sc.getYEnd() > 0) ? sc.getYEnd() : dimY;
        int zStart = (sc != null && sc.getZEnd() > 0) ? sc.getZStart() : 0;
        int zEnd = (sc != null && sc.getZEnd() > 0) ? sc.getZEnd() : dimZ;

        Element inElem = ain.getElement();
        int inVec = inElem.getVectorSize();
        int inStride = inElem.getBytesSize();
        byte[] src = ain.rawBytes();

        Element outElem = out.getElement();
        int outVec = outElem.getVectorSize();
        int outStride = outElem.getBytesSize();
        byte[] dst = out.rawBytes();
        java.util.Arrays.fill(dst, (byte) 0);
        ByteBuffer dstBuf = ByteBuffer.wrap(dst).order(ByteOrder.LITTLE_ENDIAN);

        if (slot == 0) {
            int[][] bins = new int[4][256];
            for (int z = zStart; z < zEnd; z++) {
                for (int y = yStart; y < yEnd; y++) {
                    int rowBase = (z * dimY + y) * dimX;
                    for (int x = xStart; x < xEnd; x++) {
                        int off = (rowBase + x) * inStride;
                        for (int c = 0; c < outVec; c++) {
                            int vByte = c < inVec ? (src[off + c] & 0xFF) : 0;
                            bins[c][vByte]++;
                        }
                    }
                }
            }
            for (int b = 0; b < 256; b++) {
                int dOff = b * outStride;
                for (int c = 0; c < outVec; c++) {
                    dstBuf.putInt(dOff + c * 4, bins[c][b]);
                }
            }
        } else {
            int[] bins = new int[256];
            for (int z = zStart; z < zEnd; z++) {
                for (int y = yStart; y < yEnd; y++) {
                    int rowBase = (z * dimY + y) * dimX;
                    for (int x = xStart; x < xEnd; x++) {
                        int off = (rowBase + x) * inStride;
                        int r = src[off] & 0xFF;
                        int g = inVec >= 2 ? (src[off + 1] & 0xFF) : 0;
                        int b = inVec >= 3 ? (src[off + 2] & 0xFF) : 0;
                        int a = inVec >= 4 ? (src[off + 3] & 0xFF) : 0;
                        int dot = (int) (mDotR * r + mDotG * g + mDotB * b + mDotA * a + 0.5f);
                        if (dot < 0) dot = 0;
                        if (dot > 255) dot = 255;
                        bins[dot]++;
                    }
                }
            }
            for (int b = 0; b < 256; b++) {
                dstBuf.putInt(b * 4, bins[b]);
            }
        }
    }

    public Script.KernelID getKernelID_Separate() {
        return createKernelID(0, 3, null, null);
    }

    public Script.FieldID getFieldID_Input() {
        return createFieldID(1, null);
    }
}
