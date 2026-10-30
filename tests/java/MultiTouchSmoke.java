// SPDX-License-Identifier: GPL-3.0-only
import android.view.MotionEvent;
import android.view.KeyEvent;
public final class MultiTouchSmoke {
 public static void main(String[] args){
  android.os.Looper.prepareMainLooper();
  MotionEvent event=MotionEvent.shirazObtain(10,20,MotionEvent.ACTION_DOWN,12.5f,30,0x2002);
  String description=event.toString();
  if(!description.startsWith("MotionEvent { action=ACTION_DOWN, actionButton=0, id[0]=0, x[0]=12.5, y[0]=30.0, toolType[0]=TOOL_TYPE_MOUSE")||!description.contains("buttonState=BUTTON_PRIMARY, metaState=0, flags=0x0")||!description.endsWith("eventTime=20, downTime=10, deviceId=0, source=0x2002 }"))throw new AssertionError("Motion diagnostic fields: "+description);
  if(!"ACTION_POINTER_UP(2)".equals(MotionEvent.actionToString(MotionEvent.ACTION_POINTER_UP|(2<<8)))||!"99".equals(MotionEvent.actionToString(99)))throw new AssertionError("action names");
  if(!"META_SHIFT_ON|META_CTRL_ON|0x80000000".equals(KeyEvent.metaStateToString(KeyEvent.META_SHIFT_ON|KeyEvent.META_CTRL_ON|0x80000000)))throw new AssertionError("modifier names");
  MotionEvent.PointerProperties property=new MotionEvent.PointerProperties();property.id=3;property.toolType=1;
  MotionEvent.PointerCoords point=new MotionEvent.PointerCoords();point.x=7;point.y=9;point.toolMajor=8;point.toolMinor=5;
  MotionEvent obscured=MotionEvent.obtain(10,20,0,1,new MotionEvent.PointerProperties[]{property},new MotionEvent.PointerCoords[]{point},0,0,1,1,2,0,0x1002,MotionEvent.FLAG_WINDOW_IS_OBSCURED);
  obscured.setActionButton(2);MotionEvent copy=MotionEvent.obtain(obscured),split=obscured.split(1<<3);
  if(split.getFlags()!=MotionEvent.FLAG_WINDOW_IS_OBSCURED||split.getActionButton()!=2||copy.getActionButton()!=2||split.getToolMajor()!=8||split.getToolMinor()!=5)throw new AssertionError("pointer copies preserve flags, button and geometry");
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}};
  org.shiraz.fixture.MultiTouchFixture.check(context);
 }
}
