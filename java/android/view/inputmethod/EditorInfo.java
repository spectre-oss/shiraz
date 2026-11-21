// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.InputType;
import android.util.Printer;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Editor metadata and action constants. */
public class EditorInfo implements InputType, Parcelable {
    public static final int IME_NULL = 0;
    public static final int IME_MASK_ACTION = 0xff;
    public static final int IME_ACTION_UNSPECIFIED = 0;
    public static final int IME_ACTION_NONE = 1;
    public static final int IME_ACTION_GO = 2;
    public static final int IME_ACTION_SEARCH = 3;
    public static final int IME_ACTION_SEND = 4;
    public static final int IME_ACTION_NEXT = 5;
    public static final int IME_ACTION_DONE = 6;
    public static final int IME_ACTION_PREVIOUS = 7;

    public static final int IME_FLAG_NO_PERSONALIZED_LEARNING = 0x1000000;
    public static final int IME_FLAG_NO_FULLSCREEN = 0x2000000;
    public static final int IME_FLAG_NAVIGATE_PREVIOUS = 0x4000000;
    public static final int IME_FLAG_NAVIGATE_NEXT = 0x8000000;
    public static final int IME_FLAG_NO_EXTRACT_UI = 0x10000000;
    public static final int IME_FLAG_NO_ACCESSORY_ACTION = 0x20000000;
    public static final int IME_FLAG_NO_ENTER_ACTION = 0x40000000;
    public static final int IME_FLAG_FORCE_ASCII = 0x80000000;

    public int inputType = TYPE_NULL;
    public int imeOptions = IME_NULL;
    public String privateImeOptions = null;
    public CharSequence actionLabel = null;
    public int actionId = 0;
    public int initialSelStart = -1;
    public int initialSelEnd = -1;
    public int initialCapsMode = 0;
    public CharSequence hintText = null;
    public CharSequence label = null;
    public String packageName = null;
    public int fieldId = 0;
    public String fieldName = null;
    public Bundle extras = null;
    public LocaleList hintLocales = null;
    public String[] contentMimeTypes = null;

    private List<Class<? extends HandwritingGesture>> mSupportedHandwritingGestures =
            Collections.emptyList();
    private Set<Class<? extends PreviewableHandwritingGesture>> mSupportedHandwritingGesturePreviews =
            Collections.emptySet();
    private boolean mIsStylusHandwritingEnabled;
    private SurroundingText mInitialSurroundingText;
    private int mInitialToolType = MotionEvent.TOOL_TYPE_UNKNOWN;

    public EditorInfo() {
    }

    public void setSupportedHandwritingGestures(
            List<Class<? extends HandwritingGesture>> gestures) {
        Objects.requireNonNull(gestures, "gestures");
        mSupportedHandwritingGestures = Collections.unmodifiableList(
                new ArrayList<Class<? extends HandwritingGesture>>(gestures));
    }

    public List<Class<? extends HandwritingGesture>> getSupportedHandwritingGestures() {
        return mSupportedHandwritingGestures;
    }

    public void setSupportedHandwritingGesturePreviews(
            Set<Class<? extends PreviewableHandwritingGesture>> gestures) {
        Objects.requireNonNull(gestures, "gestures");
        mSupportedHandwritingGesturePreviews = Collections.unmodifiableSet(
                new HashSet<Class<? extends PreviewableHandwritingGesture>>(gestures));
    }

    public Set<Class<? extends PreviewableHandwritingGesture>>
            getSupportedHandwritingGesturePreviews() {
        return mSupportedHandwritingGesturePreviews;
    }

    public void setStylusHandwritingEnabled(boolean enabled) {
        mIsStylusHandwritingEnabled = enabled;
    }

    public boolean isStylusHandwritingEnabled() {
        return mIsStylusHandwritingEnabled;
    }

    public void setInitialSurroundingText(CharSequence sourceText) {
        setInitialSurroundingSubText(sourceText, 0);
    }

    public void setInitialSurroundingSubText(CharSequence subText, int subTextStart) {
        Objects.requireNonNull(subText, "subText");
        int selStart = initialSelStart >= 0 ? Math.max(0, initialSelStart - subTextStart) : 0;
        int selEnd = initialSelEnd >= 0 ? Math.max(0, initialSelEnd - subTextStart) : 0;
        selStart = Math.min(selStart, subText.length());
        selEnd = Math.min(selEnd, subText.length());
        mInitialSurroundingText = new SurroundingText(subText, selStart, selEnd, subTextStart);
    }

