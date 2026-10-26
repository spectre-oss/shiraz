// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Picture;
import android.graphics.PixelFormat;

public class PictureDrawable extends Drawable {
    private Picture picture;

    public PictureDrawable(Picture picture) { this.picture = picture; }

    public Picture getPicture() { return picture; }

    public void setPicture(Picture picture) { this.picture = picture; invalidateSelf(); }

    @Override public void draw(Canvas canvas) {
        if (picture == null) return;
        int save = canvas.save();
        canvas.clipRect(getBounds());
        canvas.translate(getBounds().left, getBounds().top);
        if (picture.getWidth() > 0 && picture.getHeight() > 0)
            canvas.scale(getBounds().width() / (float) picture.getWidth(), getBounds().height() / (float) picture.getHeight());
        picture.draw(canvas);
        canvas.restoreToCount(save);
    }

    @Override public int getIntrinsicWidth() { return picture == null ? -1 : picture.getWidth(); }

    @Override public int getIntrinsicHeight() { return picture == null ? -1 : picture.getHeight(); }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    @Override public void setAlpha(int alpha) { }

    @Override public void setColorFilter(ColorFilter colorFilter) { }
}
