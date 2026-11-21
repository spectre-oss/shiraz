// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ScriptIntrinsicConvolve3x3 extends ScriptIntrinsic {
    private final float[] mValues = new float[9];
    private Allocation mInput;

    private ScriptIntrinsicConvolve3x3(long id, RenderScript rs) {
        super(id, rs);
        mValues[4] = 1.0f;
    }

    public static ScriptIntrinsicConvolve3x3 create(RenderScript rs, Element e) {
        rs.validate();
        if (!e.isCompatible(Element.U8(rs))
                && !e.isCompatible(Element.U8_2(rs))
                && !e.isCompatible(Element.U8_3(rs))
                && !e.isCompatible(Element.U8_4(rs))
                && !e.isCompatible(Element.F32(rs))
                && !e.isCompatible(Element.F32_2(rs))
                && !e.isCompatible(Element.F32_3(rs))
                && !e.isCompatible(Element.F32_4(rs))) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
        return new ScriptIntrinsicConvolve3x3(0, rs);
    }

    public void setInput(Allocation ain) {
        mRS.validate();
        if (ain == null) {
            throw new RSIllegalArgumentException("Input allocation cannot be null.");
        }
        mInput = ain;
    }

    public void setCoefficients(float[] v) {
        mRS.validate();
        if (v == null || v.length < 9) {
            throw new RSIllegalArgumentException("Coefficients array must have at least 9 elements.");
        }
        System.arraycopy(v, 0, mValues, 0, 9);
    }

    public void forEach(Allocation aout) {
        forEach(aout, null);
    }

    public void forEach(Allocation aout, Script.LaunchOptions opt) {
        mRS.validate();
        if (aout == null) {
            throw new RSIllegalArgumentException("Output allocation cannot be null.");
        }
        forEach(0, (Allocation) null, aout, null, opt);
    }

    void setGlobalField(int slot, Object value) {
        if (slot == 1 && value instanceof Allocation) {
            setInput((Allocation) value);
        } else {
            super.setGlobalField(slot, value);
        }
    }

    void executeKernel(int slot, Allocation[] ains, Allocation aout, FieldPacker v, LaunchOptions sc) {
        Allocation in = mInput;
        if (in == null && ains != null && ains.length > 0) {
            in = ains[0];
        }
        if (in == null) {
            throw new RSInvalidStateException("Input allocation is not set.");
        }
        if (aout == null) {
            throw new RSIllegalArgumentException("Output allocation cannot be null.");
        }
        convolve2D(in, aout, mValues, 1, sc);
    }

    static void convolve2D(Allocation in, Allocation aout, float[] coeff, int radius, LaunchOptions sc) {
        if (!in.getElement().isCompatible(aout.getElement())) {
            throw new RSIllegalArgumentException("Input and output element types must match.");
        }
        int width = aout.getType().getX();
        int height = Math.max(1, aout.getType().getY());
        if (in.getType().getX() != width || Math.max(1, in.getType().getY()) != height) {
            throw new RSIllegalArgumentException("Input and output dimensions must match.");
        }
        if (sc != null) {
            sc.validateForType(aout.getType());
        }

        int xStart = (sc != null && sc.getXEnd() > 0) ? sc.getXStart() : 0;
        int xEnd = (sc != null && sc.getXEnd() > 0) ? sc.getXEnd() : width;
        int yStart = (sc != null && sc.getYEnd() > 0) ? sc.getYStart() : 0;
        int yEnd = (sc != null && sc.getYEnd() > 0) ? sc.getYEnd() : height;

        Element elem = aout.getElement();
        boolean isFloat = elem.mType == Element.DataType.FLOAT_32;
        int vec = elem.getVectorSize();
        int stride = elem.getBytesSize();
        int kSize = 2 * radius + 1;

        byte[] src = in.rawBytes();
        if (in == aout) {
            src = src.clone();
        }
        byte[] dst = aout.rawBytes();
        ByteBuffer srcBuf = isFloat ? ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN) : null;
        ByteBuffer dstBuf = isFloat ? ByteBuffer.wrap(dst).order(ByteOrder.LITTLE_ENDIAN) : null;

        float[] sum = new float[4];
        for (int y = yStart; y < yEnd; y++) {
            for (int x = xStart; x < xEnd; x++) {
                sum[0] = 0f;
                sum[1] = 0f;
                sum[2] = 0f;
                sum[3] = 0f;
                for (int ky = -radius; ky <= radius; ky++) {
                    int sy = clamp(y + ky, height);
                    int kRow = (ky + radius) * kSize;
                    for (int kx = -radius; kx <= radius; kx++) {
                        int sx = clamp(x + kx, width);
                        float w = coeff[kRow + (kx + radius)];
                        int sOff = (sy * width + sx) * stride;
                        if (isFloat) {
                            for (int c = 0; c < vec; c++) {
                                sum[c] += srcBuf.getFloat(sOff + c * 4) * w;
                            }
                        } else {
                            for (int c = 0; c < vec; c++) {
                                sum[c] += (src[sOff + c] & 0xFF) * w;
                            }
                        }
                    }
                }
                int dOff = (y * width + x) * stride;
                if (isFloat) {
                    for (int c = 0; c < vec; c++) {
                        dstBuf.putFloat(dOff + c * 4, sum[c]);
                    }
                } else {
                    for (int c = 0; c < vec; c++) {
                        int iv = (int) (sum[c] + 0.5f);
                        dst[dOff + c] = (byte) Math.max(0, Math.min(255, iv));
                    }
                }
            }
        }
    }

    private static int clamp(int v, int limit) {
        if (v < 0) return 0;
        if (v >= limit) return limit - 1;
        return v;
    }

    public Script.KernelID getKernelID() {
        return createKernelID(0, 2, null, null);
    }

    public Script.FieldID getFieldID_Input() {
        return createFieldID(1, null);
    }
}
