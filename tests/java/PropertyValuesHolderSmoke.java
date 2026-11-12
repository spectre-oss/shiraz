// SPDX-License-Identifier: GPL-3.0-only
// PropertyValuesHolder and the animator APIs built on it: multi-property ObjectAnimators,
// single-value starts read from the target, ARGB and object evaluators, AOSP's conversion of
// single-property animators into one holder (getValues), clone isolation, the Material
// MotionSpec rebinding pattern, frame-driven completion and explicit failures.
import android.animation.*;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Property;
import android.view.Choreographer;

public final class PropertyValuesHolderSmoke {
    public static final class Point {
        float x;
        float y;

        public float getX() {
            return x;
        }

        public void setX(float value) {
            x = value;
        }

        public float getY() {
            return y;
        }

        public void setY(float value) {
            y = value;
        }
    }

    public static final class Paint {
        int color;

        public int getColor() {
            return color;
        }

        public void setColor(int value) {
            color = value;
        }
    }

    static final Property<Point, float[]> PAIR =
            new Property<Point, float[]>(float[].class, "pair") {
                @Override
                public float[] get(Point point) {
                    return new float[] {point.x, point.y};
                }

                @Override
                public void set(Point point, float[] value) {
                    point.x = value[0];
                    point.y = value[1];
                }
            };

    static final Property<Point, Float> READ_ONLY =
            new Property<Point, Float>(Float.class, "readOnly") {
                @Override
                public Float get(Point point) {
                    return point.x;
                }

                @Override
                public boolean isReadOnly() {
                    return true;
                }
            };

    private PropertyValuesHolderSmoke() {}

    static void check(boolean ok, String why) {
        if (!ok) {
            throw new AssertionError(why);
        }
    }

    static <T extends Animator> T linear(T animator) {
        animator.setInterpolator(null);
        return animator;
    }

