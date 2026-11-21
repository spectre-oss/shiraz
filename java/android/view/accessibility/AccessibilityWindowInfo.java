// SPDX-License-Identifier: GPL-3.0-only
package android.view.accessibility;

import android.graphics.Rect;
import android.graphics.Region;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.Display;
import java.util.ArrayList;
import java.util.Objects;

public final class AccessibilityWindowInfo implements Parcelable {
    public static final int TYPE_APPLICATION = 1;
    public static final int TYPE_INPUT_METHOD = 2;
    public static final int TYPE_SYSTEM = 3;
    public static final int TYPE_ACCESSIBILITY_OVERLAY = 4;
    public static final int TYPE_SPLIT_SCREEN_DIVIDER = 5;
    public static final int TYPE_MAGNIFICATION_OVERLAY = 6;

    private static final int UNDEFINED = -1;

    private int mDisplayId = Display.DEFAULT_DISPLAY;
    private int mType = UNDEFINED;
    private int mLayer = UNDEFINED;
    private int mId = UNDEFINED;
    private int mParentId = UNDEFINED;
    private boolean mInPictureInPicture;
    private boolean mActive;
    private boolean mFocused;
    private boolean mAccessibilityFocused;
    private CharSequence mTitle;
    private long mTransitionTime;
    private LocaleList mLocales = LocaleList.getEmptyLocaleList();
    private final Region mRegionInScreen = new Region();
    private final Rect mBoundsInScreen = new Rect();
    private AccessibilityWindowInfo mParent;
    private final ArrayList<AccessibilityWindowInfo> mChildren =
            new ArrayList<AccessibilityWindowInfo>();
    private AccessibilityNodeInfo mRoot;
    private AccessibilityNodeInfo mAnchor;
    private boolean mRecycled;

    public AccessibilityWindowInfo() {
    }

    public AccessibilityWindowInfo(AccessibilityWindowInfo info) {
        if (info != null) {
            copyFrom(info);
        }
    }

