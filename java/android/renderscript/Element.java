// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

public class Element extends BaseObj {
    public enum DataType {
        NONE(0, 0),
        FLOAT_16(1, 2),
        FLOAT_32(2, 4),
        FLOAT_64(3, 8),
        SIGNED_8(4, 1),
        SIGNED_16(5, 2),
        SIGNED_32(6, 4),
        SIGNED_64(7, 8),
        UNSIGNED_8(8, 1),
        UNSIGNED_16(9, 2),
        UNSIGNED_32(10, 4),
        UNSIGNED_64(11, 8),
        BOOLEAN(12, 1),
        UNSIGNED_5_6_5(13, 2),
        UNSIGNED_5_5_5_1(14, 2),
        UNSIGNED_4_4_4_4(15, 2),
        MATRIX_4X4(16, 64),
        MATRIX_3X3(17, 36),
        MATRIX_2X2(18, 16),
        RS_ELEMENT(1000, 32),
        RS_TYPE(1001, 32),
        RS_ALLOCATION(1002, 32),
        RS_SAMPLER(1003, 32),
        RS_SCRIPT(1004, 32),
        RS_MESH(1005, 32),
        RS_PROGRAM_FRAGMENT(1006, 32),
        RS_PROGRAM_VERTEX(1007, 32),
        RS_PROGRAM_RASTER(1008, 32),
        RS_PROGRAM_STORE(1009, 32),
        RS_FONT(1010, 32);

        final int mID;
        final int mSize;

        DataType(int id, int size) {
            mID = id;
            mSize = size;
        }
    }

    public enum DataKind {
        USER(0),
        PIXEL_L(7),
        PIXEL_A(8),
        PIXEL_LA(9),
        PIXEL_RGB(10),
        PIXEL_RGBA(11),
        PIXEL_DEPTH(12),
        PIXEL_YUV(13);

        final int mID;

        DataKind(int id) {
            mID = id;
        }
    }

    int mSize;
    Element[] mElements;
    String[] mElementNames;
    int[] mArraySizes;
    int[] mOffsetInBytes;
    int[] mVisibleElementMap;

    DataType mType;
    DataKind mKind;
    boolean mNormalized;
    int mVectorSize;

    Element(long id, RenderScript rs, Element[] e, String[] n, int[] as) {
        super(id, rs);
        mSize = 0;
        mVectorSize = 1;
        mElements = e;
        mElementNames = n;
        mArraySizes = as;
        mType = DataType.NONE;
        mKind = DataKind.USER;
        mOffsetInBytes = new int[mElements.length];
        for (int ct = 0; ct < mElements.length; ct++) {
            mOffsetInBytes[ct] = mSize;
            mSize += mElements[ct].mSize * mArraySizes[ct];
        }
        updateVisibleSubElements();
    }

    Element(long id, RenderScript rs, DataType dt, DataKind dk, boolean norm, int size) {
        super(id, rs);
        if (dt != DataType.UNSIGNED_5_6_5
                && dt != DataType.UNSIGNED_4_4_4_4
                && dt != DataType.UNSIGNED_5_5_5_1) {
            if (size == 3) {
                mSize = dt.mSize * 4;
            } else {
                mSize = dt.mSize * size;
            }
        } else {
            mSize = dt.mSize;
        }
        mType = dt;
        mKind = dk;
        mNormalized = norm;
        mVectorSize = size;
    }

    private void updateVisibleSubElements() {
        if (mElements == null) {
            return;
        }
        int noPaddingFieldCount = 0;
        int fieldCount = mElementNames.length;
        for (int ct = 0; ct < fieldCount; ct++) {
            if (mElementNames[ct].charAt(0) != '#') {
                noPaddingFieldCount++;
            }
        }
        mVisibleElementMap = new int[noPaddingFieldCount];
        for (int ct = 0, ctNoPadding = 0; ct < fieldCount; ct++) {
            if (mElementNames[ct].charAt(0) != '#') {
                mVisibleElementMap[ctNoPadding++] = ct;
            }
        }
    }

    public int getBytesSize() {
        return mSize;
    }

