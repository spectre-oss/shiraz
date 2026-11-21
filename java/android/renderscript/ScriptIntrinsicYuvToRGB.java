// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

public final class ScriptIntrinsicYuvToRGB extends ScriptIntrinsic {
    private Allocation mInput;

    ScriptIntrinsicYuvToRGB(long id, RenderScript rs) {
        super(id, rs);
    }

    public static ScriptIntrinsicYuvToRGB create(RenderScript rs, Element e) {
        rs.validate();
        if (!e.isCompatible(Element.U8_4(rs))) {
            throw new RSIllegalArgumentException("Element must be compatible with U8_4.");
        }
        return new ScriptIntrinsicYuvToRGB(0, rs);
    }

    public void setInput(Allocation ain) {
        mRS.validate();
        if (ain == null) {
            throw new RSIllegalArgumentException("Input allocation cannot be null.");
        }
        mInput = ain;
    }

    public void forEach(Allocation aout) {
        mRS.validate();
        if (aout == null) {
            throw new RSIllegalArgumentException("Output allocation cannot be null.");
        }
        forEach(0, (Allocation) null, aout, null, null);
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
        if (!aout.getElement().isCompatible(Element.U8_4(mRS))) {
            throw new RSIllegalArgumentException("Output must be compatible with U8_4.");
        }

        int width = aout.getType().getX();
        int height = Math.max(1, aout.getType().getY());
        byte[] yuv = in.rawBytes();
        byte[] rgba = aout.rawBytes();
        int frameSize = width * height;

        for (int y = 0; y < height; y++) {
            int yRow = y * width;
            int uvRow = frameSize + (y >> 1) * width;
            for (int x = 0; x < width; x++) {
                int yIdx = yRow + x;
                int Y = yIdx < yuv.length ? (yuv[yIdx] & 0xFF) : 0;

                // NV21 layout: V then U interleaved per 2x2 block
                int uvIdx = uvRow + (x & ~1);
                int V = (uvIdx < yuv.length) ? (yuv[uvIdx] & 0xFF) - 128 : 0;
                int U = (uvIdx + 1 < yuv.length) ? (yuv[uvIdx + 1] & 0xFF) - 128 : 0;

                // ITU-R BT.601 YUV -> RGB integer conversion (matching AOSP RenderScript cpu_ref)
                int y1192 = 1192 * Math.max(0, Y - 16);
                int r = (y1192 + 1634 * V) >> 10;
                int g = (y1192 - 833 * V - 400 * U) >> 10;
                int b = (y1192 + 2066 * U) >> 10;

                int dOff = yIdx * 4;
                rgba[dOff] = clampByte(r);
                rgba[dOff + 1] = clampByte(g);
                rgba[dOff + 2] = clampByte(b);
                rgba[dOff + 3] = (byte) 255;
            }
        }
    }

    private static byte clampByte(int v) {
        if (v < 0) return 0;
        if (v > 255) return (byte) 255;
        return (byte) v;
    }

    public Script.KernelID getKernelID() {
        return createKernelID(0, 2, null, null);
    }

    public Script.FieldID getFieldID_Input() {
        return createFieldID(0, null);
    }
}
