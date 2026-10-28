// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
import android.graphics.*;
public class ViewRotationSmoke {
 static class Group extends ViewGroup{Group(android.content.Context c){super(c);}protected void onLayout(boolean c,int l,int t,int r,int b){}}
 static class Child extends View{float x,y,raw;int touches;Child(android.content.Context c){super(c);}public boolean onTouchEvent(MotionEvent event){x=event.getX();y=event.getY();raw=event.getRawX();touches++;return true;}protected void onDraw(Canvas canvas){canvas.drawColor(Color.RED);}}
 static void near(float a,float b){if(Math.abs(a-b)>.01f)throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  android.os.Looper.prepare();android.content.Context context=new android.content.ContextWrapper(null);
  Group parent=new Group(context);Child child=new Child(context);parent.addView(child);parent.layout(0,0,100,100);child.layout(30,30,50,40);child.setPivotX(0);child.setPivotY(0);child.setScaleX(2);child.setRotation(90);child.setTranslationX(10);
  float[] point={5,3};child.getMatrix().mapPoints(point);near(point[0],7);near(point[1],10);
  MotionEvent event=MotionEvent.obtain(1,2,MotionEvent.ACTION_DOWN,37,40,0);if(!parent.dispatchTouchEvent(event))throw new AssertionError("rotated hit");near(child.x,5);near(child.y,3);near(child.raw,37);event.recycle();
  Bitmap image=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);parent.draw(new Canvas(image));if(image.getPixel(37,40)!=Color.RED||image.getPixel(45,35)==Color.RED)throw new AssertionError("rotated pixels");image.recycle();
  child.setRotation(-45);point=new float[]{5,3};child.getMatrix().mapPoints(point);event=MotionEvent.obtain(1,3,MotionEvent.ACTION_MOVE,30+point[0],30+point[1],0);parent.dispatchTouchEvent(event);near(child.x,5);near(child.y,3);event.recycle();
  child.setScaleX(0);event=MotionEvent.obtain(1,4,MotionEvent.ACTION_UP,37,40,0);parent.dispatchTouchEvent(event);event.recycle();
  System.out.println("VIEW_ROTATION_PIXELS_INPUT_OK");
 }
}
