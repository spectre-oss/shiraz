// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;
public final class ElevationFixture {
 private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void xml(Context context){View v=LayoutInflater.from(context).inflate(context.getResources().getIdentifier("elevation","layout","org.shiraz.fixture"),null);check(v.getElevation()==5&&v.getTranslationZ()==2.5f&&v.getZ()==7.5f,"XML elevation and translationZ");}
 public static void check(Context context){
  final int[] changes={0};FrameLayout root=new FrameLayout(context){public void onViewAdded(View child){changes[0]++;}public void onViewRemoved(View child){changes[0]++;}};root.setBackgroundColor(Color.WHITE);root.layout(0,0,100,100);
  View red=new View(context),blue=new View(context);red.setBackgroundColor(Color.RED);blue.setBackgroundColor(Color.BLUE);root.addView(red);root.addView(blue);red.layout(30,30,50,50);blue.layout(30,30,50,50);red.setOutlineProvider(ViewOutlineProvider.BOUNDS);blue.setOutlineProvider(ViewOutlineProvider.BOUNDS);red.setElevation(8);
  Bitmap image=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);try{Canvas canvas=new Canvas(image);root.draw(canvas);check(image.getPixel(40,40)==Color.RED,"Higher earlier child draws above later child");check(image.getPixel(26,40)!=Color.WHITE&&image.getPixel(0,0)==Color.WHITE,"Visible bounded shadow beyond child bounds");int near=image.getPixel(26,40)&255,far=image.getPixel(20,40)&255;check(near<far,"Shadow softens with distance");
   final int[] touched={0};red.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View v,MotionEvent event){touched[0]=1;return true;}});blue.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View v,MotionEvent event){touched[0]=2;return true;}});tap(root,40,40);check(touched[0]==1,"Hit test follows elevation");blue.setTranslationZ(12);root.draw(canvas);check(image.getPixel(40,40)==Color.BLUE,"TranslationZ changes visual stacking");tap(root,40,40);check(touched[0]==2,"Hit test follows translationZ");blue.setZ(3);check(blue.getTranslationZ()==3&&blue.getZ()==3,"setZ updates translation");red.setZ(3);root.draw(canvas);check(image.getPixel(40,40)==Color.BLUE,"Equal Z keeps child order");
   ViewGroup.LayoutParams originalParams=red.getLayoutParams();changes[0]=0;
   red.bringToFront();check(root.getChildAt(1)==red&&red.getParent()==root&&red.getLayoutParams()==originalParams&&changes[0]==0,"Reorder preserves ownership without remove/add events");root.draw(canvas);check(image.getPixel(40,40)==Color.RED,"bringToFront changes equal-Z rendering");tap(root,40,40);check(touched[0]==1,"bringToFront changes hit target");
   red.bringToFront();check(root.getChildCount()==2&&root.indexOfChild(red)==1,"Repeated front is stable");View detached=new View(context);detached.bringToFront();root.bringChildToFront(detached);check(root.getChildCount()==2,"Detached front is harmless");
   blue.setZ(9);root.draw(canvas);check(image.getPixel(40,40)==Color.BLUE,"Higher Z still overrides child order");root.bringChildToFront(blue);check(root.getChildAt(1)==blue,"Parent reorder API");
   red.setElevation(0);red.setTranslationZ(0);blue.setTranslationZ(0);root.draw(canvas);check(image.getPixel(26,40)==Color.WHITE,"Zero elevation removes shadow");red.setElevation(8);red.setAlpha(0);root.draw(canvas);check(image.getPixel(26,40)==Color.WHITE,"Invisible alpha removes shadow");red.setAlpha(1);red.setOutlineProvider(null);root.draw(canvas);check(image.getPixel(26,40)==Color.WHITE,"Null outline removes shadow");
   red.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),6);o.setAlpha(0);}});root.draw(canvas);check(image.getPixel(26,40)==Color.WHITE,"Transparent outline removes shadow");
   red.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}});red.layout(1,1,21,11);root.draw(canvas);check(image.getPixel(24,6)!=Color.WHITE,"Oval casts shadow");
   root.setPadding(10,10,10,10);root.setClipToPadding(true);red.layout(10,30,30,50);root.draw(canvas);check(image.getPixel(8,40)==Color.WHITE,"Parent padding clips shadow");
   float old=red.getElevation();try{red.setElevation(Float.NaN);throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){}check(red.getElevation()==old,"Invalid elevation leaves state intact");
  }finally{image.recycle();}
  System.out.println("ELEVATION_SHADOW_Z_INPUT_OK");
 }
 private static void tap(View root,float x,float y){MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,x,y,0);try{root.dispatchTouchEvent(down);root.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}}
}
