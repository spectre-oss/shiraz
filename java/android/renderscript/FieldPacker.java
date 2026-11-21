// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

import java.util.BitSet;

public class FieldPacker {
    private byte[] mData;
    private int mPos;
    private int mLen;
    private final BitSet mAlignment;

    public FieldPacker(int len) {
        if (len < 0) {
            throw new RSIllegalArgumentException("len must be >= 0");
        }
        mPos = 0;
        mLen = len;
        mData = new byte[len];
        mAlignment = new BitSet();
    }

    public FieldPacker(byte[] data) {
        mPos = data.length;
        mLen = data.length;
        mData = new byte[data.length];
        System.arraycopy(data, 0, mData, 0, data.length);
        mAlignment = new BitSet();
    }

    private void ensureCapacity(int additional) {
        int needed = mPos + additional;
        if (needed <= mLen) {
            return;
        }
        int newLen = mLen == 0 ? 16 : mLen * 2;
        while (newLen < needed) {
            newLen *= 2;
        }
        byte[] next = new byte[newLen];
        System.arraycopy(mData, 0, next, 0, mPos);
        mData = next;
        mLen = newLen;
    }

    public void align(int v) {
        if (v <= 0 || ((v & (v - 1)) != 0)) {
            throw new RSIllegalArgumentException("argument must be a positive non-zero power of 2: " + v);
        }
        while ((mPos & (v - 1)) != 0) {
            mAlignment.flip(mPos);
            ensureCapacity(1);
            mData[mPos++] = 0;
        }
    }

    public void subalign(int v) {
        if ((v & (v - 1)) != 0) {
            throw new RSIllegalArgumentException("argument must be a non-negative non-zero power of 2: " + v);
        }
        while ((mPos & (v - 1)) != 0) {
            mPos--;
        }
        if (mPos > 0) {
            while (mAlignment.get(mPos - 1)) {
                mPos--;
                mAlignment.flip(mPos);
            }
        }
    }

    public void reset() {
        mPos = 0;
    }

    public void reset(int i) {
        if (i < 0 || i > mLen) {
            throw new RSIllegalArgumentException("out of range argument: " + i);
        }
        mPos = i;
    }

    public void skip(int i) {
        int res = mPos + i;
        if (res < 0) {
            throw new RSIllegalArgumentException("out of range argument: " + i);
        }
        if (res > mLen) {
            ensureCapacity(i);
        }
        mPos = res;
    }

    public void addI8(byte v) {
        ensureCapacity(1);
        mData[mPos++] = v;
    }

    public byte subI8() {
        subalign(1);
        return mData[--mPos];
    }

    public void addI16(short v) {
        align(2);
        ensureCapacity(2);
        mData[mPos++] = (byte) (v & 0xff);
        mData[mPos++] = (byte) (v >> 8);
    }

    public short subI16() {
        subalign(2);
        int b1 = mData[--mPos] & 0xff;
        int b0 = mData[--mPos] & 0xff;
        return (short) ((b1 << 8) | b0);
    }

    public void addI32(int v) {
        align(4);
        ensureCapacity(4);
        mData[mPos++] = (byte) (v & 0xff);
        mData[mPos++] = (byte) ((v >> 8) & 0xff);
        mData[mPos++] = (byte) ((v >> 16) & 0xff);
        mData[mPos++] = (byte) ((v >> 24) & 0xff);
    }

