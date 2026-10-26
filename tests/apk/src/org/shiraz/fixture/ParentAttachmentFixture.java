// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;
public final class ParentAttachmentFixture {
 public static class Group extends FrameLayout {
  public int added,removed;
  public Group(Context context){super(context);}
  public void attach(View child,int index){attachViewToParent(child,index,new FrameLayout.LayoutParams(20,20));}
  public void detach(View child){detachViewFromParent(child);}
  public void detachRange(int start,int count){detachViewsFromParent(start,count);}
  public void detachAll(){detachAllViewsFromParent();}
  public void discard(View child){removeDetachedView(child,false);}
  @Override public void onViewAdded(View child){added++;}
  @Override public void onViewRemoved(View child){removed++;}
 }
 public static class Child extends View {
  public int attached,detached,starts,finishes;public boolean failDetach;
  public Child(Context context){super(context);setBackgroundColor(android.graphics.Color.RED);}
  protected void onAttachedToWindow(){attached++;}
  protected void onDetachedFromWindow(){detached++;if(failDetach)throw new IllegalStateException("expected detach failure");}
  public void onStartTemporaryDetach(){super.onStartTemporaryDetach();if(!isTemporarilyDetached())throw new AssertionError("Start flag");starts++;}
  public void onFinishTemporaryDetach(){super.onFinishTemporaryDetach();if(isTemporarilyDetached())throw new AssertionError("Finish flag");finishes++;}
 }
 public static void check(Context context){Group group=new Group(context);group.layout(0,0,40,40);Child child=new Child(context);group.attach(child,-1);if(group.getChildAt(0)!=child||child.getParent()!=group||group.added!=0||group.isLayoutRequested())throw new AssertionError("Lightweight initial attachment");group.dispatchStartTemporaryDetach();group.dispatchFinishTemporaryDetach();if(child.starts!=1||child.finishes!=1)throw new AssertionError("Subtree temporary callbacks");group.detachAll();if(child.getParent()!=null||group.getChildCount()!=0||group.removed!=0)throw new AssertionError("Temporary structural removal");group.attach(child,0);group.detachRange(0,1);group.discard(child);if(group.removed!=1||child.getParent()!=null)throw new AssertionError("Permanent detached removal");System.out.println("PARENT_ATTACHMENT_STRUCTURE_OK");}
}
