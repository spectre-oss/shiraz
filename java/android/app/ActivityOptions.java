// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;

public class ActivityOptions {
    public static final String EXTRA_USAGE_TIME_REPORT = "android.activity.usage_time";
    public static final String EXTRA_USAGE_TIME_REPORT_PACKAGES = "android.usage_time_packages";
    public static final int MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED = 0;
    public static final int MODE_BACKGROUND_ACTIVITY_START_ALLOWED = 1;
    public static final int MODE_BACKGROUND_ACTIVITY_START_DENIED = 2;

    private static final String KEY_PACKAGE_NAME = "android:activity.packageName";
    private static final String KEY_ANIM_TYPE = "android:activity.animType";
    private static final String KEY_ANIM_ENTER_RES_ID = "android:activity.animEnterRes";
    private static final String KEY_ANIM_EXIT_RES_ID = "android:activity.animExitRes";
    private static final String KEY_ANIM_BACKGROUND_COLOR = "android:activity.backgroundColor";
    private static final String KEY_ANIM_START_X = "android:activity.animStartX";
    private static final String KEY_ANIM_START_Y = "android:activity.animStartY";
    private static final String KEY_ANIM_WIDTH = "android:activity.animWidth";
    private static final String KEY_ANIM_HEIGHT = "android:activity.animHeight";
    private static final String KEY_LAUNCH_BOUNDS = "android:activity.launchBounds";
    private static final String KEY_LAUNCH_DISPLAY_ID = "android.activity.launchDisplayId";
    private static final String KEY_LOCK_TASK_MODE = "android:activity.lockTaskMode";
    private static final String KEY_SHARE_IDENTITY = "android:activity.shareIdentity";
    private static final String KEY_SPLASH_SCREEN_STYLE = "android.activity.splashScreenStyle";
    private static final String KEY_BG_START_MODE = "android.activity.pendingIntentBgActivityStartMode";
    private static final String KEY_CREATOR_BG_START_MODE = "android.activity.pendingIntentCreatorBgActivityStartMode";
    private static final String KEY_LAUNCH_INTO_PIP = "android.activity.launchIntoPipParams";
    private static final String KEY_VERIFICATION_BUNDLE = "android:activity.verificationBundle";
    private static final String KEY_USAGE_TIME_REPORT = "android:activity.usageTimeReport";

    private String mPackageName;
    private int mAnimationType;
    private int mCustomEnterResId;
    private int mCustomExitResId;
    private int mCustomBackgroundColor;
    private int mStartX;
    private int mStartY;
    private int mWidth;
    private int mHeight;
    private Rect mLaunchBounds;
    private int mLaunchDisplayId = -1;
    private boolean mLockTaskMode;
    private boolean mShareIdentity;
    private int mSplashScreenStyle = -1;
    private int mPendingIntentBgStartMode = MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED;
    private int mPendingIntentCreatorBgStartMode = MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED;
    private PictureInPictureParams mLaunchIntoPipParams;
    private Bundle mAppVerificationBundle;
    private PendingIntent mUsageTimeReport;

    private ActivityOptions() {
    }

    public static ActivityOptions makeCustomAnimation(Context context, int enterResId, int exitResId) {
        return makeCustomAnimation(context, enterResId, exitResId, 0);
    }

    public static ActivityOptions makeCustomAnimation(Context context, int enterResId, int exitResId, int backgroundColor) {
        ActivityOptions opts = new ActivityOptions();
        opts.mPackageName = context != null ? context.getPackageName() : null;
        opts.mAnimationType = 1;
        opts.mCustomEnterResId = enterResId;
        opts.mCustomExitResId = exitResId;
        opts.mCustomBackgroundColor = backgroundColor;
        return opts;
    }

    public static ActivityOptions makeScaleUpAnimation(View source, int startX, int startY, int width, int height) {
        ActivityOptions opts = new ActivityOptions();
        opts.mPackageName = source != null && source.getContext() != null ? source.getContext().getPackageName() : null;
        opts.mAnimationType = 2;
        opts.mStartX = startX;
        opts.mStartY = startY;
        opts.mWidth = width;
        opts.mHeight = height;
        return opts;
    }

    public static ActivityOptions makeClipRevealAnimation(View source, int startX, int startY, int width, int height) {
        ActivityOptions opts = new ActivityOptions();
        opts.mAnimationType = 11;
        opts.mStartX = startX;
        opts.mStartY = startY;
        opts.mWidth = width;
        opts.mHeight = height;
        return opts;
    }

