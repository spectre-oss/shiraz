// SPDX-License-Identifier: GPL-3.0-only
package android.icu.text;

import java.text.Format;
import java.util.Objects;

/** Represents a span of a string output by a formatter with optional field constraints. */
public class ConstrainedFieldPosition {
    private Format.Field mFieldConstraint;
    private Class<?> mClassConstraint;
    private Format.Field mField;
    private Object mValue;
    private int mStart;
    private int mLimit;
    private long mContext;

    public ConstrainedFieldPosition() {
        reset();
    }

    public void reset() {
        mFieldConstraint = null;
        mClassConstraint = Object.class;
        mField = null;
        mValue = null;
        mStart = 0;
        mLimit = 0;
        mContext = 0L;
    }

    public void constrainField(Format.Field field) {
        if (field == null) {
            throw new IllegalArgumentException("Cannot constrain on null field");
        }
        reset();
        mFieldConstraint = field;
    }

    public void constrainClass(Class<?> classConstraint) {
        if (classConstraint == null) {
            throw new IllegalArgumentException("Cannot constrain on null field class");
        }
        reset();
        mClassConstraint = classConstraint;
    }

    public Format.Field getField() {
        return mField;
    }

    public int getStart() {
        return mStart;
    }

    public int getLimit() {
        return mLimit;
    }

    public Object getFieldValue() {
        return mValue;
    }

    public long getInt64IterationContext() {
        return mContext;
    }

    public void setInt64IterationContext(long context) {
        mContext = context;
    }

    public void setState(Format.Field field, Object value, int start, int limit) {
        mField = field;
        mValue = value;
        mStart = start;
        mLimit = limit;
    }

    public boolean matchesField(Format.Field field, Object fieldValue) {
        if (field == null) {
            throw new IllegalArgumentException("field must not be null");
        }
        if (mFieldConstraint != null) {
            return mFieldConstraint == field;
        }
        if (mClassConstraint != null && mClassConstraint != Object.class) {
            return mClassConstraint.isAssignableFrom(field.getClass());
        }
        return true;
    }

    @Override
    public String toString() {
        return "CFPos[" + mStart + "-" + mLimit + " " + Objects.toString(mField) + "]";
    }
}