    private void copyFrom(AccessibilityWindowInfo other) {
        mDisplayId = other.mDisplayId;
        mType = other.mType;
        mLayer = other.mLayer;
        mId = other.mId;
        mParentId = other.mParentId;
        mInPictureInPicture = other.mInPictureInPicture;
        mActive = other.mActive;
        mFocused = other.mFocused;
        mAccessibilityFocused = other.mAccessibilityFocused;
        mTitle = other.mTitle == null ? null : other.mTitle.toString();
        mTransitionTime = other.mTransitionTime;
        mLocales = other.mLocales == null ? LocaleList.getEmptyLocaleList() : other.mLocales;
        mRegionInScreen.set(other.mRegionInScreen);
        mBoundsInScreen.set(other.mBoundsInScreen);
        mParent = other.mParent;
        mChildren.clear();
        mChildren.addAll(other.mChildren);
        mRoot = other.mRoot == null ? null : AccessibilityNodeInfo.obtain(other.mRoot);
        mAnchor = other.mAnchor == null ? null : AccessibilityNodeInfo.obtain(other.mAnchor);
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    void setTitle(CharSequence title) {
        mTitle = title == null ? null : title.toString();
    }

    public int getType() {
        return mType;
    }

    void setType(int type) {
        mType = type;
    }

    public int getLayer() {
        return mLayer;
    }

    void setLayer(int layer) {
        mLayer = layer;
    }

    public AccessibilityNodeInfo getRoot() {
        return getRoot(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID);
    }

    public AccessibilityNodeInfo getRoot(int prefetchingStrategy) {
        return mRoot == null ? null : AccessibilityNodeInfo.obtain(mRoot);
    }

    void setRoot(AccessibilityNodeInfo root) {
        mRoot = root;
    }

    public AccessibilityNodeInfo getAnchor() {
        return mAnchor == null ? null : AccessibilityNodeInfo.obtain(mAnchor);
    }

    void setAnchor(AccessibilityNodeInfo anchor) {
        mAnchor = anchor;
    }

    public boolean isInPictureInPictureMode() {
        return mInPictureInPicture;
    }

    void setPictureInPicture(boolean pictureInPicture) {
        mInPictureInPicture = pictureInPicture;
    }

    public AccessibilityWindowInfo getParent() {
        return mParent == null ? null : new AccessibilityWindowInfo(mParent);
    }

    void setParent(AccessibilityWindowInfo parent) {
        mParent = parent;
        mParentId = parent == null ? UNDEFINED : parent.mId;
    }

    public int getId() {
        return mId;
    }

    void setId(int id) {
        mId = id;
    }

    public void getRegionInScreen(Region outRegion) {
        Objects.requireNonNull(outRegion, "outRegion");
        outRegion.set(mRegionInScreen);
    }

    void setRegionInScreen(Region region) {
        if (region == null) {
            mRegionInScreen.setEmpty();
            mBoundsInScreen.setEmpty();
        } else {
            mRegionInScreen.set(region);
            region.getBounds(mBoundsInScreen);
        }
    }

    public void getBoundsInScreen(Rect outBounds) {
        Objects.requireNonNull(outBounds, "outBounds");
        if (!mRegionInScreen.isEmpty()) {
            mRegionInScreen.getBounds(outBounds);
        } else {
            outBounds.set(mBoundsInScreen);
        }
    }

    void setBoundsInScreen(Rect bounds) {
        if (bounds == null) {
            mBoundsInScreen.setEmpty();
            mRegionInScreen.setEmpty();
        } else {
            mBoundsInScreen.set(bounds);
            mRegionInScreen.set(bounds);
        }
    }

    public boolean isActive() {
        return mActive;
    }

    void setActive(boolean active) {
        mActive = active;
    }

    public boolean isFocused() {
        return mFocused;
    }

    void setFocused(boolean focused) {
        mFocused = focused;
    }

    public boolean isAccessibilityFocused() {
        return mAccessibilityFocused;
    }

    void setAccessibilityFocused(boolean focused) {
        mAccessibilityFocused = focused;
    }

    public int getChildCount() {
        return mChildren.size();
    }

    public AccessibilityWindowInfo getChild(int index) {
        return new AccessibilityWindowInfo(mChildren.get(index));
    }

    void addChild(AccessibilityWindowInfo child) {
        if (child != null) {
            mChildren.add(child);
        }
    }

    public int getDisplayId() {
        return mDisplayId;
    }

    void setDisplayId(int displayId) {
        mDisplayId = displayId;
    }

    public long getTransitionTimeMillis() {
        return mTransitionTime;
    }

    void setTransitionTimeMillis(long transitionTime) {
        mTransitionTime = transitionTime;
    }

    public LocaleList getLocales() {
        return mLocales == null ? LocaleList.getEmptyLocaleList() : mLocales;
    }

    void setLocales(LocaleList locales) {
        mLocales = locales == null ? LocaleList.getEmptyLocaleList() : locales;
    }

    public static AccessibilityWindowInfo obtain() {
        return new AccessibilityWindowInfo();
    }

    public static AccessibilityWindowInfo obtain(AccessibilityWindowInfo info) {
        return info == null ? null : new AccessibilityWindowInfo(info);
    }

    public void recycle() {
        if (mRecycled) {
            throw new IllegalStateException("Already recycled");
        }
        mRecycled = true;
        mType = UNDEFINED;
        mLayer = UNDEFINED;
        mId = UNDEFINED;
        mParentId = UNDEFINED;
        mDisplayId = Display.DEFAULT_DISPLAY;
        mInPictureInPicture = false;
        mActive = false;
        mFocused = false;
        mAccessibilityFocused = false;
        mTitle = null;
        mTransitionTime = 0L;
        mLocales = LocaleList.getEmptyLocaleList();
        mRegionInScreen.setEmpty();
        mBoundsInScreen.setEmpty();
        mParent = null;
        mChildren.clear();
        mRoot = null;
        mAnchor = null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        Objects.requireNonNull(parcel, "parcel");
        parcel.writeInt(mDisplayId);
        parcel.writeInt(mType);
        parcel.writeInt(mLayer);
        parcel.writeInt(mId);
        parcel.writeInt(mParentId);
        int boolMask = (mInPictureInPicture ? 1 : 0)
                | (mActive ? 2 : 0)
                | (mFocused ? 4 : 0)
                | (mAccessibilityFocused ? 8 : 0);
        parcel.writeInt(boolMask);
        parcel.writeString(mTitle == null ? null : mTitle.toString());
        parcel.writeLong(mTransitionTime);
        parcel.writeString(mLocales == null ? "" : mLocales.toLanguageTags());
        Rect bounds = new Rect();
        getBoundsInScreen(bounds);
        parcel.writeInt(bounds.left);
        parcel.writeInt(bounds.top);
        parcel.writeInt(bounds.right);
        parcel.writeInt(bounds.bottom);
    }

    private void initFromParcel(Parcel parcel) {
        mDisplayId = parcel.readInt();
        mType = parcel.readInt();
        mLayer = parcel.readInt();
        mId = parcel.readInt();
        mParentId = parcel.readInt();
        int boolMask = parcel.readInt();
        mInPictureInPicture = (boolMask & 1) != 0;
        mActive = (boolMask & 2) != 0;
        mFocused = (boolMask & 4) != 0;
        mAccessibilityFocused = (boolMask & 8) != 0;
        mTitle = parcel.readString();
        mTransitionTime = parcel.readLong();
        String tags = parcel.readString();
        mLocales = LocaleList.forLanguageTags(tags);
        int left = parcel.readInt();
        int top = parcel.readInt();
        int right = parcel.readInt();
        int bottom = parcel.readInt();
        mBoundsInScreen.set(left, top, right, bottom);
        if (left < right && top < bottom) {
            mRegionInScreen.set(left, top, right, bottom);
        } else {
            mRegionInScreen.setEmpty();
        }
    }

    @Override
    public int hashCode() {
        return mId + 31;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        AccessibilityWindowInfo other = (AccessibilityWindowInfo) obj;
        return mId == other.mId;
    }

    @Override
    public String toString() {
        Rect bounds = new Rect();
        getBoundsInScreen(bounds);
        return "AccessibilityWindowInfo[id=" + mId
                + ", displayId=" + mDisplayId
                + ", type=" + mType
                + ", layer=" + mLayer
                + ", title=" + mTitle
                + ", bounds=" + bounds
                + ", focused=" + mFocused
                + ", active=" + mActive + "]";
    }

    public static final Parcelable.Creator<AccessibilityWindowInfo> CREATOR =
            new Parcelable.Creator<AccessibilityWindowInfo>() {
                @Override
                public AccessibilityWindowInfo createFromParcel(Parcel parcel) {
                    AccessibilityWindowInfo info = new AccessibilityWindowInfo();
                    info.initFromParcel(parcel);
                    return info;
                }

                @Override
                public AccessibilityWindowInfo[] newArray(int size) {
                    return new AccessibilityWindowInfo[size];
                }
            };
}