    public int getVectorSize() {
        return mVectorSize;
    }

    public boolean isComplex() {
        if (mElements == null) {
            return false;
        }
        for (int ct = 0; ct < mElements.length; ct++) {
            if (mElements[ct].mElements != null) {
                return true;
            }
        }
        return false;
    }

    public int getSubElementCount() {
        if (mVisibleElementMap == null) {
            return 0;
        }
        return mVisibleElementMap.length;
    }

    public Element getSubElement(int index) {
        if (mVisibleElementMap == null) {
            throw new RSIllegalArgumentException("Element contains no sub-elements");
        }
        if (index < 0 || index >= mVisibleElementMap.length) {
            throw new RSIllegalArgumentException("Illegal sub-element index");
        }
        return mElements[mVisibleElementMap[index]];
    }

    public String getSubElementName(int index) {
        if (mVisibleElementMap == null) {
            throw new RSIllegalArgumentException("Element contains no sub-elements");
        }
        if (index < 0 || index >= mVisibleElementMap.length) {
            throw new RSIllegalArgumentException("Illegal sub-element index");
        }
        return mElementNames[mVisibleElementMap[index]];
    }

    public int getSubElementArraySize(int index) {
        if (mVisibleElementMap == null) {
            throw new RSIllegalArgumentException("Element contains no sub-elements");
        }
        if (index < 0 || index >= mVisibleElementMap.length) {
            throw new RSIllegalArgumentException("Illegal sub-element index");
        }
        return mArraySizes[mVisibleElementMap[index]];
    }

    public int getSubElementOffsetBytes(int index) {
        if (mVisibleElementMap == null) {
            throw new RSIllegalArgumentException("Element contains no sub-elements");
        }
        if (index < 0 || index >= mVisibleElementMap.length) {
            throw new RSIllegalArgumentException("Illegal sub-element index");
        }
        return mOffsetInBytes[mVisibleElementMap[index]];
    }

    public DataType getDataType() {
        return mType;
    }

    public DataKind getDataKind() {
        return mKind;
    }

    static Element createUser(RenderScript rs, DataType dt) {
        return new Element(0, rs, dt, DataKind.USER, false, 1);
    }

    public static Element BOOLEAN(RenderScript rs) {
        rs.validate();
        if (rs.mElement_BOOLEAN == null) {
            rs.mElement_BOOLEAN = createUser(rs, DataType.BOOLEAN);
        }
        return rs.mElement_BOOLEAN;
    }

    public static Element U8(RenderScript rs) {
        rs.validate();
        if (rs.mElement_USER_U8 == null) {
            rs.mElement_USER_U8 = createUser(rs, DataType.UNSIGNED_8);
        }
        return rs.mElement_USER_U8;
    }

    public static Element I8(RenderScript rs) {
        rs.validate();
        if (rs.mElement_I8 == null) {
            rs.mElement_I8 = createUser(rs, DataType.SIGNED_8);
        }
        return rs.mElement_I8;
    }

    public static Element U16(RenderScript rs) {
        rs.validate();
        if (rs.mElement_U16 == null) {
            rs.mElement_U16 = createUser(rs, DataType.UNSIGNED_16);
        }
        return rs.mElement_U16;
    }

    public static Element I16(RenderScript rs) {
        rs.validate();
        if (rs.mElement_I16 == null) {
            rs.mElement_I16 = createUser(rs, DataType.SIGNED_16);
        }
        return rs.mElement_I16;
    }

    public static Element U32(RenderScript rs) {
        rs.validate();
        if (rs.mElement_U32 == null) {
            rs.mElement_U32 = createUser(rs, DataType.UNSIGNED_32);
        }
        return rs.mElement_U32;
    }

    public static Element I32(RenderScript rs) {
        rs.validate();
        if (rs.mElement_I32 == null) {
            rs.mElement_I32 = createUser(rs, DataType.SIGNED_32);
        }
        return rs.mElement_I32;
    }

    public static Element U64(RenderScript rs) {
        rs.validate();
        if (rs.mElement_U64 == null) {
            rs.mElement_U64 = createUser(rs, DataType.UNSIGNED_64);
        }
        return rs.mElement_U64;
    }

