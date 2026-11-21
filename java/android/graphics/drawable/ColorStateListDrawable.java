// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;

public class ColorStateListDrawable extends Drawable implements Drawable.Callback {
    private ColorDrawable colorDrawable;
    private ColorStateListDrawableState state;
    private boolean mutated;

    public ColorStateListDrawable() {
        state = new ColorStateListDrawableState();
        initializeColorDrawable();
    }

    public ColorStateListDrawable(ColorStateList colorStateList) {
        if (colorStateList == null) {
            throw new NullPointerException("colorStateList");
        }
        state = new ColorStateListDrawableState();
        initializeColorDrawable();
        setColorStateList(colorStateList);
    }

    private ColorStateListDrawable(ColorStateListDrawableState state) {
        this.state = state;
        initializeColorDrawable();
        onStateChange(getState());
    }

    private void initializeColorDrawable() {
        colorDrawable = new ColorDrawable();
        colorDrawable.setCallback(this);
        if (state.tint != null) {
            colorDrawable.setTintList(state.tint);
        }
        if (state.blendMode != null && state.blendMode != BlendMode.SRC_IN) {
            colorDrawable.setTintMode(state.blendMode.shirazPorterDuff());
        }
    }

    @Override
    public void draw(Canvas canvas) {
        colorDrawable.draw(canvas);
    }

    @Override
    public int getAlpha() {
        return colorDrawable.getAlpha();
    }

    @Override
    public boolean isStateful() {
        return state.isStateful();
    }

    @Override
    public boolean hasFocusStateSpecified() {
        return state.hasFocusStateSpecified();
    }

    @Override
    public Drawable getCurrent() {
        return colorDrawable;
    }

    @Override
    public void applyTheme(Resources.Theme t) {
        super.applyTheme(t);
        if (state.color != null && state.color.canApplyTheme()) {
            setColorStateList(state.color.obtainForTheme(t));
        }
        if (state.tint != null && state.tint.canApplyTheme()) {
            setTintList(state.tint.obtainForTheme(t));
        }
    }

    @Override
    public boolean canApplyTheme() {
        return super.canApplyTheme() || state.canApplyTheme();
    }

    @Override
    public void setAlpha(int alpha) {
        state.alpha = alpha;
        onStateChange(getState());
    }

    public void clearAlpha() {
        state.alpha = -1;
        onStateChange(getState());
    }

    @Override
    public void setTintList(ColorStateList tint) {
        state.tint = tint;
        colorDrawable.setTintList(tint);
        onStateChange(getState());
    }

    @Override
    public void setTintBlendMode(BlendMode blendMode) {
        if (blendMode == null) {
            throw new NullPointerException("blendMode");
        }
        state.blendMode = blendMode;
        colorDrawable.setTintMode(blendMode.shirazPorterDuff());
        onStateChange(getState());
    }

    @Override
    public ColorFilter getColorFilter() {
        return colorDrawable.getColorFilter();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        colorDrawable.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return colorDrawable.getOpacity();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        colorDrawable.setBounds(bounds);
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        if (state.color != null) {
            int color = state.color.getColorForState(stateSet, state.color.getDefaultColor());
            if (state.alpha != -1) {
                int a = Math.max(0, Math.min(255, state.alpha));
                color = (color & 0x00FFFFFF) | (a << 24);
            }
            if (color != colorDrawable.getColor()) {
                colorDrawable.setColor(color);
                colorDrawable.setState(stateSet);
                return true;
            }
            return colorDrawable.setState(stateSet);
        }
        return false;
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        if (who == colorDrawable) {
            invalidateSelf();
        }
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        if (who == colorDrawable) {
            scheduleSelf(what, when);
        }
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        if (who == colorDrawable) {
            unscheduleSelf(what);
        }
    }

    @Override
    public ConstantState getConstantState() {
        state.changingConfigurations |=
                getChangingConfigurations() & ~state.getChangingConfigurations();
        return state;
    }

    public ColorStateList getColorStateList() {
        if (state.color == null) {
            return ColorStateList.valueOf(colorDrawable.getColor());
        }
        return state.color;
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations() | state.getChangingConfigurations();
    }

    @Override
    public Drawable mutate() {
        if (!mutated && super.mutate() == this) {
            state = new ColorStateListDrawableState(state);
            mutated = true;
        }
        return this;
    }

    public void setColorStateList(ColorStateList colorStateList) {
        if (colorStateList == null) {
            throw new NullPointerException("colorStateList");
        }
        state.color = colorStateList;
        onStateChange(getState());
    }

    private static final class ColorStateListDrawableState extends ConstantState {
        ColorStateList color;
        ColorStateList tint;
        int alpha = -1;
        BlendMode blendMode = BlendMode.SRC_IN;
        int changingConfigurations;

        ColorStateListDrawableState() {
        }

        ColorStateListDrawableState(ColorStateListDrawableState other) {
            color = other.color;
            tint = other.tint;
            alpha = other.alpha;
            blendMode = other.blendMode;
            changingConfigurations = other.changingConfigurations;
        }

        @Override
        public Drawable newDrawable() {
            return new ColorStateListDrawable(this);
        }

        @Override
        public int getChangingConfigurations() {
            return changingConfigurations;
        }

        boolean isStateful() {
            return (color != null && color.isStateful())
                    || (tint != null && tint.isStateful());
        }

        boolean hasFocusStateSpecified() {
            if (color != null && color.isStateful()) {
                int base = color.getColorForState(new int[0], color.getDefaultColor());
                if (color.getColorForState(new int[] {16842908}, base) != base) {
                    return true;
                }
            }
            if (tint != null && tint.isStateful()) {
                int base = tint.getColorForState(new int[0], tint.getDefaultColor());
                if (tint.getColorForState(new int[] {16842908}, base) != base) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canApplyTheme() {
            return (color != null && color.canApplyTheme())
                    || (tint != null && tint.canApplyTheme());
        }
    }
}
