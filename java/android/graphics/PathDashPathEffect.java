// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

public class PathDashPathEffect extends PathEffect {
    public enum Style {
        TRANSLATE,
        ROTATE,
        MORPH
    }

    private final Path mShape;
    private final float mAdvance;
    private final float mPhase;
    private final Style mStyle;

    public PathDashPathEffect(Path shape, float advance, float phase, Style style) {
        if (shape == null || style == null) {
            throw new NullPointerException("shape and style must not be null");
        }
        mShape = new Path(shape);
        mAdvance = advance;
        mPhase = phase;
        mStyle = style;
    }

    Path apply(Path source) {
        if (!Float.isFinite(mAdvance) || mAdvance <= 0.0f || mShape.isEmpty()) {
            return source;
        }
        float phase = Float.isFinite(mPhase) ? (mPhase % mAdvance) : 0.0f;
        if (phase < 0.0f) phase += mAdvance;

        Path out = new Path();
        PathMeasure pm = new PathMeasure(source, false);
        float[] pos = new float[2];
        float[] tan = new float[2];
        Matrix mat = new Matrix();
        do {
            float len = pm.getLength();
            for (float dist = mAdvance - phase; dist <= len; dist += mAdvance) {
                if (dist < 0.0f) continue;
                pm.getPosTan(dist, pos, tan);
                mat.reset();
                if (mStyle != Style.TRANSLATE) {
                    float deg = (float) Math.toDegrees(Math.atan2(tan[1], tan[0]));
                    mat.postRotate(deg);
                }
                mat.postTranslate(pos[0], pos[1]);
                out.addPath(mShape, mat);
            }
        } while (pm.nextContour());
        return out;
    }
}