    public static Element I64(RenderScript rs) {
        rs.validate();
        if (rs.mElement_I64 == null) {
            rs.mElement_I64 = createUser(rs, DataType.SIGNED_64);
        }
        return rs.mElement_I64;
    }

    public static Element F16(RenderScript rs) {
        rs.validate();
        if (rs.mElement_F16 == null) {
            rs.mElement_F16 = createUser(rs, DataType.FLOAT_16);
        }
        return rs.mElement_F16;
    }

    public static Element F32(RenderScript rs) {
        rs.validate();
        if (rs.mElement_F32 == null) {
            rs.mElement_F32 = createUser(rs, DataType.FLOAT_32);
        }
        return rs.mElement_F32;
    }

    public static Element F64(RenderScript rs) {
        rs.validate();
        if (rs.mElement_F64 == null) {
            rs.mElement_F64 = createUser(rs, DataType.FLOAT_64);
        }
        return rs.mElement_F64;
    }

    public static Element ELEMENT(RenderScript rs) {
        rs.validate();
        if (rs.mElement_ELEMENT == null) {
            rs.mElement_ELEMENT = createUser(rs, DataType.RS_ELEMENT);
        }
        return rs.mElement_ELEMENT;
    }

    public static Element TYPE(RenderScript rs) {
        rs.validate();
        if (rs.mElement_TYPE == null) {
            rs.mElement_TYPE = createUser(rs, DataType.RS_TYPE);
        }
        return rs.mElement_TYPE;
    }

    public static Element ALLOCATION(RenderScript rs) {
        rs.validate();
        if (rs.mElement_ALLOCATION == null) {
            rs.mElement_ALLOCATION = createUser(rs, DataType.RS_ALLOCATION);
        }
        return rs.mElement_ALLOCATION;
    }

    public static Element SAMPLER(RenderScript rs) {
        rs.validate();
        if (rs.mElement_SAMPLER == null) {
            rs.mElement_SAMPLER = createUser(rs, DataType.RS_SAMPLER);
        }
        return rs.mElement_SAMPLER;
    }

    public static Element SCRIPT(RenderScript rs) {
        rs.validate();
        if (rs.mElement_SCRIPT == null) {
            rs.mElement_SCRIPT = createUser(rs, DataType.RS_SCRIPT);
        }
        return rs.mElement_SCRIPT;
    }

    public static Element MESH(RenderScript rs) {
        rs.validate();
        if (rs.mElement_MESH == null) {
            rs.mElement_MESH = createUser(rs, DataType.RS_MESH);
        }
        return rs.mElement_MESH;
    }

    public static Element PROGRAM_FRAGMENT(RenderScript rs) {
        rs.validate();
        if (rs.mElement_PROGRAM_FRAGMENT == null) {
            rs.mElement_PROGRAM_FRAGMENT = createUser(rs, DataType.RS_PROGRAM_FRAGMENT);
        }
        return rs.mElement_PROGRAM_FRAGMENT;
    }

    public static Element PROGRAM_VERTEX(RenderScript rs) {
        rs.validate();
        if (rs.mElement_PROGRAM_VERTEX == null) {
            rs.mElement_PROGRAM_VERTEX = createUser(rs, DataType.RS_PROGRAM_VERTEX);
        }
        return rs.mElement_PROGRAM_VERTEX;
    }

    public static Element PROGRAM_RASTER(RenderScript rs) {
        rs.validate();
        if (rs.mElement_PROGRAM_RASTER == null) {
            rs.mElement_PROGRAM_RASTER = createUser(rs, DataType.RS_PROGRAM_RASTER);
        }
        return rs.mElement_PROGRAM_RASTER;
    }

    public static Element PROGRAM_STORE(RenderScript rs) {
        rs.validate();
        if (rs.mElement_PROGRAM_STORE == null) {
            rs.mElement_PROGRAM_STORE = createUser(rs, DataType.RS_PROGRAM_STORE);
        }
        return rs.mElement_PROGRAM_STORE;
    }

