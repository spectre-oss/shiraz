// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
import android.graphics.Paint;
public class GenericMotionListenerSmoke {
 public static void main(String[] args){
  android.os.Looper.prepare();final int[] fallback={0},listened={0};
  View view=new View(new android.content.ContextWrapper(null)){public boolean onGenericMotionEvent(MotionEvent event){fallback[0]++;return true;}};
  MotionEvent event=MotionEvent.obtain(0,0,MotionEvent.ACTION_SCROLL,1,1,0);
  view.setOnGenericMotionListener(new View.OnGenericMotionListener(){public boolean onGenericMotion(View v,MotionEvent e){listened[0]++;return true;}});
  if(!view.dispatchGenericMotionEvent(event)||listened[0]!=1||fallback[0]!=0)throw new AssertionError("listener consumes");
  view.setEnabled(false);view.dispatchGenericMotionEvent(event);
  if(listened[0]!=1||fallback[0]!=1)throw new AssertionError("disabled listener");
  view.setEnabled(true);
  view.setOnGenericMotionListener(new View.OnGenericMotionListener(){public boolean onGenericMotion(View v,MotionEvent e){return false;}});
  view.dispatchGenericMotionEvent(event);
  if(fallback[0]!=2)throw new AssertionError("fallback");
  view.setOnGenericMotionListener(null);view.dispatchGenericMotionEvent(event);
  if(fallback[0]!=3)throw new AssertionError("listener removed");
  android.widget.FrameLayout root=new android.widget.FrameLayout(new android.content.ContextWrapper(null));
  View child=new View(new android.content.ContextWrapper(null));
  root.addView(child,new android.widget.FrameLayout.LayoutParams(50,50));
  root.layout(0,0,100,100);child.layout(20,10,70,60);root.scrollTo(2,3);
  final int[] hovered={0};
  child.setOnGenericMotionListener(new View.OnGenericMotionListener(){public boolean onGenericMotion(View target,MotionEvent motion){
   if(motion.getAction()!=MotionEvent.ACTION_HOVER_MOVE||motion.getSource()!=0x2002||motion.getX()!=14||motion.getY()!=17)throw new AssertionError("hover coordinates/source");
   hovered[0]++;return true;
  }});
  MotionEvent hover=MotionEvent.shirazObtain(0,1,MotionEvent.ACTION_HOVER_MOVE,32,24,0x2002);
  if(!root.dispatchGenericMotionEvent(hover)||hovered[0]!=1||hover.getX()!=32||hover.getY()!=24)throw new AssertionError("nested hover delivery");
  child.setVisibility(View.GONE);
  if(root.dispatchGenericMotionEvent(hover)||hovered[0]!=1)throw new AssertionError("hidden hover target");
  hover.recycle();
  Paint paint=new Paint();paint.setARGB(128,17,34,51);
  if(paint.getColor()!=0x80112233||paint.getAlpha()!=128)throw new AssertionError("ARGB channels");
  event.recycle();System.out.println("GENERIC_MOTION_LISTENER_OK");
 }
}
