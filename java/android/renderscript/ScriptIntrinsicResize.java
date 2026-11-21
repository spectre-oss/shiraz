// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ScriptIntrinsicResize extends ScriptIntrinsic {
    private Allocation mInput;

    private ScriptIntrinsicResize(long id, RenderScript rs) {
        super(id, rs);
    }

    public static ScriptIntrinsicResize create(RenderScript rs) {
        rs.validate();
        return new ScriptIntrinsicResize(0, rs);
    }

    public void setInput(Allocation ain) {
        mRS.validate();
        if (ain == null) {
            throw new RSIllegalArgumentException("Input allocation cannot be null.");
        }
        Element e = ain.getElement();
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
        mInput = ain;
    }

    public Script.FieldID getFieldID_Input() {
        return createFieldID(0, null);
    }

    public void forEach_bicubic(Allocation aout) {
        if (aout == mInput) {
            throw new RSIllegalArgumentException("Output cannot be same as Input.");
        }
        forEach_bicubic(aout, null);
    }

    public void forEach_bicubic(Allocation aout, Script.LaunchOptions opt) {
        mRS.validate();
        if (aout == null) {
            throw new RSIllegalArgumentException("Output allocation cannot be null.");
        }
        if (aout == mInput) {
            throw new RSIllegalArgumentException("Output cannot be same as Input.");
        }
        forEach(0, (Allocation) null, aout, null, opt);
    }

    void setGlobalField(int slot, Object value) {
        if (slot == 0 && value instanceof Allocation) {
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
        if (!in.getElement().isCompatible(aout.getElement())) {
            throw new RSIllegalArgumentException("Data type must match.");
        }
        if (sc != null) {
            sc.validateForType(aout.getType());
        }

        int srcW = in.getType().getX();
        int srcH = Math.max(1, in.getType().getY());
        int dstW = aout.getType().getX();
        int dstH = Math.max(1, aout.getType().getY());

        int xStart = (sc != null && sc.getXEnd() > 0) ? sc.getXStart() : 0;
        int xEnd = (sc != null && sc.getXEnd() > 0) ? sc.getXEnd() : dstW;
        int yStart = (sc != null && sc.getYEnd() > 0) ? sc.getYStart() : 0;
        int yEnd = (sc != null && sc.getYEnd() > 0) ? sc.getYEnd() : dstH;

        Element elem = aout.getElement();
        boolean isFloat = elem.mType == Element.DataType.FLOAT_32;
        int vec = elem.getVectorSize();
        int stride = elem.getBytesSize();

        byte[] src = in.rawBytes();
        byte[] dst = aout.rawBytes();
        ByteBuffer srcBuf = isFloat ? ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN) : null;
        ByteBuffer dstBuf = isFloat ? ByteBuffer.wrap(dst).order(ByteOrder.LITTLE_ENDIAN) : null;

        float scaleX = (float) srcW / (float) dstW;
        float scaleY = (float) srcH / (float) dstH;
        float[] wx = new float[4];
        float[] wy = new float[4];
        float[] acc = new float[4];

        for (int dy = yStart; dy < yEnd; dy++) {
            float fy = (dy + 0.5f) * scaleY - 0.5f;
            int iy = (int) Math.floor(fy);
            float ty = fy - iy;
            catmullRomWeights(ty, wy);

            for (int dx = xStart; dx < xEnd; dx++) {
                float fx = (dx + 0.5f) * scaleX - 0.5f;
                int ix = (int) Math.floor(fx);
                float tx = fx - ix;
                catmullRomWeights(tx, wx);

                acc[0] = 0f;
                acc[1] = 0f;
                acc[2] = 0f;
                acc[3] = 0f;

                for (int ky = 0; ky < 4; ky++) {
                    int sy = clamp(iy - 1 + ky, srcH);
                    float wRow = wy[ky];
                    for (int kx = 0; kx < 4; kx++) {
                        int sx = clamp(ix - 1 + kx, srcW);
                        float w = wRow * wx[kx];
                        int sOff = (sy * srcW + sx) * stride;
                        if (isFloat) {
                            for (int c = 0; c < vec; c++) {
                                acc[c] += srcBuf.getFloat(sOff + c * 4) * w;
                            }
                        } else {
                            for (int c = 0; c < vec; c++) {
                                acc[c] += (src[sOff + c] & 0xFF) * w;
                            }
                        }
                    }
                }

                int dOff = (dy * dstW + dx) * stride;
                if (isFloat) {
                    for (int c = 0; c < vec; c++) {
                        dstBuf.putFloat(dOff + c * 4, acc[c]);
                    }
                } else {
                    for (int c = 0; c < vec; c++) {
                        int iv = (int) (acc[c] + 0.5f);
                        dst[dOff + c] = (byte) Math.max(0, Math.min(255, iv));
                    }
                }
            }
        }
    }

    private static void catmullRomWeights(float t, float[] out) {
        float t2 = t * t;
        float t3 = t2 * t;
        out[0] = 0.5f * (-t + 2.0f * t2 - t3);
        out[1] = 0.5f * (2.0f - 5.0f * t2 + 3.0f * t3);
        out[2] = 0.5f * (t + 4.0f * t2 - 3.0f * t3);
        out[3] = 0.5f * (-t2 + t3);
    }

    private static int clamp(int v, int limit) {
        if (v < 0) return 0;
        if (v >= limit) return limit - 1;
        return v;
    }

    public Script.KernelID getKernelID_bicubic() {
        return createKernelID(0, 2, null, null);
    }
}
