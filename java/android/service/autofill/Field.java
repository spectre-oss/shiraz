// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.view.autofill.AutofillValue;
import java.util.regex.Pattern;

/** Represents content to fill a specific field inside a Dataset. */
public final class Field {
    private final AutofillValue mValue;
    private final Pattern mFilter;
    private final Presentations mPresentations;

    Field(AutofillValue value, Pattern filter, Presentations presentations) {
        mValue = value;
        mFilter = filter;
        mPresentations = presentations;
    }

    public AutofillValue getValue() {
        return mValue;
    }

    public Pattern getFilter() {
        return mFilter;
    }

    public Presentations getPresentations() {
        return mPresentations;
    }

    public static final class Builder {
        private AutofillValue mValue;
        private Pattern mFilter;
        private Presentations mPresentations;
        private boolean mDestroyed;

        public Builder() {
        }

        public Builder setValue(AutofillValue value) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            mValue = value;
            return this;
        }

        public Builder setFilter(Pattern value) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            mFilter = value;
            return this;
        }

        public Builder setPresentations(Presentations value) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            mPresentations = value;
            return this;
        }

        public Field build() {
            if (mDestroyed) throw new IllegalStateException("Already built");
            mDestroyed = true;
            return new Field(mValue, mFilter, mPresentations);
        }
    }
}
