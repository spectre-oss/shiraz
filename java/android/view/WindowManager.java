// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.graphics.Rect;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.window.InputTransferToken;
import android.window.TrustedPresentationThresholds;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Display and app-owned window management. */
public interface WindowManager extends ViewManager {
    int COMPAT_SMALL_COVER_SCREEN_OPT_IN = 1;
    String PROPERTY_ACTIVITY_EMBEDDING_ALLOW_SYSTEM_OVERRIDE =
            "android.window.PROPERTY_ACTIVITY_EMBEDDING_ALLOW_SYSTEM_OVERRIDE";
    String PROPERTY_ACTIVITY_EMBEDDING_SPLITS_ENABLED =
            "android.window.PROPERTY_ACTIVITY_EMBEDDING_SPLITS_ENABLED";
    String PROPERTY_CAMERA_COMPAT_ALLOW_FORCE_ROTATION =
            "android.window.PROPERTY_CAMERA_COMPAT_ALLOW_FORCE_ROTATION";
    String PROPERTY_CAMERA_COMPAT_ALLOW_REFRESH =
            "android.window.PROPERTY_CAMERA_COMPAT_ALLOW_REFRESH";
    String PROPERTY_CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE =
            "android.window.PROPERTY_CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE";
    String PROPERTY_COMPAT_ALLOW_DISPLAY_ORIENTATION_OVERRIDE =
            "android.window.PROPERTY_COMPAT_ALLOW_DISPLAY_ORIENTATION_OVERRIDE";
    String PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED =
            "android.window.PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED";
    String PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE =
            "android.window.PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE";
    String PROPERTY_COMPAT_ALLOW_ORIENTATION_OVERRIDE =
            "android.window.PROPERTY_COMPAT_ALLOW_ORIENTATION_OVERRIDE";
    String PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES =
            "android.window.PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES";
    String PROPERTY_COMPAT_ALLOW_SANDBOXING_VIEW_BOUNDS_APIS =
            "android.window.PROPERTY_COMPAT_ALLOW_SANDBOXING_VIEW_BOUNDS_APIS";
    String PROPERTY_COMPAT_ALLOW_SMALL_COVER_SCREEN =
            "android.window.PROPERTY_COMPAT_ALLOW_SMALL_COVER_SCREEN";
    String PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE =
            "android.window.PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE";
    String PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE =
            "android.window.PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE";
    String PROPERTY_COMPAT_ENABLE_FAKE_FOCUS =
            "android.window.PROPERTY_COMPAT_ENABLE_FAKE_FOCUS";
    String PROPERTY_COMPAT_IGNORE_REQUESTED_ORIENTATION =
            "android.window.PROPERTY_COMPAT_IGNORE_REQUESTED_ORIENTATION";
    String PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI =
            "android.window.PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI";
    int SCREEN_RECORDING_STATE_NOT_VISIBLE = 0;
    int SCREEN_RECORDING_STATE_VISIBLE = 1;

    @Deprecated
    Display getDefaultDisplay();
    @Override
    void addView(View view, ViewGroup.LayoutParams params);
    @Override
    void updateViewLayout(View view, ViewGroup.LayoutParams params);
    @Override
    void removeView(View view);
    void removeViewImmediate(View view);

    default WindowMetrics getCurrentWindowMetrics() {
        Display d = getDefaultDisplay();
        if (d != null) {
            DisplayMetrics dm = new DisplayMetrics();
            d.getMetrics(dm);
            return new WindowMetrics(new Rect(0, 0, dm.widthPixels, dm.heightPixels),
                    new WindowInsets.Builder().build(), dm.density);
        }
        return new WindowMetrics(new Rect(), new WindowInsets.Builder().build(), 1.0f);
    }

    default WindowMetrics getMaximumWindowMetrics() {
        Display d = getDefaultDisplay();
        if (d != null) {
            DisplayMetrics dm = new DisplayMetrics();
            d.getRealMetrics(dm);
            return new WindowMetrics(new Rect(0, 0, dm.widthPixels, dm.heightPixels),
                    new WindowInsets.Builder().build(), dm.density);
        }
        return new WindowMetrics(new Rect(), new WindowInsets.Builder().build(), 1.0f);
    }

    default boolean isCrossWindowBlurEnabled() {
        return false;
    }

    default void addCrossWindowBlurEnabledListener(Consumer<Boolean> listener) {
    }

    default void addCrossWindowBlurEnabledListener(Executor executor, Consumer<Boolean> listener) {
    }

