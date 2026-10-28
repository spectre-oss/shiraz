// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
public final class ObscuredTouchSmoke {
 static MotionEvent event(int flags) {
  MotionEvent.PointerProperties p=new MotionEvent.PointerProperties();p.id=0;p.toolType=MotionEvent.TOOL_TYPE_FINGER;
  MotionEvent.PointerCoords c=new MotionEvent.PointerCoords();c.x=10;c.y=10;c.pressure=1;c.size=1;
  return MotionEvent.obtain(0,1,MotionEvent.ACTION_DOWN,1,new MotionEvent.PointerProperties[]{p},new MotionEvent.PointerCoords[]{c},0,0,1,1,0,0,0x1002,flags);
 }
 public static void main(String[] args) {
  android.content.Context context=new android.content.ContextWrapper(null){public String getPackageName(){return "org.shiraz.fixture";}public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  final int[] calls={0};
  View view=new View(context){public boolean onTouchEvent(MotionEvent e){calls[0]++;return true;}};
  MotionEvent hidden=event(MotionEvent.FLAG_WINDOW_IS_OBSCURED),visible=event(0),partial=event(MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED);
  if(view.getFilterTouchesWhenObscured()||!view.dispatchTouchEvent(hidden))throw new AssertionError("default");
  view.setFilterTouchesWhenObscured(true);int before=calls[0];
  if(!view.getFilterTouchesWhenObscured()||view.dispatchTouchEvent(hidden)||calls[0]!=before)throw new AssertionError("obscured delivery");
  if(!view.dispatchTouchEvent(visible)||!view.dispatchTouchEvent(partial))throw new AssertionError("normal delivery");
  android.widget.FrameLayout group=new android.widget.FrameLayout(context);group.addView(view);group.layout(0,0,100,100);view.layout(0,0,100,100);view.setFilterTouchesWhenObscured(false);group.setFilterTouchesWhenObscured(true);before=calls[0];
  if(group.dispatchTouchEvent(hidden)||calls[0]!=before)throw new AssertionError("group filtering");
  hidden.recycle();visible.recycle();partial.recycle();System.out.println("OBSCURED_TOUCH_OK");
 }
}
