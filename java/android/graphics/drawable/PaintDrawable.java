// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;

import android.content.res.Resources;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.AttributeSet;
import org.xmlpull.v1.XmlPullParser;

public class PaintDrawable extends ShapeDrawable {
    public PaintDrawable() {
    }

    public PaintDrawable(int color) {
        getPaint().setColor(color);
    }

    public void setCornerRadius(float radius) {
        float[] radii = null;
        if (radius > 0f) {
            radii = new float[8];
            for (int i = 0; i < 8; i++) {
                radii[i] = radius;
            }
        }
        setCornerRadii(radii);
    }

    public void setCornerRadii(float[] radii) {
        if (radii == null) {
            if (getShape() != null) {
                setShape(null);
            }
        } else {
            setShape(new RoundRectShape(radii, null, null));
        }
        invalidateSelf();
    }

    @Override
    protected boolean inflateTag(
            String name, Resources r, XmlPullParser parser, AttributeSet attrs) {
        if ("corners".equals(name)) {
            if (attrs != null) {
                float radius = attrs.getAttributeFloatValue(null, "radius", 0f);
                float tl = attrs.getAttributeFloatValue(null, "topLeftRadius", radius);
                float tr = attrs.getAttributeFloatValue(null, "topRightRadius", radius);
                float bl = attrs.getAttributeFloatValue(null, "bottomLeftRadius", radius);
                float br = attrs.getAttributeFloatValue(null, "bottomRightRadius", radius);
                if (tl != radius || tr != radius || bl != radius || br != radius) {
                    setCornerRadii(new float[] {tl, tl, tr, tr, br, br, bl, bl});
                } else if (radius > 0f) {
                    setCornerRadius(radius);
                }
            }
            return true;
        }
        return super.inflateTag(name, r, parser, attrs);
    }
}
