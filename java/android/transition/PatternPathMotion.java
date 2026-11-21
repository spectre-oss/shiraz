// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.util.AttributeSet;

/** {@link PathMotion} scaling and rotating a template {@link Path} between start and end coordinates. */
public class PatternPathMotion extends PathMotion {
    private Path originalPatternPath;
    private final Path patternPath = new Path();
    private final Matrix tempMatrix = new Matrix();

    public PatternPathMotion() {
        patternPath.lineTo(1f, 0f);
        originalPatternPath = patternPath;
    }

    public PatternPathMotion(Context context, AttributeSet attrs) {
        this();
    }

    public PatternPathMotion(Path patternPath) {
        setPatternPath(patternPath);
    }

    public Path getPatternPath() {
        return originalPatternPath;
    }

    public void setPatternPath(Path patternPath) {
        if (patternPath == null) {
            throw new NullPointerException("patternPath must not be null");
        }
        PathMeasure pm = new PathMeasure(patternPath, false);
        float length = pm.getLength();
        float[] pos = new float[2];
        pm.getPosTan(length, pos, null);
        float endX = pos[0];
        float endY = pos[1];
        pm.getPosTan(0f, pos, null);
        float startX = pos[0];
        float startY = pos[1];
        if (startX == endX && startY == endY) {
            throw new IllegalArgumentException("pattern must not end at the starting point");
        }
        tempMatrix.setTranslate(-startX, -startY);
        float dx = endX - startX;
        float dy = endY - startY;
        float distance = (float) Math.hypot(dx, dy);
        float scale = 1f / distance;
        tempMatrix.postScale(scale, scale);
        double angle = Math.atan2(dy, dx);
        tempMatrix.postRotate((float) Math.toDegrees(-angle));
        this.patternPath.reset();
        patternPath.transform(tempMatrix, this.patternPath);
        this.originalPatternPath = patternPath;
    }

    @Override
    public Path getPath(float startX, float startY, float endX, float endY) {
        float dx = endX - startX;
        float dy = endY - startY;
        float length = (float) Math.hypot(dx, dy);
        double angle = Math.atan2(dy, dx);
        tempMatrix.setScale(length, length);
        tempMatrix.postRotate((float) Math.toDegrees(angle));
        tempMatrix.postTranslate(startX, startY);
        Path path = new Path();
        patternPath.transform(tempMatrix, path);
        return path;
    }
}