    public static ActivityOptions makeThumbnailScaleUpAnimation(View source, Bitmap thumbnail, int startX, int startY) {
        ActivityOptions opts = new ActivityOptions();
        opts.mPackageName = source != null && source.getContext() != null ? source.getContext().getPackageName() : null;
        opts.mAnimationType = 3;
        opts.mStartX = startX;
        opts.mStartY = startY;
        opts.mWidth = thumbnail != null ? thumbnail.getWidth() : 0;
        opts.mHeight = thumbnail != null ? thumbnail.getHeight() : 0;
        return opts;
    }

    public static ActivityOptions makeSceneTransitionAnimation(Activity activity, View sharedElement, String sharedElementName) {
        ActivityOptions opts = new ActivityOptions();
        opts.mAnimationType = 5;
        return opts;
    }

    @SafeVarargs
    public static ActivityOptions makeSceneTransitionAnimation(Activity activity, Pair<View, String>... sharedElements) {
        ActivityOptions opts = new ActivityOptions();
        opts.mAnimationType = 5;
        return opts;
    }

    public static ActivityOptions makeTaskLaunchBehind() {
        ActivityOptions opts = new ActivityOptions();
        opts.mAnimationType = 7;
        return opts;
    }

    public static ActivityOptions makeBasic() {
        return new ActivityOptions();
    }

    public static ActivityOptions makeLaunchIntoPip(PictureInPictureParams pictureInPictureParams) {
        ActivityOptions opts = new ActivityOptions();
        opts.mLaunchIntoPipParams = pictureInPictureParams != null
                ? new PictureInPictureParams.Builder(pictureInPictureParams).build()
                : null;
        return opts;
    }

    public ActivityOptions setLaunchBounds(Rect screenSpacePixelRect) {
        mLaunchBounds = screenSpacePixelRect != null ? new Rect(screenSpacePixelRect) : null;
        return this;
    }

    public Rect getLaunchBounds() {
        return mLaunchBounds != null ? new Rect(mLaunchBounds) : null;
    }

    public boolean getLockTaskMode() {
        return mLockTaskMode;
    }

    public boolean isShareIdentityEnabled() {
        return mShareIdentity;
    }

    public int getSplashScreenStyle() {
        return mSplashScreenStyle;
    }

    public ActivityOptions setSplashScreenStyle(int style) {
        mSplashScreenStyle = style;
        return this;
    }

    public ActivityOptions setLockTaskEnabled(boolean lockTaskMode) {
        mLockTaskMode = lockTaskMode;
        return this;
    }

    public ActivityOptions setShareIdentityEnabled(boolean shareIdentity) {
        mShareIdentity = shareIdentity;
        return this;
    }

    public int getLaunchDisplayId() {
        return mLaunchDisplayId;
    }

    public ActivityOptions setLaunchDisplayId(int launchDisplayId) {
        mLaunchDisplayId = launchDisplayId;
        return this;
    }

    public ActivityOptions setPendingIntentCreatorBackgroundActivityStartMode(int mode) {
        mPendingIntentCreatorBgStartMode = mode;
        return this;
    }

    public int getPendingIntentCreatorBackgroundActivityStartMode() {
        return mPendingIntentCreatorBgStartMode;
    }