    public static Element FONT(RenderScript rs) {
        rs.validate();
        if (rs.mElement_FONT == null) {
            rs.mElement_FONT = createUser(rs, DataType.RS_FONT);
        }
        return rs.mElement_FONT;
    }

    public static Element A_8(RenderScript rs) {
        rs.validate();
        if (rs.mElement_A_8 == null) {
            rs.mElement_A_8 = createPixel(rs, DataType.UNSIGNED_8, DataKind.PIXEL_A);
        }
        return rs.mElement_A_8;
    }

    public static Element RGB_565(RenderScript rs) {
        rs.validate();
        if (rs.mElement_RGB_565 == null) {
            rs.mElement_RGB_565 = createPixel(rs, DataType.UNSIGNED_5_6_5, DataKind.PIXEL_RGB);
        }
        return rs.mElement_RGB_565;
    }

    public static Element RGB_888(RenderScript rs) {
        rs.validate();
        if (rs.mElement_RGB_888 == null) {
            rs.mElement_RGB_888 = createPixel(rs, DataType.UNSIGNED_8, DataKind.PIXEL_RGB);
        }
        return rs.mElement_RGB_888;
    }

    public static Element RGBA_5551(RenderScript rs) {
        rs.validate();
        if (rs.mElement_RGBA_5551 == null) {
            rs.mElement_RGBA_5551 = createPixel(rs, DataType.UNSIGNED_5_5_5_1, DataKind.PIXEL_RGBA);
        }
        return rs.mElement_RGBA_5551;
    }

    public static Element RGBA_4444(RenderScript rs) {
        rs.validate();
        if (rs.mElement_RGBA_4444 == null) {
            rs.mElement_RGBA_4444 = createPixel(rs, DataType.UNSIGNED_4_4_4_4, DataKind.PIXEL_RGBA);
        }
        return rs.mElement_RGBA_4444;
    }

    public static Element RGBA_8888(RenderScript rs) {
        rs.validate();
        if (rs.mElement_RGBA_8888 == null) {
            rs.mElement_RGBA_8888 = createPixel(rs, DataType.UNSIGNED_8, DataKind.PIXEL_RGBA);
        }
        return rs.mElement_RGBA_8888;
    }

    public static Element F16_2(RenderScript rs) { return createVector(rs, DataType.FLOAT_16, 2); }
    public static Element F16_3(RenderScript rs) { return createVector(rs, DataType.FLOAT_16, 3); }
    public static Element F16_4(RenderScript rs) { return createVector(rs, DataType.FLOAT_16, 4); }

    public static Element F32_2(RenderScript rs) { return createVector(rs, DataType.FLOAT_32, 2); }
    public static Element F32_3(RenderScript rs) { return createVector(rs, DataType.FLOAT_32, 3); }
    public static Element F32_4(RenderScript rs) { return createVector(rs, DataType.FLOAT_32, 4); }

    public static Element F64_2(RenderScript rs) { return createVector(rs, DataType.FLOAT_64, 2); }
    public static Element F64_3(RenderScript rs) { return createVector(rs, DataType.FLOAT_64, 3); }
    public static Element F64_4(RenderScript rs) { return createVector(rs, DataType.FLOAT_64, 4); }

    public static Element U8_2(RenderScript rs) { return createVector(rs, DataType.UNSIGNED_8, 2); }
    public static Element U8_3(RenderScript rs) { return createVector(rs, DataType.UNSIGNED_8, 3); }
    public static Element U8_4(RenderScript rs) { return createVector(rs, DataType.UNSIGNED_8, 4); }

    public static Element I8_2(RenderScript rs) { return createVector(rs, DataType.SIGNED_8, 2); }
    public static Element I8_3(RenderScript rs) { return createVector(rs, DataType.SIGNED_8, 3); }
    public static Element I8_4(RenderScript rs) { return createVector(rs, DataType.SIGNED_8, 4); }

    public static Element U16_2(RenderScript rs) { return createVector(rs, DataType.UNSIGNED_16, 2); }
    public static Element U16_3(RenderScript rs) { return createVector(rs, DataType.UNSIGNED_16, 3); }
    public static Element U16_4(RenderScript rs) { return createVector(rs, DataType.UNSIGNED_16, 4); }

