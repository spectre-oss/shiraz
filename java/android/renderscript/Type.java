// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

public class Type extends BaseObj {
    private static final int YUV_NV21 = 17;
    private static final int YUV_YV12 = 842094169;
    private static final int YUV_420_888 = 35;

    int mDimX;
    int mDimY;
    int mDimZ;
    boolean mDimMipmaps;
    boolean mDimFaces;
    int mDimYuv;
    int mElementCount;
    Element mElement;

    public enum CubemapFace {
        POSITIVE_X(0),
        NEGATIVE_X(1),
        POSITIVE_Y(2),
        NEGATIVE_Y(3),
        POSITIVE_Z(4),
        NEGATIVE_Z(5),
        POSITVE_X(0),
        POSITVE_Y(2),
        POSITVE_Z(4);

        final int mID;

        CubemapFace(int id) {
            mID = id;
        }
    }

    Type(long id, RenderScript rs) {
        super(id, rs);
    }

    public Element getElement() {
        return mElement;
    }

    public int getX() {
        return mDimX;
    }

    public int getY() {
        return mDimY;
    }

    public int getZ() {
        return mDimZ;
    }

    public int getYuv() {
        return mDimYuv;
    }

    public boolean hasMipmaps() {
        return mDimMipmaps;
    }

    public boolean hasFaces() {
        return mDimFaces;
    }

    public int getCount() {
        return mElementCount;
    }

    void calcElementCount() {
        boolean hasLod = hasMipmaps();
        int x = getX();
        int y = getY();
        int z = getZ();
        int faces = 1;
        if (hasFaces()) {
            faces = 6;
        }
        if (x == 0) {
            x = 1;
        }
        if (y == 0) {
            y = 1;
        }
        if (z == 0) {
            z = 1;
        }
        int count = x * y * z * faces;
        while (hasLod && (x > 1 || y > 1 || z > 1)) {
            if (x > 1) {
                x >>= 1;
            }
            if (y > 1) {
                y >>= 1;
            }
            if (z > 1) {
                z >>= 1;
            }
            count += x * y * z * faces;
        }
        if (mDimYuv != 0) {
            count = (getX() * getY() * 3) / 2;
        }
        mElementCount = count;
    }

    public static Type createX(RenderScript rs, Element e, int dimX) {
        if (dimX < 1) {
            throw new RSInvalidStateException("Dimension must be >= 1.");
        }
        Type t = new Type(0, rs);
        t.mElement = e;
        t.mDimX = dimX;
        t.calcElementCount();
        return t;
    }

    public static Type createXY(RenderScript rs, Element e, int dimX, int dimY) {
        if (dimX < 1 || dimY < 1) {
            throw new RSInvalidStateException("Dimension must be >= 1.");
        }
        Type t = new Type(0, rs);
        t.mElement = e;
        t.mDimX = dimX;
        t.mDimY = dimY;
        t.calcElementCount();
        return t;
    }

    public static Type createXYZ(RenderScript rs, Element e, int dimX, int dimY, int dimZ) {
        if (dimX < 1 || dimY < 1 || dimZ < 1) {
            throw new RSInvalidStateException("Dimension must be >= 1.");
        }
        Type t = new Type(0, rs);
        t.mElement = e;
        t.mDimX = dimX;
        t.mDimY = dimY;
        t.mDimZ = dimZ;
        t.calcElementCount();
        return t;
    }

    public static class Builder {
        RenderScript mRS;
        int mDimX = 1;
        int mDimY;
        int mDimZ;
        boolean mDimMipmaps;
        boolean mDimFaces;
        int mYuv;
        Element mElement;

        public Builder(RenderScript rs, Element e) {
            e.checkValid();
            mRS = rs;
            mElement = e;
        }

        public Builder setX(int value) {
            if (value < 1) {
                throw new RSIllegalArgumentException("Values of less than 1 for Dimension X are not valid.");
            }
            mDimX = value;
            return this;
        }

        public Builder setY(int value) {
            if (value < 1) {
                throw new RSIllegalArgumentException("Values of less than 1 for Dimension Y are not valid.");
            }
            mDimY = value;
            return this;
        }

        public Builder setZ(int value) {
            if (value < 1) {
                throw new RSIllegalArgumentException("Values of less than 1 for Dimension Z are not valid.");
            }
            mDimZ = value;
            return this;
        }

        public Builder setMipmaps(boolean value) {
            mDimMipmaps = value;
            return this;
        }

        public Builder setFaces(boolean value) {
            mDimFaces = value;
            return this;
        }

        public Builder setYuvFormat(int yuvFormat) {
            switch (yuvFormat) {
                case YUV_NV21:
                case YUV_YV12:
                case YUV_420_888:
                    break;
                default:
                    throw new RSIllegalArgumentException("Only ImageFormat.NV21, .YV12, and .YUV_420_888 are supported..");
            }
            mYuv = yuvFormat;
            return this;
        }

        public Type create() {
            if (mDimZ > 0) {
                if (mDimX < 1 || mDimY < 1) {
                    throw new RSInvalidStateException("Both X and Y dimension required when Z is present.");
                }
                if (mDimFaces) {
                    throw new RSInvalidStateException("Cube maps not supported with 3D types.");
                }
            }
            if (mDimY > 0 && mDimX < 1) {
                throw new RSInvalidStateException("X dimension required when Y is present.");
            }
            if (mDimFaces && mDimY < 1) {
                throw new RSInvalidStateException("Cube maps require 2D Types.");
            }
            if (mYuv != 0) {
                if (mDimZ != 0 || mDimFaces || mDimMipmaps) {
                    throw new RSInvalidStateException("YUV only supports basic 2D.");
                }
            }
            mRS.validate();
            Type t = new Type(0, mRS);
            t.mElement = mElement;
            t.mDimX = mDimX;
            t.mDimY = mDimY;
            t.mDimZ = mDimZ;
            t.mDimMipmaps = mDimMipmaps;
            t.mDimFaces = mDimFaces;
            t.mDimYuv = mYuv;
            t.calcElementCount();
            return t;
        }
    }
}
