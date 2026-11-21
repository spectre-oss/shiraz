// SPDX-License-Identifier: GPL-3.0-only
package android.view.textservice;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

/** This class contains a metadata of the input of TextServicesManager. */
public final class TextInfo implements Parcelable {
    private final CharSequence mCharSequence;
    private final int mCookie;
    private final int mSequenceNumber;

    public TextInfo(String text) {
        this(text, 0, getStringLengthOrZero(text), 0, 0);
    }

    public TextInfo(String text, int cookie, int sequenceNumber) {
        this(text, 0, getStringLengthOrZero(text), cookie, sequenceNumber);
    }

    public TextInfo(CharSequence charSequence, int start, int end, int cookie, int sequenceNumber) {
        if (TextUtils.isEmpty(charSequence)) {
            throw new IllegalArgumentException("charSequence is empty");
        }
        mCharSequence = charSequence.subSequence(start, end);
        mCookie = cookie;
        mSequenceNumber = sequenceNumber;
    }

    public TextInfo(Parcel source) {
        mCharSequence = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        mCookie = source.readInt();
        mSequenceNumber = source.readInt();
    }

    private static int getStringLengthOrZero(String text) {
        return text == null ? 0 : text.length();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        TextUtils.writeToParcel(mCharSequence, dest, flags);
        dest.writeInt(mCookie);
        dest.writeInt(mSequenceNumber);
    }

    public String getText() {
        return mCharSequence == null ? null : mCharSequence.toString();
    }

    public CharSequence getCharSequence() {
        return mCharSequence;
    }

    public int getCookie() {
        return mCookie;
    }

    public int getSequence() {
        return mSequenceNumber;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<TextInfo> CREATOR =
            new Parcelable.Creator<TextInfo>() {
                @Override
                public TextInfo createFromParcel(Parcel source) {
                    return new TextInfo(source);
                }

                @Override
                public TextInfo[] newArray(int size) {
                    return new TextInfo[size];
                }
            };
}
