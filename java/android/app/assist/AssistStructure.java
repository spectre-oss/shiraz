// SPDX-License-Identifier: GPL-3.0-only
package android.app.assist;

import android.content.ComponentName;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewStructure;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import java.util.ArrayList;

/** Assist and autofill view hierarchy snapshot for the foreground activity. */
public class AssistStructure implements Parcelable {
    private final ArrayList<WindowNode> mWindowNodes = new ArrayList<WindowNode>();
    private ComponentName mActivityComponent;
    private boolean mIsHomeActivity;
    private long mAcquisitionStartTime;
    private long mAcquisitionEndTime;

    public AssistStructure() {
    }

    AssistStructure(Parcel in) {
        mActivityComponent = in.readTypedObject(ComponentName.CREATOR);
        mIsHomeActivity = in.readInt() != 0;
        mAcquisitionStartTime = in.readLong();
        mAcquisitionEndTime = in.readLong();
        int winCount = in.readInt();
        for (int i = 0; i < winCount; i++) {
            mWindowNodes.add(new WindowNode(in));
        }
    }

    public long getAcquisitionStartTime() {
        return mAcquisitionStartTime;
    }

    public long getAcquisitionEndTime() {
        return mAcquisitionEndTime;
    }

    public ComponentName getActivityComponent() {
        return mActivityComponent;
    }

    public boolean isHomeActivity() {
        return mIsHomeActivity;
    }

    public int getWindowNodeCount() {
        return mWindowNodes.size();
    }

    public WindowNode getWindowNodeAt(int index) {
        return mWindowNodes.get(index);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeTypedObject(mActivityComponent, flags);
        out.writeInt(mIsHomeActivity ? 1 : 0);
        out.writeLong(mAcquisitionStartTime);
        out.writeLong(mAcquisitionEndTime);
        out.writeInt(mWindowNodes.size());
        for (int i = 0; i < mWindowNodes.size(); i++) {
            mWindowNodes.get(i).writeToParcel(out, flags);
        }
    }

    public static final Parcelable.Creator<AssistStructure> CREATOR =
            new Parcelable.Creator<AssistStructure>() {
                @Override
                public AssistStructure createFromParcel(Parcel in) {
                    return new AssistStructure(in);
                }

                @Override
                public AssistStructure[] newArray(int size) {
                    return new AssistStructure[size];
                }
            };

    public static class WindowNode {
        private final int mLeft;
        private final int mTop;
        private final int mWidth;
        private final int mHeight;
        private final CharSequence mTitle;
        private final int mDisplayId;
        private final ViewNode mRoot;

        public WindowNode(int left, int top, int width, int height, CharSequence title, int displayId, ViewNode root) {
            mLeft = left;
            mTop = top;
            mWidth = width;
            mHeight = height;
            mTitle = title;
            mDisplayId = displayId;
            mRoot = root != null ? root : new ViewNode();
        }

        WindowNode(Parcel in) {
            mLeft = in.readInt();
            mTop = in.readInt();
            mWidth = in.readInt();
            mHeight = in.readInt();
            mTitle = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            mDisplayId = in.readInt();
            mRoot = new ViewNode(in);
        }

        void writeToParcel(Parcel out, int flags) {
            out.writeInt(mLeft);
            out.writeInt(mTop);
            out.writeInt(mWidth);
            out.writeInt(mHeight);
            TextUtils.writeToParcel(mTitle, out, flags);
            out.writeInt(mDisplayId);
            mRoot.writeToParcel(out, flags);
        }

        public int getLeft() { return mLeft; }
        public int getTop() { return mTop; }
        public int getWidth() { return mWidth; }
        public int getHeight() { return mHeight; }
        public CharSequence getTitle() { return mTitle; }
        public int getDisplayId() { return mDisplayId; }
        public ViewNode getRootViewNode() { return mRoot; }
    }

    public static class ViewNode {
        public static final int TEXT_COLOR_UNDEFINED = 1;
        public static final int TEXT_STYLE_BOLD = 1;
        public static final int TEXT_STYLE_ITALIC = 2;
        public static final int TEXT_STYLE_UNDERLINE = 4;
        public static final int TEXT_STYLE_STRIKE_THRU = 8;

        private int mId = View.NO_ID;
        private String mIdPackage;
        private String mIdType;
        private String mIdEntry;
        private AutofillId mAutofillId;
        private int mAutofillType;
        private String[] mAutofillHints;
        private AutofillValue mAutofillValue;
        private CharSequence[] mAutofillOptions;
        private int mInputType;
        private int mLeft, mTop, mScrollX, mScrollY, mWidth, mHeight;
        private Matrix mTransformation;
        private float mElevation;
        private float mAlpha = 1.0f;
        private int mVisibility;
        private boolean mAssistBlocked;
        private boolean mEnabled = true;
        private boolean mClickable, mFocusable, mFocused, mAccessibilityFocused;
        private boolean mCheckable, mChecked, mSelected, mActivated, mOpaque, mLongClickable, mContextClickable;
        private String mClassName;
        private CharSequence mContentDescription;
        private String mWebDomain;
        private String mWebScheme;
        private ViewStructure.HtmlInfo mHtmlInfo;
        private LocaleList mLocaleList;
        private String[] mReceiveContentMimeTypes;
        private CharSequence mText;
        private int mTextSelectionStart = -1;
        private int mTextSelectionEnd = -1;
        private int mTextColor = TEXT_COLOR_UNDEFINED;
        private int mTextBackgroundColor = TEXT_COLOR_UNDEFINED;
        private float mTextSize;
        private int mTextStyle;
        private int[] mTextLineCharOffsets;
        private int[] mTextLineBaselines;
        private String mTextIdEntry;
        private String mHint;
        private String mHintIdEntry;
        private Bundle mExtras;
        private int mMinTextEms = -1;
        private int mMaxTextEms = -1;
        private int mMaxTextLength = -1;
        private int mImportantForAutofill;
        private final ArrayList<ViewNode> mChildren = new ArrayList<ViewNode>();

