// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

public class AllocationAdapter extends Allocation {
    Type mWindow;
    private final Allocation mConstrainedAllocation;
    private int mSelectedLOD;
    private Type.CubemapFace mSelectedFace = Type.CubemapFace.POSITIVE_X;
    private int mSelectedX;
    private int mSelectedY;
    private int mSelectedZ;

    AllocationAdapter(long id, RenderScript rs, Allocation alloc, Type t) {
        super(id, rs, t, alloc.mUsage, alloc.mData);
        mConstrainedAllocation = alloc;
        mWindow = t;
    }

    public void setLOD(int lod) {
        if (!mConstrainedAllocation.getType().hasMipmaps()) {
            throw new RSInvalidStateException("Cannot set LOD when the allocation type does not include mipmaps.");
        }
        if (lod < 0) {
            throw new RSIllegalArgumentException("LOD must be >= 0.");
        }
        mSelectedLOD = lod;
    }

    public void setFace(Type.CubemapFace cf) {
        if (!mConstrainedAllocation.getType().hasFaces()) {
            throw new RSInvalidStateException("Cannot set Face when the allocation type does not include faces.");
        }
        if (cf == null) {
            throw new RSIllegalArgumentException("Cannot set null face.");
        }
        mSelectedFace = cf;
    }

    public void setX(int x) {
        if (mConstrainedAllocation.getType().getX() <= x || x < 0) {
            throw new RSInvalidStateException("Cannot set X greater than dimension of allocation.");
        }
        if (mWindow.getX() == mConstrainedAllocation.getType().getX()) {
            throw new RSInvalidStateException("Cannot set X when the adapter includes X.");
        }
        if (mWindow.getX() + x >= mConstrainedAllocation.getType().getX()) {
            throw new RSInvalidStateException("Cannot set (X + window) which would be larger than dimension of allocation.");
        }
        mSelectedX = x;
    }

    public void setY(int y) {
        if (mConstrainedAllocation.getType().getY() == 0) {
            throw new RSInvalidStateException("Cannot set Y when the allocation type does not include Y dim.");
        }
        if (mConstrainedAllocation.getType().getY() <= y || y < 0) {
            throw new RSInvalidStateException("Cannot set Y greater than dimension of allocation.");
        }
        if (mWindow.getY() == mConstrainedAllocation.getType().getY()) {
            throw new RSInvalidStateException("Cannot set Y when the adapter includes Y.");
        }
        if (mWindow.getY() + y >= mConstrainedAllocation.getType().getY()) {
            throw new RSInvalidStateException("Cannot set (Y + window) which would be larger than dimension of allocation.");
        }
        mSelectedY = y;
    }

    public void setZ(int z) {
        if (mConstrainedAllocation.getType().getZ() == 0) {
            throw new RSInvalidStateException("Cannot set Z when the allocation type does not include Z dim.");
        }
        if (mConstrainedAllocation.getType().getZ() <= z || z < 0) {
            throw new RSInvalidStateException("Cannot set Z greater than dimension of allocation.");
        }
        if (mWindow.getZ() == mConstrainedAllocation.getType().getZ()) {
            throw new RSInvalidStateException("Cannot set Z when the adapter includes Z.");
        }
        if (mWindow.getZ() + z >= mConstrainedAllocation.getType().getZ()) {
            throw new RSInvalidStateException("Cannot set (Z + window) which would be larger than dimension of allocation.");
        }
        mSelectedZ = z;
    }

    public static AllocationAdapter create1D(RenderScript rs, Allocation a) {
        rs.validate();
        Type t = Type.createX(rs, a.getElement(), a.getType().getX());
        return createTyped(rs, a, t);
    }

    public static AllocationAdapter create2D(RenderScript rs, Allocation a) {
        rs.validate();
        Type t = Type.createXY(rs, a.getElement(), a.getType().getX(), a.getType().getY());
        return createTyped(rs, a, t);
    }

    public static AllocationAdapter createTyped(RenderScript rs, Allocation a, Type t) {
        rs.validate();
        a.checkValid();
        t.checkValid();
        if (!a.getType().getElement().equals(t.getElement())) {
            throw new RSInvalidStateException("Element must match Allocation type.");
        }
        if (t.hasFaces() || t.hasMipmaps()) {
            throw new RSInvalidStateException("Adapters do not support window types with Mipmaps or Faces.");
        }
        Type at = a.getType();
        if (t.getX() > at.getX() || t.getY() > at.getY() || t.getZ() > at.getZ()) {
            throw new RSInvalidStateException("Type cannot have dimension larger than the source allocation.");
        }
        return new AllocationAdapter(0, rs, a, t);
    }

    public synchronized void resize(int dimX) {
        throw new RSInvalidStateException("Resize not allowed for Adapters.");
    }
}
