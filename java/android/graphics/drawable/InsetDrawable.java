// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.*;
/** Insets a child by pixels and/or fractions of its current outer bounds. */
public class InsetDrawable extends DrawableWrapper {
 private int[] pixels;
 private float[] fractions;
 public InsetDrawable(Drawable child,int inset){this(child,inset,inset,inset,inset);}
 public InsetDrawable(Drawable child,int left,int top,int right,int bottom){this(child,new int[]{left,top,right,bottom},new float[4]);}
 public InsetDrawable(Drawable child,float inset){this(child,inset,inset,inset,inset);}
 public InsetDrawable(Drawable child,float left,float top,float right,float bottom){this(child,new int[4],new float[]{left,top,right,bottom});}
 private InsetDrawable(Drawable child,int[] dimensions,float[] proportions){super(null);if(dimensions.length!=4||proportions.length!=4)throw new IllegalArgumentException("Inset edge count");for(int i=0;i<4;i++)if(dimensions[i]<-8192||dimensions[i]>8192||!Float.isFinite(proportions[i])||proportions[i]<-16||proportions[i]>=1)throw new IllegalArgumentException("Inset range");pixels=dimensions.clone();fractions=proportions.clone();setDrawable(child);}
 public static InsetDrawable shirazCreate(Drawable child,int[] pixels,float[] fractions){return new InsetDrawable(child,pixels,fractions);}
 private static int checked(long value){if(value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)throw new IllegalArgumentException("Inset geometry overflow");return (int)value;}
 private int edge(int i){Rect b=getBounds();long extent=(i%2==0?(long)b.right-b.left:(long)b.bottom-b.top);double value=extent*fractions[i];if(value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)throw new IllegalArgumentException("Inset fraction overflow");return checked((long)(int)value+pixels[i]);}
 @Override public void setDrawable(Drawable child){super.setDrawable(child);if(pixels!=null)onBoundsChange(getBounds());}
 @Override protected void onBoundsChange(Rect bounds){if(pixels==null)return;super.onBoundsChange(new Rect(checked((long)bounds.left+edge(0)),checked((long)bounds.top+edge(1)),checked((long)bounds.right-edge(2)),checked((long)bounds.bottom-edge(3))));}
 @Override public boolean getPadding(Rect out){boolean has=super.getPadding(out);int l=edge(0),t=edge(1),r=edge(2),b=edge(3);out.set(checked((long)out.left+l),checked((long)out.top+t),checked((long)out.right+r),checked((long)out.bottom+b));return has||(l|t|r|b)!=0;}
 private int intrinsic(int child,int a,int b){float sum=fractions[a]+fractions[b];if(child<0||sum>=1)return -1;double value=child/(1.0-sum);if(value>Integer.MAX_VALUE)throw new IllegalArgumentException("Inset intrinsic overflow");return checked((long)(int)value+pixels[a]+pixels[b]);}
 @Override public int getIntrinsicWidth(){return intrinsic(super.getIntrinsicWidth(),0,2);}
 @Override public int getIntrinsicHeight(){return intrinsic(super.getIntrinsicHeight(),1,3);}
 @Override public int getOpacity(){int opacity=super.getOpacity();return opacity==PixelFormat.OPAQUE&&(edge(0)>0||edge(1)>0||edge(2)>0||edge(3)>0)?PixelFormat.TRANSLUCENT:opacity;}
}