        public ViewNode() {
        }

        ViewNode(Parcel in) {
            mId = in.readInt();
            mClassName = in.readString();
            mText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            mHint = in.readString();
            mAutofillId = in.readTypedObject(AutofillId.CREATOR);
            mAutofillValue = in.readTypedObject(AutofillValue.CREATOR);
            mExtras = in.readBundle(ViewNode.class.getClassLoader());
            int childCount = in.readInt();
            for (int i = 0; i < childCount; i++) {
                mChildren.add(new ViewNode(in));
            }
        }

        void writeToParcel(Parcel out, int flags) {
            out.writeInt(mId);
            out.writeString(mClassName);
            TextUtils.writeToParcel(mText, out, flags);
            out.writeString(mHint);
            out.writeTypedObject(mAutofillId, flags);
            out.writeTypedObject(mAutofillValue, flags);
            out.writeBundle(mExtras);
            out.writeInt(mChildren.size());
            for (int i = 0; i < mChildren.size(); i++) {
                mChildren.get(i).writeToParcel(out, flags);
            }
        }

        public int getId() { return mId; }
        public String getIdPackage() { return mIdPackage; }
        public String getIdType() { return mIdType; }
        public String getIdEntry() { return mIdEntry; }
        public AutofillId getAutofillId() { return mAutofillId; }
        public int getAutofillType() { return mAutofillType; }
        public String[] getAutofillHints() { return mAutofillHints; }
        public AutofillValue getAutofillValue() { return mAutofillValue; }
        public CharSequence[] getAutofillOptions() { return mAutofillOptions; }
        public int getInputType() { return mInputType; }
        public int getLeft() { return mLeft; }
        public int getTop() { return mTop; }
        public int getScrollX() { return mScrollX; }
        public int getScrollY() { return mScrollY; }
        public int getWidth() { return mWidth; }
        public int getHeight() { return mHeight; }
        public Matrix getTransformation() { return mTransformation; }
        public float getElevation() { return mElevation; }
        public float getAlpha() { return mAlpha; }
        public int getVisibility() { return mVisibility; }
        public boolean isAssistBlocked() { return mAssistBlocked; }
        public boolean isEnabled() { return mEnabled; }
        public boolean isClickable() { return mClickable; }
        public boolean isFocusable() { return mFocusable; }
        public boolean isFocused() { return mFocused; }
        public boolean isAccessibilityFocused() { return mAccessibilityFocused; }
        public boolean isCheckable() { return mCheckable; }
        public boolean isChecked() { return mChecked; }
        public boolean isSelected() { return mSelected; }
        public boolean isActivated() { return mActivated; }
        public boolean isOpaque() { return mOpaque; }
        public boolean isLongClickable() { return mLongClickable; }
        public boolean isContextClickable() { return mContextClickable; }
        public String getClassName() { return mClassName; }
        public CharSequence getContentDescription() { return mContentDescription; }
        public String getWebDomain() { return mWebDomain; }
        public String getWebScheme() { return mWebScheme; }
        public ViewStructure.HtmlInfo getHtmlInfo() { return mHtmlInfo; }
        public LocaleList getLocaleList() { return mLocaleList; }
        public String[] getReceiveContentMimeTypes() { return mReceiveContentMimeTypes; }
        public CharSequence getText() { return mText; }
        public int getTextSelectionStart() { return mTextSelectionStart; }
        public int getTextSelectionEnd() { return mTextSelectionEnd; }
        public int getTextColor() { return mTextColor; }
        public int getTextBackgroundColor() { return mTextBackgroundColor; }
        public float getTextSize() { return mTextSize; }
        public int getTextStyle() { return mTextStyle; }
        public int[] getTextLineCharOffsets() { return mTextLineCharOffsets; }
        public int[] getTextLineBaselines() { return mTextLineBaselines; }
        public String getTextIdEntry() { return mTextIdEntry; }
        public String getHint() { return mHint; }
        public String getHintIdEntry() { return mHintIdEntry; }
        public Bundle getExtras() { return mExtras; }
        public int getChildCount() { return mChildren.size(); }
        public ViewNode getChildAt(int index) { return mChildren.get(index); }
        public int getMinTextEms() { return mMinTextEms; }
        public int getMaxTextEms() { return mMaxTextEms; }
        public int getMaxTextLength() { return mMaxTextLength; }
        public int getImportantForAutofill() { return mImportantForAutofill; }
    }
}
