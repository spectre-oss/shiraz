// SPDX-License-Identifier: GPL-3.0-only
import android.view.View;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.os.Looper;
public final class ViewAlphaOvershootSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  Looper.prepareMainLooper();View view=new View(new android.content.ContextWrapper(null));view.layout(0,0,8,8);view.setBackgroundColor(Color.RED);Bitmap image=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);
  view.setAlpha(1.25f);check(view.getAlpha()==1.25f,"preserve overshoot property");view.shirazDrawWithAlpha(canvas);check(image.getPixel(4,4)==Color.RED,"overshoot draws opaque");
  image.eraseColor(0);view.setAlpha(-.25f);view.shirazDrawWithAlpha(canvas);check(image.getPixel(4,4)==0,"undershoot draws transparent");
  view.setAlpha(1);view.setTransitionAlpha(1.25f);view.shirazDrawWithAlpha(canvas);check(image.getPixel(4,4)==Color.RED,"transition overshoot draws opaque");view.setTransitionAlpha(1);
  ObjectAnimator animator=ObjectAnimator.ofFloat(view,"alpha",0,1);animator.setInterpolator(new TimeInterpolator(){public float getInterpolation(float value){return value*2;}});animator.setCurrentFraction(.75f);check(view.getAlpha()==1.5f,"interpolated alpha overshoot");view.setElevation(2);view.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS);view.shirazDrawShadow(canvas);image.recycle();System.out.println("VIEW_ALPHA_OVERSHOOT_OK");
 }
}
