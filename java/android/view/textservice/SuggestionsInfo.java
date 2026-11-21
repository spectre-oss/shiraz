// SPDX-License-Identifier: GPL-3.0-only
package android.view.textservice;

import android.os.Parcel;
import android.os.Parcelable;

/** This class contains a metadata of suggestions from the text service. */
public final class SuggestionsInfo implements Parcelable {
    public static final int RESULT_ATTR_IN_THE_DICTIONARY = 1;
    public static final int RESULT_ATTR_LOOKS_LIKE_TYPO = 2;
    public static final int RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS = 4;
    public static final int RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR = 8;
    public static final int RESULT_ATTR_DONT_SHOW_UI_FOR_SUGGESTIONS = 16;

    private final int mSuggestionsAttributes;
    private final String[] mSuggestions;
    private final boolean mSuggestionsAvailable;
    private int mCookie;
    private int mSequence;

    public SuggestionsInfo(int suggestionsAttributes, String[] suggestions) {
        this(suggestionsAttributes, suggestions, 0, 0);
    }

    public SuggestionsInfo(int suggestionsAttributes, String[] suggestions, int cookie, int sequence) {
        mSuggestionsAttributes = suggestionsAttributes;
        if (suggestions == null) {
            mSuggestions = new String[0];
            mSuggestionsAvailable = false;
        } else {
            mSuggestions = suggestions.clone();
            mSuggestionsAvailable = true;
        }
        mCookie = cookie;
        mSequence = sequence;
    }

    public SuggestionsInfo(Parcel source) {
        mSuggestionsAttributes = source.readInt();
        int len = source.readInt();
        if (len < 0) {
            mSuggestions = new String[0];
            mSuggestionsAvailable = false;
        } else {
            mSuggestions = new String[len];
            for (int i = 0; i < len; i++) {
                mSuggestions[i] = source.readString();
            }
            mSuggestionsAvailable = true;
        }
        mCookie = source.readInt();
        mSequence = source.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSuggestionsAttributes);
        if (!mSuggestionsAvailable) {
            dest.writeInt(-1);
        } else {
            dest.writeInt(mSuggestions.length);
            for (int i = 0; i < mSuggestions.length; i++) {
                dest.writeString(mSuggestions[i]);
            }
        }
        dest.writeInt(mCookie);
        dest.writeInt(mSequence);
    }

    public void setCookieAndSequence(int cookie, int sequence) {
        mCookie = cookie;
        mSequence = sequence;
    }

    public int getCookie() {
        return mCookie;
    }

    public int getSequence() {
        return mSequence;
    }

    public int getSuggestionsAttributes() {
        return mSuggestionsAttributes;
    }

    public int getSuggestionsCount() {
        return mSuggestionsAvailable ? mSuggestions.length : -1;
    }

    public String getSuggestionAt(int i) {
        return mSuggestions[i];
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<SuggestionsInfo> CREATOR =
            new Parcelable.Creator<SuggestionsInfo>() {
                @Override
                public SuggestionsInfo createFromParcel(Parcel source) {
                    return new SuggestionsInfo(source);
                }

                @Override
                public SuggestionsInfo[] newArray(int size) {
                    return new SuggestionsInfo[size];
                }
            };
}