    default void removeCrossWindowBlurEnabledListener(Consumer<Boolean> listener) {
    }

    default void addProposedRotationListener(Executor executor, IntConsumer listener) {
    }

    default void removeProposedRotationListener(IntConsumer listener) {
    }

    default void registerTrustedPresentationListener(IBinder window,
            TrustedPresentationThresholds thresholds, Executor executor,
            Consumer<Boolean> listener) {
    }

    default void unregisterTrustedPresentationListener(Consumer<Boolean> listener) {
    }

    default InputTransferToken registerBatchedSurfaceControlInputReceiver(
            InputTransferToken hostInputTransferToken, SurfaceControl surfaceControl,
            Choreographer choreographer, SurfaceControlInputReceiver receiver) {
        return null;
    }

    default InputTransferToken registerUnbatchedSurfaceControlInputReceiver(
            InputTransferToken hostInputTransferToken, SurfaceControl surfaceControl,
            Looper looper, SurfaceControlInputReceiver receiver) {
        return null;
    }

    default void unregisterSurfaceControlInputReceiver(SurfaceControl surfaceControl) {
    }

    default boolean transferTouchGesture(InputTransferToken transferFromToken,
            InputTransferToken transferToToken) {
        return false;
    }

    default int addScreenRecordingCallback(Executor executor, Consumer<Integer> callback) {
        return SCREEN_RECORDING_STATE_NOT_VISIBLE;
    }

    default void removeScreenRecordingCallback(Consumer<Integer> callback) {
    }

    class BadTokenException extends RuntimeException {
        public BadTokenException() {
        }

        public BadTokenException(String name) {
            super(name);
        }
    }

    class InvalidDisplayException extends RuntimeException {
        public InvalidDisplayException() {
        }

        public InvalidDisplayException(String name) {
            super(name);
        }
    }

    class LayoutParams extends ViewGroup.LayoutParams implements Parcelable {
        public static final int FIRST_APPLICATION_WINDOW = 1;
        public static final int TYPE_BASE_APPLICATION = 1;
        public static final int TYPE_APPLICATION = 2;
        public static final int TYPE_APPLICATION_STARTING = 3;
        public static final int TYPE_DRAWN_APPLICATION = 4;
        public static final int LAST_APPLICATION_WINDOW = 99;
        public static final int FIRST_SUB_WINDOW = 1000;
        public static final int TYPE_APPLICATION_PANEL = 1000;
        public static final int TYPE_APPLICATION_MEDIA = 1001;
        public static final int TYPE_APPLICATION_SUB_PANEL = 1002;
        public static final int TYPE_APPLICATION_ATTACHED_DIALOG = 1003;
        public static final int LAST_SUB_WINDOW = 1999;
        public static final int FIRST_SYSTEM_WINDOW = 2000;
        public static final int TYPE_STATUS_BAR = 2000;
        public static final int TYPE_SEARCH_BAR = 2001;
        @Deprecated public static final int TYPE_PHONE = 2002;
        @Deprecated public static final int TYPE_SYSTEM_ALERT = 2003;
        @Deprecated public static final int TYPE_TOAST = 2005;
        @Deprecated public static final int TYPE_SYSTEM_OVERLAY = 2006;
        @Deprecated public static final int TYPE_PRIORITY_PHONE = 2007;
        public static final int TYPE_SYSTEM_DIALOG = 2008;
        public static final int TYPE_KEYGUARD_DIALOG = 2009;
        @Deprecated public static final int TYPE_SYSTEM_ERROR = 2010;
        public static final int TYPE_INPUT_METHOD = 2011;
        public static final int TYPE_INPUT_METHOD_DIALOG = 2012;
        public static final int TYPE_WALLPAPER = 2013;
        public static final int TYPE_PRIVATE_PRESENTATION = 2030;
        public static final int TYPE_ACCESSIBILITY_OVERLAY = 2032;
        public static final int TYPE_APPLICATION_OVERLAY = 2038;
        public static final int LAST_SYSTEM_WINDOW = 2999;

        @Deprecated public static final int MEMORY_TYPE_NORMAL = 0;
        @Deprecated public static final int MEMORY_TYPE_HARDWARE = 1;
        @Deprecated public static final int MEMORY_TYPE_GPU = 2;
        @Deprecated public static final int MEMORY_TYPE_PUSH_BUFFERS = 3;

