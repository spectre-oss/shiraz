// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WallpaperManager {
    public static final String ACTION_CROP_AND_SET_WALLPAPER =
            "android.service.wallpaper.CROP_AND_SET_WALLPAPER";
    public static final String ACTION_LIVE_WALLPAPER_CHOOSER =
            "android.service.wallpaper.LIVE_WALLPAPER_CHOOSER";
    public static final String ACTION_CHANGE_LIVE_WALLPAPER =
            "android.service.wallpaper.CHANGE_LIVE_WALLPAPER";
    public static final String EXTRA_LIVE_WALLPAPER_COMPONENT =
            "android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT";
    public static final String WALLPAPER_PREVIEW_META_DATA = "android.wallpaper.preview";
    public static final String COMMAND_TAP = "android.wallpaper.tap";
    public static final String COMMAND_SECONDARY_TAP = "android.wallpaper.secondaryTap";
    public static final String COMMAND_DROP = "android.home.drop";

    public static final int FLAG_SYSTEM = 1;
    public static final int FLAG_LOCK = 2;

    public interface OnColorsChangedListener {
        void onColorsChanged(WallpaperColors colors, int which);
    }

    private final Context mContext;
    private final List<OnColorsChangedListener> mColorListeners =
            new ArrayList<OnColorsChangedListener>();
    private int mDesiredWidth = -1;
    private int mDesiredHeight = -1;

    WallpaperManager(Context context) {
        mContext = context;
    }

    public static WallpaperManager getInstance(Context context) {
        Objects.requireNonNull(context, "context");
        try {
            Object svc = context.getSystemService("wallpaper");
            if (svc instanceof WallpaperManager) {
                return (WallpaperManager) svc;
            }
        } catch (RuntimeException ignored) {
        }
        return new WallpaperManager(context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context);
    }

    public Drawable getDrawable() {
        return getDrawable(FLAG_SYSTEM);
    }

    public Drawable getDrawable(int which) {
        return null;
    }

    public Drawable getBuiltInDrawable() {
        return getBuiltInDrawable(0, 0, false, 0f, 0f, FLAG_SYSTEM);
    }

    public Drawable getBuiltInDrawable(int which) {
        return getBuiltInDrawable(0, 0, false, 0f, 0f, which);
    }

    public Drawable getBuiltInDrawable(int outWidth, int outHeight, boolean scaleToFit,
            float horizontalAlignment, float verticalAlignment) {
        return getBuiltInDrawable(outWidth, outHeight, scaleToFit,
                horizontalAlignment, verticalAlignment, FLAG_SYSTEM);
    }

    public Drawable getBuiltInDrawable(int outWidth, int outHeight, boolean scaleToFit,
            float horizontalAlignment, float verticalAlignment, int which) {
        return null;
    }

    public Drawable peekDrawable() {
        return peekDrawable(FLAG_SYSTEM);
    }

    public Drawable peekDrawable(int which) {
        return null;
    }

    public Drawable getFastDrawable() {
        return getFastDrawable(FLAG_SYSTEM);
    }

    public Drawable getFastDrawable(int which) {
        return null;
    }

    public Drawable peekFastDrawable() {
        return peekFastDrawable(FLAG_SYSTEM);
    }

    public Drawable peekFastDrawable(int which) {
        return null;
    }

    public ParcelFileDescriptor getWallpaperFile(int which) {
        return null;
    }

    public synchronized void addOnColorsChangedListener(OnColorsChangedListener listener, Handler handler) {
        Objects.requireNonNull(listener, "listener");
        if (!mColorListeners.contains(listener)) {
            mColorListeners.add(listener);
        }
    }

    public synchronized void removeOnColorsChangedListener(OnColorsChangedListener callback) {
        mColorListeners.remove(callback);
    }

    public WallpaperColors getWallpaperColors(int which) {
        return null;
    }

    public void forgetLoadedWallpaper() {
    }

    public WallpaperInfo getWallpaperInfo() {
        return getWallpaperInfo(FLAG_SYSTEM);
    }

    public WallpaperInfo getWallpaperInfo(int which) {
        return null;
    }

    public int getWallpaperId(int which) {
        return -1;
    }

    public Intent getCropAndSetWallpaperIntent(Uri imageUri) {
        if (imageUri == null) {
            throw new IllegalArgumentException("Image URI must not be null");
        }
        Intent cropAndSetWallpaperIntent = new Intent(ACTION_CROP_AND_SET_WALLPAPER);
        cropAndSetWallpaperIntent.setDataAndType(imageUri, "image/*");
        cropAndSetWallpaperIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return cropAndSetWallpaperIntent;
    }

    public void setResource(int resid) throws IOException {
        setResource(resid, FLAG_SYSTEM | FLAG_LOCK);
    }

    public int setResource(int resid, int which) throws IOException {
        return 0;
    }

    public void setBitmap(Bitmap bitmap) throws IOException {
        setBitmap(bitmap, null, true, FLAG_SYSTEM | FLAG_LOCK);
    }

    public int setBitmap(Bitmap fullImage, Rect visibleCropHint, boolean allowBackup) throws IOException {
        return setBitmap(fullImage, visibleCropHint, allowBackup, FLAG_SYSTEM | FLAG_LOCK);
    }

    public int setBitmap(Bitmap fullImage, Rect visibleCropHint, boolean allowBackup, int which)
            throws IOException {
        return 0;
    }

    public void setStream(InputStream bitmapData) throws IOException {
        setStream(bitmapData, null, true, FLAG_SYSTEM | FLAG_LOCK);
    }

    public int setStream(InputStream bitmapData, Rect visibleCropHint, boolean allowBackup)
            throws IOException {
        return setStream(bitmapData, visibleCropHint, allowBackup, FLAG_SYSTEM | FLAG_LOCK);
    }

    public int setStream(InputStream bitmapData, Rect visibleCropHint, boolean allowBackup, int which)
            throws IOException {
        return 0;
    }

    public boolean hasResourceWallpaper(int resid) {
        return false;
    }

    public int getDesiredMinimumWidth() {
        return mDesiredWidth;
    }

    public int getDesiredMinimumHeight() {
        return mDesiredHeight;
    }

    public void suggestDesiredDimensions(int minimumWidth, int minimumHeight) {
        mDesiredWidth = minimumWidth;
        mDesiredHeight = minimumHeight;
    }

    public void setDisplayPadding(Rect padding) {
    }

    @Deprecated
    public void clearWallpaper() {
        try {
            clear(FLAG_SYSTEM | FLAG_LOCK);
        } catch (IOException ignored) {
        }
    }

    public void setWallpaperOffsets(IBinder windowToken, float xOffset, float yOffset) {
    }

    public void setWallpaperOffsetSteps(float xStep, float yStep) {
    }

    public void sendWallpaperCommand(IBinder windowToken, String action,
            int x, int y, int z, Bundle extras) {
    }

    public boolean isWallpaperSupported() {
        return false;
    }

    public boolean isSetWallpaperAllowed() {
        return false;
    }

    public void clearWallpaperOffsets(IBinder windowToken) {
    }

    public void clear() throws IOException {
        clear(FLAG_SYSTEM | FLAG_LOCK);
    }

    public void clear(int which) throws IOException {
    }
}
