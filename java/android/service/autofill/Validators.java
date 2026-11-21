// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;

/** Factory for boolean Validator combinators. */
public final class Validators {
    private Validators() {
    }

    public static Validator and(Validator... validators) {
        if (validators == null || validators.length == 0) {
            throw new IllegalArgumentException("validators cannot be null or empty");
        }
        for (int i = 0; i < validators.length; i++) {
            if (validators[i] == null) {
                throw new NullPointerException("validators[" + i + "] is null");
            }
        }
        return new CompositeValidator("AND", validators.clone());
    }

    public static Validator or(Validator... validators) {
        if (validators == null || validators.length == 0) {
            throw new IllegalArgumentException("validators cannot be null or empty");
        }
        for (int i = 0; i < validators.length; i++) {
            if (validators[i] == null) {
                throw new NullPointerException("validators[" + i + "] is null");
            }
        }
        return new CompositeValidator("OR", validators.clone());
    }

    public static Validator not(Validator validator) {
        if (validator == null) {
            throw new NullPointerException("validator cannot be null");
        }
        return new CompositeValidator("NOT", new Validator[]{validator});
    }

    private static final class CompositeValidator implements Validator, Parcelable {
        private final String mOp;
        private final Validator[] mChildren;

        CompositeValidator(String op, Validator[] children) {
            mOp = op;
            mChildren = children;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mOp);
            dest.writeInt(mChildren.length);
            for (int i = 0; i < mChildren.length; i++) {
                dest.writeParcelable(
                        mChildren[i] instanceof Parcelable ? (Parcelable) mChildren[i] : null,
                        flags);
            }
        }

        @Override
        public String toString() {
            return mOp + Arrays.toString(mChildren);
        }
    }
}
