// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;

public class ColorDrawable extends Drawable {
    private int baseColor, color;
    private android.content.res.ColorStateList tint;
    private PorterDuff.Mode tintMode=PorterDuff.Mode.SRC_IN;
    public void setTintList(android.content.res.ColorStateList value){tint=value;refreshTintOwner();}
    public void setTintMode(PorterDuff.Mode value){tintMode=value;invalidateSelf();}
    public boolean isStateful(){return tint!=null&&tint.isStateful();}
    protected boolean onStateChange(int[] state){if(tint==null)return false;invalidateSelf();return true;}
    private final Paint paint = new Paint();
    public ColorDrawable() { }
    public ColorDrawable(int value) { baseColor = color = value; }
    public int getColor() { return color; }
    public void setColor(int value) { if (baseColor != value || color != value) { baseColor = color = value; invalidateSelf(); } }
    public void setAlpha(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("alpha");
        int next = (baseColor & 0xffffff) | (((baseColor >>> 24) * (value + (value >> 7)) >> 8) << 24);
        if (next != color) { color = next; invalidateSelf(); }
    }
    public int getAlpha() { return color >>> 24; }
    public void setColorFilter(ColorFilter filter) { if (filter != paint.getColorFilter()) { paint.setColorFilter(filter); invalidateSelf(); } }
    public ColorFilter getColorFilter() { return paint.getColorFilter(); }
    public int getOpacity() { if (paint.getColorFilter() != null || tint!=null&&tintMode!=null) return PixelFormat.TRANSLUCENT; return getAlpha() == 255 ? PixelFormat.OPAQUE : getAlpha() == 0 ? PixelFormat.TRANSPARENT : PixelFormat.TRANSLUCENT; }
    @Override public void getOutline(Outline outline) { outline.setRect(getBounds()); outline.setAlpha(getAlpha()/255f); }
    public void draw(Canvas canvas) { Rect b = getBounds(); Paint drawing=new Paint(paint);drawing.setColor(color);if(drawing.getColorFilter()==null&&tint!=null&&tintMode!=null)drawing.setColorFilter(new PorterDuffColorFilter(tint.getColorForState(getState(),tint.getDefaultColor()),tintMode));canvas.drawRect(b.left,b.top,b.right,b.bottom,drawing); }
}
