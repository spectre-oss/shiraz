// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.content.Context;
import android.content.res.Configuration;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.window.InputTransferToken;

public class SurfaceControlViewHost {
    private final Context mContext;
    private final Display mDisplay;
    private final InputTransferToken mHostInputTransferToken;
    private final SurfaceControl mSurfaceControl;
    private final InputTransferToken mSurfaceInputTransferToken;
    private View mView;
    private int mWidth;
    private int mHeight;
    private boolean mReleased;

    public SurfaceControlViewHost(Context context, Display display, IBinder hostToken) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        if (display == null) {
            throw new NullPointerException("display");
        }
        mContext = context;
        mDisplay = display;
        mHostInputTransferToken = hostToken != null ? new InputTransferToken() : null;
        mSurfaceInputTransferToken = new InputTransferToken();
        mSurfaceControl = new SurfaceControl.Builder()
                .setName("SurfaceControlViewHost")
                .build();
    }

    public SurfaceControlViewHost(
            Context context, Display display, InputTransferToken hostInputTransferToken) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        if (display == null) {
            throw new NullPointerException("display");
        }
        mContext = context;
        mDisplay = display;
        mHostInputTransferToken = hostInputTransferToken;
        mSurfaceInputTransferToken = new InputTransferToken();
        mSurfaceControl = new SurfaceControl.Builder()
                .setName("SurfaceControlViewHost")
                .build();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            release();
        } finally {
            super.finalize();
        }
    }

    public SurfacePackage getSurfacePackage() {
        if (mReleased || !mSurfaceControl.isValid()) {
            return null;
        }
        return new SurfacePackage(
                new SurfaceControl(mSurfaceControl), mSurfaceInputTransferToken);
    }

    public void setView(View view, int width, int height) {
        if (view == null) {
            throw new NullPointerException("view");
        }
        if (mView != null) {
            throw new IllegalStateException("View is already set on SurfaceControlViewHost");
        }
        mView = view;
        relayout(width, height);
    }

    public View getView() {
        return mView;
    }

    public void relayout(int width, int height) {
        mWidth = width;
        mHeight = height;
        if (mSurfaceControl.isValid() && width >= 0 && height >= 0) {
            new SurfaceControl.Transaction()
                    .setBufferSize(mSurfaceControl, width, height)
                    .setVisibility(mSurfaceControl, true)
                    .apply();
        }
        if (mView != null && width >= 0 && height >= 0) {
            int wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
            int hSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
            mView.measure(wSpec, hSpec);
            mView.layout(0, 0, width, height);
        }
    }

    public void release() {
        if (!mReleased) {
            mReleased = true;
            mSurfaceControl.release();
            mView = null;
        }
    }

    @Deprecated
    public boolean transferTouchGestureToHost() {
        return mHostInputTransferToken != null && !mReleased;
    }

    public static final class SurfacePackage implements Parcelable {
        private SurfaceControl mSurfaceControl;
        private final InputTransferToken mInputTransferToken;

        SurfacePackage(SurfaceControl sc, InputTransferToken token) {
            mSurfaceControl = sc;
            mInputTransferToken = token != null ? token : new InputTransferToken();
        }

        public SurfacePackage(SurfacePackage other) {
            if (other == null) {
                throw new NullPointerException("other");
            }
            if (other.mSurfaceControl == null || !other.mSurfaceControl.isValid()) {
                throw new IllegalStateException("SurfacePackage surfaceControl is released");
            }
            mSurfaceControl = new SurfaceControl(other.mSurfaceControl);
            mInputTransferToken = other.mInputTransferToken;
        }

        public SurfaceControl getSurfaceControl() {
            return mSurfaceControl;
        }

        public void notifyConfigurationChanged(Configuration c) {
            if (c == null) {
                throw new NullPointerException("configuration");
            }
        }

        public void notifyDetachedFromWindow() {
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeTypedObject(mSurfaceControl, flags);
            out.writeTypedObject(mInputTransferToken, flags);
        }

        public void release() {
            if (mSurfaceControl != null) {
                mSurfaceControl.release();
            }
        }

        public InputTransferToken getInputTransferToken() {
            return mInputTransferToken;
        }

        @Override
        public String toString() {
            return "{SurfacePackage: surfaceControl=" + mSurfaceControl + "}";
        }

        public static final Parcelable.Creator<SurfacePackage> CREATOR =
                new Parcelable.Creator<SurfacePackage>() {
                    @Override
                    public SurfacePackage createFromParcel(Parcel in) {
                        SurfaceControl sc = in.readTypedObject(SurfaceControl.CREATOR);
                        InputTransferToken token =
                                in.readTypedObject(InputTransferToken.CREATOR);
                        return new SurfacePackage(sc, token);
                    }

                    @Override
                    public SurfacePackage[] newArray(int size) {
                        return new SurfacePackage[size];
                    }
                };
    }
}