    public int subI32() {
        subalign(4);
        int b3 = mData[--mPos] & 0xff;
        int b2 = mData[--mPos] & 0xff;
        int b1 = mData[--mPos] & 0xff;
        int b0 = mData[--mPos] & 0xff;
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    public void addI64(long v) {
        align(8);
        ensureCapacity(8);
        mData[mPos++] = (byte) (v & 0xffL);
        mData[mPos++] = (byte) ((v >> 8) & 0xffL);
        mData[mPos++] = (byte) ((v >> 16) & 0xffL);
        mData[mPos++] = (byte) ((v >> 24) & 0xffL);
        mData[mPos++] = (byte) ((v >> 32) & 0xffL);
        mData[mPos++] = (byte) ((v >> 40) & 0xffL);
        mData[mPos++] = (byte) ((v >> 48) & 0xffL);
        mData[mPos++] = (byte) ((v >> 56) & 0xffL);
    }

    public long subI64() {
        subalign(8);
        long b7 = mData[--mPos] & 0xffL;
        long b6 = mData[--mPos] & 0xffL;
        long b5 = mData[--mPos] & 0xffL;
        long b4 = mData[--mPos] & 0xffL;
        long b3 = mData[--mPos] & 0xffL;
        long b2 = mData[--mPos] & 0xffL;
        long b1 = mData[--mPos] & 0xffL;
        long b0 = mData[--mPos] & 0xffL;
        return (b7 << 56) | (b6 << 48) | (b5 << 40) | (b4 << 32)
                | (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    public void addU8(short v) {
        if (v < 0 || v > 0xff) {
            throw new IllegalArgumentException("Saving value out of range for type");
        }
        addI8((byte) v);
    }

    public void addU16(int v) {
        if (v < 0 || v > 0xffff) {
            throw new IllegalArgumentException("Saving value out of range for type");
        }
        addI16((short) v);
    }

    public void addU32(long v) {
        if (v < 0 || v > 0xffffffffL) {
            throw new IllegalArgumentException("Saving value out of range for type");
        }
        addI32((int) v);
    }

    public void addU64(long v) {
        if (v < 0) {
            throw new IllegalArgumentException("Saving value out of range for type");
        }
        addI64(v);
    }

    public void addF32(float v) {
        addI32(Float.floatToRawIntBits(v));
    }

    public float subF32() {
        return Float.intBitsToFloat(subI32());
    }

    public void addF64(double v) {
        addI64(Double.doubleToRawLongBits(v));
    }

    public double subF64() {
        return Double.longBitsToDouble(subI64());
    }

    public void addObj(BaseObj obj) {
        if (obj != null) {
            addI64(obj.getID(null));
            addI64(0);
            addI64(0);
            addI64(0);
        } else {
            addI64(0);
            addI64(0);
            addI64(0);
            addI64(0);
        }
    }

    public void addF32(Float2 v) {
        addF32(v.x);
        addF32(v.y);
    }

    public void addF32(Float3 v) {
        addF32(v.x);
        addF32(v.y);
        addF32(v.z);
    }

    public void addF32(Float4 v) {
        addF32(v.x);
        addF32(v.y);
        addF32(v.z);
        addF32(v.w);
    }

    public void addF64(Double2 v) {
        addF64(v.x);
        addF64(v.y);
    }

    public void addF64(Double3 v) {
        addF64(v.x);
        addF64(v.y);
        addF64(v.z);
    }

    public void addF64(Double4 v) {
        addF64(v.x);
        addF64(v.y);
        addF64(v.z);
        addF64(v.w);
    }

    public void addI8(Byte2 v) {
        addI8(v.x);
        addI8(v.y);
    }

    public void addI8(Byte3 v) {
        addI8(v.x);
        addI8(v.y);
        addI8(v.z);
    }

    public void addI8(Byte4 v) {
        addI8(v.x);
        addI8(v.y);
        addI8(v.z);
        addI8(v.w);
    }

    public void addU8(Short2 v) {
        addU8(v.x);
        addU8(v.y);
    }

    public void addU8(Short3 v) {
        addU8(v.x);
        addU8(v.y);
        addU8(v.z);
    }

    public void addU8(Short4 v) {
        addU8(v.x);
        addU8(v.y);
        addU8(v.z);
        addU8(v.w);
    }

    public void addI16(Short2 v) {
        addI16(v.x);
        addI16(v.y);
    }

    public void addI16(Short3 v) {
        addI16(v.x);
        addI16(v.y);
        addI16(v.z);
    }

    public void addI16(Short4 v) {
        addI16(v.x);
        addI16(v.y);
        addI16(v.z);
        addI16(v.w);
    }

    public void addU16(Int2 v) {
        addU16(v.x);
        addU16(v.y);
    }

    public void addU16(Int3 v) {
        addU16(v.x);
        addU16(v.y);
        addU16(v.z);
    }

    public void addU16(Int4 v) {
        addU16(v.x);
        addU16(v.y);
        addU16(v.z);
        addU16(v.w);
    }

    public void addI32(Int2 v) {
        addI32(v.x);
        addI32(v.y);
    }

    public void addI32(Int3 v) {
        addI32(v.x);
        addI32(v.y);
        addI32(v.z);
    }

    public void addI32(Int4 v) {
        addI32(v.x);
        addI32(v.y);
        addI32(v.z);
        addI32(v.w);
    }

    public void addU32(Long2 v) {
        addU32(v.x);
        addU32(v.y);
    }

    public void addU32(Long3 v) {
        addU32(v.x);
        addU32(v.y);
        addU32(v.z);
    }

    public void addU32(Long4 v) {
        addU32(v.x);
        addU32(v.y);
        addU32(v.z);
        addU32(v.w);
    }

    public void addI64(Long2 v) {
        addI64(v.x);
        addI64(v.y);
    }

    public void addI64(Long3 v) {
        addI64(v.x);
        addI64(v.y);
        addI64(v.z);
    }

    public void addI64(Long4 v) {
        addI64(v.x);
        addI64(v.y);
        addI64(v.z);
        addI64(v.w);
    }

    public void addU64(Long2 v) {
        addU64(v.x);
        addU64(v.y);
    }

    public void addU64(Long3 v) {
        addU64(v.x);
        addU64(v.y);
        addU64(v.z);
    }

    public void addU64(Long4 v) {
        addU64(v.x);
        addU64(v.y);
        addU64(v.z);
        addU64(v.w);
    }

    public Float2 subFloat2() {
        Float2 v = new Float2();
        v.y = subF32();
        v.x = subF32();
        return v;
    }

    public Float3 subFloat3() {
        Float3 v = new Float3();
        v.z = subF32();
        v.y = subF32();
        v.x = subF32();
        return v;
    }

    public Float4 subFloat4() {
        Float4 v = new Float4();
        v.w = subF32();
        v.z = subF32();
        v.y = subF32();
        v.x = subF32();
        return v;
    }

    public Double2 subDouble2() {
        Double2 v = new Double2();
        v.y = subF64();
        v.x = subF64();
        return v;
    }

    public Double3 subDouble3() {
        Double3 v = new Double3();
        v.z = subF64();
        v.y = subF64();
        v.x = subF64();
        return v;
    }

    public Double4 subDouble4() {
        Double4 v = new Double4();
        v.w = subF64();
        v.z = subF64();
        v.y = subF64();
        v.x = subF64();
        return v;
    }

    public Byte2 subByte2() {
        Byte2 v = new Byte2();
        v.y = subI8();
        v.x = subI8();
        return v;
    }

    public Byte3 subByte3() {
        Byte3 v = new Byte3();
        v.z = subI8();
        v.y = subI8();
        v.x = subI8();
        return v;
    }

    public Byte4 subByte4() {
        Byte4 v = new Byte4();
        v.w = subI8();
        v.z = subI8();
        v.y = subI8();
        v.x = subI8();
        return v;
    }

    public Short2 subShort2() {
        Short2 v = new Short2();
        v.y = subI16();
        v.x = subI16();
        return v;
    }

    public Short3 subShort3() {
        Short3 v = new Short3();
        v.z = subI16();
        v.y = subI16();
        v.x = subI16();
        return v;
    }

    public Short4 subShort4() {
        Short4 v = new Short4();
        v.w = subI16();
        v.z = subI16();
        v.y = subI16();
        v.x = subI16();
        return v;
    }

    public Int2 subInt2() {
        Int2 v = new Int2();
        v.y = subI32();
        v.x = subI32();
        return v;
    }

    public Int3 subInt3() {
        Int3 v = new Int3();
        v.z = subI32();
        v.y = subI32();
        v.x = subI32();
        return v;
    }

    public Int4 subInt4() {
        Int4 v = new Int4();
        v.w = subI32();
        v.z = subI32();
        v.y = subI32();
        v.x = subI32();
        return v;
    }

    public Long2 subLong2() {
        Long2 v = new Long2();
        v.y = subI64();
        v.x = subI64();
        return v;
    }

    public Long3 subLong3() {
        Long3 v = new Long3();
        v.z = subI64();
        v.y = subI64();
        v.x = subI64();
        return v;
    }

    public Long4 subLong4() {
        Long4 v = new Long4();
        v.w = subI64();
        v.z = subI64();
        v.y = subI64();
        v.x = subI64();
        return v;
    }

    public void addMatrix(Matrix4f v) {
        for (int i = 0; i < v.mMat.length; i++) {
            addF32(v.mMat[i]);
        }
    }

    public Matrix4f subMatrix4f() {
        Matrix4f v = new Matrix4f();
        for (int i = v.mMat.length - 1; i >= 0; i--) {
            v.mMat[i] = subF32();
        }
        return v;
    }

    public void addMatrix(Matrix3f v) {
        for (int i = 0; i < v.mMat.length; i++) {
            addF32(v.mMat[i]);
        }
    }

    public Matrix3f subMatrix3f() {
        Matrix3f v = new Matrix3f();
        for (int i = v.mMat.length - 1; i >= 0; i--) {
            v.mMat[i] = subF32();
        }
        return v;
    }

    public void addMatrix(Matrix2f v) {
        for (int i = 0; i < v.mMat.length; i++) {
            addF32(v.mMat[i]);
        }
    }

    public Matrix2f subMatrix2f() {
        Matrix2f v = new Matrix2f();
        for (int i = v.mMat.length - 1; i >= 0; i--) {
            v.mMat[i] = subF32();
        }
        return v;
    }

    public void addBoolean(boolean v) {
        addI8((byte) (v ? 1 : 0));
    }

    public boolean subBoolean() {
        return subI8() == 1;
    }

    public final byte[] getData() {
        return mData;
    }

    int getPos() {
        return mPos;
    }
}
