// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.res.Configuration;

public class GrammaticalInflectionManager {
    private static final int GENDER_NOT_SPECIFIED = 0;
    private static final int GENDER_NEUTRAL = 1;
    private static final int GENDER_FEMININE = 2;
    private static final int GENDER_MASCULINE = 3;

    private volatile int mGender = GENDER_NOT_SPECIFIED;

    GrammaticalInflectionManager() {
    }

    public int getApplicationGrammaticalGender() {
        return mGender;
    }

    public void setRequestedApplicationGrammaticalGender(int grammaticalGender) {
        if (grammaticalGender != GENDER_NOT_SPECIFIED
                && grammaticalGender != GENDER_NEUTRAL
                && grammaticalGender != GENDER_FEMININE
                && grammaticalGender != GENDER_MASCULINE) {
            throw new IllegalArgumentException("Unknown grammatical gender: " + grammaticalGender);
        }
        mGender = grammaticalGender;
    }

    public int getSystemGrammaticalGender() {
        return GENDER_NOT_SPECIFIED;
    }
}
