// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.FrameLayout;
public final class ForegroundInvalidationSmoke {
 static final class Foreground extends Drawable {
  int padding,minimum;
  public void draw(Canvas c){}
  public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
  public boolean isStateful(){return true;}
  protected boolean onStateChange(int[] state){invalidateSelf();return true;}
  public boolean getPadding(Rect out){out.set(padding,padding,padding,padding);return padding!=0;}
  public int getMinimumWidth(){return minimum;}public int getMinimumHeight(){return minimum;}
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void layout(View root){int exact=View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY);root.measure(exact,exact);root.layout(0,0,100,100);}
 public static void main(String[] args){
  Context c=new ContextWrapper(null);FrameLayout root=new FrameLayout(c);View button=new View(c);root.addView(button,new FrameLayout.LayoutParams(100,100));
  Foreground foreground=new Foreground();button.setForeground(foreground);final int[] clicks={0};button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){clicks[0]++;}});layout(root);
  MotionEvent down=MotionEvent.obtain(1,1,MotionEvent.ACTION_DOWN,40,40,0),up=MotionEvent.obtain(1,2,MotionEvent.ACTION_UP,40,40,0);
  try{check(root.dispatchTouchEvent(down),"down not handled");check(!root.isLayoutRequested(),"paint-only pressed foreground requested layout");check(root.dispatchTouchEvent(up)&&clicks[0]==1,"click lost");}
  finally{down.recycle();up.recycle();}
  check(!root.isLayoutRequested(),"release foreground requested layout");
  foreground.invalidateSelf();check(!root.isLayoutRequested(),"animation repaint requested layout");
  foreground.padding=7;foreground.invalidateSelf();check(root.isLayoutRequested(),"padding change ignored");layout(root);
  foreground.invalidateSelf();check(!root.isLayoutRequested(),"unchanged padding requested layout");
  foreground.minimum=120;foreground.invalidateSelf();check(root.isLayoutRequested(),"minimum size change ignored");layout(root);
  button.setForeground(new Foreground());check(root.isLayoutRequested(),"foreground replacement ignored");
  System.out.println("FOREGROUND_INVALIDATION_OK");
 }
}
