// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

public final class ScriptIntrinsicConvolve5x5 extends ScriptIntrinsic {
    private final float[] mValues = new float[25];
    private Allocation mInput;

    private ScriptIntrinsicConvolve5x5(long id, RenderScript rs) {
        super(id, rs);
        mValues[12] = 1.0f;
    }

    public static ScriptIntrinsicConvolve5x5 create(RenderScript rs, Element e) {
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
        return new ScriptIntrinsicConvolve5x5(0, rs);
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
        if (v == null || v.length < 25) {
            throw new RSIllegalArgumentException("Coefficients array must have at least 25 elements.");
        }
        System.arraycopy(v, 0, mValues, 0, 25);
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
        ScriptIntrinsicConvolve3x3.convolve2D(in, aout, mValues, 2, sc);
    }

    public Script.KernelID getKernelID() {
        return createKernelID(0, 2, null, null);
    }

    public Script.FieldID getFieldID_Input() {
        return createFieldID(1, null);
    }
}
