// SPDX-License-Identifier: GPL-3.0-only
package android.view.textservice;

import android.os.Parcel;
import android.os.Parcelable;

/** Sentence-level spell-check suggestions with per-span offsets and lengths. */
public final class SentenceSuggestionsInfo implements Parcelable {
    private final SuggestionsInfo[] mSuggestionsInfos;
    private final int[] mOffsets;
    private final int[] mLengths;

    public SentenceSuggestionsInfo(SuggestionsInfo[] suggestionsInfos, int[] offsets, int[] lengths) {
        if (suggestionsInfos == null || offsets == null || lengths == null) {
            throw new NullPointerException();
        }
        if (suggestionsInfos.length != offsets.length || offsets.length != lengths.length) {
            throw new IllegalArgumentException();
        }
        mSuggestionsInfos = suggestionsInfos.clone();
        mOffsets = offsets.clone();
        mLengths = lengths.clone();
    }

    public SentenceSuggestionsInfo(Parcel source) {
        mSuggestionsInfos = source.createTypedArray(SuggestionsInfo.CREATOR);
        mOffsets = source.createIntArray();
        mLengths = source.createIntArray();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedArray(mSuggestionsInfos, flags);
        dest.writeIntArray(mOffsets);
        dest.writeIntArray(mLengths);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public int getSuggestionsCount() {
        return mSuggestionsInfos.length;
    }

    public SuggestionsInfo getSuggestionsInfoAt(int i) {
        if (i >= 0 && i < mSuggestionsInfos.length) {
            return mSuggestionsInfos[i];
        }
        return null;
    }

    public int getOffsetAt(int i) {
        if (i >= 0 && i < mOffsets.length) {
            return mOffsets[i];
        }
        return -1;
    }

    public int getLengthAt(int i) {
        if (i >= 0 && i < mLengths.length) {
            return mLengths[i];
        }
        return -1;
    }

    public static final Parcelable.Creator<SentenceSuggestionsInfo> CREATOR =
            new Parcelable.Creator<SentenceSuggestionsInfo>() {
                @Override
                public SentenceSuggestionsInfo createFromParcel(Parcel source) {
                    return new SentenceSuggestionsInfo(source);
                }

                @Override
                public SentenceSuggestionsInfo[] newArray(int size) {
                    return new SentenceSuggestionsInfo[size];
                }
            };
}
