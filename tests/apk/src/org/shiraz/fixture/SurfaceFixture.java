// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;
import android.graphics.*;
import java.util.ArrayList;
public final class SurfaceFixture {
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static FrameLayout prepare(Context context){
  FrameLayout root=new FrameLayout(context);root.setBackgroundColor(Color.BLUE);SurfaceView surface=new SurfaceView(context);root.addView(surface,new FrameLayout.LayoutParams(20,12));
  final ArrayList<String> events=new ArrayList<String>();surface.getHolder().addCallback(new SurfaceHolder.Callback2(){public void surfaceCreated(SurfaceHolder holder){check(holder.isCreating()&&holder.getSurface().isValid(),"Create validity");events.add("create");}public void surfaceChanged(SurfaceHolder holder,int format,int w,int h){check(holder.getSurfaceFrame().equals(new Rect(0,0,w,h)),"Callback dimensions");events.add("change:"+w+"x"+h);}public void surfaceDestroyed(SurfaceHolder holder){events.add("destroy");}public void surfaceRedrawNeeded(SurfaceHolder holder){events.add("redraw");}});
  root.setTag(events);root.layout(0,0,20,12);surface.layout(0,0,20,12);check(!surface.getHolder().getSurface().isValid()&&surface.getHolder().lockCanvas()==null,"Detached surface valid");return root;
 }
 private static int pixel(FrameLayout root,int x,int y){Bitmap bitmap=Bitmap.createBitmap(20,12,Bitmap.Config.ARGB_8888);try{root.draw(new Canvas(bitmap));return bitmap.getPixel(x,y);}finally{bitmap.recycle();}}
 public static void checkAttached(final FrameLayout root){
  SurfaceView view=(SurfaceView)root.getChildAt(0);final SurfaceHolder holder=view.getHolder();ArrayList<String> events=(ArrayList<String>)root.getTag();
  check(events.toString().equals("[create, change:20x12, redraw]"),"Initial lifecycle "+events);check(!holder.isCreating(),"Creating flag leaked");
  holder.setFormat(PixelFormat.RGBA_8888);Rect dirty=new Rect(2,2,3,3);Canvas canvas=holder.lockCanvas(dirty);check(dirty.equals(new Rect(0,0,20,12)),"Full dirty region");canvas.drawColor(Color.RED);check(pixel(root,0,0)==Color.BLUE,"Unposted frame visible");holder.unlockCanvasAndPost(canvas);check(pixel(root,0,0)==Color.RED,"Posted frame missing");try{canvas.drawColor(Color.GREEN);throw new AssertionError("Posted canvas writable");}catch(IllegalStateException expected){}
  canvas=holder.lockCanvas();canvas.drawColor(Color.GREEN);holder.setFixedSize(10,6);holder.unlockCanvasAndPost(canvas);check(pixel(root,0,0)==Color.BLUE,"Old-size frame published");check(holder.getSurfaceFrame().equals(new Rect(0,0,10,6)),"Fixed surface geometry");canvas=holder.lockCanvas();canvas.drawColor(Color.GREEN);holder.unlockCanvasAndPost(canvas);check(pixel(root,19,11)==Color.GREEN,"Fixed buffer not scaled to view");
  holder.setSizeFromLayout();check(holder.getSurfaceFrame().equals(new Rect(0,0,20,12)),"Layout surface geometry");view.layout(0,0,12,20);check(holder.getSurfaceFrame().equals(new Rect(0,0,12,20)),"Portrait resize geometry");view.layout(0,0,20,12);
  root.setVisibility(View.INVISIBLE);check(!holder.getSurface().isValid()&&holder.lockCanvas()==null&&events.get(events.size()-1).equals("destroy"),"Ancestor hide lifecycle");root.setVisibility(View.VISIBLE);check(holder.getSurface().isValid()&&events.get(events.size()-3).equals("create"),"Ancestor show lifecycle");
  final Throwable[] error={null};Thread producer=new Thread(new Runnable(){public void run(){try{Canvas drawing=holder.lockCanvas();drawing.drawColor(Color.RED);holder.unlockCanvasAndPost(drawing);}catch(Throwable failure){error[0]=failure;}}});producer.start();try{producer.join(3000);}catch(InterruptedException failure){throw new AssertionError(failure);}check(!producer.isAlive()&&error[0]==null&&pixel(root,1,1)==Color.RED,"Worker frame publication");
  final Canvas owned=holder.lockCanvas();Thread wrong=new Thread(new Runnable(){public void run(){try{holder.unlockCanvasAndPost(owned);error[0]=new AssertionError("Foreign unlock accepted");}catch(IllegalArgumentException expected){}}});wrong.start();try{wrong.join(3000);}catch(InterruptedException failure){throw new AssertionError(failure);}check(!wrong.isAlive()&&error[0]==null,"Canvas ownership");holder.unlockCanvasAndPost(owned);
  view.setWillNotDraw(true);check(pixel(root,1,1)==Color.RED,"willNotDraw suppressed surface composition");SurfaceView upper=new SurfaceView(root.getContext());upper.getHolder().setFormat(PixelFormat.RGBA_8888);root.addView(upper,new FrameLayout.LayoutParams(4,4));upper.layout(2,2,6,6);Canvas upperCanvas=upper.getHolder().lockCanvas();upperCanvas.drawColor(Color.GREEN);upper.getHolder().unlockCanvasAndPost(upperCanvas);check(pixel(root,3,3)==Color.GREEN&&pixel(root,1,1)==Color.RED,"Multiple surface composition");root.removeView(upper);check(!upper.getHolder().getSurface().isValid(),"Removed surface valid");
  holder.setFormat(PixelFormat.RGB_565);canvas=holder.lockCanvas();canvas.drawColor(0xff123456);holder.unlockCanvasAndPost(canvas);check(pixel(root,1,1)==0xff103452,"RGB565 quantization");
  Bitmap copy=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);try{copy.setPixels(new int[]{Color.RED,Color.GREEN,Color.BLUE,Color.WHITE},0,2,0,0,2,2);check(copy.getPixel(1,1)==Color.WHITE,"Bulk pixel write");try{copy.setPixels(new int[]{Color.BLACK},0,2,0,0,2,2);throw new AssertionError("Short pixel input accepted");}catch(ArrayIndexOutOfBoundsException expected){}check(copy.getPixel(1,1)==Color.WHITE,"Invalid pixel write mutated image");}finally{copy.recycle();}
  System.out.println("SURFACE_LIFECYCLE_PIXELS_RESIZE_OK");
 }
 public static void checkDetached(FrameLayout root){SurfaceHolder holder=((SurfaceView)root.getChildAt(0)).getHolder();check(!holder.getSurface().isValid()&&holder.lockCanvas()==null,"Detached surface still drawable");}
 public static void install(final View window){window.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View view){View prior=view.findFocus();FrameLayout root=prepare(view.getContext());((ViewGroup)view).addView(root);try{checkAttached(root);}finally{((ViewGroup)view).removeView(root);checkDetached(root);if(prior!=null)prior.requestFocus();}}public void onViewDetachedFromWindow(View view){}});}
}
