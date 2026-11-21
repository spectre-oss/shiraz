// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

public class ScriptIntrinsicBlend extends ScriptIntrinsic {
    private static final int MODE_CLEAR = 0;
    private static final int MODE_SRC = 1;
    private static final int MODE_DST = 2;
    private static final int MODE_SRC_OVER = 3;
    private static final int MODE_DST_OVER = 4;
    private static final int MODE_SRC_IN = 5;
    private static final int MODE_DST_IN = 6;
    private static final int MODE_SRC_OUT = 7;
    private static final int MODE_DST_OUT = 8;
    private static final int MODE_SRC_ATOP = 9;
    private static final int MODE_DST_ATOP = 10;
    private static final int MODE_XOR = 11;
    private static final int MODE_MULTIPLY = 34;
    private static final int MODE_ADD = 35;
    private static final int MODE_SUBTRACT = 36;

    ScriptIntrinsicBlend(long id, RenderScript rs) {
        super(id, rs);
    }

    public static ScriptIntrinsicBlend create(RenderScript rs, Element e) {
        rs.validate();
        if (!e.isCompatible(Element.U8_4(rs))) {
            throw new RSIllegalArgumentException("Element must be compatible with uchar4.");
        }
        return new ScriptIntrinsicBlend(0, rs);
    }

    private void blend(int slot, Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        mRS.validate();
        if (ain == null || aout == null) {
            throw new RSIllegalArgumentException("Input and output allocations must be non-null.");
        }
        if (!ain.getElement().isCompatible(Element.U8_4(mRS))) {
            throw new RSIllegalArgumentException("Input is not of expected format.");
        }
        if (!aout.getElement().isCompatible(Element.U8_4(mRS))) {
            throw new RSIllegalArgumentException("Output is not of expected format.");
        }
        forEach(slot, ain, aout, null, opt);
    }