        public static final int FLAG_ALLOW_LOCK_WHILE_SCREEN_ON = 0x00000001;
        public static final int FLAG_DIM_BEHIND = 0x00000002;
        @Deprecated public static final int FLAG_BLUR_BEHIND = 0x00000004;
        public static final int FLAG_NOT_FOCUSABLE = 0x00000008;
        public static final int FLAG_NOT_TOUCHABLE = 0x00000010;
        public static final int FLAG_NOT_TOUCH_MODAL = 0x00000020;
        @Deprecated public static final int FLAG_TOUCHABLE_WHEN_WAKING = 0x00000040;
        public static final int FLAG_KEEP_SCREEN_ON = 0x00000080;
        public static final int FLAG_LAYOUT_IN_SCREEN = 0x00000100;
        public static final int FLAG_LAYOUT_NO_LIMITS = 0x00000200;
        public static final int FLAG_FULLSCREEN = 0x00000400;
        public static final int FLAG_FORCE_NOT_FULLSCREEN = 0x00000800;
        @Deprecated public static final int FLAG_DITHER = 0x00001000;
        public static final int FLAG_SECURE = 0x00002000;
        public static final int FLAG_SCALED = 0x00004000;
        public static final int FLAG_IGNORE_CHEEK_PRESSES = 0x00008000;
        public static final int FLAG_LAYOUT_INSET_DECOR = 0x00010000;
        public static final int FLAG_ALT_FOCUSABLE_IM = 0x00020000;
        public static final int FLAG_WATCH_OUTSIDE_TOUCH = 0x00040000;
        @Deprecated public static final int FLAG_SHOW_WHEN_LOCKED = 0x00080000;
        public static final int FLAG_SHOW_WALLPAPER = 0x00100000;
        @Deprecated public static final int FLAG_TURN_SCREEN_ON = 0x00200000;
        @Deprecated public static final int FLAG_DISMISS_KEYGUARD = 0x00400000;
        public static final int FLAG_SPLIT_TOUCH = 0x00800000;
        public static final int FLAG_HARDWARE_ACCELERATED = 0x01000000;
        @Deprecated public static final int FLAG_LAYOUT_IN_OVERSCAN = 0x02000000;
        @Deprecated public static final int FLAG_TRANSLUCENT_STATUS = 0x04000000;
        @Deprecated public static final int FLAG_TRANSLUCENT_NAVIGATION = 0x08000000;
        public static final int FLAG_LOCAL_FOCUS_MODE = 0x10000000;
        public static final int FLAG_LAYOUT_ATTACHED_IN_DECOR = 0x40000000;
        public static final int FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = 0x80000000;

        public static final int SOFT_INPUT_MASK_STATE = 0x0f;
        public static final int SOFT_INPUT_STATE_UNSPECIFIED = 0;
        public static final int SOFT_INPUT_STATE_UNCHANGED = 1;
        public static final int SOFT_INPUT_STATE_HIDDEN = 2;
        public static final int SOFT_INPUT_STATE_ALWAYS_HIDDEN = 3;
        public static final int SOFT_INPUT_STATE_VISIBLE = 4;
        public static final int SOFT_INPUT_STATE_ALWAYS_VISIBLE = 5;
        public static final int SOFT_INPUT_MASK_ADJUST = 0xf0;
        public static final int SOFT_INPUT_ADJUST_UNSPECIFIED = 0x00;
        @Deprecated public static final int SOFT_INPUT_ADJUST_RESIZE = 0x10;
        public static final int SOFT_INPUT_ADJUST_PAN = 0x20;
        public static final int SOFT_INPUT_ADJUST_NOTHING = 0x30;
        public static final int SOFT_INPUT_IS_FORWARD_NAVIGATION = 0x100;

        public static final float BRIGHTNESS_OVERRIDE_NONE = -1.0f;
        public static final float BRIGHTNESS_OVERRIDE_OFF = 0.0f;
        public static final float BRIGHTNESS_OVERRIDE_FULL = 1.0f;

        public static final int ROTATION_ANIMATION_ROTATE = 0;
        public static final int ROTATION_ANIMATION_CROSSFADE = 1;
        public static final int ROTATION_ANIMATION_JUMPCUT = 2;
        public static final int ROTATION_ANIMATION_SEAMLESS = 3;

        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT = 0;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES = 1;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER = 2;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS = 3;

        public static final int DISPLAY_FLAG_DISABLE_HDR_CONVERSION = 1;

