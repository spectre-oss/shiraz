// SPDX-License-Identifier: GPL-3.0-only
package android.text;
import android.graphics.Paint;

/** Paint plus text-layout metadata; layout consumers apply backgrounds and baseline shifts. */
public class TextPaint extends Paint {
    public int bgColor, baselineShift, linkColor, underlineColor;
    public int[] drawableState;
    public float density=1, underlineThickness;
    public TextPaint() { super(); }
    public TextPaint(int flags) { super(flags); }
    public TextPaint(Paint source) { super(source); }
    public void set(TextPaint source) {
        super.set(source);
        bgColor=source.bgColor; baselineShift=source.baselineShift; linkColor=source.linkColor;
        drawableState=source.drawableState; density=source.density;
        underlineColor=source.underlineColor; underlineThickness=source.underlineThickness;
    }
}
