// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

import java.util.concurrent.atomic.AtomicLong;

public class BaseObj {
    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private long mID;
    private boolean mDestroyed;
    private String mName;
    RenderScript mRS;

    BaseObj(long id, RenderScript rs) {
        if (rs != null) {
            rs.validate();
        }
        mRS = rs;
        mID = id == 0 ? NEXT_ID.getAndIncrement() : id;
        mDestroyed = false;
    }

    long getID(RenderScript rs) {
        if (mDestroyed) {
            throw new RSInvalidStateException("using a destroyed object.");
        }
        if (mID == 0) {
            throw new RSRuntimeException("Internal error: Object id 0.");
        }
        if (rs != null && mRS != null && rs != mRS) {
            throw new RSInvalidStateException("using object with mismatched context.");
        }
        return mID;
    }

    void checkValid() {
        if (mDestroyed || mID == 0) {
            throw new RSIllegalArgumentException("Invalid object.");
        }
    }

    public void setName(String name) {
        if (name == null) {
            throw new RSIllegalArgumentException("setName requires a string of non-zero length.");
        }
        if (name.length() < 1) {
            throw new RSIllegalArgumentException("setName does not accept a zero length string.");
        }
        if (mName != null) {
            throw new RSIllegalArgumentException("setName object already has a name.");
        }
        mName = name;
    }

    public String getName() {
        return mName;
    }

    protected void finalize() throws Throwable {
        try {
            if (!mDestroyed) {
                mID = 0;
                mDestroyed = true;
            }
        } finally {
            super.finalize();
        }
    }

    public void destroy() {
        if (mDestroyed) {
            throw new RSInvalidStateException("Object already destroyed.");
        }
        mDestroyed = true;
        mID = 0;
    }

    boolean isDestroyed() {
        return mDestroyed;
    }

    public int hashCode() {
        return (int) ((mID & 0xFFFFFFFL) ^ (mID >> 32));
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BaseObj b = (BaseObj) obj;
        return mID == b.mID && !mDestroyed && !b.mDestroyed;
    }
}
