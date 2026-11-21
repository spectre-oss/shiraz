// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

import android.util.SparseArray;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Script extends BaseObj {
    private final SparseArray<KernelID> mKernels = new SparseArray<KernelID>();
    private final SparseArray<InvokeID> mInvokes = new SparseArray<InvokeID>();
    private final SparseArray<FieldID> mFields = new SparseArray<FieldID>();
    private final SparseArray<Object> mVars = new SparseArray<Object>();
    private final SparseArray<Allocation> mBoundAllocations = new SparseArray<Allocation>();
    private String mTimeZone;

    Script(long id, RenderScript rs) {
        super(id, rs);
    }

    public static final class KernelID extends BaseObj {
        final Script mScript;
        final int mSlot;
        final int mSig;

        KernelID(long id, RenderScript rs, Script s, int slot, int sig) {
            super(id, rs);
            mScript = s;
            mSlot = slot;
            mSig = sig;
        }
    }

    protected KernelID createKernelID(int slot, int sig, Element ein, Element eout) {
        KernelID k = mKernels.get(slot);
        if (k != null) {
            return k;
        }
        mRS.validate();
        k = new KernelID(0, mRS, this, slot, sig);
        mKernels.put(slot, k);
        return k;
    }

    public static final class InvokeID extends BaseObj {
        final Script mScript;
        final int mSlot;

        InvokeID(long id, RenderScript rs, Script s, int slot) {
            super(id, rs);
            mScript = s;
            mSlot = slot;
        }
    }

    protected InvokeID createInvokeID(int slot) {
        InvokeID i = mInvokes.get(slot);
        if (i != null) {
            return i;
        }
        mRS.validate();
        i = new InvokeID(0, mRS, this, slot);
        mInvokes.put(slot, i);
        return i;
    }

    public static final class FieldID extends BaseObj {
        final Script mScript;
        final int mSlot;

        FieldID(long id, RenderScript rs, Script s, int slot) {
            super(id, rs);
            mScript = s;
            mSlot = slot;
        }
    }

    protected FieldID createFieldID(int slot, Element e) {
        FieldID f = mFields.get(slot);
        if (f != null) {
            return f;
        }
        mRS.validate();
        f = new FieldID(0, mRS, this, slot);
        mFields.put(slot, f);
        return f;
    }

    void executeKernel(int slot, Allocation[] ains, Allocation aout, FieldPacker v, LaunchOptions sc) {
        checkValid();
        if (ains == null && aout == null) {
            throw new RSIllegalArgumentException("At least one of ain or aout is required to be non-null.");
        }
    }

    void executeInvoke(int slot, FieldPacker v) {
        checkValid();
    }

    void setGlobalField(int slot, Object value) {
        checkValid();
        if (value instanceof BaseObj) {
            setVar(slot, (BaseObj) value);
        } else if (value instanceof Integer) {
            setVar(slot, ((Integer) value).intValue());
        } else if (value instanceof Float) {
            setVar(slot, ((Float) value).floatValue());
        } else if (value instanceof Double) {
            setVar(slot, ((Double) value).doubleValue());
        } else if (value instanceof Long) {
            setVar(slot, ((Long) value).longValue());
        } else if (value instanceof Boolean) {
            setVar(slot, ((Boolean) value).booleanValue());
        } else {
            mVars.put(slot, value);
        }
    }

    Object getGlobalField(int slot) {
        checkValid();
        return mVars.get(slot);
    }

    protected void invoke(int slot) {
        mRS.validate();
        executeInvoke(slot, null);
    }

    protected void invoke(int slot, FieldPacker v) {
        mRS.validate();
        executeInvoke(slot, v);
    }

    protected void forEach(int slot, Allocation ain, Allocation aout, FieldPacker v) {
        forEach(slot, ain, aout, v, null);
    }

    protected void forEach(int slot, Allocation ain, Allocation aout, FieldPacker v, LaunchOptions sc) {
        mRS.validate();
        if (ain == null && aout == null) {
            throw new RSIllegalArgumentException("At least one of ain or aout is required to be non-null.");
        }
        Allocation[] ains = ain != null ? new Allocation[] { ain } : null;
        forEach(slot, ains, aout, v, sc);
    }

    protected void forEach(int slot, Allocation[] ains, Allocation aout, FieldPacker v) {
        forEach(slot, ains, aout, v, null);
    }

    protected void forEach(int slot, Allocation[] ains, Allocation aout, FieldPacker v, LaunchOptions sc) {
        mRS.validate();
        if (ains != null) {
            for (int i = 0; i < ains.length; i++) {
                if (ains[i] != null) {
                    mRS.validateObject(ains[i]);
                }
            }
        }
        if (aout != null) {
            mRS.validateObject(aout);
        }
        if ((ains == null || ains.length == 0) && aout == null) {
            throw new RSIllegalArgumentException("At least one of ain or aout is required to be non-null.");
        }
        executeKernel(slot, ains, aout, v, sc);
    }

    protected void reduce(int slot, Allocation[] ains, Allocation aout, LaunchOptions sc) {
        mRS.validate();
        if (ains == null || ains.length < 1) {
            throw new RSIllegalArgumentException("At least one input is required.");
        }
        if (aout == null) {
            throw new RSIllegalArgumentException("aout is required to be non-null.");
        }
        for (int i = 0; i < ains.length; i++) {
            mRS.validateObject(ains[i]);
        }
        mRS.validateObject(aout);
        executeKernel(slot, ains, aout, null, sc);
    }

    public void bindAllocation(Allocation va, int slot) {
        mRS.validate();
        if (va != null) {
            mRS.validateObject(va);
            mBoundAllocations.put(slot, va);
        } else {
            mBoundAllocations.remove(slot);
        }
    }

    public void setVar(int index, float v) {
        mRS.validate();
        mVars.put(index, Float.valueOf(v));
    }

    public float getVarF(int index) {
        mRS.validate();
        Object val = mVars.get(index);
        if (val instanceof Number) {
            return ((Number) val).floatValue();
        }
        return 0.0f;
    }

    public void setVar(int index, double v) {
        mRS.validate();
        mVars.put(index, Double.valueOf(v));
    }

    public double getVarD(int index) {
        mRS.validate();
        Object val = mVars.get(index);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return 0.0;
    }

    public void setVar(int index, int v) {
        mRS.validate();
        mVars.put(index, Integer.valueOf(v));
    }

    public int getVarI(int index) {
        mRS.validate();
        Object val = mVars.get(index);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return 0;
    }

    public void setVar(int index, long v) {
        mRS.validate();
        mVars.put(index, Long.valueOf(v));
    }

    public long getVarJ(int index) {
        mRS.validate();
        Object val = mVars.get(index);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return 0L;
    }

    public void setVar(int index, boolean v) {
        mRS.validate();
        mVars.put(index, Boolean.valueOf(v));
    }

    public boolean getVarB(int index) {
        mRS.validate();
        Object val = mVars.get(index);
        if (val instanceof Boolean) {
            return ((Boolean) val).booleanValue();
        }
        if (val instanceof Number) {
            return ((Number) val).intValue() != 0;
        }
        return false;
    }

    public void setVar(int index, BaseObj o) {
        mRS.validate();
        if (o != null) {
            mRS.validateObject(o);
        }
        mVars.put(index, o);
    }

    public void setVar(int index, FieldPacker v) {
        mRS.validate();
        mVars.put(index, v != null ? v.getData().clone() : null);
    }

    public void setVar(int index, FieldPacker v, Element e, int[] dims) {
        mRS.validate();
        if (e != null) {
            mRS.validateObject(e);
        }
        mVars.put(index, v != null ? v.getData().clone() : null);
    }

    public void getVarV(int index, FieldPacker v) {
        mRS.validate();
        if (v == null) {
            return;
        }
        Object val = mVars.get(index);
        byte[] dst = v.getData();
        if (val instanceof byte[]) {
            byte[] src = (byte[]) val;
            System.arraycopy(src, 0, dst, 0, Math.min(src.length, dst.length));
        } else if (val instanceof Integer && dst.length >= 4) {
            ByteBuffer.wrap(dst).order(ByteOrder.LITTLE_ENDIAN).putInt(((Integer) val).intValue());
        } else if (val instanceof Float && dst.length >= 4) {
            ByteBuffer.wrap(dst).order(ByteOrder.LITTLE_ENDIAN).putFloat(((Float) val).floatValue());
        } else if (val instanceof Long && dst.length >= 8) {
            ByteBuffer.wrap(dst).order(ByteOrder.LITTLE_ENDIAN).putLong(((Long) val).longValue());
        } else if (val instanceof Double && dst.length >= 8) {
            ByteBuffer.wrap(dst).order(ByteOrder.LITTLE_ENDIAN).putDouble(((Double) val).doubleValue());
        } else if (val instanceof Boolean && dst.length >= 1) {
            dst[0] = (byte) (((Boolean) val).booleanValue() ? 1 : 0);
        }
    }

    public void setTimeZone(String timeZone) {
        mRS.validate();
        if (timeZone == null) {
            throw new IllegalArgumentException("timeZone cannot be null.");
        }
        mTimeZone = timeZone;
    }

    public static class Builder {
        RenderScript mRS;

        Builder(RenderScript rs) {
            mRS = rs;
        }
    }

    public static class FieldBase {
        protected Element mElement;
        protected Allocation mAllocation;

        protected void init(RenderScript rs, int dimx) {
            mAllocation = Allocation.createSized(rs, mElement, dimx, Allocation.USAGE_SCRIPT);
        }

        protected void init(RenderScript rs, int dimx, int usages) {
            mAllocation = Allocation.createSized(rs, mElement, dimx, Allocation.USAGE_SCRIPT | usages);
        }

        protected FieldBase() {}

        public Element getElement() {
            return mElement;
        }

        public Type getType() {
            return mAllocation.getType();
        }

        public Allocation getAllocation() {
            return mAllocation;
        }

        public void updateAllocation() {}
    }

    public static final class LaunchOptions {
        private int xstart = 0;
        private int ystart = 0;
        private int xend = 0;
        private int yend = 0;
        private int zstart = 0;
        private int zend = 0;

        public LaunchOptions() {}

        public LaunchOptions setX(int xstartArg, int xendArg) {
            if (xstartArg < 0 || xendArg <= xstartArg) {
                throw new RSIllegalArgumentException("Invalid dimensions");
            }
            xstart = xstartArg;
            xend = xendArg;
            return this;
        }

        public LaunchOptions setY(int ystartArg, int yendArg) {
            if (ystartArg < 0 || yendArg <= ystartArg) {
                throw new RSIllegalArgumentException("Invalid dimensions");
            }
            ystart = ystartArg;
            yend = yendArg;
            return this;
        }

        public LaunchOptions setZ(int zstartArg, int zendArg) {
            if (zstartArg < 0 || zendArg <= zstartArg) {
                throw new RSIllegalArgumentException("Invalid dimensions");
            }
            zstart = zstartArg;
            zend = zendArg;
            return this;
        }

        public int getXStart() {
            return xstart;
        }

        public int getXEnd() {
            return xend;
        }

        public int getYStart() {
            return ystart;
        }

        public int getYEnd() {
            return yend;
        }

        public int getZStart() {
            return zstart;
        }

        public int getZEnd() {
            return zend;
        }

        void validateForType(Type t) {
            if (xend > 0 && xend > t.getX()) {
                throw new RSIllegalArgumentException("LaunchOptions X out of bounds");
            }
            if (yend > 0 && yend > Math.max(1, t.getY())) {
                throw new RSIllegalArgumentException("LaunchOptions Y out of bounds");
            }
            if (zend > 0 && zend > Math.max(1, t.getZ())) {
                throw new RSIllegalArgumentException("LaunchOptions Z out of bounds");
            }
        }
    }
}
