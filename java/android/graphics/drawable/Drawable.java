// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;
import java.lang.ref.WeakReference;
import java.util.Arrays;

/** CPU drawable lifecycle; subclasses define resource inflation, tint and cloning support. */
public abstract class Drawable {
    public interface Callback {
        void invalidateDrawable(Drawable who);
        void scheduleDrawable(Drawable who, Runnable what, long when);
        void unscheduleDrawable(Drawable who, Runnable what);
    }
    private final Rect bounds = new Rect();
    private WeakReference<Callback> callback;
    private int[] state = new int[0];
    private int level,changingConfigurations;
    public void setChangingConfigurations(int value) { changingConfigurations=value; }
    public int getChangingConfigurations() { return changingConfigurations; }
    private boolean visible = true;
    public static abstract class ConstantState {
        public abstract Drawable newDrawable();
        public Drawable newDrawable(android.content.res.Resources resources) { return newDrawable(); }
        public Drawable newDrawable(android.content.res.Resources resources,android.content.res.Resources.Theme theme) { Drawable value=newDrawable(resources); if (theme != null && value.canApplyTheme()) value.applyTheme(theme); return value; }
        public abstract int getChangingConfigurations();
        public boolean canApplyTheme() { return false; }
    }
    public ConstantState getConstantState() { return null; }
    public boolean canApplyTheme() { return false; }
    public void applyTheme(android.content.res.Resources.Theme theme) { }
    private int layoutDirection;
    public int getLayoutDirection() { return layoutDirection; }
    public boolean setLayoutDirection(int value) { if (value != 0 && value != 1) throw new IllegalArgumentException("drawable direction"); if (value == layoutDirection) return false; layoutDirection=value; return onLayoutDirectionChanged(value); }
    public boolean onLayoutDirectionChanged(int value) { return false; }
    public void setAutoMirrored(boolean value) { if (value) throw new UnsupportedOperationException("Drawable mirroring is not implemented"); }
    public boolean isAutoMirrored() { return false; }
    public void setTint(int color) { setTintList(android.content.res.ColorStateList.valueOf(color)); }
    public void setTintList(android.content.res.ColorStateList colors) { throw new UnsupportedOperationException("Drawable state tinting is not implemented"); }
    protected final void refreshTintOwner() {
        // A tint changes pixels, not the owner's pressed/checked state.
        invalidateSelf();
    }
    public void setTintMode(PorterDuff.Mode mode) { throw new UnsupportedOperationException("Drawable tint mode is not implemented"); }
    public void getOutline(Outline outline) { outline.setRect(getBounds()); outline.setAlpha(0); }
    public abstract void draw(Canvas canvas);
    public abstract void setAlpha(int alpha);
    public abstract void setColorFilter(ColorFilter filter);
    public abstract int getOpacity();
    public ColorFilter getColorFilter() { return null; }
    public void setColorFilter(int color,PorterDuff.Mode mode) { setColorFilter(new PorterDuffColorFilter(color,mode)); }
    public int getAlpha() { return 255; }
    public void clearColorFilter() { setColorFilter(null); }
    public void setBounds(int left, int top, int right, int bottom) {
        if (bounds.left == left && bounds.top == top && bounds.right == right && bounds.bottom == bottom) return;
        if (!bounds.isEmpty()) invalidateSelf();
        bounds.set(left, top, right, bottom); onBoundsChange(bounds);
    }
    public void setBounds(Rect value) { setBounds(value.left, value.top, value.right, value.bottom); }
    public final Rect getBounds() { return bounds; }
    public Rect getDirtyBounds() { return getBounds(); }
    public final Rect copyBounds() { return new Rect(bounds); }
    public final void copyBounds(Rect out) { out.set(bounds); }
    protected void onBoundsChange(Rect bounds) { }
    public final void setCallback(Callback value) { callback = value == null ? null : new WeakReference<Callback>(value); }
    public Callback getCallback() { return callback == null ? null : callback.get(); }
    public void invalidateSelf() { Callback owner = getCallback(); if (owner != null) owner.invalidateDrawable(this); }
    public void scheduleSelf(Runnable task, long when) { Callback owner = getCallback(); if (owner != null) owner.scheduleDrawable(this, task, when); }
    public void unscheduleSelf(Runnable task) { Callback owner = getCallback(); if (owner != null) owner.unscheduleDrawable(this, task); }
    public boolean hasFocusStateSpecified(){return false;}
    public boolean isStateful() { return false; }
    public boolean setState(int[] values) {
        int[] next = values == null ? new int[0] : values.clone();
        if (Arrays.equals(state, next)) return false;
        state = next; return onStateChange(state);
    }
    public int[] getState() { return state.clone(); }
    protected boolean onStateChange(int[] state) { return false; }
    public final boolean setLevel(int value) { if (level == value) return false; level = value; return onLevelChange(value); }
    public final int getLevel() { return level; }
    protected boolean onLevelChange(int value) { return false; }
    public boolean setVisible(boolean value, boolean restart) { if (visible == value) return false; visible = value; invalidateSelf(); return true; }
    public final boolean isVisible() { return visible; }
    public int getIntrinsicWidth() { return -1; }
    public int getIntrinsicHeight() { return -1; }
    public int getMinimumWidth() { return Math.max(0, getIntrinsicWidth()); }
    public int getMinimumHeight() { return Math.max(0, getIntrinsicHeight()); }
    public boolean getPadding(Rect out) { out.set(0,0,0,0); return false; }
    public Drawable getCurrent() { return this; }
    public Drawable mutate() { return this; }
    public void setHotspot(float x,float y) { }
    public void setHotspotBounds(int left,int top,int right,int bottom) { }
    public void getHotspotBounds(Rect out) {out.set(getBounds());}
    public void jumpToCurrentState() { }
    public static int resolveOpacity(int first, int second) {
        if (first == second) return first;
        if (first == PixelFormat.UNKNOWN || second == PixelFormat.UNKNOWN) return PixelFormat.UNKNOWN;
        if (first == PixelFormat.TRANSLUCENT || second == PixelFormat.TRANSLUCENT) return PixelFormat.TRANSLUCENT;
        if (first == PixelFormat.TRANSPARENT || second == PixelFormat.TRANSPARENT) return PixelFormat.TRANSPARENT;
        return PixelFormat.OPAQUE;
    }
    public static Drawable createFromPath(String pathName) {
        if (pathName == null) return null;
        Bitmap bitmap = BitmapFactory.decodeFile(pathName);
        return bitmap == null ? null : new BitmapDrawable((android.content.res.Resources) null, bitmap);
    }
}
