// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

/** Base for immutable geometry effects. Unsupported effect kinds reject at Paint assignment. */
public class PathEffect {
    public static void shirazValidate(PathEffect value) {
        if (value == null) return;
        if (value instanceof DashPathEffect
                || value instanceof CornerPathEffect
                || value instanceof ComposePathEffect
                || value instanceof SumPathEffect
                || value instanceof DiscretePathEffect
                || value instanceof PathDashPathEffect) {
            return;
        }
        throw new UnsupportedOperationException("Unsupported PathEffect implementation");
    }

    static Path shirazApply(PathEffect effect, Path source) {
        if (effect == null || source == null) {
            return source;
        }
        if (effect instanceof CornerPathEffect) {
            return ((CornerPathEffect) effect).apply(source);
        }
        if (effect instanceof DiscretePathEffect) {
            return ((DiscretePathEffect) effect).apply(source);
        }
        if (effect instanceof PathDashPathEffect) {
            return ((PathDashPathEffect) effect).apply(source);
        }
        if (effect instanceof ComposePathEffect) {
            return ((ComposePathEffect) effect).apply(source);
        }
        if (effect instanceof SumPathEffect) {
            return ((SumPathEffect) effect).apply(source);
        }
        if (effect instanceof DashPathEffect) {
            return applyDash((DashPathEffect) effect, source);
        }
        return source;
    }

    static Path applyDash(DashPathEffect dash, Path source) {
        float[] intervals = dash.shirazIntervals();
        float period = 0f;
        for (int i = 0; i < intervals.length; i++) {
            period += intervals[i];
        }
        if (period <= 0f) {
            return source;
        }
        float phase = dash.shirazPhase() % period;
        if (phase < 0f) phase += period;

        Path out = new Path();
        out.setFillType(source.getFillType());
        PathMeasure pm = new PathMeasure(source, false);
        do {
            float len = pm.getLength();
            if (len <= 0f) continue;
            int idx = 0;
            float offset = phase;
            while (idx < intervals.length && offset >= intervals[idx]) {
                offset -= intervals[idx];
                idx = (idx + 1) % intervals.length;
            }
            float dist = 0f;
            while (dist < len) {
                float step = intervals[idx] - offset;
                offset = 0f;
                float nextDist = Math.min(len, dist + step);
                if ((idx & 1) == 0 && nextDist > dist) {
                    pm.getSegment(dist, nextDist, out, true);
                }
                dist = nextDist;
                idx = (idx + 1) % intervals.length;
            }
        } while (pm.nextContour());
        return out;
    }
}