    public CharSequence getInitialTextBeforeCursor(int length, int flags) {
        if (mInitialSurroundingText == null) return null;
        CharSequence text = mInitialSurroundingText.getText();
        int selStart = Math.min(mInitialSurroundingText.getSelectionStart(), text.length());
        int start = Math.max(0, selStart - Math.max(0, length));
        return text.subSequence(start, selStart);
    }

    public CharSequence getInitialSelectedText(int flags) {
        if (mInitialSurroundingText == null) return null;
        CharSequence text = mInitialSurroundingText.getText();
        int a = Math.min(mInitialSurroundingText.getSelectionStart(), text.length());
        int b = Math.min(mInitialSurroundingText.getSelectionEnd(), text.length());
        if (a == b) return null;
        return text.subSequence(Math.min(a, b), Math.max(a, b));
    }

    public CharSequence getInitialTextAfterCursor(int length, int flags) {
        if (mInitialSurroundingText == null) return null;
        CharSequence text = mInitialSurroundingText.getText();
        int selEnd = Math.min(mInitialSurroundingText.getSelectionEnd(), text.length());
        int end = Math.min(text.length(), selEnd + Math.max(0, length));
        return text.subSequence(selEnd, end);
    }

    public SurroundingText getInitialSurroundingText(
            int beforeLength, int afterLength, int flags) {
        return mInitialSurroundingText;
    }

    public final void makeCompatible(int targetSdkVersion) {
    }

    public int getInitialToolType() {
        return mInitialToolType;
    }

    public void setInitialToolType(int toolType) {
        mInitialToolType = toolType;
    }

    public void dump(Printer pw, String prefix) {
        if (pw == null) return;
        pw.println(prefix + "inputType=0x" + Integer.toHexString(inputType)
                + " imeOptions=0x" + Integer.toHexString(imeOptions)
                + " privateImeOptions=" + privateImeOptions);
        pw.println(prefix + "packageName=" + packageName
                + " fieldId=" + fieldId + " fieldName=" + fieldName);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(inputType);
        dest.writeInt(imeOptions);
        dest.writeString(privateImeOptions);
        dest.writeString(actionLabel != null ? actionLabel.toString() : null);
        dest.writeInt(actionId);
        dest.writeInt(initialSelStart);
        dest.writeInt(initialSelEnd);
        dest.writeInt(initialCapsMode);
        dest.writeString(hintText != null ? hintText.toString() : null);
        dest.writeString(label != null ? label.toString() : null);
        dest.writeString(packageName);
        dest.writeInt(fieldId);
        dest.writeString(fieldName);
        dest.writeBundle(extras);
        dest.writeTypedObject(hintLocales, flags);
        dest.writeStringArray(contentMimeTypes);
        dest.writeBoolean(mIsStylusHandwritingEnabled);
        dest.writeInt(mInitialToolType);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<EditorInfo> CREATOR =
            new Parcelable.Creator<EditorInfo>() {
                @Override
                public EditorInfo createFromParcel(Parcel source) {
                    EditorInfo res = new EditorInfo();
                    res.inputType = source.readInt();
                    res.imeOptions = source.readInt();
                    res.privateImeOptions = source.readString();
                    res.actionLabel = source.readString();
                    res.actionId = source.readInt();
                    res.initialSelStart = source.readInt();
                    res.initialSelEnd = source.readInt();
                    res.initialCapsMode = source.readInt();
                    res.hintText = source.readString();
                    res.label = source.readString();
                    res.packageName = source.readString();
                    res.fieldId = source.readInt();
                    res.fieldName = source.readString();
                    res.extras = source.readBundle();
                    res.hintLocales = source.readTypedObject(LocaleList.CREATOR);
                    res.contentMimeTypes = source.createStringArray();
                    res.mIsStylusHandwritingEnabled = source.readBoolean();
                    res.mInitialToolType = source.readInt();
                    return res;
                }

                @Override
                public EditorInfo[] newArray(int size) {
                    return new EditorInfo[size];
                }
            };
}
