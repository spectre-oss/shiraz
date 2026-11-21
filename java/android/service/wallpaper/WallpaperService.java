// SPDX-License-Identifier: GPL-3.0-only
package android.service.wallpaper;

import android.app.Service;
import android.app.WallpaperColors;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** A wallpaper service is responsible for showing a live wallpaper behind applications. */
public abstract class WallpaperService extends Service {
    public static final String SERVICE_INTERFACE = "android.service.wallpaper.WallpaperService";
    public static final String SERVICE_META_DATA = "android.service.wallpaper";

    private final IBinder mBinder = new Binder();

    public WallpaperService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return mBinder;
    }

    public abstract Engine onCreateEngine();

    @Override
    protected void dump(FileDescriptor fd, PrintWriter out, String[] args) {
        if (out != null) {
            out.println("WallpaperService: " + getClass().getName());
        }
    }

    public class Engine {
        private SurfaceHolder mSurfaceHolder;
        private int mWallpaperFlags = 1;
        private int mDesiredMinWidth;
        private int mDesiredMinHeight;
        private boolean mVisible;
        private boolean mPreview;
        private boolean mTouchEventsEnabled;
        private boolean mOffsetNotificationsEnabled = true;

        public Engine() {
        }

        public SurfaceHolder getSurfaceHolder() {
            return mSurfaceHolder;
        }

        public int getWallpaperFlags() {
            return mWallpaperFlags;
        }

        public int getDesiredMinimumWidth() {
            return mDesiredMinWidth;
        }

        public int getDesiredMinimumHeight() {
            return mDesiredMinHeight;
        }

        public boolean isVisible() {
            return mVisible;
        }

        public boolean isPreview() {
            return mPreview;
        }

        public void setTouchEventsEnabled(boolean enabled) {
            mTouchEventsEnabled = enabled;
        }

        public void setOffsetNotificationsEnabled(boolean enabled) {
            mOffsetNotificationsEnabled = enabled;
        }

        public void onCreate(SurfaceHolder surfaceHolder) {
            mSurfaceHolder = surfaceHolder;
        }

        public void onDestroy() {
        }

        public void onVisibilityChanged(boolean visible) {
            mVisible = visible;
        }

        public void onApplyWindowInsets(WindowInsets insets) {
        }

        public void onTouchEvent(MotionEvent event) {
        }

        public void onOffsetsChanged(
                float xOffset, float yOffset, float xOffsetStep, float yOffsetStep,
                int xPixelOffset, int yPixelOffset) {
        }

        public Bundle onCommand(
                String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            return null;
        }

        public void onDesiredSizeChanged(int desiredWidth, int desiredHeight) {
            mDesiredMinWidth = desiredWidth;
            mDesiredMinHeight = desiredHeight;
        }

        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }

        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
        }

        public void onSurfaceCreated(SurfaceHolder holder) {
            mSurfaceHolder = holder;
        }

        public void onSurfaceDestroyed(SurfaceHolder holder) {
        }

        public void onWallpaperFlagsChanged(int which) {
            mWallpaperFlags = which;
        }

        public void onZoomChanged(float zoom) {
        }

        public void notifyColorsChanged() {
            onComputeColors();
        }

        public WallpaperColors onComputeColors() {
            return null;
        }

        protected void dump(String prefix, FileDescriptor fd, PrintWriter out, String[] args) {
            if (out != null) {
                out.println(prefix + "mVisible=" + mVisible + " mPreview=" + mPreview
                        + " mTouchEventsEnabled=" + mTouchEventsEnabled
                        + " mOffsetNotificationsEnabled=" + mOffsetNotificationsEnabled);
            }
        }

        public Context getDisplayContext() {
            return WallpaperService.this;
        }
    }
}