        public static final int LAYOUT_CHANGED = 1 << 0;
        public static final int TYPE_CHANGED = 1 << 1;
        public static final int FLAGS_CHANGED = 1 << 2;
        public static final int FORMAT_CHANGED = 1 << 3;
        public static final int ANIMATION_CHANGED = 1 << 4;
        public static final int DIM_AMOUNT_CHANGED = 1 << 5;
        public static final int TITLE_CHANGED = 1 << 6;
        public static final int ALPHA_CHANGED = 1 << 7;
        public static final int MEMORY_TYPE_CHANGED = 1 << 8;
        public static final int SOFT_INPUT_MODE_CHANGED = 1 << 9;
        public static final int SCREEN_ORIENTATION_CHANGED = 1 << 10;
        public static final int SCREEN_BRIGHTNESS_CHANGED = 1 << 11;
        public static final int ROTATION_ANIMATION_CHANGED = 1 << 12;

        public int x;
        public int y;
        public float horizontalWeight;
        public float verticalWeight;
        public int type = TYPE_APPLICATION;
        public int flags;
        @Deprecated public int memoryType;
        public int softInputMode;
        public int gravity = Gravity.CENTER;
        public float horizontalMargin;
        public float verticalMargin;
        public int format = -1;
        public int windowAnimations;
        public float alpha = 1.0f;
        public float dimAmount = 1.0f;
        public float screenBrightness = BRIGHTNESS_OVERRIDE_NONE;
        public float buttonBrightness = BRIGHTNESS_OVERRIDE_NONE;
        public int rotationAnimation = ROTATION_ANIMATION_ROTATE;
        public IBinder token;
        public String packageName;
        public int screenOrientation = -1;
        @Deprecated public float preferredRefreshRate;
        public int preferredDisplayModeId;
        @Deprecated public int systemUiVisibility;
        public int layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        public boolean preferMinimalPostProcessing;

        private CharSequence mTitle = "";
        private int mFitInsetsTypes = WindowInsets.Type.systemBars();
        private int mFitInsetsSides = WindowInsets.Side.all();
        private boolean mFitInsetsIgnoringVisibility;
        private boolean mWallpaperTouchEventsEnabled = true;
        private boolean mCanPlayMoveAnimation = true;
        private boolean mHdrConversionEnabled = true;
        private int mColorMode;
        private float mDesiredHdrHeadroom;
        private boolean mFrameRateBoostOnTouch = true;
        private boolean mFrameRatePowerSavingsBalanced = true;
        private int mBlurBehindRadius;

        public LayoutParams() {
            super(MATCH_PARENT, MATCH_PARENT);
        }

        public LayoutParams(int _type) {
            super(MATCH_PARENT, MATCH_PARENT);
            type = _type;
        }

        public LayoutParams(int _type, int _flags) {
            super(MATCH_PARENT, MATCH_PARENT);
            type = _type;
            flags = _flags;
        }

        public LayoutParams(int _type, int _flags, int _format) {
            super(MATCH_PARENT, MATCH_PARENT);
            type = _type;
            flags = _flags;
            format = _format;
        }

        public LayoutParams(int w, int h, int _type, int _flags, int _format) {
            super(w, h);
            type = _type;
            flags = _flags;
            format = _format;
        }

        public LayoutParams(int w, int h, int xpos, int ypos, int _type, int _flags, int _format) {
            super(w, h);
            x = xpos;
            y = ypos;
            type = _type;
            flags = _flags;
            format = _format;
        }

        public LayoutParams(LayoutParams source) {
            this();
            copyFrom(source);
        }

        public LayoutParams(Parcel in) {
            super(in.readInt(), in.readInt());
            x = in.readInt();
            y = in.readInt();
            type = in.readInt();
            flags = in.readInt();
            format = in.readInt();
            windowAnimations = in.readInt();
            alpha = in.readFloat();
            dimAmount = in.readFloat();
            screenBrightness = in.readFloat();
            buttonBrightness = in.readFloat();
            gravity = in.readInt();
            softInputMode = in.readInt();
            layoutInDisplayCutoutMode = in.readInt();
            String t = in.readString();
            mTitle = t != null ? t : "";
        }

        public static boolean mayUseInputMethod(int flags) {
            switch (flags & (FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM)) {
                case 0:
                case FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM:
                    return true;
                default:
                    return false;
            }
        }

        public void setFitInsetsTypes(int types) {
            mFitInsetsTypes = types;
        }

        public void setFitInsetsSides(int sides) {
            mFitInsetsSides = sides;
        }

        public void setFitInsetsIgnoringVisibility(boolean ignore) {
            mFitInsetsIgnoringVisibility = ignore;
        }

