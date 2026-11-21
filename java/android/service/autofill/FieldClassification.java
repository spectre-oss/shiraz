// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Represents the field classification results for a given field. */
public final class FieldClassification {
    private final List<Match> mMatches;

    public FieldClassification(ArrayList<Match> matches) {
        mMatches = matches != null
                ? Collections.unmodifiableList(new ArrayList<Match>(matches))
                : Collections.<Match>emptyList();
    }

    public List<Match> getMatches() {
        return mMatches;
    }

    @Override
    public String toString() {
        return "FieldClassification: " + mMatches;
    }

    public static final class Match {
        private final String mCategoryId;
        private final float mScore;

        public Match(String categoryId, float score) {
            if (categoryId == null) throw new NullPointerException("categoryId");
            mCategoryId = categoryId;
            mScore = score;
        }

        public String getCategoryId() {
            return mCategoryId;
        }

        public float getScore() {
            return mScore;
        }

        @Override
        public String toString() {
            return "Match: categoryId=" + mCategoryId + ", score=" + mScore;
        }
    }
}