    public static void main(String[] args) {
        Looper.prepareMainLooper();
        Point point = new Point();
        ObjectAnimator both =
                linear(
                        ObjectAnimator.ofPropertyValuesHolder(
                                point,
                                PropertyValuesHolder.ofFloat("x", 0, 10),
                                PropertyValuesHolder.ofFloat("y", 20, 40)));
        both.setCurrentFraction(.5f);
        check(point.x == 5 && point.y == 30, "Two holders animate their own properties");
        check(Float.valueOf(30).equals(both.getAnimatedValue("y")), "Animated value by holder name");
        check(Float.valueOf(5).equals(both.getAnimatedValue()), "First holder is the animated value");
        check("x,y".equals(both.getPropertyName()), "Holder names join as the property name");
        check(both.getValues().length == 2 && "y".equals(both.getValues()[1].getPropertyName()), "Holders kept");

        point.x = 4;
        ObjectAnimator single =
                linear(ObjectAnimator.ofPropertyValuesHolder(point, PropertyValuesHolder.ofFloat("x", 8)));
        single.setCurrentFraction(.5f);
        check(point.x == 6, "Single-value holder starts from the target");

        Paint paint = new Paint();
        ObjectAnimator argb = linear(ObjectAnimator.ofArgb(paint, "color", 0xff000000, 0xffffffff));
        argb.setCurrentFraction(0);
        check(paint.color == 0xff000000, "ARGB start");
        argb.setCurrentFraction(.5f);
        check(paint.color == (Integer) ArgbEvaluator.getInstance().evaluate(.5f, 0xff000000, 0xffffffff),
                "ARGB midpoint uses ArgbEvaluator");
        check(argb.getValues().length == 1 && "color".equals(argb.getValues()[0].getPropertyName()),
                "ofArgb exposes its holder");

        TypeEvaluator<float[]> pairs =
                new TypeEvaluator<float[]>() {
                    @Override
                    public float[] evaluate(float f, float[] a, float[] b) {
                        return new float[] {a[0] + f * (b[0] - a[0]), a[1] + f * (b[1] - a[1])};
                    }
                };
        ObjectAnimator object =
                linear(ObjectAnimator.ofObject(point, PAIR, pairs, new float[] {0, 0}, new float[] {2, 4}));
        object.setCurrentFraction(.25f);
        check(point.x == .5f && point.y == 1, "Object holder through Property and evaluator");

        ObjectAnimator legacy = linear(ObjectAnimator.ofFloat(point, "x", 1, 3));
        PropertyValuesHolder[] holders = legacy.getValues();
        check(holders.length == 1 && "x".equals(holders[0].getPropertyName()), "Single property becomes a holder");
        check(legacy.getValues() == holders, "getValues returns the live holders");
        holders[0].setFloatValues(10, 20);
        legacy.setCurrentFraction(.5f);
        check(point.x == 15, "Holder edits drive the animator");
        ObjectAnimator copy = legacy.clone();
        copy.getValues()[0].setFloatValues(0, 2);
        legacy.setCurrentFraction(.5f);
        check(point.x == 15, "Cloned holders are independent");
        copy.setCurrentFraction(.5f);
        check(point.x == 1, "Clone animates its own holders");
        legacy.setFloatValues(100, 200);
        legacy.setCurrentFraction(.5f);
        check(point.x == 150, "setFloatValues updates the first holder");

        ValueAnimator plain =
                linear(
                        ValueAnimator.ofPropertyValuesHolder(
                                PropertyValuesHolder.ofInt("a", 0, 100), PropertyValuesHolder.ofFloat("b", 1, 2)));
        plain.setCurrentFraction(.25f);
        check(Integer.valueOf(25).equals(plain.getAnimatedValue("a")), "ValueAnimator int holder");
        check(Float.valueOf(1.25f).equals(plain.getAnimatedValue("b")), "ValueAnimator float holder");
        check(Integer.valueOf(25).equals(plain.getAnimatedValue()) && plain.getAnimatedValue("c") == null,
                "ValueAnimator holder lookup");
        ValueAnimator floats = linear(ValueAnimator.ofObject(new FloatEvaluator(), 1f, 3f));
        floats.setCurrentFraction(.5f);
        check(Float.valueOf(2).equals(floats.getAnimatedValue()), "FloatEvaluator");
        ValueAnimator ints = linear(ValueAnimator.ofObject(new IntEvaluator(), 0, 7));
        ints.setCurrentFraction(.5f);
        check(Integer.valueOf(3).equals(ints.getAnimatedValue()), "IntEvaluator truncates");
        ValueAnimator colors = linear(ValueAnimator.ofArgb(0xff00ff00, 0xff0000ff));
        colors.setCurrentFraction(1);
        check(Integer.valueOf(0xff0000ff).equals(colors.getAnimatedValue()), "ValueAnimator.ofArgb end");

        check(new ValueAnimator().getValues() == null, "No values, no holders");
        ObjectAnimator timing = new ObjectAnimator();
        timing.setPropertyName("alpha");
        timing.setDuration(150);
        check(timing.getValues() == null, "Timing-only ObjectAnimator has no holders");

        // Material MotionSpec: clone holders from a spec, bind them to a new target, rename to a Property.
        PropertyValuesHolder[] spec = {PropertyValuesHolder.ofFloat("opacity", 0, 1)};
        PropertyValuesHolder[] cloned = {spec[0].clone()};
        Point view = new Point();
        Property<Point, Float> xProperty = Property.of(Point.class, Float.class, "x");
        ObjectAnimator motion = linear(ObjectAnimator.ofPropertyValuesHolder(view, cloned));
        motion.setProperty(xProperty);
        motion.setCurrentFraction(.5f);
        check(view.x == .5f && "x".equals(cloned[0].getPropertyName()), "setProperty rebinds the first holder");
        check("opacity".equals(spec[0].getPropertyName()), "Spec holder untouched by its clone");

        Point driven = new Point();
        ObjectAnimator frames =
                ObjectAnimator.ofPropertyValuesHolder(
                        driven, PropertyValuesHolder.ofFloat("x", 0, 10), PropertyValuesHolder.ofFloat("y", 0, -10));
        frames.setDuration(100);
        long now = SystemClock.uptimeMillis();
        frames.start();
        Choreographer choreographer = Choreographer.getInstance();
        choreographer.shirazDoFrame(now * 1000000, null);
        choreographer.shirazDoFrame((now + 50) * 1000000, null);
        check(driven.x > 0 && driven.x < 10 && driven.y < 0 && driven.y > -10, "Frame-driven midpoint");
        choreographer.shirazDoFrame((now + 150) * 1000000, null);
        check(!frames.isStarted() && driven.x == 10 && driven.y == -10, "Frame-driven end values");

        try {
            ObjectAnimator.ofPropertyValuesHolder(point, (PropertyValuesHolder[]) null);
            throw new AssertionError("Null holders accepted");
        } catch (NullPointerException expected) {
        }
        try {
            ObjectAnimator.ofPropertyValuesHolder(point, PropertyValuesHolder.ofFloat(READ_ONLY, 0, 1))
                    .setCurrentFraction(.5f);
            throw new AssertionError("Read-only property animated");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            linear(ValueAnimator.ofObject(null, "a", "b")).setCurrentFraction(.5f);
            throw new AssertionError("Object values without evaluator");
        } catch (IllegalStateException expected) {
        }
        try {
            PropertyValuesHolder.ofFloat("x", Float.NaN);
            throw new AssertionError("NaN holder value");
        } catch (IllegalArgumentException expected) {
        }
        try {
            timing.setTarget(view);
            timing.setCurrentFraction(.5f);
            throw new AssertionError("Value-less ObjectAnimator applied");
        } catch (IllegalStateException expected) {
        }
        System.out.println("PROPERTY_VALUES_HOLDER_OK");
    }
}
