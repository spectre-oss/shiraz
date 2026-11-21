// SPDX-License-Identifier: GPL-3.0-only
package android.animation;

/** Time/value pair defining a keyframe sample for {@link PropertyValuesHolder}. */
public abstract class Keyframe implements Cloneable {
    boolean hasValue;
    float fraction;
    Class<?> valueType;
    private TimeInterpolator interpolator;

    public Keyframe() {}

    public static Keyframe ofInt(float fraction, int value) {
        return new IntKeyframe(fraction, value);
    }

    public static Keyframe ofInt(float fraction) {
        return new IntKeyframe(fraction);
    }

    public static Keyframe ofFloat(float fraction, float value) {
        return new FloatKeyframe(fraction, value);
    }

    public static Keyframe ofFloat(float fraction) {
        return new FloatKeyframe(fraction);
    }

    public static Keyframe ofObject(float fraction, Object value) {
        return new ObjectKeyframe(fraction, value);
    }

    public static Keyframe ofObject(float fraction) {
        return new ObjectKeyframe(fraction, null);
    }

    public boolean hasValue() {
        return hasValue;
    }

    public abstract Object getValue();

    public abstract void setValue(Object value);

    public float getFraction() {
        return fraction;
    }

    public void setFraction(float fraction) {
        this.fraction = fraction;
    }

    public TimeInterpolator getInterpolator() {
        return interpolator;
    }

    public void setInterpolator(TimeInterpolator interpolator) {
        this.interpolator = interpolator;
    }

    public Class getType() {
        return valueType;
    }

    @Override
    public abstract Keyframe clone();

    private static final class ObjectKeyframe extends Keyframe {
        private Object value;

        ObjectKeyframe(float fraction, Object value) {
            this.fraction = fraction;
            this.value = value;
            this.hasValue = value != null;
            this.valueType = hasValue ? value.getClass() : Object.class;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public void setValue(Object value) {
            this.value = value;
            this.hasValue = value != null;
            if (hasValue) {
                this.valueType = value.getClass();
            }
        }

        @Override
        public Keyframe clone() {
            ObjectKeyframe copy =
                    hasValue
                            ? new ObjectKeyframe(getFraction(), value)
                            : new ObjectKeyframe(getFraction(), null);
            copy.hasValue = hasValue;
            copy.valueType = valueType;
            copy.setInterpolator(getInterpolator());
            return copy;
        }
    }

    private static final class IntKeyframe extends Keyframe {
        private int value;

        IntKeyframe(float fraction, int value) {
            this.fraction = fraction;
            this.value = value;
            this.valueType = int.class;
            this.hasValue = true;
        }

        IntKeyframe(float fraction) {
            this.fraction = fraction;
            this.valueType = int.class;
        }

        @Override
        public Object getValue() {
            return Integer.valueOf(value);
        }

        @Override
        public void setValue(Object value) {
            if (value != null && value.getClass() == Integer.class) {
                this.value = ((Integer) value).intValue();
                this.hasValue = true;
            }
        }

        @Override
        public Keyframe clone() {
            IntKeyframe copy =
                    hasValue ? new IntKeyframe(getFraction(), value) : new IntKeyframe(getFraction());
            copy.setInterpolator(getInterpolator());
            return copy;
        }
    }

    private static final class FloatKeyframe extends Keyframe {
        private float value;

        FloatKeyframe(float fraction, float value) {
            this.fraction = fraction;
            this.value = value;
            this.valueType = float.class;
            this.hasValue = true;
        }

        FloatKeyframe(float fraction) {
            this.fraction = fraction;
            this.valueType = float.class;
        }

        @Override
        public Object getValue() {
            return Float.valueOf(value);
        }

        @Override
        public void setValue(Object value) {
            if (value != null && value.getClass() == Float.class) {
                this.value = ((Float) value).floatValue();
                this.hasValue = true;
            }
        }

        @Override
        public Keyframe clone() {
            FloatKeyframe copy =
                    hasValue
                            ? new FloatKeyframe(getFraction(), value)
                            : new FloatKeyframe(getFraction());
            copy.setInterpolator(getInterpolator());
            return copy;
        }
    }
}