    public void update(ActivityOptions otherOptions) {
        if (otherOptions == null) {
            return;
        }
        if (otherOptions.mPackageName != null) {
            mPackageName = otherOptions.mPackageName;
        }
        mAnimationType = otherOptions.mAnimationType;
        mCustomEnterResId = otherOptions.mCustomEnterResId;
        mCustomExitResId = otherOptions.mCustomExitResId;
        mCustomBackgroundColor = otherOptions.mCustomBackgroundColor;
        mStartX = otherOptions.mStartX;
        mStartY = otherOptions.mStartY;
        mWidth = otherOptions.mWidth;
        mHeight = otherOptions.mHeight;
        mLaunchBounds = otherOptions.mLaunchBounds != null ? new Rect(otherOptions.mLaunchBounds) : null;
        if (otherOptions.mLaunchDisplayId != -1) {
            mLaunchDisplayId = otherOptions.mLaunchDisplayId;
        }
        mLockTaskMode = otherOptions.mLockTaskMode;
        mShareIdentity = otherOptions.mShareIdentity;
        if (otherOptions.mSplashScreenStyle != -1) {
            mSplashScreenStyle = otherOptions.mSplashScreenStyle;
        }
        if (otherOptions.mPendingIntentBgStartMode != MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED) {
            mPendingIntentBgStartMode = otherOptions.mPendingIntentBgStartMode;
        }
        if (otherOptions.mPendingIntentCreatorBgStartMode != MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED) {
            mPendingIntentCreatorBgStartMode = otherOptions.mPendingIntentCreatorBgStartMode;
        }
        if (otherOptions.mLaunchIntoPipParams != null) {
            mLaunchIntoPipParams = otherOptions.mLaunchIntoPipParams;
        }
        if (otherOptions.mAppVerificationBundle != null) {
            mAppVerificationBundle = new Bundle(otherOptions.mAppVerificationBundle);
        }
        if (otherOptions.mUsageTimeReport != null) {
            mUsageTimeReport = otherOptions.mUsageTimeReport;
        }
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        if (mPackageName != null) {
            b.putString(KEY_PACKAGE_NAME, mPackageName);
        }
        if (mAnimationType != 0) {
            b.putInt(KEY_ANIM_TYPE, mAnimationType);
        }
        if (mCustomEnterResId != 0) {
            b.putInt(KEY_ANIM_ENTER_RES_ID, mCustomEnterResId);
        }
        if (mCustomExitResId != 0) {
            b.putInt(KEY_ANIM_EXIT_RES_ID, mCustomExitResId);
        }
        if (mCustomBackgroundColor != 0) {
            b.putInt(KEY_ANIM_BACKGROUND_COLOR, mCustomBackgroundColor);
        }
        if (mStartX != 0 || mStartY != 0 || mWidth != 0 || mHeight != 0) {
            b.putInt(KEY_ANIM_START_X, mStartX);
            b.putInt(KEY_ANIM_START_Y, mStartY);
            b.putInt(KEY_ANIM_WIDTH, mWidth);
            b.putInt(KEY_ANIM_HEIGHT, mHeight);
        }
        if (mLaunchBounds != null) {
            b.putParcelable(KEY_LAUNCH_BOUNDS, new Rect(mLaunchBounds));
        }
        if (mLaunchDisplayId != -1) {
            b.putInt(KEY_LAUNCH_DISPLAY_ID, mLaunchDisplayId);
        }
        if (mLockTaskMode) {
            b.putBoolean(KEY_LOCK_TASK_MODE, true);
        }
        if (mShareIdentity) {
            b.putBoolean(KEY_SHARE_IDENTITY, true);
        }
        if (mSplashScreenStyle != -1) {
            b.putInt(KEY_SPLASH_SCREEN_STYLE, mSplashScreenStyle);
        }
        if (mPendingIntentBgStartMode != MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED) {
            b.putInt(KEY_BG_START_MODE, mPendingIntentBgStartMode);
        }
        if (mPendingIntentCreatorBgStartMode != MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED) {
            b.putInt(KEY_CREATOR_BG_START_MODE, mPendingIntentCreatorBgStartMode);
        }
        if (mLaunchIntoPipParams != null) {
            b.putParcelable(KEY_LAUNCH_INTO_PIP, mLaunchIntoPipParams);
        }
        if (mAppVerificationBundle != null) {
            b.putBundle(KEY_VERIFICATION_BUNDLE, new Bundle(mAppVerificationBundle));
        }
        if (mUsageTimeReport != null) {
            b.putParcelable(KEY_USAGE_TIME_REPORT, mUsageTimeReport);
        }
        return b;
    }

    public void requestUsageTimeReport(PendingIntent receiver) {
        mUsageTimeReport = receiver;
    }

    public ActivityOptions setAppVerificationBundle(Bundle bundle) {
        mAppVerificationBundle = bundle != null ? new Bundle(bundle) : null;
        return this;
    }

    public ActivityOptions setPendingIntentBackgroundActivityStartMode(int state) {
        mPendingIntentBgStartMode = state;
        return this;
    }

    public int getPendingIntentBackgroundActivityStartMode() {
        return mPendingIntentBgStartMode;
    }

    @Deprecated
    public void setPendingIntentBackgroundActivityLaunchAllowed(boolean allowed) {
        mPendingIntentBgStartMode = allowed
                ? MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                : MODE_BACKGROUND_ACTIVITY_START_DENIED;
    }

    @Deprecated
    public boolean isPendingIntentBackgroundActivityLaunchAllowed() {
        return mPendingIntentBgStartMode == MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
    }

    @Override
    public String toString() {
        return "ActivityOptions(" + hashCode() + ", animType=" + mAnimationType
                + ", displayId=" + mLaunchDisplayId + ")";
    }
}