        public int getFitInsetsTypes() {
            return mFitInsetsTypes;
        }

        public int getFitInsetsSides() {
            return mFitInsetsSides;
        }

        public boolean isFitInsetsIgnoringVisibility() {
            return mFitInsetsIgnoringVisibility;
        }

        public void setWallpaperTouchEventsEnabled(boolean enable) {
            mWallpaperTouchEventsEnabled = enable;
        }

        public boolean areWallpaperTouchEventsEnabled() {
            return mWallpaperTouchEventsEnabled;
        }

        public void setCanPlayMoveAnimation(boolean enable) {
            mCanPlayMoveAnimation = enable;
        }

        public boolean canPlayMoveAnimation() {
            return mCanPlayMoveAnimation;
        }

        public final void setTitle(CharSequence title) {
            mTitle = title == null ? "" : title;
        }

        public final CharSequence getTitle() {
            return mTitle != null ? mTitle : "";
        }

        public boolean isHdrConversionEnabled() {
            return mHdrConversionEnabled;
        }

        public void setHdrConversionEnabled(boolean enabled) {
            mHdrConversionEnabled = enabled;
        }

        public void setColorMode(int colorMode) {
            mColorMode = colorMode;
        }

        public int getColorMode() {
            return mColorMode;
        }

        public void setDesiredHdrHeadroom(float desiredHeadroom) {
            mDesiredHdrHeadroom = desiredHeadroom;
        }

        public float getDesiredHdrHeadroom() {
            return mDesiredHdrHeadroom;
        }

        public void setFrameRateBoostOnTouchEnabled(boolean enabled) {
            mFrameRateBoostOnTouch = enabled;
        }

        public boolean getFrameRateBoostOnTouchEnabled() {
            return mFrameRateBoostOnTouch;
        }

        public void setFrameRatePowerSavingsBalanced(boolean enabled) {
            mFrameRatePowerSavingsBalanced = enabled;
        }

        public boolean isFrameRatePowerSavingsBalanced() {
            return mFrameRatePowerSavingsBalanced;
        }

        public void setBlurBehindRadius(int blurBehindRadius) {
            mBlurBehindRadius = blurBehindRadius;
        }

        public int getBlurBehindRadius() {
            return mBlurBehindRadius;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int parcelableFlags) {
            out.writeInt(width);
            out.writeInt(height);
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(type);
            out.writeInt(flags);
            out.writeInt(format);
            out.writeInt(windowAnimations);
            out.writeFloat(alpha);
            out.writeFloat(dimAmount);
            out.writeFloat(screenBrightness);
            out.writeFloat(buttonBrightness);
            out.writeInt(gravity);
            out.writeInt(softInputMode);
            out.writeInt(layoutInDisplayCutoutMode);
            out.writeString(mTitle != null ? mTitle.toString() : "");
        }

        public static final Parcelable.Creator<LayoutParams> CREATOR =
                new Parcelable.Creator<LayoutParams>() {
                    @Override
                    public LayoutParams createFromParcel(Parcel in) {
                        return new LayoutParams(in);
                    }

                    @Override
                    public LayoutParams[] newArray(int size) {
                        return new LayoutParams[size];
                    }
                };

