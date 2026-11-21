// SPDX-License-Identifier: GPL-3.0-only
import android.content.Context;
import android.content.ContextWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
public final class TemporaryDetachTouchSmoke {
 static final class Group extends FrameLayout {
  Group(Context c){super(c);}
  void detach(View child){detachViewFromParent(child);}
  void attach(View child){attachViewToParent(child,-1,child.getLayoutParams());}
  void discard(View child){removeDetachedView(child,false);}
 }
 static final class Button extends View {
  int cancels,clicks;
  Button(Context c){super(c);setOnClickListener(new OnClickListener(){public void onClick(View v){clicks++;}});}
  public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)cancels++;return super.onTouchEvent(e);}
 }
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void event(Group g,int action){MotionEvent e=MotionEvent.obtain(1,2,action,30,30,0);try{g.dispatchTouchEvent(e);}finally{e.recycle();}}
 public static void main(String[] args){
  Context c=new ContextWrapper(null);Group root=new Group(c);Button button=new Button(c);root.addView(button,new FrameLayout.LayoutParams(100,100));
  int spec=View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY);root.measure(spec,spec);root.layout(0,0,100,100);
  event(root,0);root.detach(button);check(button.cancels==0,"temporary detach canceled touch");root.attach(button);event(root,1);check(button.clicks==1,"reattached row lost click");
  event(root,0);root.detach(button);root.discard(button);check(button.cancels==1,"discard must cancel");event(root,1);check(button.clicks==1,"discarded row clicked");
  root.addView(button);button.layout(0,0,100,100);event(root,0);root.detach(button);event(root,1);check(button.cancels==2&&button.clicks==1,"unattached row accepted release");root.attach(button);
  event(root,0);root.detach(button);Group other=new Group(c);other.attach(button);check(button.cancels==3,"reparent must cancel old gesture");event(root,1);check(button.clicks==1,"reparented row clicked");
  System.out.println("TEMPORARY_DETACH_TOUCH_OK");
 }
}
