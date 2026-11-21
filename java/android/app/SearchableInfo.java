// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;

public final class SearchableInfo implements Parcelable {
    private final ComponentName mSearchActivity;
    private String mSuggestAuthority;
    private String mSuggestPackage;
    private String mSuggestPath;
    private String mSuggestSelection;
    private String mSuggestIntentAction;
    private String mSuggestIntentData;
    private int mSuggestThreshold;
    private int mHintId;
    private int mSettingsDescriptionId;
    private int mInputType = InputType.TYPE_CLASS_TEXT;
    private int mImeOptions = EditorInfo.IME_ACTION_SEARCH;
    private boolean mIncludeInGlobalSearch;
    private boolean mQueryAfterZeroResults;
    private boolean mAutoUrlDetect;
    private int mSearchMode;
    private int mVoiceSearchMode;
    private int mVoiceLanguageModeId;
    private int mVoicePromptTextId;
    private int mVoiceLanguageId;
    private int mVoiceMaxResults;

    SearchableInfo(ComponentName searchActivity) {
        mSearchActivity = searchActivity;
    }

    private SearchableInfo(Parcel in) {
        mHintId = in.readInt();
        mSearchActivity = in.readInt() != 0 ? new ComponentName(in.readString(), in.readString()) : null;
        mSearchMode = in.readInt();
        mInputType = in.readInt();
        mImeOptions = in.readInt();
        mIncludeInGlobalSearch = in.readInt() != 0;
        mQueryAfterZeroResults = in.readInt() != 0;
        mAutoUrlDetect = in.readInt() != 0;
        mSettingsDescriptionId = in.readInt();
        mSuggestAuthority = in.readString();
        mSuggestPath = in.readString();
        mSuggestSelection = in.readString();
        mSuggestIntentAction = in.readString();
        mSuggestIntentData = in.readString();
        mSuggestThreshold = in.readInt();
        mVoiceSearchMode = in.readInt();
        mVoiceLanguageModeId = in.readInt();
        mVoicePromptTextId = in.readInt();
        mVoiceLanguageId = in.readInt();
        mVoiceMaxResults = in.readInt();
        mSuggestPackage = in.readString();
    }

    public String getSuggestAuthority() {
        return mSuggestAuthority;
    }

    public String getSuggestPackage() {
        return mSuggestPackage;
    }

    public ComponentName getSearchActivity() {
        return mSearchActivity;
    }

    public boolean shouldRewriteQueryFromData() {
        return (mSearchMode & 0x10) != 0;
    }

    public boolean shouldRewriteQueryFromText() {
        return (mSearchMode & 0x20) != 0;
    }

    public int getSettingsDescriptionId() {
        return mSettingsDescriptionId;
    }

    public String getSuggestPath() {
        return mSuggestPath;
    }

    public String getSuggestSelection() {
        return mSuggestSelection;
    }

    public String getSuggestIntentAction() {
        return mSuggestIntentAction;
    }

    public String getSuggestIntentData() {
        return mSuggestIntentData;
    }

    public int getSuggestThreshold() {
        return mSuggestThreshold;
    }

    public int getHintId() {
        return mHintId;
    }

    public boolean getVoiceSearchEnabled() {
        return (mVoiceSearchMode & 1) != 0;
    }

    public boolean getVoiceSearchLaunchWebSearch() {
        return (mVoiceSearchMode & 2) != 0;
    }

    public boolean getVoiceSearchLaunchRecognizer() {
        return (mVoiceSearchMode & 4) != 0;
    }

    public int getVoiceLanguageModeId() {
        return mVoiceLanguageModeId;
    }

    public int getVoicePromptTextId() {
        return mVoicePromptTextId;
    }

    public int getVoiceLanguageId() {
        return mVoiceLanguageId;
    }

    public int getVoiceMaxResults() {
        return mVoiceMaxResults;
    }

    public int getInputType() {
        return mInputType;
    }

    public int getImeOptions() {
        return mImeOptions;
    }

    public boolean shouldIncludeInGlobalSearch() {
        return mIncludeInGlobalSearch;
    }

    public boolean queryAfterZeroResults() {
        return mQueryAfterZeroResults;
    }

    public boolean autoUrlDetect() {
        return mAutoUrlDetect;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mHintId);
        if (mSearchActivity != null) {
            dest.writeInt(1);
            dest.writeString(mSearchActivity.getPackageName());
            dest.writeString(mSearchActivity.getClassName());
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mSearchMode);
        dest.writeInt(mInputType);
        dest.writeInt(mImeOptions);
        dest.writeInt(mIncludeInGlobalSearch ? 1 : 0);
        dest.writeInt(mQueryAfterZeroResults ? 1 : 0);
        dest.writeInt(mAutoUrlDetect ? 1 : 0);
        dest.writeInt(mSettingsDescriptionId);
        dest.writeString(mSuggestAuthority);
        dest.writeString(mSuggestPath);
        dest.writeString(mSuggestSelection);
        dest.writeString(mSuggestIntentAction);
        dest.writeString(mSuggestIntentData);
        dest.writeInt(mSuggestThreshold);
        dest.writeInt(mVoiceSearchMode);
        dest.writeInt(mVoiceLanguageModeId);
        dest.writeInt(mVoicePromptTextId);
        dest.writeInt(mVoiceLanguageId);
        dest.writeInt(mVoiceMaxResults);
        dest.writeString(mSuggestPackage);
    }

    public static final Parcelable.Creator<SearchableInfo> CREATOR =
            new Parcelable.Creator<SearchableInfo>() {
                @Override
                public SearchableInfo createFromParcel(Parcel in) {
                    return new SearchableInfo(in);
                }

                @Override
                public SearchableInfo[] newArray(int size) {
                    return new SearchableInfo[size];
                }
            };
}
