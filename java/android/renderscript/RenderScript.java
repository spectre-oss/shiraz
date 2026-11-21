// SPDX-License-Identifier: GPL-3.0-only
package android.renderscript;

import android.content.Context;
import java.util.ArrayList;

public class RenderScript {
    public static final int CREATE_FLAG_NONE = 0x0000;
    public static final int CREATE_FLAG_LOW_LATENCY = 0x0002;
    public static final int CREATE_FLAG_LOW_POWER = 0x0004;

    private static final ArrayList<RenderScript> sProcessContextList = new ArrayList<RenderScript>();

    public enum ContextType {
        NORMAL,
        DEBUG,
        PROFILE
    }

    public enum Priority {
        LOW,
        NORMAL
    }

    public static class RSMessageHandler implements Runnable {
        protected int[] mData;
        protected int mID;
        protected int mLength;

        public RSMessageHandler() {}

        public void run() {}
    }

    public static class RSErrorHandler implements Runnable {
        protected String mErrorMessage;
        protected int mErrorNum;

        public RSErrorHandler() {}

        public void run() {}
    }

    private final Context mApplicationContext;
    private final ContextType mContextType;
    private final boolean mMultiContext;
    private Priority mPriority = Priority.NORMAL;
    private RSMessageHandler mMessageCallback;
    private RSErrorHandler mErrorCallback;
    private boolean mDestroyed;

    // Cached standard elements & samplers per RS context
    Element mElement_USER_U8;
    Element mElement_I8;
    Element mElement_U16;
    Element mElement_I16;
    Element mElement_U32;
    Element mElement_I32;
    Element mElement_U64;
    Element mElement_I64;
    Element mElement_F16;
    Element mElement_F32;
    Element mElement_F64;
    Element mElement_BOOLEAN;
    Element mElement_ELEMENT;
    Element mElement_TYPE;
    Element mElement_ALLOCATION;
    Element mElement_SAMPLER;
    Element mElement_SCRIPT;
    Element mElement_MESH;
    Element mElement_PROGRAM_FRAGMENT;
    Element mElement_PROGRAM_VERTEX;
    Element mElement_PROGRAM_RASTER;
    Element mElement_PROGRAM_STORE;
    Element mElement_FONT;
    Element mElement_A_8;
    Element mElement_RGB_565;
    Element mElement_RGB_888;
    Element mElement_RGBA_5551;
    Element mElement_RGBA_4444;
    Element mElement_RGBA_8888;
    Element mElement_YUV;
    Element mElement_MATRIX_4X4;
    Element mElement_MATRIX_3X3;
    Element mElement_MATRIX_2X2;

    Sampler mSampler_CLAMP_NEAREST;
    Sampler mSampler_CLAMP_LINEAR;
    Sampler mSampler_CLAMP_LINEAR_MIP_LINEAR;
    Sampler mSampler_WRAP_NEAREST;
    Sampler mSampler_WRAP_LINEAR;
    Sampler mSampler_WRAP_LINEAR_MIP_LINEAR;
    Sampler mSampler_MIRRORED_REPEAT_NEAREST;
    Sampler mSampler_MIRRORED_REPEAT_LINEAR;
    Sampler mSampler_MIRRORED_REPEAT_LINEAR_MIP_LINEAR;

    ScriptIntrinsicBLAS mBLAS;

    RenderScript(Context ctx, ContextType ct, boolean multiContext) {
        Context appCtx = ctx != null ? ctx.getApplicationContext() : null;
        mApplicationContext = appCtx != null ? appCtx : ctx;
        mContextType = ct != null ? ct : ContextType.NORMAL;
        mMultiContext = multiContext;
    }

    void validate() {
        if (mDestroyed) {
            throw new RSInvalidStateException("Calling RS with no Context active.");
        }
    }

    void validateObject(BaseObj obj) {
        if (obj != null) {
            obj.getID(this);
        }
    }

    boolean isDestroyed() {
        return mDestroyed;
    }

    public static long getMinorVersion() {
        return 1L;
    }

    public void setMessageHandler(RSMessageHandler msg) {
        mMessageCallback = msg;
    }

    public RSMessageHandler getMessageHandler() {
        return mMessageCallback;
    }

    public void sendMessage(int id, int[] data) {
        validate();
        RSMessageHandler cb = mMessageCallback;
        if (cb != null) {
            cb.mID = id;
            cb.mData = data != null ? data.clone() : null;
            cb.mLength = data != null ? data.length * 4 : 0;
            cb.run();
        }
    }

    public void setErrorHandler(RSErrorHandler msg) {
        mErrorCallback = msg;
    }

    public RSErrorHandler getErrorHandler() {
        return mErrorCallback;
    }

    public void setPriority(Priority p) {
        validate();
        if (p == null) {
            throw new RSIllegalArgumentException("Priority cannot be null.");
        }
        mPriority = p;
    }

    public final Context getApplicationContext() {
        return mApplicationContext;
    }

    public static RenderScript create(Context ctx) {
        return create(ctx, ContextType.NORMAL);
    }

    public static RenderScript create(Context ctx, ContextType ct) {
        return create(ctx, ct, CREATE_FLAG_NONE);
    }

    public static RenderScript create(Context ctx, ContextType ct, int flags) {
        if (ct == null) {
            ct = ContextType.NORMAL;
        }
        if ((flags & ~(CREATE_FLAG_LOW_LATENCY | CREATE_FLAG_LOW_POWER)) != 0) {
            throw new RSIllegalArgumentException("Invalid flags passed.");
        }
        synchronized (sProcessContextList) {
            for (RenderScript existing : sProcessContextList) {
                if (!existing.mDestroyed && existing.mContextType == ct && existing.mApplicationContext == (ctx != null && ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx)) {
                    return existing;
                }
            }
            RenderScript rs = new RenderScript(ctx, ct, false);
            sProcessContextList.add(rs);
            return rs;
        }
    }

    public static void releaseAllContexts() {
        ArrayList<RenderScript> copy;
        synchronized (sProcessContextList) {
            copy = new ArrayList<RenderScript>(sProcessContextList);
            sProcessContextList.clear();
        }
        for (RenderScript rs : copy) {
            if (!rs.mDestroyed) {
                rs.mDestroyed = true;
            }
        }
    }

    public static RenderScript createMultiContext(Context ctx, ContextType ct, int flags, int API_number) {
        if ((flags & ~(CREATE_FLAG_LOW_LATENCY | CREATE_FLAG_LOW_POWER)) != 0) {
            throw new RSIllegalArgumentException("Invalid flags passed.");
        }
        return new RenderScript(ctx, ct, true);
    }

    public void contextDump() {
        validate();
    }

    public void finish() {
        validate();
    }

    protected void finalize() throws Throwable {
        try {
            mDestroyed = true;
        } finally {
            super.finalize();
        }
    }

    public void destroy() {
        if (mMultiContext) {
            validate();
            mDestroyed = true;
            return;
        }
        synchronized (sProcessContextList) {
            if (mDestroyed) {
                throw new RSInvalidStateException("Context already destroyed.");
            }
            mDestroyed = true;
            sProcessContextList.remove(this);
        }
    }
}
