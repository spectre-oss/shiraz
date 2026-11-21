// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/**
 * Information about a Keyboard Shortcut.
 */
public final class KeyboardShortcutInfo implements Parcelable {
    private final CharSequence mLabel;
    private final char mBaseCharacter;
    private final int mKeycode;
    private final int mModifiers;

    public KeyboardShortcutInfo(CharSequence label, int keycode, int modifiers) {
        if (keycode < KeyEvent.KEYCODE_UNKNOWN) {
            throw new IllegalArgumentException("Invalid keycode: " + keycode);
        }
        mLabel = label;
        mKeycode = keycode;
        mBaseCharacter = Character.MIN_VALUE;
        mModifiers = modifiers;
    }

    public KeyboardShortcutInfo(CharSequence label, char baseCharacter, int modifiers) {
        if (baseCharacter == Character.MIN_VALUE) {
            throw new IllegalArgumentException("Invalid baseCharacter");
        }
        mLabel = label;
        mBaseCharacter = baseCharacter;
        mKeycode = KeyEvent.KEYCODE_UNKNOWN;
        mModifiers = modifiers;
    }

    KeyboardShortcutInfo(Parcel source) {
        mLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        mBaseCharacter = (char) source.readInt();
        mKeycode = source.readInt();
        mModifiers = source.readInt();
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public int getKeycode() {
        return mKeycode;
    }

    public char getBaseCharacter() {
        return mBaseCharacter;
    }

    public int getModifiers() {
        return mModifiers;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        TextUtils.writeToParcel(mLabel, dest, flags);
        dest.writeInt((int) mBaseCharacter);
        dest.writeInt(mKeycode);
        dest.writeInt(mModifiers);
    }

    public static final Parcelable.Creator<KeyboardShortcutInfo> CREATOR =
            new Parcelable.Creator<KeyboardShortcutInfo>() {
                @Override
                public KeyboardShortcutInfo createFromParcel(Parcel source) {
                    return new KeyboardShortcutInfo(source);
                }

                @Override
                public KeyboardShortcutInfo[] newArray(int size) {
                    return new KeyboardShortcutInfo[size];
                }
            };
}
