// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.AttributeSet;
import android.view.View;

/** CPU image view with compiled XML attributes; affine image matrix rendering. */
public class ImageView extends View {
    public enum ScaleType { MATRIX, FIT_XY, FIT_START, FIT_CENTER, FIT_END, CENTER, CENTER_CROP, CENTER_INSIDE }
    private Drawable image;
    private int imageWidth=-1,imageHeight=-1;
    private ColorFilter colorFilter;
    private boolean filterSet;
    private android.content.res.ColorStateList tint;
    private PorterDuff.Mode tintMode;
    private boolean hasTint, hasTintMode;
    public void setImageTintList(android.content.res.ColorStateList value) { tint=value;hasTint=true;applyImageTint(); }
    public android.content.res.ColorStateList getImageTintList() { return tint; }
    public void setImageTintMode(PorterDuff.Mode value) { tintMode=value;hasTintMode=true;applyImageTint(); }
    public PorterDuff.Mode getImageTintMode() { return tintMode; }
    private void applyImageTint() {
        if(image==null||!hasTint&&!hasTintMode)return;
        image=image.mutate();if(hasTint)image.setTintList(tint);if(hasTintMode)image.setTintMode(tintMode);
        if(image.isStateful())image.setState(getDrawableState());invalidate();
    }
    private void readTint(Context context,AttributeSet attrs,int style,int resource,shiraz.runtime.XmlAttributes a) {
        if(!a.has("tint")&&!a.has("tintMode"))return;
        android.content.res.TypedArray values=context.obtainStyledAttributes(attrs,new int[]{0x01010121,0x010103fb},style,resource);
        try {
            if(a.has("tint")){setImageTintList(values.getColorStateList(0));setImageTintMode(PorterDuff.Mode.SRC_ATOP);}
            if(a.has("tintMode")){PorterDuff.Mode mode;switch(values.getInt(1,-1)){
                case -1:mode=null;break;case 3:mode=PorterDuff.Mode.SRC_OVER;break;case 5:mode=PorterDuff.Mode.SRC_IN;break;
                case 9:mode=PorterDuff.Mode.SRC_ATOP;break;case 14:mode=PorterDuff.Mode.MULTIPLY;break;
                case 15:mode=PorterDuff.Mode.SCREEN;break;case 16:mode=PorterDuff.Mode.ADD;break;
                default:throw new IllegalArgumentException("Image tint mode");}setImageTintMode(mode);}
        } finally { values.recycle(); }
    }
    private final Matrix imageMatrix=new Matrix();
    private ScaleType scaleType=ScaleType.FIT_CENTER;
    private boolean adjust, crop, alphaSet;
    private int maxWidth=Integer.MAX_VALUE, maxHeight=Integer.MAX_VALUE, alpha=255, level;
    public ImageView(Context context) { super(context); }
    public ImageView(Context context,AttributeSet attrs) { this(context,attrs,0,0); }
    public ImageView(Context context,AttributeSet attrs,int style) { this(context,attrs,style,0); }
    public ImageView(Context context,AttributeSet attrs,int style,int resource) {super(context,attrs,style,resource);if(attrs!=null||style!=0||resource!=0){shiraz.runtime.XmlAttributes a=new shiraz.runtime.XmlAttributes(context,attrs,style,resource);a.reject("baseline baselineAlignBottom");readTint(context,attrs,style,resource,a);if(a.has("src"))setImageDrawable(a.drawable("src"));int scale=a.integer("scaleType",3);if(scale<0||scale>=ScaleType.values().length)throw new IllegalArgumentException("Image scale type");setScaleType(ScaleType.values()[scale]);setAdjustViewBounds(a.bool("adjustViewBounds",false));setCropToPadding(a.bool("cropToPadding",false));setMaxWidth(a.dimension("maxWidth",Integer.MAX_VALUE,false));setMaxHeight(a.dimension("maxHeight",Integer.MAX_VALUE,false));}}
    public final void setColorFilter(int color,PorterDuff.Mode mode) { setColorFilter(new PorterDuffColorFilter(color,mode)); }
    public final void setColorFilter(int color) { setColorFilter(color,PorterDuff.Mode.SRC_ATOP); }
    public final void clearColorFilter() { setColorFilter((ColorFilter)null); }
    public ColorFilter getColorFilter() { return colorFilter; }
    public void setColorFilter(ColorFilter value) {
        ColorFilter.shirazValidate(value); if (image != null) image.setColorFilter(value);
        colorFilter=value; filterSet=true; invalidate();
    }
    public Drawable getDrawable() { return image; }
    public void setImageDrawable(Drawable value) {
        if (image == value) return;
        if (image != null) { image.setCallback(null); unscheduleDrawable(image); }
        int oldWidth=imageWidth,oldHeight=imageHeight;image=value;
        if (value != null) {
            value.setCallback(this); value.setVisible(getVisibility() == VISIBLE,false); value.setState(getDrawableState());
            value.setLevel(level); if (alphaSet) value.setAlpha(alpha); if (filterSet) value.setColorFilter(colorFilter);
        }
        applyImageTint();imageWidth=image==null?-1:image.getIntrinsicWidth();imageHeight=image==null?-1:image.getIntrinsicHeight();if(oldWidth!=imageWidth||oldHeight!=imageHeight)requestLayout(); invalidate();
    }
    public void setImageBitmap(Bitmap value) { setImageDrawable(value == null ? null : new BitmapDrawable(getResources(),value)); }
    public void setImageResource(int id) { setImageDrawable(id == 0 ? null : getContext().getDrawable(id)); }
    public void setImageLevel(int value) { level=value; if (image != null && image.setLevel(value)) { requestLayout(); invalidate(); } }
    public void setImageAlpha(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("image alpha");
        if (image != null) image.setAlpha(value); alpha=value; alphaSet=true; invalidate();
    }
    public int getImageAlpha() { return alpha; }
    public void setAlpha(int value) { setImageAlpha(value); }
    public ScaleType getScaleType() { return scaleType; }
    public void setScaleType(ScaleType value) {
        if (value == null) throw new NullPointerException("scale type");
        if (scaleType != value) { scaleType=value; requestLayout(); invalidate(); }
    }
    public void setImageMatrix(Matrix value) {
        Matrix next=value==null?new Matrix():new Matrix(value);float[] v=new float[9];next.getValues(v);
        if(v[6]!=0||v[7]!=0||v[8]!=1)throw new UnsupportedOperationException("Perspective image matrices are not implemented");
        if(!imageMatrix.equals(next)){imageMatrix.set(next);invalidate();}
    }
    public Matrix getImageMatrix() {
        int w=add(getWidth(),-(long)getPaddingLeft()-getPaddingRight()),h=add(getHeight(),-(long)getPaddingTop()-getPaddingBottom());
        return imageTransform(w,h);
    }
    private Matrix imageTransform(int availableW,int availableH) {
        Matrix result=new Matrix();if(image==null)return result;
        int iw=image.getIntrinsicWidth(),ih=image.getIntrinsicHeight();if(iw<=0||ih<=0||scaleType==ScaleType.FIT_XY)return result;
        if(scaleType==ScaleType.MATRIX)return new Matrix(imageMatrix);
        float sx=1,sy=1,dx=0,dy=0;
        if(scaleType==ScaleType.CENTER){dx=Math.round((availableW-iw)*.5f);dy=Math.round((availableH-ih)*.5f);}
        else {
            float scale=scaleType==ScaleType.CENTER_CROP?Math.max((float)availableW/iw,(float)availableH/ih):Math.min((float)availableW/iw,(float)availableH/ih);
            if(scaleType==ScaleType.CENTER_INSIDE)scale=Math.min(1,scale);sx=sy=scale;
            if(scaleType!=ScaleType.FIT_START){float factor=scaleType==ScaleType.FIT_END?1:.5f;dx=(availableW-iw*scale)*factor;dy=(availableH-ih*scale)*factor;if(scaleType==ScaleType.CENTER_CROP||scaleType==ScaleType.CENTER_INSIDE){dx=Math.round(dx);dy=Math.round(dy);}}
        }
        result.setScale(sx,sy);result.postTranslate(dx,dy);return result;
    }
    public boolean getAdjustViewBounds() { return adjust; }
    public void setAdjustViewBounds(boolean value) { adjust=value; if (value) setScaleType(ScaleType.FIT_CENTER); requestLayout(); }
    public int getMaxWidth() { return maxWidth; }
    public int getMaxHeight() { return maxHeight; }
    public void setMaxWidth(int value) { if (value < 0) throw new IllegalArgumentException("max width"); maxWidth=value; requestLayout(); }
    public void setMaxHeight(int value) { if (value < 0) throw new IllegalArgumentException("max height"); maxHeight=value; requestLayout(); }
    public boolean getCropToPadding() { return crop; }
    public void setCropToPadding(boolean value) { crop=value; invalidate(); }
    protected boolean verifyDrawable(Drawable value) { return value == image || super.verifyDrawable(value); }
    protected void drawableStateChanged() { super.drawableStateChanged(); if (image != null && image.isStateful()) image.setState(getDrawableState()); }
    public void setVisibility(int value) { super.setVisibility(value); if (image != null) image.setVisible(value == VISIBLE,false); }
    public void invalidateDrawable(Drawable value) { super.invalidateDrawable(value); if (image!=null&&value == image) {int width=image.getIntrinsicWidth(),height=image.getIntrinsicHeight();if(width!=imageWidth||height!=imageHeight){imageWidth=width;imageHeight=height;requestLayout();}} }
    private static int add(long size,long padding) { return (int)Math.min(0x00ffffffL,Math.max(0L,size+padding)); }
    private static int adjustedSize(int desired,int maximum,int spec) {
        int mode=MeasureSpec.getMode(spec), limit=MeasureSpec.getSize(spec);
        return mode == MeasureSpec.EXACTLY ? limit : mode == MeasureSpec.AT_MOST ? Math.min(desired,Math.min(maximum,limit)) : Math.min(desired,maximum);
    }
    protected void onMeasure(int widthSpec,int heightSpec) {
        int iw=image == null ? 0 : Math.max(1,image.getIntrinsicWidth()), ih=image == null ? 0 : Math.max(1,image.getIntrinsicHeight());
        long hp=(long)getPaddingLeft()+getPaddingRight(), vp=(long)getPaddingTop()+getPaddingBottom();
        int dw=Math.max(add(iw,hp),getSuggestedMinimumWidth()), dh=Math.max(add(ih,vp),getSuggestedMinimumHeight());
        if (!adjust || image == null) { setMeasuredDimension(resolveSizeAndState(dw,widthSpec,0),resolveSizeAndState(dh,heightSpec,0)); return; }
        boolean resizeW=MeasureSpec.getMode(widthSpec) != MeasureSpec.EXACTLY, resizeH=MeasureSpec.getMode(heightSpec) != MeasureSpec.EXACTLY;
        int w=adjustedSize(dw,maxWidth,widthSpec), h=adjustedSize(dh,maxHeight,heightSpec);
        double ratio=(double)iw/ih;
        if (resizeW) {
            int candidate=add((int)Math.min(0x00ffffff,Math.max(0,h-vp)*ratio),hp);
            if (!resizeH) w=adjustedSize(candidate,maxWidth,widthSpec);
            if (candidate <= w) w=candidate;
        }
        if (resizeH && Math.abs((double)Math.max(0,w-hp)/Math.max(1,h-vp)-ratio) > .0000001) {
            int candidate=add((int)Math.min(0x00ffffff,Math.max(0,w-hp)/ratio),vp);
            if (!resizeW) h=adjustedSize(candidate,maxHeight,heightSpec);
            if (candidate <= h) h=candidate;
        }
        setMeasuredDimension(w,h);
    }
    protected void onDraw(Canvas canvas) {
        if (image == null) return;
        int availableW=add(getWidth(),-(long)getPaddingLeft()-getPaddingRight()), availableH=add(getHeight(),-(long)getPaddingTop()-getPaddingBottom());
        if (availableW == 0 || availableH == 0) return;
        int iw=image.getIntrinsicWidth(), ih=image.getIntrinsicHeight();
        int saved=canvas.save();
        try {
            canvas.clipRect(0,0,getWidth(),getHeight());
            if (crop) canvas.clipRect(getPaddingLeft(),getPaddingTop(),(float)((long)getWidth()-getPaddingRight()),(float)((long)getHeight()-getPaddingBottom()));
            canvas.translate(getPaddingLeft(),getPaddingTop());
            if (iw <= 0 || ih <= 0 || scaleType == ScaleType.FIT_XY) image.setBounds(0,0,availableW,availableH);
            else {
                image.setBounds(0,0,iw,ih);
                Matrix transform=imageTransform(availableW,availableH);float[] v=new float[9];transform.getValues(v);
                // Collapsed image animations have no visible area.
                if((double)v[0]*v[4]-(double)v[1]*v[3]==0)return;
                canvas.concat(transform);
            }
            image.draw(canvas);
        } finally { canvas.restoreToCount(saved); }
    }
}