    void executeKernel(int slot, Allocation[] ains, Allocation aout, FieldPacker v, LaunchOptions sc) {
        if (ains == null || ains.length < 1 || ains[0] == null || aout == null) {
            throw new RSIllegalArgumentException("Input and output allocations are required.");
        }
        Allocation ain = ains[0];
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

        for (int z = zStart; z < zEnd; z++) {
            for (int y = yStart; y < yEnd; y++) {
                int rowBase = (z * dimY + y) * dimX;
                for (int x = xStart; x < xEnd; x++) {
                    int off = (rowBase + x) * 4;
                    int sr = src[off] & 0xFF;
                    int sg = src[off + 1] & 0xFF;
                    int sb = src[off + 2] & 0xFF;
                    int sa = src[off + 3] & 0xFF;

                    int dr = dst[off] & 0xFF;
                    int dg = dst[off + 1] & 0xFF;
                    int db = dst[off + 2] & 0xFF;
                    int da = dst[off + 3] & 0xFF;

                    int or, og, ob, oa;
                    switch (slot) {
                        case MODE_CLEAR:
                            or = 0; og = 0; ob = 0; oa = 0;
                            break;
                        case MODE_SRC:
                            or = sr; og = sg; ob = sb; oa = sa;
                            break;
                        case MODE_DST:
                            or = dr; og = dg; ob = db; oa = da;
                            break;
                        case MODE_SRC_OVER: {
                            int invSa = 255 - sa;
                            or = clamp255(sr + ((dr * invSa) >> 8));
                            og = clamp255(sg + ((dg * invSa) >> 8));
                            ob = clamp255(sb + ((db * invSa) >> 8));
                            oa = clamp255(sa + ((da * invSa) >> 8));
                            break;
                        }
                        case MODE_DST_OVER: {
                            int invDa = 255 - da;
                            or = clamp255(dr + ((sr * invDa) >> 8));
                            og = clamp255(dg + ((sg * invDa) >> 8));
                            ob = clamp255(db + ((sb * invDa) >> 8));
                            oa = clamp255(da + ((sa * invDa) >> 8));
                            break;
                        }
                        case MODE_SRC_IN:
                            or = (sr * da) >> 8;
                            og = (sg * da) >> 8;
                            ob = (sb * da) >> 8;
                            oa = (sa * da) >> 8;
                            break;
                        case MODE_DST_IN:
                            or = (dr * sa) >> 8;
                            og = (dg * sa) >> 8;
                            ob = (db * sa) >> 8;
                            oa = (da * sa) >> 8;
                            break;
                        case MODE_SRC_OUT: {
                            int invDa = 255 - da;
                            or = (sr * invDa) >> 8;
                            og = (sg * invDa) >> 8;
                            ob = (sb * invDa) >> 8;
                            oa = (sa * invDa) >> 8;
                            break;
                        }
                        case MODE_DST_OUT: {
                            int invSa = 255 - sa;
                            or = (dr * invSa) >> 8;
                            og = (dg * invSa) >> 8;
                            ob = (db * invSa) >> 8;
                            oa = (da * invSa) >> 8;
                            break;
                        }
                        case MODE_SRC_ATOP: {
                            int invSa = 255 - sa;
                            or = clamp255(((sr * da) >> 8) + ((dr * invSa) >> 8));
                            og = clamp255(((sg * da) >> 8) + ((dg * invSa) >> 8));
                            ob = clamp255(((sb * da) >> 8) + ((db * invSa) >> 8));
                            oa = da;
                            break;
                        }
                        case MODE_DST_ATOP: {
                            int invDa = 255 - da;
                            or = clamp255(((dr * sa) >> 8) + ((sr * invDa) >> 8));
                            og = clamp255(((dg * sa) >> 8) + ((sg * invDa) >> 8));
                            ob = clamp255(((db * sa) >> 8) + ((sb * invDa) >> 8));
                            oa = sa;
                            break;
                        }
                        case MODE_XOR:
                            or = sr ^ dr;
                            og = sg ^ dg;
                            ob = sb ^ db;
                            oa = sa ^ da;
                            break;
                        case MODE_MULTIPLY:
                            or = (sr * dr) >> 8;
                            og = (sg * dg) >> 8;
                            ob = (sb * db) >> 8;
                            oa = (sa * da) >> 8;
                            break;
                        case MODE_ADD:
                            or = Math.min(255, sr + dr);
                            og = Math.min(255, sg + dg);
                            ob = Math.min(255, sb + db);
                            oa = Math.min(255, sa + da);
                            break;
                        case MODE_SUBTRACT:
                            or = Math.max(0, dr - sr);
                            og = Math.max(0, dg - sg);
                            ob = Math.max(0, db - sb);
                            oa = Math.max(0, da - sa);
                            break;
                        default:
                            throw new RSIllegalArgumentException("Unknown blend slot: " + slot);
                    }
                    dst[off] = (byte) or;
                    dst[off + 1] = (byte) og;
                    dst[off + 2] = (byte) ob;
                    dst[off + 3] = (byte) oa;
                }
            }
        }
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    public void forEachClear(Allocation ain, Allocation aout) {
        forEachClear(ain, aout, null);
    }

    public void forEachClear(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_CLEAR, ain, aout, opt);
    }

    public Script.KernelID getKernelIDClear() {
        return createKernelID(MODE_CLEAR, 3, null, null);
    }

    public void forEachSrc(Allocation ain, Allocation aout) {
        forEachSrc(ain, aout, null);
    }

