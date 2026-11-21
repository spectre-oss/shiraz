// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Printer;
import java.io.IOException;
import java.util.Objects;
import org.xmlpull.v1.XmlPullParserException;

public final class WallpaperInfo implements Parcelable {
    private final ResolveInfo mService;
    private final String mSettingsActivityName;
    private final int mThumbnailResource;
    private final int mAuthorResource;
    private final int mDescriptionResource;
    private final int mContextUriResource;
    private final int mContextDescriptionResource;
    private final boolean mShowMetadataInPreview;
    private final boolean mSupportsAmbientMode;
    private final String mSettingsSliceUri;
    private final boolean mSupportMultipleDisplays;
    private final boolean mShouldUseDefaultUnfoldTransition;

    public WallpaperInfo(Context context, ResolveInfo service)
            throws IOException, XmlPullParserException {
        Objects.requireNonNull(context, "context");
        mService = Objects.requireNonNull(service, "service");
        if (service.serviceInfo == null) {
            throw new XmlPullParserException("ResolveInfo does not contain a ServiceInfo");
        }
        mSettingsActivityName = null;
        mThumbnailResource = -1;
        mAuthorResource = -1;
        mDescriptionResource = -1;
        mContextUriResource = -1;
        mContextDescriptionResource = -1;
        mShowMetadataInPreview = false;
        mSupportsAmbientMode = false;
        mSettingsSliceUri = null;
        mSupportMultipleDisplays = false;
        mShouldUseDefaultUnfoldTransition = false;
    }

    WallpaperInfo(Parcel source) {
        mSettingsActivityName = source.readString();
        mThumbnailResource = source.readInt();
        mAuthorResource = source.readInt();
        mDescriptionResource = source.readInt();
        mContextUriResource = source.readInt();
        mContextDescriptionResource = source.readInt();
        mShowMetadataInPreview = source.readInt() != 0;
        mSupportsAmbientMode = source.readInt() != 0;
        mSettingsSliceUri = source.readString();
        mSupportMultipleDisplays = source.readInt() != 0;
        mShouldUseDefaultUnfoldTransition = source.readInt() != 0;
        if (source.readInt() != 0) {
            ResolveInfo ri = new ResolveInfo();
            ServiceInfo si = new ServiceInfo();
            si.packageName = source.readString();
            si.name = source.readString();
            ri.serviceInfo = si;
            mService = ri;
        } else {
            mService = null;
        }
    }

    public String getPackageName() {
        return mService != null && mService.serviceInfo != null ? mService.serviceInfo.packageName : null;
    }

    public String getServiceName() {
        return mService != null && mService.serviceInfo != null ? mService.serviceInfo.name : null;
    }

    public ServiceInfo getServiceInfo() {
        return mService != null ? mService.serviceInfo : null;
    }

    public ComponentName getComponent() {
        if (mService == null || mService.serviceInfo == null) {
            return null;
        }
        return new ComponentName(mService.serviceInfo.packageName, mService.serviceInfo.name);
    }

    public CharSequence loadLabel(PackageManager pm) {
        return mService.loadLabel(pm);
    }

    public Drawable loadIcon(PackageManager pm) {
        return mService.loadIcon(pm);
    }

    public Drawable loadThumbnail(PackageManager pm) {
        if (mThumbnailResource < 0 || mService == null || mService.serviceInfo == null) {
            return null;
        }
        try {
            Resources res = pm.getResourcesForApplication(mService.serviceInfo.applicationInfo);
            return res != null ? res.getDrawable(mThumbnailResource, null) : null;
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
            return null;
        }
    }

    public CharSequence loadAuthor(PackageManager pm) throws Resources.NotFoundException {
        if (mAuthorResource <= 0) {
            throw new Resources.NotFoundException();
        }
        CharSequence text = pm.getText(getPackageName(), mAuthorResource, mService.serviceInfo.applicationInfo);
        if (text == null) {
            throw new Resources.NotFoundException();
        }
        return text;
    }

    public CharSequence loadDescription(PackageManager pm) throws Resources.NotFoundException {
        if (mDescriptionResource <= 0) {
            throw new Resources.NotFoundException();
        }
        CharSequence text = pm.getText(getPackageName(), mDescriptionResource, mService.serviceInfo.applicationInfo);
        if (text == null) {
            throw new Resources.NotFoundException();
        }
        return text;
    }

    public Uri loadContextUri(PackageManager pm) throws Resources.NotFoundException {
        if (mContextUriResource <= 0) {
            throw new Resources.NotFoundException();
        }
        CharSequence text = pm.getText(getPackageName(), mContextUriResource, mService.serviceInfo.applicationInfo);
        if (text == null) {
            throw new Resources.NotFoundException();
        }
        return Uri.parse(text.toString());
    }

    public CharSequence loadContextDescription(PackageManager pm) throws Resources.NotFoundException {
        if (mContextDescriptionResource <= 0) {
            throw new Resources.NotFoundException();
        }
        CharSequence text = pm.getText(getPackageName(), mContextDescriptionResource,
                mService.serviceInfo.applicationInfo);
        if (text == null) {
            throw new Resources.NotFoundException();
        }
        return text;
    }

    public boolean getShowMetadataInPreview() {
        return mShowMetadataInPreview;
    }

    public String getSettingsActivity() {
        return mSettingsActivityName;
    }

    public Uri getSettingsSliceUri() {
        return mSettingsSliceUri != null ? Uri.parse(mSettingsSliceUri) : null;
    }

    public boolean supportsMultipleDisplays() {
        return mSupportMultipleDisplays;
    }

    public boolean shouldUseDefaultUnfoldTransition() {
        return mShouldUseDefaultUnfoldTransition;
    }

    public void dump(Printer pw, String prefix) {
        if (pw != null) {
            pw.println((prefix != null ? prefix : "") + "Service: " + getComponent());
        }
    }

    @Override
    public String toString() {
        return "WallpaperInfo{" + getServiceName() + ", settings: " + mSettingsActivityName + "}";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mSettingsActivityName);
        dest.writeInt(mThumbnailResource);
        dest.writeInt(mAuthorResource);
        dest.writeInt(mDescriptionResource);
        dest.writeInt(mContextUriResource);
        dest.writeInt(mContextDescriptionResource);
        dest.writeInt(mShowMetadataInPreview ? 1 : 0);
        dest.writeInt(mSupportsAmbientMode ? 1 : 0);
        dest.writeString(mSettingsSliceUri);
        dest.writeInt(mSupportMultipleDisplays ? 1 : 0);
        dest.writeInt(mShouldUseDefaultUnfoldTransition ? 1 : 0);
        if (mService != null && mService.serviceInfo != null) {
            dest.writeInt(1);
            dest.writeString(mService.serviceInfo.packageName);
            dest.writeString(mService.serviceInfo.name);
        } else {
            dest.writeInt(0);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<WallpaperInfo> CREATOR =
            new Parcelable.Creator<WallpaperInfo>() {
                @Override
                public WallpaperInfo createFromParcel(Parcel source) {
                    return new WallpaperInfo(source);
                }

                @Override
                public WallpaperInfo[] newArray(int size) {
                    return new WallpaperInfo[size];
                }
            };
}
