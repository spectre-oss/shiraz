// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

public final class ScriptIntrinsicLUT extends ScriptIntrinsic {
    private final byte[] mCache = new byte[1024];

    private ScriptIntrinsicLUT(long id, RenderScript rs) {
        super(id, rs);
        for (int ct = 0; ct < 256; ct++) {
            mCache[ct] = (byte) ct;
            mCache[ct + 256] = (byte) ct;
            mCache[ct + 512] = (byte) ct;
            mCache[ct + 768] = (byte) ct;
        }
    }

    public static ScriptIntrinsicLUT create(RenderScript rs, Element e) {
        rs.validate();
        if (!e.isCompatible(Element.U8_4(rs))) {
            throw new RSIllegalArgumentException("Element must be compatible with U8_4.");
        }
        return new ScriptIntrinsicLUT(0, rs);
    }

    public void destroy() {
        super.destroy();
    }

    private void validate(int index, int value) {
        if (index < 0 || index > 255) {
            throw new RSIllegalArgumentException("Index out of range (0-255).");
        }
        if (value < 0 || value > 255) {
            throw new RSIllegalArgumentException("Value out of range (0-255).");
        }
    }

    public void setRed(int index, int value) {
        mRS.validate();
        validate(index, value);
        mCache[index] = (byte) value;
    }

    public void setGreen(int index, int value) {
        mRS.validate();
        validate(index, value);
        mCache[index + 256] = (byte) value;
    }

    public void setBlue(int index, int value) {
        mRS.validate();
        validate(index, value);
        mCache[index + 512] = (byte) value;
    }

    public void setAlpha(int index, int value) {
        mRS.validate();
        validate(index, value);
        mCache[index + 768] = (byte) value;
    }

    public void forEach(Allocation ain, Allocation aout) {
        forEach(ain, aout, null);
    }

    public void forEach(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        mRS.validate();
        if (ain == null || aout == null) {
            throw new RSIllegalArgumentException("Input and output allocations must be non-null.");
        }
        forEach(0, ain, aout, null, opt);
    }

    void executeKernel(int slot, Allocation[] ains, Allocation aout, FieldPacker v, LaunchOptions sc) {
        if (ains == null || ains.length < 1 || ains[0] == null || aout == null) {
            throw new RSIllegalArgumentException("Input and output allocations are required.");
        }
        Allocation ain = ains[0];
        if (!ain.getElement().isCompatible(Element.U8_4(mRS))
                || !aout.getElement().isCompatible(Element.U8_4(mRS))) {
            throw new RSIllegalArgumentException("Element must be compatible with U8_4.");
        }
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

        byte[] src = ain.rawBytes();
        byte[] dst = aout.rawBytes();
        byte[] lut = mCache;

        for (int z = zStart; z < zEnd; z++) {
            for (int y = yStart; y < yEnd; y++) {
                int rowBase = (z * dimY + y) * dimX;
                for (int x = xStart; x < xEnd; x++) {
                    int off = (rowBase + x) * 4;
                    int r = src[off] & 0xFF;
                    int g = src[off + 1] & 0xFF;
                    int b = src[off + 2] & 0xFF;
                    int a = src[off + 3] & 0xFF;
                    dst[off] = lut[r];
                    dst[off + 1] = lut[g + 256];
                    dst[off + 2] = lut[b + 512];
                    dst[off + 3] = lut[a + 768];
                }
            }
        }
    }

    public Script.KernelID getKernelID() {
        return createKernelID(0, 3, null, null);
    }
}
