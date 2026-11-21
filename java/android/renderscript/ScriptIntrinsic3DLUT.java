// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

public final class ScriptIntrinsic3DLUT extends ScriptIntrinsic {
    private Allocation mLUT;
    private Element mElement;

    private ScriptIntrinsic3DLUT(long id, RenderScript rs, Element e) {
        super(id, rs);
        mElement = e;
    }

    public static ScriptIntrinsic3DLUT create(RenderScript rs, Element e) {
        rs.validate();
        if (!e.isCompatible(Element.U8_4(rs))) {
            throw new RSIllegalArgumentException("Element must be compatible with uchar4.");
        }
        return new ScriptIntrinsic3DLUT(0, rs, e);
    }

    public void setLUT(Allocation lut) {
        mRS.validate();
        if (lut == null) {
            throw new RSIllegalArgumentException("LUT cannot be null.");
        }
        Type t = lut.getType();
        if (t.getZ() == 0) {
            throw new RSIllegalArgumentException("LUT must be a 3D Allocation.");
        }
        if (!t.getElement().isCompatible(mElement)) {
            throw new RSIllegalArgumentException("LUT element type must match.");
        }
        mLUT = lut;
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
        if (mLUT == null) {
            throw new RSInvalidStateException("3D LUT allocation is not set.");
        }
        if (ains == null || ains.length < 1 || ains[0] == null || aout == null) {
            throw new RSIllegalArgumentException("Input and output allocations are required.");
        }
        Allocation ain = ains[0];
        if (!ain.getElement().isCompatible(mElement) || !aout.getElement().isCompatible(mElement)) {
            throw new RSIllegalArgumentException("Element must be compatible with uchar4.");
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

        Type lutType = mLUT.getType();
        int lx = lutType.getX();
        int ly = lutType.getY();
        int lz = lutType.getZ();
        byte[] lut = mLUT.rawBytes();

        byte[] src = ain.rawBytes();
        byte[] dst = aout.rawBytes();

        float scaleX = (lx - 1) / 255.0f;
        float scaleY = (ly - 1) / 255.0f;
        float scaleZ = (lz - 1) / 255.0f;

        for (int z = zStart; z < zEnd; z++) {
            for (int y = yStart; y < yEnd; y++) {
                int rowBase = (z * dimY + y) * dimX;
                for (int x = xStart; x < xEnd; x++) {
                    int off = (rowBase + x) * 4;
                    int r = src[off] & 0xFF;
                    int g = src[off + 1] & 0xFF;
                    int b = src[off + 2] & 0xFF;
                    byte a = src[off + 3];

                    float fx = r * scaleX;
                    float fy = g * scaleY;
                    float fz = b * scaleZ;

                    int x0 = (int) fx;
                    int y0 = (int) fy;
                    int z0 = (int) fz;
                    int x1 = Math.min(x0 + 1, lx - 1);
                    int y1 = Math.min(y0 + 1, ly - 1);
                    int z1 = Math.min(z0 + 1, lz - 1);

                    float dx = fx - x0;
                    float dy = fy - y0;
                    float dz = fz - z0;

                    int o000 = (((z0 * ly + y0) * lx) + x0) * 4;
                    int o100 = (((z0 * ly + y0) * lx) + x1) * 4;
                    int o010 = (((z0 * ly + y1) * lx) + x0) * 4;
                    int o110 = (((z0 * ly + y1) * lx) + x1) * 4;
                    int o001 = (((z1 * ly + y0) * lx) + x0) * 4;
                    int o101 = (((z1 * ly + y0) * lx) + x1) * 4;
                    int o011 = (((z1 * ly + y1) * lx) + x0) * 4;
                    int o111 = (((z1 * ly + y1) * lx) + x1) * 4;

                    for (int c = 0; c < 3; c++) {
                        float c00 = lerp(lut[o000 + c] & 0xFF, lut[o100 + c] & 0xFF, dx);
                        float c10 = lerp(lut[o010 + c] & 0xFF, lut[o110 + c] & 0xFF, dx);
                        float c01 = lerp(lut[o001 + c] & 0xFF, lut[o101 + c] & 0xFF, dx);
                        float c11 = lerp(lut[o011 + c] & 0xFF, lut[o111 + c] & 0xFF, dx);
                        float c0 = lerp(c00, c10, dy);
                        float c1 = lerp(c01, c11, dy);
                        float out = lerp(c0, c1, dz);
                        int iv = (int) (out + 0.5f);
                        dst[off + c] = (byte) Math.max(0, Math.min(255, iv));
                    }
                    dst[off + 3] = a;
                }
            }
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public Script.KernelID getKernelID() {
        return createKernelID(0, 3, null, null);
    }
}
