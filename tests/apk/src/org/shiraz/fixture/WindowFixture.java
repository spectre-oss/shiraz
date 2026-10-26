// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import java.lang.reflect.*;
public final class WindowFixture {
 private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static class Sample extends Activity {int contents;public Sample(Context context){attachBaseContext(context);}public void onContentChanged(){contents++;}}
 public static void check(Context context) {
  final Sample activity=new Sample(context);final Window window=activity.getWindow();
  check(window==activity.getWindow()&&window.getContext()==activity&&window.getCallback()==activity&&window.peekDecorView()==null,"window ownership/lazy decor");
  check(activity.requestWindowFeature(Window.FEATURE_NO_TITLE)&&window.hasFeature(Window.FEATURE_NO_TITLE),"window feature");
  window.setNavigationBarColor(0xff123456);window.setStatusBarColor(0xff654321);check(window.getNavigationBarColor()==0xff123456&&window.getStatusBarColor()==0xff654321,"system bar preferences");
  window.setBackgroundDrawable(new ColorDrawable(0xff0000ff));
  View content=new View(activity);content.setId(123);content.setBackgroundColor(0xffff0000);final int[] clicks={0},touches={0};content.setOnClickListener(new View.OnClickListener(){public void onClick(View v){clicks[0]++;}});
  activity.setContentView(content,new ViewGroup.LayoutParams(3,4));View decor=window.getDecorView();
  check(activity.contents==1&&decor==window.peekDecorView()&&window.findViewById(123)==content&&activity.findViewById(android.R.id.content) instanceof ViewGroup,"content container and callbacks");
  decor.measure(View.MeasureSpec.makeMeasureSpec(10,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(20,View.MeasureSpec.EXACTLY));decor.layout(0,0,10,20);
  Bitmap bitmap=Bitmap.createBitmap(10,20,Bitmap.Config.ARGB_8888);decor.draw(new Canvas(bitmap));check(bitmap.getPixel(2,3)==0xffff0000&&bitmap.getPixel(9,19)==0xff0000ff,"decor background/content pixels");bitmap.recycle();
  final Window.Callback original=window.getCallback();
  Window.Callback wrapped=(Window.Callback)Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class<?>[]{Window.Callback.class},new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args)throws Throwable{if(method.getName().equals("dispatchTouchEvent"))touches[0]++;try{return method.invoke(original,args);}catch(InvocationTargetException e){throw e.getCause();}}});
  window.setCallback(wrapped);for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent event=MotionEvent.obtain(1,2,action,1,1,0);try{check(window.getCallback().dispatchTouchEvent(event),"window touch dispatch");}finally{event.recycle();}}
  check(clicks[0]==1&&touches[0]==2,"wrapped callback routes actual content click");
  View extra=new View(activity);window.addContentView(extra,new ViewGroup.LayoutParams(1,1));check(activity.contents==2,"add-content callback");
  try{window.requestFeature(Window.FEATURE_NO_TITLE);throw new AssertionError("late feature accepted");}catch(IllegalStateException expected){}
  View replacement=new View(activity);window.setContentView(replacement);check(content.getParent()==null&&extra.getParent()==null&&activity.contents==3,"content replacement");
  System.out.println("SHIRAZ_FIXTURE_WINDOW_OK");
 }
}