    public static Element I16_2(RenderScript rs) { return createVector(rs, DataType.SIGNED_16, 2); }
    public static Element I16_3(RenderScript rs) { return createVector(rs, DataType.SIGNED_16, 3); }
    public static Element I16_4(RenderScript rs) { return createVector(rs, DataType.SIGNED_16, 4); }

    public static Element U32_2(RenderScript rs) { return createVector(rs, DataType.UNSIGNED_32, 2); }
    public static Element U32_3(RenderScript rs) { return createVector(rs, DataType.UNSIGNED_32, 3); }
    public static Element U32_4(RenderScript rs) { return createVector(rs, DataType.UNSIGNED_32, 4); }

    public static Element I32_2(RenderScript rs) { return createVector(rs, DataType.SIGNED_32, 2); }
    public static Element I32_3(RenderScript rs) { return createVector(rs, DataType.SIGNED_32, 3); }
    public static Element I32_4(RenderScript rs) { return createVector(rs, DataType.SIGNED_32, 4); }

    public static Element U64_2(RenderScript rs) { return createVector(rs, DataType.UNSIGNED_64, 2); }
    public static Element U64_3(RenderScript rs) { return createVector(rs, DataType.UNSIGNED_64, 3); }
    public static Element U64_4(RenderScript rs) { return createVector(rs, DataType.UNSIGNED_64, 4); }

    public static Element I64_2(RenderScript rs) { return createVector(rs, DataType.SIGNED_64, 2); }
    public static Element I64_3(RenderScript rs) { return createVector(rs, DataType.SIGNED_64, 3); }
    public static Element I64_4(RenderScript rs) { return createVector(rs, DataType.SIGNED_64, 4); }

    public static Element YUV(RenderScript rs) {
        rs.validate();
        if (rs.mElement_YUV == null) {
            rs.mElement_YUV = createPixel(rs, DataType.UNSIGNED_8, DataKind.PIXEL_YUV);
        }
        return rs.mElement_YUV;
    }

    public static Element MATRIX_4X4(RenderScript rs) {
        rs.validate();
        if (rs.mElement_MATRIX_4X4 == null) {
            rs.mElement_MATRIX_4X4 = createUser(rs, DataType.MATRIX_4X4);
        }
        return rs.mElement_MATRIX_4X4;
    }

    public static Element MATRIX4X4(RenderScript rs) {
        return MATRIX_4X4(rs);
    }

    public static Element MATRIX_3X3(RenderScript rs) {
        rs.validate();
        if (rs.mElement_MATRIX_3X3 == null) {
            rs.mElement_MATRIX_3X3 = createUser(rs, DataType.MATRIX_3X3);
        }
        return rs.mElement_MATRIX_3X3;
    }

    public static Element MATRIX_2X2(RenderScript rs) {
        rs.validate();
        if (rs.mElement_MATRIX_2X2 == null) {
            rs.mElement_MATRIX_2X2 = createUser(rs, DataType.MATRIX_2X2);
        }
        return rs.mElement_MATRIX_2X2;
    }

    public static Element createVector(RenderScript rs, DataType dt, int size) {
        rs.validate();
        if (size < 2 || size > 4) {
            throw new RSIllegalArgumentException("Vector size out of range 2-4.");
        }
        switch (dt) {
            case FLOAT_16:
            case FLOAT_32:
            case FLOAT_64:
            case SIGNED_8:
            case SIGNED_16:
            case SIGNED_32:
            case SIGNED_64:
            case UNSIGNED_8:
            case UNSIGNED_16:
            case UNSIGNED_32:
            case UNSIGNED_64:
            case BOOLEAN:
                return new Element(0, rs, dt, DataKind.USER, false, size);
            default:
                throw new RSIllegalArgumentException("Cannot create vector of non-primitive type.");
        }
    }

