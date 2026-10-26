// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.View;

public final class DrawableFixture {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class Owner implements Drawable.Callback {
        int invalidations, schedules, cancellations; Drawable who; Runnable task; long when;
        public void invalidateDrawable(Drawable value) { invalidations++; who = value; }
        public void scheduleDrawable(Drawable value, Runnable runnable, long time) { schedules++; who = value; task = runnable; when = time; }
        public void unscheduleDrawable(Drawable value, Runnable runnable) { cancellations++; who = value; task = runnable; }
    }
    private static final class Stateful extends ColorDrawable {
        int states, levels; Rect changed;
        public boolean isStateful() { return true; }
        protected boolean onStateChange(int[] state) { states++; invalidateSelf(); return true; }
        protected boolean onLevelChange(int level) { levels++; return true; }
        protected void onBoundsChange(Rect bounds) { changed = new Rect(bounds); }
    }
    public static void check() {
        ColorDrawable scaledChild=new ColorDrawable(Color.RED);ScaleDrawable scaled=new ScaleDrawable(scaledChild,android.view.Gravity.RIGHT,1,0);scaled.setBounds(0,0,20,10);Bitmap scaledPixels=Bitmap.createBitmap(20,10,Bitmap.Config.ARGB_8888);try{Canvas c=new Canvas(scaledPixels);scaled.draw(c);check(scaledPixels.getPixel(19,5)==0,"Zero scale level is hidden");scaled.setLevel(5000);check(scaledChild.getBounds().equals(new Rect(10,0,20,10)),"Half level changes bounds");scaled.draw(c);check(scaledPixels.getPixel(9,5)==0&&scaledPixels.getPixel(10,5)==Color.RED,"Scaled pixels and right gravity");scaled.setLevel(10000);check(scaledChild.getBounds().equals(new Rect(0,0,20,10)),"Full scale bounds");scaled.setLevel(2500);scaled.setBounds(0,0,40,12);check(scaledChild.getBounds().equals(new Rect(30,0,40,12)),"Resized scale bounds");ColorDrawable replacement=new ColorDrawable(Color.BLUE);scaled.setDrawable(replacement);check(replacement.getBounds().equals(new Rect(30,0,40,12))&&replacement.getLevel()==2500&&scaledChild.getCallback()==null,"Replacement inherits scale and level");ScaleDrawable rtl=new ScaleDrawable(new ColorDrawable(Color.RED),android.view.Gravity.START|android.view.Gravity.TOP,1,1);rtl.setBounds(0,0,20,20);rtl.setLevel(5000);rtl.setLayoutDirection(1);check(rtl.getDrawable().getBounds().equals(new Rect(10,0,20,10)),"RTL scale gravity");}finally{scaledPixels.recycle();}
        Bitmap target = Bitmap.createBitmap(6,4,Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(target);
        ColorDrawable color = new ColorDrawable(0x80ff0000); Owner owner = new Owner(); color.setCallback(owner);
        color.setBounds(1,1,4,3); check(owner.invalidations == 0, "empty previous bounds do not invalidate");
        color.draw(canvas); check(target.getPixel(0,1) == 0 && target.getPixel(1,1) == 0x80ff0000 && target.getPixel(4,1) == 0, "drawable rectangle crop");
        color.setAlpha(128); check(color.getAlpha() == 64 && color.getColor() == 0x40ff0000, "alpha modulates base color");
        color.setAlpha(255); check(color.getAlpha() == 128, "alpha replacement does not compound");
        color.setColor(Color.BLUE); check(color.getOpacity() == PixelFormat.OPAQUE && color.getAlpha() == 255, "setColor resets alpha");
        color.setAlpha(0); check(color.getOpacity() == PixelFormat.TRANSPARENT, "transparent color opacity");
        int invalidations = owner.invalidations; color.setBounds(new Rect(2,0,5,4));
        check(owner.invalidations == invalidations + 1, "old occupied bounds invalidated");
        Rect copy = color.copyBounds(); copy.setEmpty(); check(!color.getBounds().isEmpty(), "independent bounds copy");
        check(color.setVisible(false,false) && !color.setVisible(false,true) && !color.isVisible(), "visibility transitions");
        Runnable task = new Runnable() { public void run() { } }; color.scheduleSelf(task,123); color.unscheduleSelf(task);
        check(owner.schedules == 1 && owner.cancellations == 1 && owner.who == color && owner.task == task && owner.when == 123, "callback identity and absolute time");
        color.setCallback(null); invalidations = owner.invalidations; color.invalidateSelf(); color.scheduleSelf(task,456);
        check(owner.invalidations == invalidations && owner.schedules == 1, "detached callback");
        Stateful stateful = new Stateful(); int[] state = {1,2}; check(stateful.setState(state), "state callback"); state[0] = 9;
        check(stateful.getState()[0] == 1 && !stateful.setState(new int[]{1,2}) && stateful.states == 1, "stable state snapshot");
        check(stateful.setLevel(42) && !stateful.setLevel(42) && stateful.getLevel() == 42 && stateful.levels == 1, "level changes");
        stateful.setBounds(1,2,3,4); check(stateful.changed.equals(new Rect(1,2,3,4)), "bounds callback");
        Bitmap source = Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888); source.setPixel(0,0,Color.RED); source.setPixel(1,0,Color.BLUE);
        BitmapDrawable image = new BitmapDrawable(source); image.setFilterBitmap(false); image.setBounds(1,1,5,3); target.eraseColor(0); image.draw(canvas);
        check(target.getPixel(1,1) == Color.RED && target.getPixel(2,2) == Color.RED && target.getPixel(3,1) == Color.BLUE && target.getPixel(5,1) == 0, "scaled bitmap drawable pixels");
        check(image.getIntrinsicWidth() == 2 && image.getMinimumHeight() == 1 && image.getBitmap() == source, "bitmap intrinsic size");
        image.setAlpha(128); target.eraseColor(Color.BLUE); image.draw(canvas); check(target.getPixel(1,1) == 0xff80007f, "bitmap drawable alpha");
        try { image.setColorFilter(new ColorFilter()); throw new AssertionError("unsupported color filter accepted"); } catch (UnsupportedOperationException expected) { }
        check(image.getAlpha() == 128, "failed filter leaves state intact");
        BitmapDrawable empty = new BitmapDrawable((Bitmap)null); empty.draw(canvas); check(empty.getIntrinsicWidth() == -1 && empty.getOpacity() == PixelFormat.TRANSPARENT, "empty bitmap drawable");
        check(Drawable.resolveOpacity(PixelFormat.OPAQUE,PixelFormat.TRANSLUCENT) == PixelFormat.TRANSLUCENT, "opacity combination");
        System.out.println("SHIRAZ_FIXTURE_DRAWABLES_OK");
    }
    private static final class MaterialBackgroundView extends View {
        int modernCalls, legacyCalls;
        MaterialBackgroundView(Context context) { super(context); }
        @Override public void setBackground(Drawable drawable) { modernCalls++; setBackgroundDrawable(drawable); }
        @Override public void setBackgroundDrawable(Drawable drawable) { legacyCalls++; super.setBackgroundDrawable(drawable); }
    }
    public static void checkView(Context context) {
        android.widget.FrameLayout padded=new android.widget.FrameLayout(context);
        padded.setBackground(new android.graphics.drawable.InsetDrawable(new ColorDrawable(Color.WHITE),7,11,13,17));
        check(padded.getPaddingLeft()==7&&padded.getPaddingTop()==11&&padded.getPaddingRight()==13&&padded.getPaddingBottom()==17,"Background supplies content padding");
        View child=new View(context);padded.addView(child,new android.widget.FrameLayout.LayoutParams(-1,-1));
        padded.measure(View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(80,View.MeasureSpec.EXACTLY));padded.layout(0,0,100,80);
        check(child.getLeft()==7&&child.getTop()==11&&child.getRight()==87&&child.getBottom()==63,"Frame content respects background inset");
        padded.setBackground(null);check(padded.getPaddingLeft()==7,"Removing background preserves padding");
        padded.setPadding(1,2,3,4);padded.setBackground(new ColorDrawable(Color.RED));check(padded.getPaddingLeft()==1&&padded.getPaddingBottom()==4,"Paddingless background preserves explicit padding");
        MaterialBackgroundView material = new MaterialBackgroundView(context);
        ColorDrawable replacement = new ColorDrawable(Color.BLUE);
        material.setBackground(replacement);
        check(material.modernCalls == 1 && material.legacyCalls == 1 && material.getBackground() == replacement, "Material background dispatch terminates");
        material.setBackgroundDrawable(null);
        check(material.modernCalls == 1 && material.legacyCalls == 2 && replacement.getCallback() == null, "Legacy background replacement does not redispatch modern setter");
        final int[] legacy = {0};
        View compatibility = new View(context) { @Override public void setBackgroundDrawable(Drawable drawable) { legacy[0]++; super.setBackgroundDrawable(drawable); } };
        compatibility.setBackground(replacement);
        check(legacy[0] == 1 && compatibility.getBackground() == replacement, "Modern setter dispatches legacy override");
        compatibility.setBackground(null);
        View tinted = new View(context);tinted.layout(0,0,2,2);
        android.content.res.ColorStateList palette=new android.content.res.ColorStateList(new int[][]{new int[]{16842919},new int[]{}},new int[]{Color.RED,Color.BLUE});
        tinted.setBackgroundTintList(palette);tinted.setBackground(new ColorDrawable(Color.WHITE));
        Bitmap tintPixels=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);
        try{Canvas tintCanvas=new Canvas(tintPixels);tinted.draw(tintCanvas);check(tintPixels.getPixel(0,0)==Color.BLUE,"Background tint before drawable");
        tinted.setPressed(true);tinted.draw(tintCanvas);check(tintPixels.getPixel(0,0)==Color.RED,"Background tint pressed state");
        tinted.setBackground(new ColorDrawable(Color.GREEN));tinted.draw(tintCanvas);check(tintPixels.getPixel(0,0)==Color.RED,"Replacement inherits tint and state");
        tinted.setBackgroundTintMode(null);tinted.draw(tintCanvas);check(tintPixels.getPixel(0,0)==Color.GREEN,"Null tint mode clears effect");
        tinted.setBackgroundTintMode(PorterDuff.Mode.SRC_IN);tinted.setBackgroundTintList(null);tinted.draw(tintCanvas);check(tintPixels.getPixel(0,0)==Color.GREEN,"Null tint list clears effect");
        check(tinted.getBackgroundTintList()==null&&tinted.getBackgroundTintMode()==PorterDuff.Mode.SRC_IN,"Tint getters");
        }finally{tintPixels.recycle();}
        View view = new View(context); Stateful background = new Stateful(); background.setColor(Color.RED);
        view.layout(0,0,2,3); view.setBackground(background);
        check(view.getBackground() == background && background.getCallback() == view && background.getBounds().equals(new Rect(0,0,2,3)), "view background ownership");
        Bitmap pixels = Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888); view.draw(new Canvas(pixels));
        check(pixels.getPixel(1,2) == Color.RED && pixels.getPixel(2,2) == 0, "background restricted to local view bounds");
        int states = background.states; view.setPressed(true); check(background.states == states + 1, "pressed drawable state");
        view.layout(0,0,3,2); check(background.getBounds().equals(new Rect(0,0,3,2)), "background resize bounds");
        view.setVisibility(View.INVISIBLE); check(!background.isVisible(), "background visibility");
        final boolean[] stale = {false};
        background.scheduleSelf(new Runnable() { public void run() { stale[0] = true; } },0);
        view.setBackground(null); check(background.getCallback() == null, "background replacement detaches owner");
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() { public void run() {
            check(!stale[0], "replaced background must not execute queued animation");
            System.out.println("SHIRAZ_FIXTURE_DRAWABLE_DETACH_OK");
        } });
        System.out.println("SHIRAZ_FIXTURE_DRAWABLE_VIEW_OK");
    }
}