        public final int copyFrom(LayoutParams o) {
            if (o == null) throw new NullPointerException("attributes");
            int changes = 0;
            if (width != o.width || height != o.height || x != o.x || y != o.y
                    || horizontalWeight != o.horizontalWeight || verticalWeight != o.verticalWeight
                    || horizontalMargin != o.horizontalMargin || verticalMargin != o.verticalMargin
                    || gravity != o.gravity || layoutInDisplayCutoutMode != o.layoutInDisplayCutoutMode) {
                width = o.width;
                height = o.height;
                x = o.x;
                y = o.y;
                horizontalWeight = o.horizontalWeight;
                verticalWeight = o.verticalWeight;
                horizontalMargin = o.horizontalMargin;
                verticalMargin = o.verticalMargin;
                gravity = o.gravity;
                layoutInDisplayCutoutMode = o.layoutInDisplayCutoutMode;
                changes |= LAYOUT_CHANGED;
            }
            if (type != o.type) {
                type = o.type;
                changes |= TYPE_CHANGED;
            }
            if (flags != o.flags) {
                flags = o.flags;
                changes |= FLAGS_CHANGED;
            }
            if (format != o.format) {
                format = o.format;
                changes |= FORMAT_CHANGED;
            }
            if (windowAnimations != o.windowAnimations) {
                windowAnimations = o.windowAnimations;
                changes |= ANIMATION_CHANGED;
            }
            if (dimAmount != o.dimAmount) {
                dimAmount = o.dimAmount;
                changes |= DIM_AMOUNT_CHANGED;
            }
            if (alpha != o.alpha) {
                alpha = o.alpha;
                changes |= ALPHA_CHANGED;
            }
            if (!Objects.equals(mTitle, o.mTitle)) {
                mTitle = o.mTitle;
                changes |= TITLE_CHANGED;
            }
            if (softInputMode != o.softInputMode) {
                softInputMode = o.softInputMode;
                changes |= SOFT_INPUT_MODE_CHANGED;
            }
            if (screenOrientation != o.screenOrientation) {
                screenOrientation = o.screenOrientation;
                changes |= SCREEN_ORIENTATION_CHANGED;
            }
            if (screenBrightness != o.screenBrightness || buttonBrightness != o.buttonBrightness) {
                screenBrightness = o.screenBrightness;
                buttonBrightness = o.buttonBrightness;
                changes |= SCREEN_BRIGHTNESS_CHANGED;
            }
            if (rotationAnimation != o.rotationAnimation) {
                rotationAnimation = o.rotationAnimation;
                changes |= ROTATION_ANIMATION_CHANGED;
            }
            if (memoryType != o.memoryType) {
                memoryType = o.memoryType;
                changes |= MEMORY_TYPE_CHANGED;
            }
            if (preferredRefreshRate != o.preferredRefreshRate) {
                preferredRefreshRate = o.preferredRefreshRate;
                changes |= 1 << 21;
            }
            if (preferredDisplayModeId != o.preferredDisplayModeId) {
                preferredDisplayModeId = o.preferredDisplayModeId;
                changes |= 1 << 23;
            }
            if (systemUiVisibility != o.systemUiVisibility) {
                systemUiVisibility = o.systemUiVisibility;
                changes |= 1 << 13;
            }
            if (token != o.token
                    || !Objects.equals(packageName, o.packageName)
                    || preferMinimalPostProcessing != o.preferMinimalPostProcessing
                    || mFitInsetsTypes != o.mFitInsetsTypes
                    || mFitInsetsSides != o.mFitInsetsSides
                    || mFitInsetsIgnoringVisibility != o.mFitInsetsIgnoringVisibility
                    || mWallpaperTouchEventsEnabled != o.mWallpaperTouchEventsEnabled
                    || mCanPlayMoveAnimation != o.mCanPlayMoveAnimation
                    || mHdrConversionEnabled != o.mHdrConversionEnabled
                    || mColorMode != o.mColorMode
                    || mDesiredHdrHeadroom != o.mDesiredHdrHeadroom
                    || mFrameRateBoostOnTouch != o.mFrameRateBoostOnTouch
                    || mFrameRatePowerSavingsBalanced != o.mFrameRatePowerSavingsBalanced
                    || mBlurBehindRadius != o.mBlurBehindRadius) {
                token = o.token;
                packageName = o.packageName;
                preferMinimalPostProcessing = o.preferMinimalPostProcessing;
                mFitInsetsTypes = o.mFitInsetsTypes;
                mFitInsetsSides = o.mFitInsetsSides;
                mFitInsetsIgnoringVisibility = o.mFitInsetsIgnoringVisibility;
                mWallpaperTouchEventsEnabled = o.mWallpaperTouchEventsEnabled;
                mCanPlayMoveAnimation = o.mCanPlayMoveAnimation;
                mHdrConversionEnabled = o.mHdrConversionEnabled;
                mColorMode = o.mColorMode;
                mDesiredHdrHeadroom = o.mDesiredHdrHeadroom;
                mFrameRateBoostOnTouch = o.mFrameRateBoostOnTouch;
                mFrameRatePowerSavingsBalanced = o.mFrameRatePowerSavingsBalanced;
                mBlurBehindRadius = o.mBlurBehindRadius;
                changes |= LAYOUT_CHANGED;
            }
            return changes;
        }

        public String debug(String output) {
            return output + "Contents of " + this + ":";
        }

        @Override
        public String toString() {
            return "WM.LayoutParams{(" + x + "," + y + ")(" + width + "x" + height + ")"
                    + " ty=" + type + " fl=#" + Integer.toHexString(flags)
                    + " fmt=" + format + " title=" + mTitle + "}";
        }
    }
}
