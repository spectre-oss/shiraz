// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;
public final class TranslationFixture {
 private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 private static boolean event(View root,int action,float x,float y){MotionEvent e=MotionEvent.obtain(0,1,action,x,y,0);try{return root.dispatchTouchEvent(e);}finally{e.recycle();}}
 public static void xml(Context context){View v=LayoutInflater.from(context).inflate(context.getResources().getIdentifier("translation","layout","org.shiraz.fixture"),null);check(v.getTranslationX()==2.5f&&v.getTranslationY()==-3.5f,"XML signed fractional translation");}
 public static void check(Context context){
  FrameLayout root=new FrameLayout(context);root.layout(0,0,60,60);
  View child=new View(context);root.addView(child);child.layout(10,10,20,20);child.setBackgroundColor(Color.RED);
  check(child.getMatrix().isIdentity(),"Layout bounds are outside local property matrix");
  child.setTranslationX(20.5f);child.setTranslationY(-4.5f);root.scrollTo(2,1);
  check(child.getLeft()==10&&child.getTop()==10&&child.getX()==30.5f&&child.getY()==5.5f,"Translation preserves layout bounds");
  Matrix matrix=child.getMatrix();float[] mapped={2.5f,2.5f};matrix.mapPoints(mapped);
  check(mapped[0]+child.getLeft()-root.getScrollX()==31&&mapped[1]+child.getTop()-root.getScrollY()==7,"Local matrix agrees with rendered and input position");
  Matrix inverse=new Matrix();check(matrix.invert(inverse),"Translation invertible");inverse.mapPoints(mapped);check(mapped[0]==2.5f&&mapped[1]==2.5f,"Inverse local transform");
  final float[] point={0,0};child.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View v,MotionEvent e){point[0]=e.getX();point[1]=e.getY();return true;}});
  Bitmap image=Bitmap.createBitmap(60,60,Bitmap.Config.ARGB_8888);
  try{root.draw(new Canvas(image));check(image.getPixel(31,7)==Color.RED&&image.getPixel(12,12)==0,"Translated pixels with parent scroll");
   check(!event(root,0,12,12),"Original position excluded");check(event(root,0,31,7),"Translated hit target");check(point[0]==2.5f&&point[1]==2.5f,"Inverse fractional coordinates");
   child.setTranslationX(25.5f);check(event(root,2,31,7)&&point[0]==-2.5f,"Gesture follows moving target");event(root,1,31,7);
   child.setX(15);child.setY(25);mapped[0]=mapped[1]=0;child.getMatrix().mapPoints(mapped);check(child.getMatrix()==matrix&&mapped[0]==5&&mapped[1]==15,"Stable matrix updates after property mutation");check(child.getTranslationX()==5&&child.getTranslationY()==15,"Absolute position setters");image.eraseColor(0);root.draw(new Canvas(image));check(image.getPixel(15,26)==Color.RED&&image.getPixel(31,7)==0,"Translation changes redraw");
   try{child.setTranslationX(Float.NaN);throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){}check(child.getX()==15,"Invalid translation is atomic");
  }finally{image.recycle();}
  System.out.println("TRANSLATION_PIXELS_INPUT_OK");
 }
}
