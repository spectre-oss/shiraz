// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;
import android.content.res.Resources;

/** Density-aware bitmap bounds rendering; tiling and nine-patch drawing are separate. */
public class BitmapDrawable extends Drawable {
    private final Bitmap bitmap;
    private android.content.res.ColorStateList tint;
    private PorterDuff.Mode tintMode=PorterDuff.Mode.SRC_IN;
    public void setTintList(android.content.res.ColorStateList value){tint=value;refreshTintOwner();}
    public void setTintMode(PorterDuff.Mode value){tintMode=value;invalidateSelf();}
    public boolean isStateful(){return tint!=null&&tint.isStateful();}
    protected boolean onStateChange(int[] state){if(tint==null)return false;invalidateSelf();return true;}
    private int targetDensity=160, gravity=android.view.Gravity.FILL;
    private final Rect destination=new Rect();
    public int getGravity() { return gravity; }
    public void setGravity(int value) { if(value!=gravity) {gravity=value;invalidateSelf();} }
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    public BitmapDrawable(Bitmap bitmap) { this(null, bitmap); }
    public BitmapDrawable(Resources resources, Bitmap bitmap) {
        if (resources != null) targetDensity=resources.getDisplayMetrics().densityDpi;
        this.bitmap = bitmap;
    }
    public void setTargetDensity(int value) { if (value < 0 || value > 65535) throw new IllegalArgumentException("target density"); value=value == 0 ? 160 : value; if (value != targetDensity) { targetDensity=value; invalidateSelf(); } }
    public void setTargetDensity(android.util.DisplayMetrics value) { setTargetDensity(value.densityDpi); }
    public void setTargetDensity(Canvas value) { setTargetDensity(value.getDensity()); }
    public final Bitmap getBitmap() { return bitmap; }
    public final Paint getPaint() { return paint; }
    public void setFilterBitmap(boolean value) { if (value != paint.isFilterBitmap()) { paint.setFilterBitmap(value); invalidateSelf(); } }
    public boolean isFilterBitmap() { return paint.isFilterBitmap(); }
    public void setAntiAlias(boolean value) { if (value != paint.isAntiAlias()) { paint.setAntiAlias(value); invalidateSelf(); } }
    public boolean hasAntiAlias() { return paint.isAntiAlias(); }
    public void setAlpha(int value) { if (value != paint.getAlpha()) { paint.setAlpha(value); invalidateSelf(); } }
    public int getAlpha() { return paint.getAlpha(); }
    public void setColorFilter(ColorFilter filter) { if (filter != paint.getColorFilter()) { paint.setColorFilter(filter); invalidateSelf(); } }
    public ColorFilter getColorFilter() { return paint.getColorFilter(); }
    public int getIntrinsicWidth() { return bitmap == null ? -1 : bitmap.getScaledWidth(targetDensity); }
    public int getIntrinsicHeight() { return bitmap == null ? -1 : bitmap.getScaledHeight(targetDensity); }
    // ARGB bitmap mutability prevents an opacity promise without scanning/caching pixels.
    public int getOpacity() { return bitmap == null || getAlpha() == 0 && paint.getColorFilter() == null ? PixelFormat.TRANSPARENT : PixelFormat.TRANSLUCENT; }
    public boolean onLayoutDirectionChanged(int value) { invalidateSelf(); return true; }
    public void draw(Canvas canvas) { if (bitmap != null && !getBounds().isEmpty()) { android.view.Gravity.apply(gravity,getIntrinsicWidth(),getIntrinsicHeight(),getBounds(),destination,getLayoutDirection()); Paint drawing=paint;if(paint.getColorFilter()==null&&tint!=null&&tintMode!=null){drawing=new Paint(paint);drawing.setColorFilter(new PorterDuffColorFilter(tint.getColorForState(getState(),tint.getDefaultColor()),tintMode));}canvas.drawBitmap(bitmap,null,destination,drawing); } }
}
