// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.method.*;
public final class TransformationFixture {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static Bitmap draw(TextView view){view.measure(View.MeasureSpec.makeMeasureSpec(180,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(50,View.MeasureSpec.EXACTLY));view.layout(0,0,180,50);Bitmap result=Bitmap.createBitmap(180,50,Bitmap.Config.ARGB_8888);view.draw(new Canvas(result));return result;}
 private static void equal(TextView actual,TextView expected){Bitmap a=draw(actual),b=draw(expected);try{for(int y=0;y<50;y++)for(int x=0;x<180;x++)check(a.getPixel(x,y)==b.getPixel(x,y),"Transformed text pixels");}finally{a.recycle();b.recycle();}}
 public static void check(Context context){
  TextView view=new TextView(context),expected=new TextView(context);view.setText("Street map");view.setAllCaps(true);expected.setText("STREET MAP");equal(view,expected);check(view.getText().toString().equals("Street map")&&view.getTransformationMethod()!=null,"All caps mutated source");view.setAllCaps(false);expected.setText("Street map");equal(view,expected);
  final String[] prefix={"A:"};final int[] focus={0,0};final Rect previous=new Rect(1,2,3,4);
  TransformationMethod custom=new TransformationMethod(){public CharSequence getTransformation(CharSequence source,View host){return prefix[0]+source;}public void onFocusChanged(View host,CharSequence source,boolean gain,int direction,Rect old){if(gain){check(direction==View.FOCUS_RIGHT&&previous.equals(old),"Focus metadata");focus[0]++;}else focus[1]++;}};
  view.setTransformationMethod(custom);expected.setText("A:Street map");equal(view,expected);prefix[0]="B:";expected.setText("B:Street map");equal(view,expected);check(view.getTransformationMethod()==custom,"Transformation identity");
  FrameLayout root=new FrameLayout(context);root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);root.addView(view);view.setFocusable(true);check(root.requestFocus(View.FOCUS_RIGHT,previous),"Child focus request");check(view.requestFocus()&&focus[0]==1,"Duplicate focus callback");root.clearFocus();check(focus[1]==1,"Focus loss callback");
  view.setText("mask");view.setTransformationMethod(PasswordTransformationMethod.getInstance());expected.setText("\u2022\u2022\u2022\u2022");equal(view,expected);check(view.getText().toString().equals("mask"),"Mask changed stored text");android.view.accessibility.AccessibilityNodeInfo node=view.createAccessibilityNodeInfo();check(node.isPassword()&&node.getText().toString().equals(expected.getText().toString()),"Masked accessibility node");node.recycle();android.view.accessibility.AccessibilityEvent event=android.view.accessibility.AccessibilityEvent.obtain();view.onInitializeAccessibilityEvent(event);view.onPopulateAccessibilityEvent(event);check(event.isPassword()&&!event.getText().contains("mask"),"Masked accessibility event");event.recycle();
  view.setTransformationMethod(null);expected.setText("mask");equal(view,expected);view.setText("");view.setHint("Hint");view.setAllCaps(true);expected.setText("");expected.setHint("Hint");equal(view,expected);
  System.out.println("TEXT_TRANSFORMATION_PIXELS_FOCUS_OK");
 }
}
