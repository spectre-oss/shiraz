// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Objects;

/** Application restriction schema and current value entry for managed configurations. */
public class RestrictionEntry implements Parcelable {
    public static final int TYPE_NULL = 0;
    public static final int TYPE_BOOLEAN = 1;
    public static final int TYPE_CHOICE = 2;
    public static final int TYPE_MULTI_SELECT = 4;
    public static final int TYPE_INTEGER = 5;
    public static final int TYPE_STRING = 6;
    public static final int TYPE_BUNDLE = 7;
    public static final int TYPE_BUNDLE_ARRAY = 8;

    private int mType;
    private final String mKey;
    private String mTitle;
    private String mDescription;
    private String[] mChoiceEntries;
    private String[] mChoiceValues;
    private String mCurrentValue;
    private String[] mCurrentValues;
    private RestrictionEntry[] mRestrictions;

    public RestrictionEntry(int type, String key) {
        mType = type;
        mKey = key;
    }

    public RestrictionEntry(String key, String selectedString) {
        mKey = key;
        mType = TYPE_CHOICE;
        mCurrentValue = selectedString;
    }

    public RestrictionEntry(String key, boolean selectedState) {
        mKey = key;
        mType = TYPE_BOOLEAN;
        setSelectedState(selectedState);
    }

    public RestrictionEntry(String key, String[] selectedStrings) {
        mKey = key;
        mType = TYPE_MULTI_SELECT;
        mCurrentValues = selectedStrings;
    }

    public RestrictionEntry(String key, int selectedInt) {
        mKey = key;
        mType = TYPE_INTEGER;
        setIntValue(selectedInt);
    }

    private RestrictionEntry(String key, RestrictionEntry[] restrictionEntries, boolean isBundleArray) {
        mKey = key;
        if (isBundleArray) {
            mType = TYPE_BUNDLE_ARRAY;
            if (restrictionEntries != null) {
                for (RestrictionEntry restriction : restrictionEntries) {
                    if (restriction.getType() != TYPE_BUNDLE) {
                        throw new IllegalArgumentException(
                                "bundle_array restriction can only have nested restriction entries of type bundle");
                    }
                }
            }
        } else {
            mType = TYPE_BUNDLE;
        }
        setRestrictions(restrictionEntries);
    }

    public static RestrictionEntry createBundleEntry(String key, RestrictionEntry[] restrictionEntries) {
        return new RestrictionEntry(key, restrictionEntries, false);
    }

    public static RestrictionEntry createBundleArrayEntry(String key, RestrictionEntry[] restrictionEntries) {
        return new RestrictionEntry(key, restrictionEntries, true);
    }

    public void setType(int type) {
        mType = type;
    }

    public int getType() {
        return mType;
    }

    public String getSelectedString() {
        return mCurrentValue;
    }

    public String[] getAllSelectedStrings() {
        return mCurrentValues;
    }

    public boolean getSelectedState() {
        return Boolean.parseBoolean(mCurrentValue);
    }

    public int getIntValue() {
        return Integer.parseInt(mCurrentValue);
    }

    public void setIntValue(int value) {
        mCurrentValue = Integer.toString(value);
    }

    public void setSelectedString(String selectedString) {
        mCurrentValue = selectedString;
    }

    public void setSelectedState(boolean state) {
        mCurrentValue = Boolean.toString(state);
    }

    public void setAllSelectedStrings(String[] allSelectedStrings) {
        mCurrentValues = allSelectedStrings;
    }

    public void setChoiceValues(String[] choiceValues) {
        mChoiceValues = choiceValues;
    }

    public void setChoiceValues(Context context, int stringArrayResId) {
        mChoiceValues = context.getResources().getStringArray(stringArrayResId);
    }

    public RestrictionEntry[] getRestrictions() {
        return mRestrictions;
    }

    public void setRestrictions(RestrictionEntry[] restrictions) {
        mRestrictions = restrictions;
    }

    public String[] getChoiceValues() {
        return mChoiceValues;
    }

