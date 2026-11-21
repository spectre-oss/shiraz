// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import java.util.Objects;

/** Metadata about datasets currently saved by an AutofillService. */
public final class SavedDatasetsInfo {
    public static final String TYPE_OTHER = "other";
    public static final String TYPE_PASSWORDS = "passwords";

    private final String mType;
    private final int mCount;

    public SavedDatasetsInfo(String type, int count) {
        if (!Objects.equals(type, TYPE_PASSWORDS) && !Objects.equals(type, TYPE_OTHER)) {
            throw new IllegalArgumentException("Invalid type: " + type);
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        mType = type;
        mCount = count;
    }

    public String getType() {
        return mType;
    }

    public int getCount() {
        return mCount;
    }

    @Override
    public String toString() {
        return "SavedDatasetsInfo { type = " + mType + ", count = " + mCount + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SavedDatasetsInfo)) return false;
        SavedDatasetsInfo other = (SavedDatasetsInfo) o;
        return mCount == other.mCount && Objects.equals(mType, other.mType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mType, Integer.valueOf(mCount));
    }
}
