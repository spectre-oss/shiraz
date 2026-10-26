// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;import android.graphics.*;import android.graphics.drawable.*;
public final class FocusHighlightSmoke {
 static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 static int pixel(View view){Bitmap b=Bitmap.createBitmap(10,10,Bitmap.Config.ARGB_8888);try{view.draw(new Canvas(b));return b.getPixel(5,5);}finally{b.recycle();}}
 public static void main(String[] args){
  android.os.Looper.prepareMainLooper();
  android.os.Parcel parcel=android.os.Parcel.obtain();
  KeyEvent original=new KeyEvent(10,20,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_G,2,KeyEvent.META_SHIFT_ON,7,34,8,0x101);original.writeToParcel(parcel,0);parcel.setDataPosition(0);KeyEvent copy=KeyEvent.CREATOR.createFromParcel(parcel);
  check(copy.getKeyCode()==KeyEvent.KEYCODE_G&&copy.getRepeatCount()==2&&copy.getMetaState()==KeyEvent.META_SHIFT_ON&&copy.getDownTime()==10&&copy.getEventTime()==20&&copy.getDeviceId()==7&&copy.getScanCode()==34&&copy.getFlags()==8&&copy.getSource()==0x101,"key IPC round trip");parcel.recycle();
  parcel=android.os.Parcel.obtain();new KeyEvent(30,"😀",-1,0).writeToParcel(parcel,0);parcel.setDataPosition(0);check(KeyEvent.CREATOR.createFromParcel(parcel).getCharacters().equals("😀"),"committed text IPC");parcel.recycle();
  KeyCharacterMap map=KeyCharacterMap.load(-1);KeyEvent[] generated=map.getEvents("gG!".toCharArray());
  StringBuilder typed=new StringBuilder();for(KeyEvent e:generated)if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getUnicodeChar()!=0)typed.append((char)e.getUnicodeChar());
  check(typed.toString().equals("gG!"),"key synthesis round trip");check(map.getEvents("😀".toCharArray())==null,"unmappable text uses IME fallback");

  int[] directions={View.FOCUS_LEFT,View.FOCUS_UP,View.FOCUS_RIGHT,View.FOCUS_DOWN,View.FOCUS_BACKWARD,View.FOCUS_FORWARD};
  int[] sounds={1,2,3,4,2,4};
  for(int i=0;i<directions.length;i++){check(SoundEffectConstants.getContantForFocusDirection(directions[i])==sounds[i],"focus sound");check(SoundEffectConstants.getConstantForFocusDirection(directions[i],true)==sounds[i]+4,"repeat sound");}
  try{SoundEffectConstants.getContantForFocusDirection(0);throw new AssertionError("invalid direction");}catch(IllegalArgumentException expected){}
  android.text.method.TextKeyListener listener=android.text.method.TextKeyListener.getInstance();
  android.text.SpannableStringBuilder input=new android.text.SpannableStringBuilder("a😀");android.text.Selection.setSelection(input,input.length());
  check(listener.onKeyDown(null,input,KeyEvent.KEYCODE_DEL,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DEL))&&input.toString().equals("a"),"Gecko deletion preserves Unicode");
  check(!listener.onKeyDown(null,input,KeyEvent.KEYCODE_G,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_G))&&input.toString().equals("a"),"physical key must not duplicate committed text");
  check(android.text.method.TextKeyListener.shouldCap(android.text.method.TextKeyListener.Capitalize.SENTENCES,"Hi. ",4),"sentence capitalization");
  android.text.method.TextKeyListener.clear(input);check(input.length()==0,"clear editable");
  final android.content.res.Resources r=android.content.res.Resources.shirazOpen(args[0],96,160);final android.content.res.Resources.Theme theme=r.newTheme();theme.applyStyle(r.getIdentifier("FocusHighlightFixture","style","org.shiraz.fixture"),true);
  final shiraz.runtime.HostWindowManager windows=new shiraz.runtime.HostWindowManager(r);
  android.content.Context c=new android.content.ContextWrapper(null){public Object getSystemService(String name){if(WINDOW_SERVICE.equals(name))return windows;return super.getSystemService(name);}public android.content.res.Resources getResources(){return r;}public android.content.res.Resources.Theme getTheme(){return theme;}};
  android.widget.FrameLayout root=new android.widget.FrameLayout(c),group=new android.widget.FrameLayout(c);View child=new View(c);root.addView(group);group.addView(child);root.layout(0,0,96,160);group.layout(10,20,80,100);child.layout(7,9,30,40);root.scrollTo(1,2);group.scrollTo(4,5);group.setTranslationX(2);group.setTranslationY(3);child.setTranslationX(1);child.setTranslationY(2);child.scrollTo(100,100);
  int[] point={-1,-1,123};child.getLocationOnScreen(point);check(point[0]==0&&point[1]==0,"detached coordinates");ViewTreeObserver positionObserver=ViewTreeObserver.shirazCreateWindowObserver();positionObserver.shirazSetWindowOrigin(100,200);root.shirazAttachTree(positionObserver);child.getLocationInWindow(point);check(point[0]==15&&point[1]==27&&point[2]==123,"nested layout scroll translation coordinates");child.getLocationOnScreen(point);check(point[0]==115&&point[1]==227,"logical window origin");root.shirazDetachTree();positionObserver.shirazKill();child.getLocationInWindow(point);check(point[0]==0&&point[1]==0,"coordinates after detach");try{child.getLocationOnScreen(new int[1]);throw new AssertionError("short coordinate output");}catch(IllegalArgumentException expected){}
  android.graphics.Rect visibleFrame=new android.graphics.Rect();child.getWindowVisibleDisplayFrame(visibleFrame);check(visibleFrame.left==0&&visibleFrame.top==0&&visibleFrame.right==96&&visibleFrame.bottom==160,"logical visible display bounds");
  View v=new View(c);check(v.getDisplay()==null,"detached view has no display");v.layout(0,0,10,10);v.setBackgroundColor(Color.RED);v.setFocusable(true);v.requestFocus();check(pixel(v)==Color.RED,"Detached highlight suppressed");
  ViewTreeObserver observer=ViewTreeObserver.shirazCreateWindowObserver();v.shirazAttachTree(observer);
  try{check(v.getDisplay().getRefreshRate()==60f,"pre-window nominal refresh rate");check(v.getDisplay()==windows.getDefaultDisplay()&&v.getDisplay().getWidth()==96&&v.getDisplay().getHeight()==160,"attached logical display");check(v.getDefaultFocusHighlightEnabled()&&pixel(v)==Color.GREEN,"Themed focused highlight");v.setDefaultFocusHighlightEnabled(false);check(pixel(v)==Color.RED,"Disable highlight");v.setDefaultFocusHighlightEnabled(true);check(pixel(v)==Color.GREEN,"Reenable highlight");
   StateListDrawable background=new StateListDrawable();background.addState(new int[]{16842908},new ColorDrawable(Color.BLUE));background.addState(new int[0],new ColorDrawable(Color.RED));v.setBackground(background);check(background.hasFocusStateSpecified()&&pixel(v)==Color.BLUE,"Background focus state suppresses fallback");v.clearFocus();check(pixel(v)==Color.RED,"Unfocused rendering");
  }finally{v.shirazDetachTree();check(v.getDisplay()==null,"detached display cleared");observer.shirazKill();r.shirazClose();}
  System.out.println("FOCUS_HIGHLIGHT_OK");
 }
}
