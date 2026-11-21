// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

public class DiscretePathEffect extends PathEffect {
    private final float mSegmentLength;
    private final float mDeviation;

    public DiscretePathEffect(float segmentLength, float deviation) {
        mSegmentLength = segmentLength;
        mDeviation = deviation;
    }

    Path apply(Path source) {
        if (!Float.isFinite(mSegmentLength) || mSegmentLength <= 0.0f
                || !Float.isFinite(mDeviation)) {
            return source;
        }
        Path out = new Path();
        out.setFillType(source.getFillType());
        PathMeasure pm = new PathMeasure(source, false);
        float[] pos = new float[2];
        float[] tan = new float[2];
        int seed = 0x9E3779B9;
        do {
            float len = pm.getLength();
            if (len <= 0.0f) continue;
            int n = Math.max(1, (int) Math.ceil(len / mSegmentLength));
            float step = len / n;
            for (int i = 0; i <= n; i++) {
                float d = Math.min(len, i * step);
                pm.getPosTan(d, pos, tan);
                seed = seed * 1664525 + 1013904223;
                float u = ((seed >>> 8) / (float) (1 << 23)) - 1.0f;
                float px = pos[0] - tan[1] * u * mDeviation;
                float py = pos[1] + tan[0] * u * mDeviation;
                if (i == 0) {
                    out.moveTo(px, py);
                } else {
                    out.lineTo(px, py);
                }
            }
            if (pm.isClosed()) {
                out.close();
            }
        } while (pm.nextContour());
        return out;
    }
}
