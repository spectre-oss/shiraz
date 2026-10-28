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
  Paint paint=new Paint();paint.setARGB(128,17,34,51);
  if(paint.getColor()!=0x80112233||paint.getAlpha()!=128)throw new AssertionError("ARGB channels");
  event.recycle();System.out.println("GENERIC_MOTION_LISTENER_OK");
 }
}
