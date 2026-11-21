// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

public final class ScriptIntrinsicBlur extends ScriptIntrinsic {
    private Allocation mInput;
    private float mRadius = 5.0f;
    private float[] mWeights;
    private int mKernelRadius;

    private ScriptIntrinsicBlur(long id, RenderScript rs) {
        super(id, rs);
        rebuildWeights(5.0f);
    }

    public static ScriptIntrinsicBlur create(RenderScript rs, Element e) {
        rs.validate();
        if (!e.isCompatible(Element.U8_4(rs)) && !e.isCompatible(Element.U8(rs))) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
        return new ScriptIntrinsicBlur(0, rs);
    }

    public void setInput(Allocation ain) {
        mRS.validate();
        if (ain == null) {
            throw new RSIllegalArgumentException("Input allocation cannot be null.");
        }
        if (ain.getType().getY() == 0) {
            throw new RSIllegalArgumentException("Input set to a 1D Allocation");
        }
        Element e = ain.getElement();
        if (!e.isCompatible(Element.U8_4(mRS)) && !e.isCompatible(Element.U8(mRS))) {
            throw new RSIllegalArgumentException("Unsupported element type.");
        }
        mInput = ain;
    }

    public void setRadius(float radius) {
        mRS.validate();
        if (radius <= 0.0f || radius > 25.0f) {
            throw new RSIllegalArgumentException("Radius out of range (0 < r <= 25).");
        }
        mRadius = radius;
        rebuildWeights(radius);
    }

    private void rebuildWeights(float radius) {
        int r = Math.max(1, (int) Math.ceil(radius));
        float sigma = 0.4f * radius + 0.6f;
        float twoSigmaSq = 2.0f * sigma * sigma;
        float[] weights = new float[2 * r + 1];
        float sum = 0.0f;
        for (int i = -r; i <= r; i++) {
            float w = (float) Math.exp(-(i * i) / twoSigmaSq);
            weights[i + r] = w;
            sum += w;
        }
        for (int i = 0; i < weights.length; i++) {
            weights[i] /= sum;
        }
        mKernelRadius = r;
        mWeights = weights;
    }

    public void forEach(Allocation aout) {
        forEach(aout, null);
    }

    public void forEach(Allocation aout, Script.LaunchOptions opt) {
        mRS.validate();
        if (aout == null) {
            throw new RSIllegalArgumentException("Output allocation cannot be null.");
        }
        if (aout.getType().getY() == 0) {
            throw new RSIllegalArgumentException("Output is a 1D Allocation");
        }
        forEach(0, (Allocation) null, aout, null, opt);
    }

    void setGlobalField(int slot, Object value) {
        if (slot == 1 && value instanceof Allocation) {
            setInput((Allocation) value);
        } else if (slot == 0 && value instanceof Number) {
            setRadius(((Number) value).floatValue());
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
            throw new RSIllegalArgumentException("Input and output element types do not match.");
        }
        int width = aout.getType().getX();
        int height = aout.getType().getY();
        if (in.getType().getX() != width || in.getType().getY() != height) {
            throw new RSIllegalArgumentException("Input and output dimensions must match.");
        }
        if (sc != null) {
            sc.validateForType(aout.getType());
        }

        int channels = aout.getElement().getBytesSize();
        byte[] src = in.rawBytes();
        if (in == aout) {
            src = src.clone();
        }
        byte[] dst = aout.rawBytes();

        int xStart = (sc != null && sc.getXEnd() > 0) ? sc.getXStart() : 0;
        int xEnd = (sc != null && sc.getXEnd() > 0) ? sc.getXEnd() : width;
        int yStart = (sc != null && sc.getYEnd() > 0) ? sc.getYStart() : 0;
        int yEnd = (sc != null && sc.getYEnd() > 0) ? sc.getYEnd() : height;

        int r = mKernelRadius;
        float[] weights = mWeights;

        // Horizontal pass over needed rows [max(0, yStart - r) .. min(height, yEnd + r))
        int hRowStart = Math.max(0, yStart - r);
        int hRowEnd = Math.min(height, yEnd + r);
        float[] horiz = new float[(hRowEnd - hRowStart) * width * channels];

        for (int y = hRowStart; y < hRowEnd; y++) {
            int srcRowBase = y * width * channels;
            int tmpRowBase = (y - hRowStart) * width * channels;
            for (int x = 0; x < width; x++) {
                int outOff = tmpRowBase + x * channels;
                if (channels == 4) {
                    float c0 = 0f, c1 = 0f, c2 = 0f, c3 = 0f;
                    for (int k = -r; k <= r; k++) {
                        int sx = clampCoord(x + k, width);
                        int sOff = srcRowBase + sx * 4;
                        float w = weights[k + r];
                        c0 += (src[sOff] & 0xFF) * w;
                        c1 += (src[sOff + 1] & 0xFF) * w;
                        c2 += (src[sOff + 2] & 0xFF) * w;
                        c3 += (src[sOff + 3] & 0xFF) * w;
                    }
                    horiz[outOff] = c0;
                    horiz[outOff + 1] = c1;
                    horiz[outOff + 2] = c2;
                    horiz[outOff + 3] = c3;
                } else {
                    float c0 = 0f;
                    for (int k = -r; k <= r; k++) {
                        int sx = clampCoord(x + k, width);
                        c0 += (src[srcRowBase + sx] & 0xFF) * weights[k + r];
                    }
                    horiz[outOff] = c0;
                }
            }
        }

        // Vertical pass over [yStart..yEnd), [xStart..xEnd)
        for (int y = yStart; y < yEnd; y++) {
            int dstRowBase = y * width * channels;
            for (int x = xStart; x < xEnd; x++) {
                int dstOff = dstRowBase + x * channels;
                if (channels == 4) {
                    float c0 = 0f, c1 = 0f, c2 = 0f, c3 = 0f;
                    for (int k = -r; k <= r; k++) {
                        int sy = clampCoord(y + k, height) - hRowStart;
                        int tOff = (sy * width + x) * 4;
                        float w = weights[k + r];
                        c0 += horiz[tOff] * w;
                        c1 += horiz[tOff + 1] * w;
                        c2 += horiz[tOff + 2] * w;
                        c3 += horiz[tOff + 3] * w;
                    }
                    dst[dstOff] = clampByte(c0);
                    dst[dstOff + 1] = clampByte(c1);
                    dst[dstOff + 2] = clampByte(c2);
                    dst[dstOff + 3] = clampByte(c3);
                } else {
                    float c0 = 0f;
                    for (int k = -r; k <= r; k++) {
                        int sy = clampCoord(y + k, height) - hRowStart;
                        c0 += horiz[sy * width + x] * weights[k + r];
                    }
                    dst[dstOff] = clampByte(c0);
                }
            }
        }
    }

    private static int clampCoord(int v, int limit) {
        if (v < 0) {
            return 0;
        }
        if (v >= limit) {
            return limit - 1;
        }
        return v;
    }

    private static byte clampByte(float v) {
        int i = (int) (v + 0.5f);
        if (i < 0) {
            return 0;
        }
        if (i > 255) {
            return (byte) 255;
        }
        return (byte) i;
    }

    public Script.KernelID getKernelID() {
        return createKernelID(0, 2, null, null);
    }

    public Script.FieldID getFieldID_Input() {
        return createFieldID(1, null);
    }
}
