// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable.shapes;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.Objects;

public class ArcShape extends RectShape {
    private final float startAngle;
    private final float sweepAngle;

    public ArcShape(float startAngle, float sweepAngle) {
        if (!Float.isFinite(startAngle) || !Float.isFinite(sweepAngle)) {
            throw new IllegalArgumentException("Arc angles must be finite");
        }
        this.startAngle = startAngle;
        this.sweepAngle = sweepAngle;
    }

    public final float getStartAngle() {
        return startAngle;
    }

    public final float getSweepAngle() {
        return sweepAngle;
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        RectF r = rect();
        canvas.drawArc(r.left, r.top, r.right, r.bottom, startAngle, sweepAngle, true, paint);
    }

    @Override
    public void getOutline(Outline outline) {
        outline.setEmpty();
    }

    @Override
    public ArcShape clone() throws CloneNotSupportedException {
        return (ArcShape) super.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ArcShape that = (ArcShape) o;
        return Float.compare(that.getWidth(), getWidth()) == 0
                && Float.compare(that.getHeight(), getHeight()) == 0
                && Float.compare(that.startAngle, startAngle) == 0
                && Float.compare(that.sweepAngle, sweepAngle) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Float.valueOf(getWidth()),
                Float.valueOf(getHeight()),
                Float.valueOf(startAngle),
                Float.valueOf(sweepAngle));
    }
}
