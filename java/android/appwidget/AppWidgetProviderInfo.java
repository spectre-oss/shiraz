// SPDX-License-Identifier: GPL-3.0-only
package android.appwidget;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

public class AppWidgetProviderInfo implements Parcelable, Cloneable {
    public static final int RESIZE_NONE = 0;
    public static final int RESIZE_HORIZONTAL = 1;
    public static final int RESIZE_VERTICAL = 2;
    public static final int RESIZE_BOTH = 3;

    public static final int WIDGET_CATEGORY_HOME_SCREEN = 1;
    public static final int WIDGET_CATEGORY_KEYGUARD = 2;
    public static final int WIDGET_CATEGORY_SEARCHBOX = 4;

    public static final int WIDGET_FEATURE_RECONFIGURABLE = 1;
    public static final int WIDGET_FEATURE_HIDE_FROM_PICKER = 2;
    public static final int WIDGET_FEATURE_CONFIGURATION_OPTIONAL = 4;

    public ComponentName provider;
    public int minWidth;
    public int minHeight;
    public int minResizeWidth;
    public int minResizeHeight;
    public int maxResizeWidth;
    public int maxResizeHeight;
    public int targetCellWidth;
    public int targetCellHeight;
    public int updatePeriodMillis;
    public int initialLayout;
    public int initialKeyguardLayout;
    public ComponentName configure;
    @Deprecated
    public String label;
    public int icon;
    public int autoAdvanceViewId;
    public int previewImage;
    public int previewLayout;
    public int resizeMode;
    public int widgetCategory;
    public int widgetFeatures;
    public int descriptionRes;
    public int generatedPreviewCategories;

    ActivityInfo providerInfo;

    public AppWidgetProviderInfo() {
    }

    @SuppressWarnings("deprecation")
    public AppWidgetProviderInfo(Parcel in) {
        provider = in.readTypedObject(ComponentName.CREATOR);
        minWidth = in.readInt();
        minHeight = in.readInt();
        minResizeWidth = in.readInt();
        minResizeHeight = in.readInt();
        maxResizeWidth = in.readInt();
        maxResizeHeight = in.readInt();
        targetCellWidth = in.readInt();
        targetCellHeight = in.readInt();
        updatePeriodMillis = in.readInt();
        initialLayout = in.readInt();
        initialKeyguardLayout = in.readInt();
        configure = in.readTypedObject(ComponentName.CREATOR);
        label = in.readString();
        icon = in.readInt();
        previewImage = in.readInt();
        previewLayout = in.readInt();
        autoAdvanceViewId = in.readInt();
        resizeMode = in.readInt();
        widgetCategory = in.readInt();
        widgetFeatures = in.readInt();
        descriptionRes = in.readInt();
        generatedPreviewCategories = in.readInt();
        providerInfo = in.readParcelable(ActivityInfo.class.getClassLoader());
    }

    @SuppressWarnings("deprecation")
    public final String loadLabel(PackageManager packageManager) {
        if (providerInfo != null && packageManager != null) {
            CharSequence cs = providerInfo.loadLabel(packageManager);
            if (cs != null) {
                return cs.toString().trim();
            }
        }
        return label;
    }

    public final Drawable loadIcon(Context context, int density) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        int resId = icon != 0 ? icon : (providerInfo != null ? providerInfo.icon : 0);
        return loadDrawable(context, resId);
    }

    public final Drawable loadPreviewImage(Context context, int density) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        return loadDrawable(context, previewImage);
    }

    public final CharSequence loadDescription(Context context) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        if (descriptionRes != 0 && provider != null) {
            try {
                Resources res = context.getPackageManager()
                        .getResourcesForApplication(provider.getPackageName());
                if (res != null) {
                    return res.getText(descriptionRes);
                }
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            }
        }
        return null;
    }

    private Drawable loadDrawable(Context context, int resId) {
        if (resId == 0 || provider == null) {
            return null;
        }
        try {
            Resources res = context.getPackageManager()
                    .getResourcesForApplication(provider.getPackageName());
            if (res != null) {
                return res.getDrawable(resId);
            }
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
        }
        return null;
    }

    public final UserHandle getProfile() {
        return UserHandle.of(UserHandle.myUserId());
    }

    public ActivityInfo getActivityInfo() {
        return providerInfo;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void writeToParcel(Parcel out, int flags) {
        out.writeTypedObject(provider, flags);
        out.writeInt(minWidth);
        out.writeInt(minHeight);
        out.writeInt(minResizeWidth);
        out.writeInt(minResizeHeight);
        out.writeInt(maxResizeWidth);
        out.writeInt(maxResizeHeight);
        out.writeInt(targetCellWidth);
        out.writeInt(targetCellHeight);
        out.writeInt(updatePeriodMillis);
        out.writeInt(initialLayout);
        out.writeInt(initialKeyguardLayout);
        out.writeTypedObject(configure, flags);
        out.writeString(label);
        out.writeInt(icon);
        out.writeInt(previewImage);
        out.writeInt(previewLayout);
        out.writeInt(autoAdvanceViewId);
        out.writeInt(resizeMode);
        out.writeInt(widgetCategory);
        out.writeInt(widgetFeatures);
        out.writeInt(descriptionRes);
        out.writeInt(generatedPreviewCategories);
        out.writeParcelable(providerInfo, flags);
    }

    @Override
    @SuppressWarnings("deprecation")
    public AppWidgetProviderInfo clone() {
        AppWidgetProviderInfo that = new AppWidgetProviderInfo();
        that.provider = provider == null ? null : provider.clone();
        that.minWidth = minWidth;
        that.minHeight = minHeight;
        that.minResizeWidth = minResizeWidth;
        that.minResizeHeight = minResizeHeight;
        that.maxResizeWidth = maxResizeWidth;
        that.maxResizeHeight = maxResizeHeight;
        that.targetCellWidth = targetCellWidth;
        that.targetCellHeight = targetCellHeight;
        that.updatePeriodMillis = updatePeriodMillis;
        that.initialLayout = initialLayout;
        that.initialKeyguardLayout = initialKeyguardLayout;
        that.configure = configure == null ? null : configure.clone();
        that.label = label;
        that.icon = icon;
        that.previewImage = previewImage;
        that.previewLayout = previewLayout;
        that.autoAdvanceViewId = autoAdvanceViewId;
        that.resizeMode = resizeMode;
        that.widgetCategory = widgetCategory;
        that.widgetFeatures = widgetFeatures;
        that.descriptionRes = descriptionRes;
        that.generatedPreviewCategories = generatedPreviewCategories;
        that.providerInfo = providerInfo;
        return that;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "AppWidgetProviderInfo(" + getProfile() + '/' + provider + ')';
    }

    public static final Parcelable.Creator<AppWidgetProviderInfo> CREATOR =
            new Parcelable.Creator<AppWidgetProviderInfo>() {
                @Override
                public AppWidgetProviderInfo createFromParcel(Parcel parcel) {
                    return new AppWidgetProviderInfo(parcel);
                }

                @Override
                public AppWidgetProviderInfo[] newArray(int size) {
                    return new AppWidgetProviderInfo[size];
                }
            };
}