    public static Element createPixel(RenderScript rs, DataType dt, DataKind dk) {
        rs.validate();
        if (dk != DataKind.PIXEL_L
                && dk != DataKind.PIXEL_A
                && dk != DataKind.PIXEL_LA
                && dk != DataKind.PIXEL_RGB
                && dk != DataKind.PIXEL_RGBA
                && dk != DataKind.PIXEL_DEPTH
                && dk != DataKind.PIXEL_YUV) {
            throw new RSIllegalArgumentException("Unsupported DataKind");
        }
        if (dt != DataType.UNSIGNED_8
                && dt != DataType.UNSIGNED_16
                && dt != DataType.UNSIGNED_5_6_5
                && dt != DataType.UNSIGNED_4_4_4_4
                && dt != DataType.UNSIGNED_5_5_5_1) {
            throw new RSIllegalArgumentException("Unsupported DataType");
        }
        if (dt == DataType.UNSIGNED_5_6_5 && dk != DataKind.PIXEL_RGB) {
            throw new RSIllegalArgumentException("Bad kind and type combo");
        }
        if (dt == DataType.UNSIGNED_5_5_5_1 && dk != DataKind.PIXEL_RGBA) {
            throw new RSIllegalArgumentException("Bad kind and type combo");
        }
        if (dt == DataType.UNSIGNED_4_4_4_4 && dk != DataKind.PIXEL_RGBA) {
            throw new RSIllegalArgumentException("Bad kind and type combo");
        }
        if (dt == DataType.UNSIGNED_16 && dk != DataKind.PIXEL_DEPTH) {
            throw new RSIllegalArgumentException("Bad kind and type combo");
        }
        int size = 1;
        switch (dk) {
            case PIXEL_LA:
                size = 2;
                break;
            case PIXEL_RGB:
                size = 3;
                break;
            case PIXEL_RGBA:
                size = 4;
                break;
            case PIXEL_DEPTH:
                size = 2;
                break;
            default:
                break;
        }
        return new Element(0, rs, dt, dk, true, size);
    }

    public boolean isCompatible(Element e) {
        if (this.equals(e)) {
            return true;
        }
        return (mSize == e.mSize)
                && (mType != DataType.NONE)
                && (mType == e.mType)
                && (mVectorSize == e.mVectorSize);
    }

    public static class Builder {
        RenderScript mRS;
        Element[] mElements;
        String[] mElementNames;
        int[] mArraySizes;
        int mCount;
        int mSkipPadding;

        public Builder(RenderScript rs) {
            mRS = rs;
            mCount = 0;
            mElements = new Element[8];
            mElementNames = new String[8];
            mArraySizes = new int[8];
        }

        public Builder add(Element element, String name, int arraySize) {
            if (arraySize < 1) {
                throw new RSIllegalArgumentException("Array size cannot be less than 1.");
            }
            if (mSkipPadding != 0) {
                if (name.startsWith("#padding_")) {
                    mSkipPadding = 0;
                    return this;
                }
            }
            if (element.mVectorSize == 3) {
                mSkipPadding = 1;
            } else {
                mSkipPadding = 0;
            }
            if (mCount == mElements.length) {
                Element[] e = new Element[mCount + 8];
                String[] s = new String[mCount + 8];
                int[] as = new int[mCount + 8];
                System.arraycopy(mElements, 0, e, 0, mCount);
                System.arraycopy(mElementNames, 0, s, 0, mCount);
                System.arraycopy(mArraySizes, 0, as, 0, mCount);
                mElements = e;
                mElementNames = s;
                mArraySizes = as;
            }
            mElements[mCount] = element;
            mElementNames[mCount] = name;
            mArraySizes[mCount] = arraySize;
            mCount++;
            return this;
        }

        public Builder add(Element element, String name) {
            return add(element, name, 1);
        }

        public Element create() {
            mRS.validate();
            Element[] ein = new Element[mCount];
            String[] sin = new String[mCount];
            int[] asin = new int[mCount];
            System.arraycopy(mElements, 0, ein, 0, mCount);
            System.arraycopy(mElementNames, 0, sin, 0, mCount);
            System.arraycopy(mArraySizes, 0, asin, 0, mCount);
            return new Element(0, mRS, ein, sin, asin);
        }
    }
}
