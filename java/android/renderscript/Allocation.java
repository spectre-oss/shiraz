// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Surface;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class Allocation extends BaseObj {
    public static final int USAGE_SCRIPT = 0x0001;
    public static final int USAGE_GRAPHICS_TEXTURE = 0x0002;
    public static final int USAGE_GRAPHICS_VERTEX = 0x0004;
    public static final int USAGE_GRAPHICS_CONSTANTS = 0x0008;
    public static final int USAGE_GRAPHICS_RENDER_TARGET = 0x0010;
    public static final int USAGE_IO_INPUT = 0x0020;
    public static final int USAGE_IO_OUTPUT = 0x0040;
    public static final int USAGE_SHARED = 0x0080;

    public enum MipmapControl {
        MIPMAP_NONE(0),
        MIPMAP_FULL(1),
        MIPMAP_ON_SYNC_TO_TEXTURE(2);

        final int mID;

        MipmapControl(int id) {
            mID = id;
        }
    }

    public interface OnBufferAvailableListener {
        void onBufferAvailable(Allocation a);
    }

    Type mType;
    Bitmap mBitmap;
    int mUsage;
    int mSize;
    MipmapControl mMipmapControl;
    boolean mAutoPadding;
    byte[] mData;
    ByteBuffer mByteBuffer;
    Surface mSurface;
    OnBufferAvailableListener mBufferNotifier;
    long mTimeStamp = -1;

    int mCurrentDimX;
    int mCurrentDimY;
    int mCurrentDimZ;
    int mCurrentCount;

    Allocation(long id, RenderScript rs, Type t, int usage) {
        super(id, rs);
        if ((usage & ~(USAGE_SCRIPT
                | USAGE_GRAPHICS_TEXTURE
                | USAGE_GRAPHICS_VERTEX
                | USAGE_GRAPHICS_CONSTANTS
                | USAGE_GRAPHICS_RENDER_TARGET
                | USAGE_IO_INPUT
                | USAGE_IO_OUTPUT
                | USAGE_SHARED)) != 0) {
            throw new RSIllegalArgumentException("Unknown usage specified.");
        }
        mUsage = usage;
        if (t != null) {
            updateCacheInfo(t);
            mSize = t.getCount() * t.getElement().getBytesSize();
            mData = new byte[mSize];
        }
    }

    Allocation(long id, RenderScript rs, Type t, int usage, byte[] sharedData) {
        super(id, rs);
        mUsage = usage;
        if (t != null) {
            updateCacheInfo(t);
        }
        mData = sharedData;
        mSize = sharedData != null ? sharedData.length : 0;
    }

    private void updateCacheInfo(Type t) {
        mType = t;
        mCurrentDimX = t.getX();
        mCurrentDimY = t.getY();
        mCurrentDimZ = t.getZ();
        mCurrentCount = mCurrentDimX;
        if (mCurrentDimY > 1) {
            mCurrentCount *= mCurrentDimY;
        }
        if (mCurrentDimZ > 1) {
            mCurrentCount *= mCurrentDimZ;
        }
    }

    byte[] rawBytes() {
        checkValid();
        return mData;
    }

    public Element getElement() {
        return mType.getElement();
    }

    public int getUsage() {
        return mUsage;
    }

    public void setAutoPadding(boolean useAutoPadding) {
        mAutoPadding = useAutoPadding;
    }

    public int getBytesSize() {
        if (mType.mDimYuv != 0) {
            return (int) Math.ceil(mType.getCount() * mType.getElement().getBytesSize() * 1.5);
        }
        return mType.getCount() * mType.getElement().getBytesSize();
    }

    protected void finalize() throws Throwable {
        super.finalize();
    }

    public Type getType() {
        return mType;
    }

    public void syncAll(int srcLocation) {
        checkValid();
        switch (srcLocation) {
            case USAGE_SCRIPT:
            case USAGE_GRAPHICS_TEXTURE:
            case USAGE_GRAPHICS_CONSTANTS:
            case USAGE_GRAPHICS_VERTEX:
            case USAGE_SHARED:
                if (mBitmap != null && (srcLocation == USAGE_SHARED || (mUsage & USAGE_SHARED) != 0)) {
                    if (srcLocation == USAGE_SHARED) {
                        copyFrom(mBitmap);
                    } else if (mBitmap.isMutable()) {
                        copyTo(mBitmap);
                    }
                }
                break;
            default:
                throw new RSIllegalArgumentException("Source must be exactly one usage type.");
        }
    }

    public void ioSend() {
        checkValid();
        if ((mUsage & USAGE_IO_OUTPUT) == 0) {
            throw new RSIllegalArgumentException("Can only send buffer if IO_OUTPUT usage specified.");
        }
        mTimeStamp = System.nanoTime();
    }

    public void ioReceive() {
        checkValid();
        if ((mUsage & USAGE_IO_INPUT) == 0) {
            throw new RSIllegalArgumentException("Can only receive if IO_INPUT usage specified.");
        }
        mTimeStamp = System.nanoTime();
    }

    public void copyFrom(BaseObj[] d) {
        checkValid();
        if (d.length != mCurrentCount) {
            throw new RSIllegalArgumentException("Array size mismatch, allocation sizeX = "
                    + mCurrentCount + ", array length = " + d.length);
        }
        ByteBuffer buf = ByteBuffer.wrap(mData).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < d.length; i++) {
            long objId = d[i] != null ? d[i].getID(mRS) : 0L;
            if (getElement().getBytesSize() >= 32) {
                buf.putLong(i * 32, objId);
            } else if (getElement().getBytesSize() >= 4) {
                buf.putInt(i * 4, (int) objId);
            }
        }
    }

    private Class<?> unwrapPrimitiveArray(Object array) {
        if (array == null) {
            throw new RSIllegalArgumentException("Object cannot be null");
        }
        Class<?> cls = array.getClass();
        if (!cls.isArray()) {
            throw new RSIllegalArgumentException("Object passed is not an array of primitives.");
        }
        Class<?> comp = cls.getComponentType();
        if (!comp.isPrimitive()) {
            throw new RSIllegalArgumentException("Object passed is not an array of primitives.");
        }
        return comp;
    }

    private int componentByteSize(Class<?> comp) {
        if (comp == byte.class || comp == boolean.class) return 1;
        if (comp == short.class || comp == char.class) return 2;
        if (comp == int.class || comp == float.class) return 4;
        if (comp == long.class || comp == double.class) return 8;
        throw new RSIllegalArgumentException("Unsupported array component: " + comp);
    }

    private void writeArrayRangeToBytes(Object array, int arrayElemOffset, int dstByteOffset, int byteCount, int elemCount) {
        Class<?> comp = unwrapPrimitiveArray(array);
        Element el = getElement();
        if (mAutoPadding && el.getVectorSize() == 3) {
            int compBytes = el.getDataType().mSize;
            int srcItems = elemCount * 3;
            if (arrayElemOffset + srcItems > Array.getLength(array)) {
                throw new RSIllegalArgumentException("Array too small for auto-padded vec3 copy");
            }
            ByteBuffer dst = ByteBuffer.wrap(mData).order(ByteOrder.LITTLE_ENDIAN);
            int dstPos = dstByteOffset;
            for (int e = 0; e < elemCount; e++) {
                for (int c = 0; c < 3; c++) {
                    int idx = arrayElemOffset + e * 3 + c;
                    if (comp == byte.class) {
                        dst.put(dstPos + c, ((byte[]) array)[idx]);
                    } else if (comp == short.class) {
                        dst.putShort(dstPos + c * 2, ((short[]) array)[idx]);
                    } else if (comp == int.class) {
                        dst.putInt(dstPos + c * 4, ((int[]) array)[idx]);
                    } else if (comp == float.class) {
                        dst.putFloat(dstPos + c * 4, ((float[]) array)[idx]);
                    } else if (comp == long.class) {
                        dst.putLong(dstPos + c * 8, ((long[]) array)[idx]);
                    } else if (comp == double.class) {
                        dst.putDouble(dstPos + c * 8, ((double[]) array)[idx]);
                    }
                }
                for (int pad = 3 * compBytes; pad < 4 * compBytes; pad++) {
                    dst.put(dstPos + pad, (byte) 0);
                }
                dstPos += 4 * compBytes;
            }
            return;
        }
        int compSize = componentByteSize(comp);
        int itemsNeeded = byteCount / compSize;
        if (arrayElemOffset + itemsNeeded > Array.getLength(array)) {
            throw new RSIllegalArgumentException("Array too small for allocation copy.");
        }
        if (comp == byte.class) {
            System.arraycopy(array, arrayElemOffset, mData, dstByteOffset, byteCount);
            return;
        }
        ByteBuffer dst = ByteBuffer.wrap(mData, dstByteOffset, byteCount).order(ByteOrder.LITTLE_ENDIAN);
        if (comp == short.class) {
            dst.asShortBuffer().put((short[]) array, arrayElemOffset, itemsNeeded);
        } else if (comp == int.class) {
            dst.asIntBuffer().put((int[]) array, arrayElemOffset, itemsNeeded);
        } else if (comp == float.class) {
            dst.asFloatBuffer().put((float[]) array, arrayElemOffset, itemsNeeded);
        } else if (comp == long.class) {
            dst.asLongBuffer().put((long[]) array, arrayElemOffset, itemsNeeded);
        } else if (comp == double.class) {
            dst.asDoubleBuffer().put((double[]) array, arrayElemOffset, itemsNeeded);
        } else if (comp == boolean.class) {
            boolean[] ba = (boolean[]) array;
            for (int i = 0; i < itemsNeeded; i++) {
                dst.put((byte) (ba[arrayElemOffset + i] ? 1 : 0));
            }
        }
    }

    private void readBytesToArrayRange(int srcByteOffset, Object array, int arrayElemOffset, int byteCount, int elemCount) {
        Class<?> comp = unwrapPrimitiveArray(array);
        Element el = getElement();
        if (mAutoPadding && el.getVectorSize() == 3) {
            int compBytes = el.getDataType().mSize;
            int dstItems = elemCount * 3;
            if (arrayElemOffset + dstItems > Array.getLength(array)) {
                throw new RSIllegalArgumentException("Array too small for auto-padded vec3 copy");
            }
            ByteBuffer src = ByteBuffer.wrap(mData).order(ByteOrder.LITTLE_ENDIAN);
            int srcPos = srcByteOffset;
            for (int e = 0; e < elemCount; e++) {
                for (int c = 0; c < 3; c++) {
                    int idx = arrayElemOffset + e * 3 + c;
                    if (comp == byte.class) {
                        ((byte[]) array)[idx] = src.get(srcPos + c);
                    } else if (comp == short.class) {
                        ((short[]) array)[idx] = src.getShort(srcPos + c * 2);
                    } else if (comp == int.class) {
                        ((int[]) array)[idx] = src.getInt(srcPos + c * 4);
                    } else if (comp == float.class) {
                        ((float[]) array)[idx] = src.getFloat(srcPos + c * 4);
                    } else if (comp == long.class) {
                        ((long[]) array)[idx] = src.getLong(srcPos + c * 8);
                    } else if (comp == double.class) {
                        ((double[]) array)[idx] = src.getDouble(srcPos + c * 8);
                    }
                }
                srcPos += 4 * compBytes;
            }
            return;
        }
        int compSize = componentByteSize(comp);
        int itemsNeeded = byteCount / compSize;
        if (arrayElemOffset + itemsNeeded > Array.getLength(array)) {
            throw new RSIllegalArgumentException("Array too small for allocation copyTo.");
        }
        if (comp == byte.class) {
            System.arraycopy(mData, srcByteOffset, array, arrayElemOffset, byteCount);
            return;
        }
        ByteBuffer src = ByteBuffer.wrap(mData, srcByteOffset, byteCount).order(ByteOrder.LITTLE_ENDIAN);
        if (comp == short.class) {
            src.asShortBuffer().get((short[]) array, arrayElemOffset, itemsNeeded);
        } else if (comp == int.class) {
            src.asIntBuffer().get((int[]) array, arrayElemOffset, itemsNeeded);
        } else if (comp == float.class) {
            src.asFloatBuffer().get((float[]) array, arrayElemOffset, itemsNeeded);
        } else if (comp == long.class) {
            src.asLongBuffer().get((long[]) array, arrayElemOffset, itemsNeeded);
        } else if (comp == double.class) {
            src.asDoubleBuffer().get((double[]) array, arrayElemOffset, itemsNeeded);
        } else if (comp == boolean.class) {
            boolean[] ba = (boolean[]) array;
            for (int i = 0; i < itemsNeeded; i++) {
                ba[arrayElemOffset + i] = src.get() != 0;
            }
        }
    }

    private void validateArrayType(Object array) {
        Class<?> comp = unwrapPrimitiveArray(array);
        Element.DataType dt = getElement().getDataType();
        if (dt == Element.DataType.FLOAT_32 && comp != float.class) {
            throw new RSIllegalArgumentException("32 bit float source does not match allocation type " + dt);
        }
        if (dt == Element.DataType.FLOAT_64 && comp != double.class) {
            throw new RSIllegalArgumentException("64 bit float source does not match allocation type " + dt);
        }
        if ((dt == Element.DataType.SIGNED_8 || dt == Element.DataType.UNSIGNED_8) && comp != byte.class) {
            throw new RSIllegalArgumentException("8 bit source does not match allocation type " + dt);
        }
        if ((dt == Element.DataType.SIGNED_16 || dt == Element.DataType.UNSIGNED_16 || dt == Element.DataType.FLOAT_16)
                && comp != short.class) {
            throw new RSIllegalArgumentException("16 bit source does not match allocation type " + dt);
        }
        if ((dt == Element.DataType.SIGNED_32 || dt == Element.DataType.UNSIGNED_32) && comp != int.class) {
            throw new RSIllegalArgumentException("32 bit integer source does not match allocation type " + dt);
        }
        if ((dt == Element.DataType.SIGNED_64 || dt == Element.DataType.UNSIGNED_64) && comp != long.class) {
            throw new RSIllegalArgumentException("64 bit integer source does not match allocation type " + dt);
        }
    }

    public void copyFromUnchecked(Object array) {
        checkValid();
        copy1DRangeFromUnchecked(0, mCurrentCount, array);
    }

    public void copyFromUnchecked(int[] d) { copyFromUnchecked((Object) d); }
    public void copyFromUnchecked(short[] d) { copyFromUnchecked((Object) d); }
    public void copyFromUnchecked(byte[] d) { copyFromUnchecked((Object) d); }
    public void copyFromUnchecked(float[] d) { copyFromUnchecked((Object) d); }

    public void copyFrom(Object array) {
        checkValid();
        validateArrayType(array);
        copyFromUnchecked(array);
    }

    public void copyFrom(int[] d) { copyFrom((Object) d); }
    public void copyFrom(short[] d) { copyFrom((Object) d); }
    public void copyFrom(byte[] d) { copyFrom((Object) d); }
    public void copyFrom(float[] d) { copyFrom((Object) d); }

    public void copyFrom(Bitmap b) {
        checkValid();
        if (b == null) {
            throw new RSIllegalArgumentException("Bitmap cannot be null.");
        }
        if (b.getConfig() == null) {
            Bitmap newBitmap = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(newBitmap);
            c.drawBitmap(b, 0, 0, null);
            copyFrom(newBitmap);
            return;
        }
        validateBitmapSize(b);
        validateBitmapFormat(b);
        int byteCount = b.getByteCount();
        ByteBuffer buf = ByteBuffer.wrap(mData, 0, byteCount).order(ByteOrder.nativeOrder());
        b.copyPixelsToBuffer(buf);
    }

    public void copyFrom(Allocation a) {
        checkValid();
        if (a == null || !mType.equals(a.getType())) {
            throw new RSIllegalArgumentException("Types of allocations must match.");
        }
        System.arraycopy(a.mData, 0, mData, 0, Math.min(mData.length, a.mData.length));
    }

    public void setFromFieldPacker(int xoff, FieldPacker fp) {
        setFromFieldPacker(xoff, 0, 0, 0, fp);
    }

    public void setFromFieldPacker(int xoff, int component_number, FieldPacker fp) {
        setFromFieldPacker(xoff, 0, 0, component_number, fp);
    }

    public void setFromFieldPacker(int xoff, int yoff, int zoff, int component_number, FieldPacker fp) {
        checkValid();
        if (component_number >= mType.mElement.getSubElementCount() && component_number != 0) {
            throw new RSIllegalArgumentException("Component_number " + component_number + " out of range.");
        }
        if (xoff < 0 || yoff < 0 || zoff < 0) {
            throw new RSIllegalArgumentException("Offset must be >= 0.");
        }
        byte[] data = fp.getData();
        int fpLen = fp.getPos() > 0 ? fp.getPos() : data.length;
        int eSize = mType.mElement.getBytesSize();
        int subOffset = component_number > 0 ? mType.mElement.getSubElementOffsetBytes(component_number) : 0;
        int dimX = mCurrentDimX;
        int dimY = mCurrentDimY > 0 ? mCurrentDimY : 1;
        int elemIdx = (zoff * dimY + yoff) * dimX + xoff;
        int dstOffset = elemIdx * eSize + subOffset;
        if (dstOffset + fpLen > mData.length) {
            throw new RSIllegalArgumentException("FieldPacker data exceeds Allocation bounds.");
        }
        System.arraycopy(data, 0, mData, dstOffset, fpLen);
    }

    public void generateMipmaps() {
        checkValid();
    }

    public void copy1DRangeFromUnchecked(int off, int count, Object array) {
        checkValid();
        if (off < 0 || count < 0 || off + count > mCurrentCount) {
            throw new RSIllegalArgumentException("Invalid 1D range: off=" + off + " count=" + count);
        }
        int eSize = getElement().getBytesSize();
        writeArrayRangeToBytes(array, 0, off * eSize, count * eSize, count);
    }

    public void copy1DRangeFromUnchecked(int off, int count, int[] d) { copy1DRangeFromUnchecked(off, count, (Object) d); }
    public void copy1DRangeFromUnchecked(int off, int count, short[] d) { copy1DRangeFromUnchecked(off, count, (Object) d); }
    public void copy1DRangeFromUnchecked(int off, int count, byte[] d) { copy1DRangeFromUnchecked(off, count, (Object) d); }
    public void copy1DRangeFromUnchecked(int off, int count, float[] d) { copy1DRangeFromUnchecked(off, count, (Object) d); }

    public void copy1DRangeFrom(int off, int count, Object array) {
        checkValid();
        validateArrayType(array);
        copy1DRangeFromUnchecked(off, count, array);
    }

    public void copy1DRangeFrom(int off, int count, int[] d) { copy1DRangeFrom(off, count, (Object) d); }
    public void copy1DRangeFrom(int off, int count, short[] d) { copy1DRangeFrom(off, count, (Object) d); }
    public void copy1DRangeFrom(int off, int count, byte[] d) { copy1DRangeFrom(off, count, (Object) d); }
    public void copy1DRangeFrom(int off, int count, float[] d) { copy1DRangeFrom(off, count, (Object) d); }

    public void copy1DRangeFrom(int off, int count, Allocation data, int dataOff) {
        checkValid();
        data.checkValid();
        int eSize = getElement().getBytesSize();
        System.arraycopy(data.mData, dataOff * eSize, mData, off * eSize, count * eSize);
    }

    private void validate2DRange(int xoff, int yoff, int w, int h) {
        if (xoff < 0 || yoff < 0 || w < 0 || h < 0) {
            throw new RSIllegalArgumentException("Offset or dimension cannot be negative.");
        }
        if (xoff + w > mCurrentDimX || yoff + h > mCurrentDimY) {
            throw new RSIllegalArgumentException("Updated region larger than allocation.");
        }
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, Object array) {
        checkValid();
        validateArrayType(array);
        validate2DRange(xoff, yoff, w, h);
        int eSize = getElement().getBytesSize();
        Class<?> comp = unwrapPrimitiveArray(array);
        int compSize = componentByteSize(comp);
        int elemsPerRow = (mAutoPadding && getElement().getVectorSize() == 3) ? (w * 3) : ((w * eSize) / compSize);
        for (int row = 0; row < h; row++) {
            int dstByteOff = ((yoff + row) * mCurrentDimX + xoff) * eSize;
            writeArrayRangeToBytes(array, row * elemsPerRow, dstByteOff, w * eSize, w);
        }
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, byte[] data) { copy2DRangeFrom(xoff, yoff, w, h, (Object) data); }
    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, short[] data) { copy2DRangeFrom(xoff, yoff, w, h, (Object) data); }
    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, int[] data) { copy2DRangeFrom(xoff, yoff, w, h, (Object) data); }
    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, float[] data) { copy2DRangeFrom(xoff, yoff, w, h, (Object) data); }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, Allocation data, int dataXoff, int dataYoff) {
        checkValid();
        data.checkValid();
        validate2DRange(xoff, yoff, w, h);
        int eSize = getElement().getBytesSize();
        for (int row = 0; row < h; row++) {
            int srcOff = ((dataYoff + row) * data.mCurrentDimX + dataXoff) * eSize;
            int dstOff = ((yoff + row) * mCurrentDimX + xoff) * eSize;
            System.arraycopy(data.mData, srcOff, mData, dstOff, w * eSize);
        }
    }

    public void copy2DRangeFrom(int xoff, int yoff, Bitmap data) {
        checkValid();
        if (data == null) {
            throw new RSIllegalArgumentException("Bitmap cannot be null.");
        }
        validateBitmapFormat(data);
        int w = data.getWidth();
        int h = data.getHeight();
        validate2DRange(xoff, yoff, w, h);
        byte[] tmp = new byte[data.getByteCount()];
        data.copyPixelsToBuffer(ByteBuffer.wrap(tmp).order(ByteOrder.nativeOrder()));
        int eSize = getElement().getBytesSize();
        for (int row = 0; row < h; row++) {
            System.arraycopy(tmp, row * w * eSize, mData, ((yoff + row) * mCurrentDimX + xoff) * eSize, w * eSize);
        }
    }

    private void validate3DRange(int xoff, int yoff, int zoff, int w, int h, int d) {
        if (xoff < 0 || yoff < 0 || zoff < 0 || w < 0 || h < 0 || d < 0) {
            throw new RSIllegalArgumentException("Offset or dimension cannot be negative.");
        }
        if (xoff + w > mCurrentDimX || yoff + h > mCurrentDimY || zoff + d > mCurrentDimZ) {
            throw new RSIllegalArgumentException("Updated region larger than allocation.");
        }
    }

    public void copy3DRangeFrom(int xoff, int yoff, int zoff, int w, int h, int d, Object array) {
        checkValid();
        validateArrayType(array);
        validate3DRange(xoff, yoff, zoff, w, h, d);
        int eSize = getElement().getBytesSize();
        Class<?> comp = unwrapPrimitiveArray(array);
        int compSize = componentByteSize(comp);
        int elemsPerRow = (mAutoPadding && getElement().getVectorSize() == 3) ? (w * 3) : ((w * eSize) / compSize);
        int rowIndex = 0;
        for (int z = 0; z < d; z++) {
            for (int y = 0; y < h; y++) {
                int dstOff = (((zoff + z) * mCurrentDimY + (yoff + y)) * mCurrentDimX + xoff) * eSize;
                writeArrayRangeToBytes(array, rowIndex * elemsPerRow, dstOff, w * eSize, w);
                rowIndex++;
            }
        }
    }

    public void copy3DRangeFrom(int xoff, int yoff, int zoff, int w, int h, int d,
            Allocation data, int dataXoff, int dataYoff, int dataZoff) {
        checkValid();
        data.checkValid();
        validate3DRange(xoff, yoff, zoff, w, h, d);
        int eSize = getElement().getBytesSize();
        for (int z = 0; z < d; z++) {
            for (int y = 0; y < h; y++) {
                int srcOff = (((dataZoff + z) * data.mCurrentDimY + (dataYoff + y)) * data.mCurrentDimX + dataXoff) * eSize;
                int dstOff = (((zoff + z) * mCurrentDimY + (yoff + y)) * mCurrentDimX + xoff) * eSize;
                System.arraycopy(data.mData, srcOff, mData, dstOff, w * eSize);
            }
        }
    }

    public void copyTo(Bitmap b) {
        checkValid();
        if (b == null) {
            throw new RSIllegalArgumentException("Bitmap cannot be null.");
        }
        validateBitmapFormat(b);
        validateBitmapSize(b);
        ByteBuffer buf = ByteBuffer.wrap(mData, 0, b.getByteCount()).order(ByteOrder.nativeOrder());
        b.copyPixelsFromBuffer(buf);
    }

    public void copyTo(Object array) {
        checkValid();
        validateArrayType(array);
        copy1DRangeToUnchecked(0, mCurrentCount, array);
    }

    public void copyTo(byte[] d) { copyTo((Object) d); }
    public void copyTo(short[] d) { copyTo((Object) d); }
    public void copyTo(int[] d) { copyTo((Object) d); }
    public void copyTo(float[] d) { copyTo((Object) d); }

    public synchronized void resize(int dimX) {
        if (mType.getY() > 0 || mType.getZ() > 0 || mType.hasFaces() || mType.hasMipmaps()) {
            throw new RSInvalidStateException("Resize only support for 1D allocations at this time.");
        }
        mType = Type.createX(mRS, mType.getElement(), dimX);
        updateCacheInfo(mType);
        int newSize = dimX * getElement().getBytesSize();
        byte[] next = new byte[newSize];
        System.arraycopy(mData, 0, next, 0, Math.min(mData.length, newSize));
        mData = next;
        mSize = newSize;
        mByteBuffer = null;
    }

    public void copy1DRangeToUnchecked(int off, int count, Object array) {
        checkValid();
        if (off < 0 || count < 0 || off + count > mCurrentCount) {
            throw new RSIllegalArgumentException("Invalid 1D range: off=" + off + " count=" + count);
        }
        int eSize = getElement().getBytesSize();
        readBytesToArrayRange(off * eSize, array, 0, count * eSize, count);
    }

    public void copy1DRangeToUnchecked(int off, int count, int[] d) { copy1DRangeToUnchecked(off, count, (Object) d); }
    public void copy1DRangeToUnchecked(int off, int count, short[] d) { copy1DRangeToUnchecked(off, count, (Object) d); }
    public void copy1DRangeToUnchecked(int off, int count, byte[] d) { copy1DRangeToUnchecked(off, count, (Object) d); }
    public void copy1DRangeToUnchecked(int off, int count, float[] d) { copy1DRangeToUnchecked(off, count, (Object) d); }

    public void copy1DRangeTo(int off, int count, Object array) {
        checkValid();
        validateArrayType(array);
        copy1DRangeToUnchecked(off, count, array);
    }

    public void copy1DRangeTo(int off, int count, int[] d) { copy1DRangeTo(off, count, (Object) d); }
    public void copy1DRangeTo(int off, int count, short[] d) { copy1DRangeTo(off, count, (Object) d); }
    public void copy1DRangeTo(int off, int count, byte[] d) { copy1DRangeTo(off, count, (Object) d); }
    public void copy1DRangeTo(int off, int count, float[] d) { copy1DRangeTo(off, count, (Object) d); }

    public void copy2DRangeTo(int xoff, int yoff, int w, int h, Object array) {
        checkValid();
        validateArrayType(array);
        validate2DRange(xoff, yoff, w, h);
        int eSize = getElement().getBytesSize();
        Class<?> comp = unwrapPrimitiveArray(array);
        int compSize = componentByteSize(comp);
        int elemsPerRow = (mAutoPadding && getElement().getVectorSize() == 3) ? (w * 3) : ((w * eSize) / compSize);
        for (int row = 0; row < h; row++) {
            int srcOff = ((yoff + row) * mCurrentDimX + xoff) * eSize;
            readBytesToArrayRange(srcOff, array, row * elemsPerRow, w * eSize, w);
        }
    }

    public void copy2DRangeTo(int xoff, int yoff, int w, int h, byte[] data) { copy2DRangeTo(xoff, yoff, w, h, (Object) data); }
    public void copy2DRangeTo(int xoff, int yoff, int w, int h, short[] data) { copy2DRangeTo(xoff, yoff, w, h, (Object) data); }
    public void copy2DRangeTo(int xoff, int yoff, int w, int h, int[] data) { copy2DRangeTo(xoff, yoff, w, h, (Object) data); }
    public void copy2DRangeTo(int xoff, int yoff, int w, int h, float[] data) { copy2DRangeTo(xoff, yoff, w, h, (Object) data); }

    public void copy3DRangeTo(int xoff, int yoff, int zoff, int w, int h, int d, Object array) {
        checkValid();
        validateArrayType(array);
        validate3DRange(xoff, yoff, zoff, w, h, d);
        int eSize = getElement().getBytesSize();
        Class<?> comp = unwrapPrimitiveArray(array);
        int compSize = componentByteSize(comp);
        int elemsPerRow = (mAutoPadding && getElement().getVectorSize() == 3) ? (w * 3) : ((w * eSize) / compSize);
        int rowIndex = 0;
        for (int z = 0; z < d; z++) {
            for (int y = 0; y < h; y++) {
                int srcOff = (((zoff + z) * mCurrentDimY + (yoff + y)) * mCurrentDimX + xoff) * eSize;
                readBytesToArrayRange(srcOff, array, rowIndex * elemsPerRow, w * eSize, w);
                rowIndex++;
            }
        }
    }

    public static Allocation createTyped(RenderScript rs, Type type, MipmapControl mips, int usage) {
        rs.validate();
        if (type == null) {
            throw new RSIllegalArgumentException("Type cannot be null.");
        }
        type.checkValid();
        Allocation a = new Allocation(0, rs, type, usage);
        a.mMipmapControl = mips;
        return a;
    }

    public static Allocation createTyped(RenderScript rs, Type type, int usage) {
        return createTyped(rs, type, MipmapControl.MIPMAP_NONE, usage);
    }

    public static Allocation createTyped(RenderScript rs, Type type) {
        return createTyped(rs, type, MipmapControl.MIPMAP_NONE, USAGE_SCRIPT);
    }

    public static Allocation createSized(RenderScript rs, Element e, int count, int usage) {
        rs.validate();
        e.checkValid();
        Type t = Type.createX(rs, e, count);
        return createTyped(rs, t, MipmapControl.MIPMAP_NONE, usage);
    }

    public static Allocation createSized(RenderScript rs, Element e, int count) {
        return createSized(rs, e, count, USAGE_SCRIPT);
    }

    private static Element elementFromBitmap(RenderScript rs, Bitmap b) {
        Bitmap.Config bc = b.getConfig();
        if (bc == Bitmap.Config.ALPHA_8) {
            return Element.A_8(rs);
        }
        if (bc == Bitmap.Config.ARGB_4444) {
            return Element.RGBA_4444(rs);
        }
        if (bc == Bitmap.Config.ARGB_8888) {
            return Element.RGBA_8888(rs);
        }
        if (bc == Bitmap.Config.RGB_565) {
            return Element.RGB_565(rs);
        }
        throw new RSInvalidStateException("Bad bitmap type: " + bc);
    }

    private static Type typeFromBitmap(RenderScript rs, Bitmap b, MipmapControl mip) {
        Element e = elementFromBitmap(rs, b);
        Type.Builder tb = new Type.Builder(rs, e);
        tb.setX(b.getWidth());
        tb.setY(b.getHeight());
        tb.setMipmaps(mip == MipmapControl.MIPMAP_FULL);
        return tb.create();
    }

    private void validateBitmapFormat(Bitmap b) {
        Bitmap.Config bc = b.getConfig();
        if (bc == null) {
            throw new RSIllegalArgumentException("Bitmap has an unsupported format for this operation");
        }
        Element e = getElement();
        switch (bc) {
            case ALPHA_8:
                if (e.getDataKind() != Element.DataKind.PIXEL_A
                        && !(e.getDataType() == Element.DataType.UNSIGNED_8 && e.getVectorSize() == 1)) {
                    throw new RSIllegalArgumentException("Allocation kind is " + e.getDataKind() + ", expected PIXEL_A");
                }
                break;
            case ARGB_8888:
                if (!e.isCompatible(Element.RGBA_8888(mRS))) {
                    throw new RSIllegalArgumentException("Allocation element is incompatible with ARGB_8888");
                }
                break;
            case RGB_565:
                if (!e.isCompatible(Element.RGB_565(mRS))) {
                    throw new RSIllegalArgumentException("Allocation element is incompatible with RGB_565");
                }
                break;
            case ARGB_4444:
                if (!e.isCompatible(Element.RGBA_4444(mRS)) && !e.isCompatible(Element.RGBA_8888(mRS))) {
                    throw new RSIllegalArgumentException("Allocation element is incompatible with ARGB_4444");
                }
                break;
            default:
                throw new RSIllegalArgumentException("Unsupported bitmap config: " + bc);
        }
    }

    private void validateBitmapSize(Bitmap b) {
        if (mCurrentDimX != b.getWidth() || mCurrentDimY != b.getHeight()) {
            throw new RSIllegalArgumentException("Cannot update allocation from bitmap, sizes mismatch");
        }
    }

    public static Allocation createFromBitmap(RenderScript rs, Bitmap b, MipmapControl mips, int usage) {
        rs.validate();
        if (b == null) {
            throw new RSIllegalArgumentException("Bitmap cannot be null.");
        }
        if (b.getConfig() == null) {
            if ((usage & USAGE_SHARED) != 0) {
                throw new RSIllegalArgumentException("USAGE_SHARED cannot be used with a Bitmap that has a null config.");
            }
            Bitmap newBitmap = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(newBitmap);
            c.drawBitmap(b, 0, 0, null);
            return createFromBitmap(rs, newBitmap, mips, usage);
        }
        Type t = typeFromBitmap(rs, b, mips);
        Allocation a = createTyped(rs, t, mips, usage);
        a.mBitmap = b;
        a.copyFrom(b);
        return a;
    }

    public ByteBuffer getByteBuffer() {
        checkValid();
        if (mByteBuffer == null) {
            mByteBuffer = ByteBuffer.wrap(mData).order(ByteOrder.LITTLE_ENDIAN);
        }
        return mByteBuffer;
    }

    public static Allocation[] createAllocations(RenderScript rs, Type t, int mip, int numAlloc) {
        rs.validate();
        t.checkValid();
        if (numAlloc < 1) {
            throw new RSIllegalArgumentException("Can't create less than 1 allocation.");
        }
        Allocation[] out = new Allocation[numAlloc];
        for (int i = 0; i < numAlloc; i++) {
            out[i] = createTyped(rs, t, mip);
        }
        return out;
    }

    public long getStride() {
        checkValid();
        return (long) mCurrentDimX * getElement().getBytesSize();
    }

    public long getTimeStamp() {
        return mTimeStamp;
    }

    public Surface getSurface() {
        if ((mUsage & USAGE_IO_INPUT) == 0) {
            throw new RSInvalidStateException("Can only get surface if IO_INPUT usage specified.");
        }
        return mSurface;
    }

    public void setSurface(Surface sur) {
        checkValid();
        if ((mUsage & USAGE_IO_OUTPUT) == 0) {
            throw new RSInvalidStateException("Can only set surface if IO_OUTPUT usage specified.");
        }
        mSurface = sur;
    }

    public static Allocation createFromBitmap(RenderScript rs, Bitmap b) {
        return createFromBitmap(rs, b, MipmapControl.MIPMAP_NONE, USAGE_SCRIPT | USAGE_SHARED);
    }

    public static Allocation createCubemapFromBitmap(RenderScript rs, Bitmap b, MipmapControl mips, int usage) {
        rs.validate();
        int height = b.getHeight();
        int width = b.getWidth();
        if (width % 6 != 0 || width / 6 != height) {
            throw new RSIllegalArgumentException("Cubemap height must be power of 2 and width must be 6 * height");
        }
        Element e = elementFromBitmap(rs, b);
        Type.Builder tb = new Type.Builder(rs, e);
        tb.setX(height);
        tb.setY(height);
        tb.setFaces(true);
        tb.setMipmaps(mips == MipmapControl.MIPMAP_FULL);
        Type t = tb.create();
        Allocation a = createTyped(rs, t, mips, usage);
        byte[] strip = new byte[b.getByteCount()];
        b.copyPixelsToBuffer(ByteBuffer.wrap(strip).order(ByteOrder.nativeOrder()));
        int eSize = e.getBytesSize();
        int faceRowBytes = height * eSize;
        int stripRowBytes = width * eSize;
        int faceBytes = height * faceRowBytes;
        for (int face = 0; face < 6; face++) {
            for (int y = 0; y < height; y++) {
                System.arraycopy(strip, y * stripRowBytes + face * faceRowBytes,
                        a.mData, face * faceBytes + y * faceRowBytes, faceRowBytes);
            }
        }
        return a;
    }

    public static Allocation createCubemapFromBitmap(RenderScript rs, Bitmap b) {
        return createCubemapFromBitmap(rs, b, MipmapControl.MIPMAP_NONE, USAGE_GRAPHICS_TEXTURE);
    }

    public static Allocation createCubemapFromCubeFaces(RenderScript rs,
            Bitmap xpos, Bitmap xneg, Bitmap ypos, Bitmap yneg, Bitmap zpos, Bitmap zneg,
            MipmapControl mips, int usage) {
        rs.validate();
        int side = xpos.getWidth();
        if (xpos.getHeight() != side
                || xneg.getWidth() != side || xneg.getHeight() != side
                || ypos.getWidth() != side || ypos.getHeight() != side
                || yneg.getWidth() != side || yneg.getHeight() != side
                || zpos.getWidth() != side || zpos.getHeight() != side
                || zneg.getWidth() != side || zneg.getHeight() != side) {
            throw new RSIllegalArgumentException("Cube faces must be square and equal size.");
        }
        Element e = elementFromBitmap(rs, xpos);
        Type.Builder tb = new Type.Builder(rs, e);
        tb.setX(side);
        tb.setY(side);
        tb.setFaces(true);
        tb.setMipmaps(mips == MipmapControl.MIPMAP_FULL);
        Allocation a = createTyped(rs, tb.create(), mips, usage);
        Bitmap[] faces = {xpos, xneg, ypos, yneg, zpos, zneg};
        int faceBytes = side * side * e.getBytesSize();
        for (int i = 0; i < 6; i++) {
            ByteBuffer dst = ByteBuffer.wrap(a.mData, i * faceBytes, faceBytes).order(ByteOrder.nativeOrder());
            faces[i].copyPixelsToBuffer(dst);
        }
        return a;
    }

    public static Allocation createCubemapFromCubeFaces(RenderScript rs,
            Bitmap xpos, Bitmap xneg, Bitmap ypos, Bitmap yneg, Bitmap zpos, Bitmap zneg) {
        return createCubemapFromCubeFaces(rs, xpos, xneg, ypos, yneg, zpos, zneg,
                MipmapControl.MIPMAP_NONE, USAGE_GRAPHICS_TEXTURE);
    }

    public static Allocation createFromBitmapResource(RenderScript rs, Resources res, int id,
            MipmapControl mips, int usage) {
        rs.validate();
        if ((usage & (USAGE_SHARED | USAGE_IO_INPUT | USAGE_IO_OUTPUT)) != 0) {
            throw new RSIllegalArgumentException("Unsupported usage specified.");
        }
        Bitmap b = BitmapFactory.decodeResource(res, id);
        Allocation alloc = createFromBitmap(rs, b, mips, usage);
        b.recycle();
        return alloc;
    }

    public static Allocation createFromBitmapResource(RenderScript rs, Resources res, int id) {
        return createFromBitmapResource(rs, res, id, MipmapControl.MIPMAP_NONE, USAGE_GRAPHICS_TEXTURE);
    }

    public static Allocation createFromString(RenderScript rs, String str, int usage) {
        rs.validate();
        if (str == null) {
            throw new RSIllegalArgumentException("String cannot be null.");
        }
        byte[] allocArray = str.getBytes(StandardCharsets.UTF_8);
        Allocation alloc = createSized(rs, Element.U8(rs), allocArray.length, usage);
        alloc.copyFrom(allocArray);
        return alloc;
    }

    public void setOnBufferAvailableListener(OnBufferAvailableListener callback) {
        checkValid();
        if ((mUsage & USAGE_IO_INPUT) == 0) {
            throw new RSIllegalArgumentException("Callback can only be set when USAGE_IO_INPUT is specified.");
        }
        mBufferNotifier = callback;
    }

    public void destroy() {
        super.destroy();
        mBitmap = null;
        mByteBuffer = null;
    }
}