    public void setChoiceEntries(String[] choiceEntries) {
        mChoiceEntries = choiceEntries;
    }

    public void setChoiceEntries(Context context, int stringArrayResId) {
        mChoiceEntries = context.getResources().getStringArray(stringArrayResId);
    }

    public String[] getChoiceEntries() {
        return mChoiceEntries;
    }

    public String getDescription() {
        return mDescription;
    }

    public void setDescription(String description) {
        mDescription = description;
    }

    public String getKey() {
        return mKey;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof RestrictionEntry)) return false;
        final RestrictionEntry other = (RestrictionEntry) o;
        if (mType != other.mType || !Objects.equals(mKey, other.mKey)) {
            return false;
        }
        if (mCurrentValues == null && other.mCurrentValues == null
                && mRestrictions == null && other.mRestrictions == null
                && Objects.equals(mCurrentValue, other.mCurrentValue)) {
            return true;
        }
        if (mCurrentValue == null && other.mCurrentValue == null
                && mRestrictions == null && other.mRestrictions == null
                && Arrays.equals(mCurrentValues, other.mCurrentValues)) {
            return true;
        }
        if (mCurrentValue == null && other.mCurrentValue == null
                && mCurrentValues == null && other.mCurrentValues == null
                && Arrays.equals(mRestrictions, other.mRestrictions)) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + Objects.hashCode(mKey);
        if (mCurrentValue != null) {
            result = 31 * result + mCurrentValue.hashCode();
        } else if (mCurrentValues != null) {
            for (String value : mCurrentValues) {
                if (value != null) {
                    result = 31 * result + value.hashCode();
                }
            }
        } else if (mRestrictions != null) {
            result = 31 * result + Arrays.hashCode(mRestrictions);
        }
        return result;
    }

    public RestrictionEntry(Parcel in) {
        mType = in.readInt();
        mKey = in.readString();
        mTitle = in.readString();
        mDescription = in.readString();
        mChoiceEntries = readStringArray(in);
        mChoiceValues = readStringArray(in);
        mCurrentValue = in.readString();
        mCurrentValues = readStringArray(in);
        mRestrictions = in.createTypedArray(CREATOR);
    }

    private static void writeStringArray(Parcel dest, String[] arr) {
        if (arr == null) {
            dest.writeInt(-1);
        } else {
            dest.writeInt(arr.length);
            for (String s : arr) {
                dest.writeString(s);
            }
        }
    }

    private static String[] readStringArray(Parcel in) {
        int n = in.readInt();
        if (n < 0) return null;
        String[] arr = new String[n];
        for (int i = 0; i < n; i++) {
            arr[i] = in.readString();
        }
        return arr;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeString(mKey);
        dest.writeString(mTitle);
        dest.writeString(mDescription);
        writeStringArray(dest, mChoiceEntries);
        writeStringArray(dest, mChoiceValues);
        dest.writeString(mCurrentValue);
        writeStringArray(dest, mCurrentValues);
        dest.writeTypedArray(mRestrictions, 0);
    }

    public static final Creator<RestrictionEntry> CREATOR = new Creator<RestrictionEntry>() {
        @Override
        public RestrictionEntry createFromParcel(Parcel source) {
            return new RestrictionEntry(source);
        }

        @Override
        public RestrictionEntry[] newArray(int size) {
            return new RestrictionEntry[size];
        }
    };

    @Override
    public String toString() {
        return "RestrictionEntry{mType=" + mType
                + ", mKey='" + mKey + '\''
                + ", mTitle='" + mTitle + '\''
                + ", mDescription='" + mDescription + '\''
                + ", mChoiceEntries=" + Arrays.toString(mChoiceEntries)
                + ", mChoiceValues=" + Arrays.toString(mChoiceValues)
                + ", mCurrentValue='" + mCurrentValue + '\''
                + ", mCurrentValues=" + Arrays.toString(mCurrentValues)
                + ", mRestrictions=" + Arrays.toString(mRestrictions)
                + '}';
    }
}