    public void forEachSrc(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_SRC, ain, aout, opt);
    }

    public Script.KernelID getKernelIDSrc() {
        return createKernelID(MODE_SRC, 3, null, null);
    }

    public void forEachDst(Allocation ain, Allocation aout) {
        forEachDst(ain, aout, null);
    }

    public void forEachDst(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_DST, ain, aout, opt);
    }

    public Script.KernelID getKernelIDDst() {
        return createKernelID(MODE_DST, 3, null, null);
    }

    public void forEachSrcOver(Allocation ain, Allocation aout) {
        forEachSrcOver(ain, aout, null);
    }

    public void forEachSrcOver(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_SRC_OVER, ain, aout, opt);
    }

    public Script.KernelID getKernelIDSrcOver() {
        return createKernelID(MODE_SRC_OVER, 3, null, null);
    }

    public void forEachDstOver(Allocation ain, Allocation aout) {
        forEachDstOver(ain, aout, null);
    }

    public void forEachDstOver(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_DST_OVER, ain, aout, opt);
    }

    public Script.KernelID getKernelIDDstOver() {
        return createKernelID(MODE_DST_OVER, 3, null, null);
    }

    public void forEachSrcIn(Allocation ain, Allocation aout) {
        forEachSrcIn(ain, aout, null);
    }

    public void forEachSrcIn(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_SRC_IN, ain, aout, opt);
    }

    public Script.KernelID getKernelIDSrcIn() {
        return createKernelID(MODE_SRC_IN, 3, null, null);
    }

    public void forEachDstIn(Allocation ain, Allocation aout) {
        forEachDstIn(ain, aout, null);
    }

    public void forEachDstIn(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_DST_IN, ain, aout, opt);
    }

    public Script.KernelID getKernelIDDstIn() {
        return createKernelID(MODE_DST_IN, 3, null, null);
    }

    public void forEachSrcOut(Allocation ain, Allocation aout) {
        forEachSrcOut(ain, aout, null);
    }

    public void forEachSrcOut(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_SRC_OUT, ain, aout, opt);
    }

    public Script.KernelID getKernelIDSrcOut() {
        return createKernelID(MODE_SRC_OUT, 3, null, null);
    }

    public void forEachDstOut(Allocation ain, Allocation aout) {
        forEachDstOut(ain, aout, null);
    }

    public void forEachDstOut(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_DST_OUT, ain, aout, opt);
    }

    public Script.KernelID getKernelIDDstOut() {
        return createKernelID(MODE_DST_OUT, 3, null, null);
    }

    public void forEachSrcAtop(Allocation ain, Allocation aout) {
        forEachSrcAtop(ain, aout, null);
    }

    public void forEachSrcAtop(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_SRC_ATOP, ain, aout, opt);
    }

    public Script.KernelID getKernelIDSrcAtop() {
        return createKernelID(MODE_SRC_ATOP, 3, null, null);
    }

    public void forEachDstAtop(Allocation ain, Allocation aout) {
        forEachDstAtop(ain, aout, null);
    }

    public void forEachDstAtop(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_DST_ATOP, ain, aout, opt);
    }

    public Script.KernelID getKernelIDDstAtop() {
        return createKernelID(MODE_DST_ATOP, 3, null, null);
    }

    public void forEachXor(Allocation ain, Allocation aout) {
        forEachXor(ain, aout, null);
    }

    public void forEachXor(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_XOR, ain, aout, opt);
    }

    public Script.KernelID getKernelIDXor() {
        return createKernelID(MODE_XOR, 3, null, null);
    }

    public void forEachMultiply(Allocation ain, Allocation aout) {
        forEachMultiply(ain, aout, null);
    }

    public void forEachMultiply(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_MULTIPLY, ain, aout, opt);
    }

    public Script.KernelID getKernelIDMultiply() {
        return createKernelID(MODE_MULTIPLY, 3, null, null);
    }

    public void forEachAdd(Allocation ain, Allocation aout) {
        forEachAdd(ain, aout, null);
    }

    public void forEachAdd(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_ADD, ain, aout, opt);
    }

    public Script.KernelID getKernelIDAdd() {
        return createKernelID(MODE_ADD, 3, null, null);
    }

    public void forEachSubtract(Allocation ain, Allocation aout) {
        forEachSubtract(ain, aout, null);
    }

    public void forEachSubtract(Allocation ain, Allocation aout, Script.LaunchOptions opt) {
        blend(MODE_SUBTRACT, ain, aout, opt);
    }

    public Script.KernelID getKernelIDSubtract() {
        return createKernelID(MODE_SUBTRACT, 3, null, null);
    }
}
