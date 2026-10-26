// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.view.*;
import java.util.ArrayList;
public final class MultiTouchFixture {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static final class Target extends View {
  final ArrayList<String> events=new ArrayList<String>();Target(Context context){super(context);}
  public boolean onTouchEvent(MotionEvent event){String s=event.getActionMasked()+":"+event.getActionIndex()+":"+event.getPointerCount();for(int i=0;i<event.getPointerCount();i++)s+=":"+event.getPointerId(i)+"@"+(int)event.getX(i);events.add(s);return true;}
 }
 static final class Group extends ViewGroup {
  boolean intercept;int own;Group(Context context){super(context);}
  protected void onLayout(boolean changed,int l,int t,int r,int b){}
  public boolean onInterceptTouchEvent(MotionEvent event){return intercept;}
  public boolean onTouchEvent(MotionEvent event){own++;return true;}
 }
 static MotionEvent event(int action,int... ids){MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[ids.length];MotionEvent.PointerCoords[] points=new MotionEvent.PointerCoords[ids.length];for(int i=0;i<ids.length;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=ids[i];props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;points[i]=new MotionEvent.PointerCoords();points[i].x=ids[i]==3?20:120;points[i].y=20;points[i].pressure=1;}return MotionEvent.obtain(1,2,action,ids.length,props,points,0,0,1,1,0,0,0x1002,0);}
 static void send(Group group,int action,int...ids){MotionEvent e=event(action,ids);try{check(group.dispatchTouchEvent(e),"Unhandled touch");}finally{e.recycle();}}
 static void expected(Target target,String...events){check(target.events.equals(java.util.Arrays.asList(events)),"Events "+target.events+" expected "+java.util.Arrays.toString(events));}
 public static void check(Context context){
  Group root=new Group(context);Target left=new Target(context),right=new Target(context);root.addView(left);root.addView(right);root.layout(0,0,200,100);left.layout(0,0,100,100);right.layout(100,0,200,100);
  check(root.isMotionEventSplittingEnabled(),"Default splitting");
  send(root,0,3);send(root,5|(1<<8),3,9);send(root,2,3,9);send(root,6,3,9);send(root,1,9);
  expected(left,"0:0:1:3@20","2:0:1:3@20","2:0:1:3@20","1:0:1:3@20");expected(right,"0:0:1:9@20","2:0:1:9@20","2:0:1:9@20","1:0:1:9@20");
  left.events.clear();right.events.clear();root.setMotionEventSplittingEnabled(false);check(!root.isMotionEventSplittingEnabled(),"Disabled splitting");
  send(root,0,3);send(root,5|(1<<8),3,9);send(root,6,3,9);send(root,1,9);
  expected(left,"0:0:1:3@20","5:1:2:3@20:9@120","6:0:2:3@20:9@120","1:0:1:9@120");check(right.events.isEmpty(),"Unsplit pointer changed child");
  left.events.clear();right.events.clear();root.setMotionEventSplittingEnabled(true);
  send(root,0,3);send(root,5|(1<<8),3,9);left.setVisibility(View.INVISIBLE);send(root,2,3,9);send(root,3,3,9);
  expected(left,"0:0:1:3@20","2:0:1:3@20","2:0:1:3@20","3:0:1:3@20");expected(right,"0:0:1:9@20","2:0:1:9@20","3:0:1:9@20");
  left.setVisibility(View.VISIBLE);left.events.clear();right.events.clear();send(root,0,3);send(root,5|(1<<8),3,9);root.intercept=true;send(root,2,3,9);check(root.own>0,"Intercept not delivered to parent");send(root,3,3,9);root.intercept=false;
  check(left.events.get(left.events.size()-1).startsWith("3:"),"Left not cancelled");check(right.events.get(right.events.size()-1).startsWith("3:"),"Right not cancelled");
  left.events.clear();right.events.clear();right.setVisibility(View.INVISIBLE);left.layout(0,0,200,100);send(root,0,3);send(root,5|(1<<8),3,9);send(root,6|(1<<8),3,9);send(root,1,3);expected(left,"0:0:1:3@20","5:1:2:3@20:9@120","6:1:2:3@20:9@120","1:0:1:3@20");check(right.events.isEmpty(),"Hidden target received pointers");
  Group parentOwned=new Group(context);parentOwned.layout(0,0,200,100);send(parentOwned,0,3);Target late=new Target(context);parentOwned.addView(late);late.layout(0,0,200,100);send(parentOwned,5|(1<<8),3,9);send(parentOwned,3,3,9);check(late.events.isEmpty(),"Pointer down stole parent gesture");
  MotionEvent e=event(5|(1<<8),3,31),copy=MotionEvent.obtain(e);e.offsetLocation(5,7);check(copy.getX()==20&&e.getX(1)==125&&e.getRawX()==20,"Copy/offset/raw independence");check(e.findPointerIndex(31)==1&&e.getPointerId(1)==31,"Pointer identities");MotionEvent.PointerCoords point=new MotionEvent.PointerCoords();e.getPointerCoords(1,point);check(point.x==125,"Coordinate export");copy.recycle();e.recycle();
  try{event(0,3,3);throw new AssertionError("Duplicate pointer accepted");}catch(IllegalArgumentException expected){}
  try{event(5|(2<<8),3,9);throw new AssertionError("Invalid action index accepted");}catch(IllegalArgumentException expected){}
  System.out.println("MULTITOUCH_SPLIT_CANCEL_OK");
 }
}
