// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.content.Context;
import android.graphics.Path;
import android.util.AttributeSet;

/** Curved Bezier {@link PathMotion} constrained by minimum horizontal/vertical and maximum arc angles. */
public class ArcMotion extends PathMotion {
    private static final float DEFAULT_MIN_ANGLE_DEGREES = 0f;
    private static final float DEFAULT_MAX_ANGLE_DEGREES = 70f;
    private static final float DEFAULT_MAX_TANGENT = (float) Math.tan(Math.toRadians(DEFAULT_MAX_ANGLE_DEGREES / 2f));

    private float minimumHorizontalAngle = DEFAULT_MIN_ANGLE_DEGREES;
    private float minimumVerticalAngle = DEFAULT_MIN_ANGLE_DEGREES;
    private float maximumAngle = DEFAULT_MAX_ANGLE_DEGREES;
    private float minimumHorizontalTangent = 0f;
    private float minimumVerticalTangent = 0f;
    private float maximumTangent = DEFAULT_MAX_TANGENT;

    public ArcMotion() {}

    public ArcMotion(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setMinimumHorizontalAngle(float angleInDegrees) {
        minimumHorizontalAngle = angleInDegrees;
        minimumHorizontalTangent = toTangent(angleInDegrees);
    }

    public float getMinimumHorizontalAngle() {
        return minimumHorizontalAngle;
    }

    public void setMinimumVerticalAngle(float angleInDegrees) {
        minimumVerticalAngle = angleInDegrees;
        minimumVerticalTangent = toTangent(angleInDegrees);
    }

    public float getMinimumVerticalAngle() {
        return minimumVerticalAngle;
    }

    public void setMaximumAngle(float angleInDegrees) {
        maximumAngle = angleInDegrees;
        maximumTangent = toTangent(angleInDegrees);
    }

    public float getMaximumAngle() {
        return maximumAngle;
    }

    private static float toTangent(float arcInDegrees) {
        if (arcInDegrees < 0f || arcInDegrees > 90f) {
            throw new IllegalArgumentException("Arc must be between 0 and 90 degrees");
        }
        return (float) Math.tan(Math.toRadians(arcInDegrees / 2f));
    }

    @Override
    public Path getPath(float startX, float startY, float endX, float endY) {
        Path path = new Path();
        path.moveTo(startX, startY);
        float deltaX = endX - startX;
        float deltaY = endY - startY;
        float h2 = deltaX * deltaX + deltaY * deltaY;
        float dx = (startX + endX) / 2f;
        float dy = (startY + endY) / 2f;
        float midDist2 = h2 * 0.25f;
        boolean isMovingUpwards = startY > endY;
        float eDistX;
        float eDistY;
        float minimumArcDist2;
        if (Math.abs(deltaX) < Math.abs(deltaY)) {
            float eDistYOffset = Math.abs(h2 / (2f * deltaY));
            if (isMovingUpwards) {
                eDistY = endY + eDistYOffset;
                eDistX = endX;
            } else {
                eDistY = startY + eDistYOffset;
                eDistX = startX;
            }
            minimumArcDist2 = midDist2 * minimumVerticalTangent * minimumVerticalTangent;
        } else {
            float eDistXOffset = h2 / (2f * deltaX);
            if (isMovingUpwards) {
                eDistX = startX + eDistXOffset;
                eDistY = startY;
            } else {
                eDistX = endX - eDistXOffset;
                eDistY = endY;
            }
            minimumArcDist2 = midDist2 * minimumHorizontalTangent * minimumHorizontalTangent;
        }
        float arcDistX = dx - eDistX;
        float arcDistY = dy - eDistY;
        float arcDist2 = arcDistX * arcDistX + arcDistY * arcDistY;
        float maximumArcDist2 = midDist2 * maximumTangent * maximumTangent;
        float newArcDistance2 = 0f;
        if (arcDist2 != 0f && arcDist2 < minimumArcDist2) {
            newArcDistance2 = minimumArcDist2;
        } else if (arcDist2 > maximumArcDist2) {
            newArcDistance2 = maximumArcDist2;
        }
        if (newArcDistance2 != 0f) {
            float ratio = (float) Math.sqrt(newArcDistance2 / arcDist2);
            eDistX = dx + ratio * (eDistX - dx);
            eDistY = dy + ratio * (eDistY - dy);
        }
        float control1X = (startX + eDistX) / 2f;
        float control1Y = (startY + eDistY) / 2f;
        float control2X = (eDistX + endX) / 2f;
        float control2Y = (eDistY + endY) / 2f;
        path.cubicTo(control1X, control1Y, control2X, control2Y, endX, endY);
        return path;
    }
}
