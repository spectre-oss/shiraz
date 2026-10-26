// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
public final class AccessibilityFixture {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static final class Capture extends FrameLayout {public int received;public AccessibilityEvent event;public Capture(Context c){super(c);}public boolean requestSendAccessibilityEvent(View child,AccessibilityEvent value){received++;if(event!=null)event.recycle();event=AccessibilityEvent.obtain(value);return true;}}
 public static FrameLayout prepare(Context context){
  final Capture root=new Capture(context);FrameLayout group=new FrameLayout(context);final TextView child=new TextView(context);child.setText("Route to Vienna");child.setContentDescription("Destination");child.setFocusable(true);child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);group.addView(child);root.addView(group);
  final int[] calls={0,0};child.setOnClickListener(new View.OnClickListener(){public void onClick(View v){calls[0]++;}});
  child.setAccessibilityDelegate(new View.AccessibilityDelegate(){public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){calls[1]++;super.onInitializeAccessibilityNodeInfo(host,info);info.getExtras().putString("delegate","present");info.setContentDescription("Delegate destination");}});
  root.setTag(new Object[]{child,calls,group});return root;
 }
 public static void checkAttached(FrameLayout frame){
  Capture root=(Capture)frame;Object[] values=(Object[])root.getTag();final TextView child=(TextView)values[0];int[] calls=(int[])values[1];FrameLayout group=(FrameLayout)values[2];
  root.layout(2,3,202,103);group.layout(5,6,105,86);child.layout(7,8,87,48);
  AccessibilityNodeInfo info=child.createAccessibilityNodeInfo();check(calls[1]==1,"delegate invoked once");check(info.getText().toString().equals("Route to Vienna")&&info.getContentDescription().toString().equals("Delegate destination")&&info.getExtras().getString("delegate").equals("present"),"delegate and widget metadata");
  check(info.isEnabled()&&info.isClickable()&&info.isFocusable()&&info.isVisibleToUser(),"live node flags");Rect bounds=new Rect();info.getBoundsInParent(bounds);check(bounds.equals(new Rect(7,8,87,48)),"parent bounds");info.getBoundsInScreen(bounds);check(bounds.equals(new Rect(14,17,94,57)),"virtual display bounds");
  check(info.performAction(AccessibilityNodeInfo.ACTION_CLICK)&&calls[0]==1,"click action");check(info.performAction(AccessibilityNodeInfo.ACTION_FOCUS)&&child.isFocused(),"focus action");check(info.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)&&!child.isFocused(),"clear focus action");
  check(info.performAction(AccessibilityNodeInfo.ACTION_SELECT)&&child.isSelected(),"selection action");boolean selected=false;for(int state:child.getDrawableState())selected|=state==16842913;check(selected,"selection drawable state");check(info.performAction(AccessibilityNodeInfo.ACTION_CLEAR_SELECTION)&&!child.isSelected(),"clear selection");
  child.setEnabled(false);check(!info.performAction(AccessibilityNodeInfo.ACTION_CLICK)&&calls[0]==1,"disabled action");child.setEnabled(true);check(!info.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD),"unsupported action reported success");
  AccessibilityNodeInfo.AccessibilityAction show=AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN;
  check(show.getId()==android.R.id.accessibilityActionShowOnScreen&&show.getLabel()==null,"Show-on-screen action identity");
  AccessibilityNodeInfo.AccessibilityAction[] standard={AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION,AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP,AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT,AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN,AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT,AccessibilityNodeInfo.AccessibilityAction.ACTION_CONTEXT_CLICK,AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS,AccessibilityNodeInfo.AccessibilityAction.ACTION_MOVE_WINDOW};
  int[] ids={android.R.id.accessibilityActionScrollToPosition,android.R.id.accessibilityActionScrollUp,android.R.id.accessibilityActionScrollLeft,android.R.id.accessibilityActionScrollDown,android.R.id.accessibilityActionScrollRight,android.R.id.accessibilityActionContextClick,android.R.id.accessibilityActionSetProgress,android.R.id.accessibilityActionMoveWindow};
  for(int i=0;i<ids.length;i++)check(standard[i].getId()==ids[i],"Standard action ID");
  int priorMask=info.getActions();info.addAction(show);check(info.getActions()==priorMask&&info.getActionList().contains(show),"Modern action excluded from legacy mask");
  info.addAction(new AccessibilityNodeInfo.AccessibilityAction(show.getId(),"Reveal"));int found=0;for(AccessibilityNodeInfo.AccessibilityAction action:info.getActionList())if(action.equals(show)){found++;check("Reveal".contentEquals(action.getLabel()),"Replacement action label");}check(found==1,"Action replacement deduplicated");
  check(!info.performAction(show.getId()),"Unavailable action does not claim success");check(info.removeAction(show),"Action removal by ID");
  check(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT.getId()==AccessibilityNodeInfo.ACTION_SET_TEXT&&AccessibilityNodeInfo.AccessibilityAction.ACTION_COPY.getId()==AccessibilityNodeInfo.ACTION_COPY,"Legacy action objects");
  AccessibilityNodeInfo copy=AccessibilityNodeInfo.obtain(info);info.getExtras().putString("delegate","changed");check(copy.getExtras().getString("delegate").equals("present"),"snapshot extras independent");info.recycle();check(copy.getText().toString().equals("Route to Vienna"),"copy lifetime");copy.recycle();
  AccessibilityNodeInfo parent=root.createAccessibilityNodeInfo();check(parent.getChildCount()==1,"flattened non-important container");AccessibilityNodeInfo node=parent.getChild(0);check(node.getText().toString().equals("Route to Vienna"),"child resolution");node.recycle();parent.recycle();
  group.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);parent=root.createAccessibilityNodeInfo();check(parent.getChildCount()==0&&!child.isImportantForAccessibility(),"hidden accessibility subtree");parent.recycle();group.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
  group.setAccessibilityDelegate(new View.AccessibilityDelegate(){public boolean onRequestSendAccessibilityEvent(ViewGroup host,View view,AccessibilityEvent event){return false;}});
  AccessibilityEvent event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED);child.sendAccessibilityEventUnchecked(event);check(root.received==0,"delegate event filter");event.recycle();group.setAccessibilityDelegate(null);
  event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_CLICKED);child.sendAccessibilityEventUnchecked(event);check(root.received==1&&root.event.getText().contains("Route to Vienna")&&root.event.getContentDescription().toString().equals("Destination"),"local event routing");event.recycle();root.event.recycle();root.event=null;
  child.setAccessibilityDelegate(null);info=child.createAccessibilityNodeInfo();check(info.getContentDescription().toString().equals("Destination")&&calls[1]>=1,"delegate removal");info.recycle();
  child.setVisibility(View.INVISIBLE);info=child.createAccessibilityNodeInfo();check(!info.isVisibleToUser()&&!info.performAction(AccessibilityNodeInfo.ACTION_CLICK),"hidden action");info.recycle();child.setVisibility(View.VISIBLE);
  System.out.println("ACCESSIBILITY_DELEGATE_STATE_ACTIONS_OK");
 }
 public static void install(final View window){
  window.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View view){final View priorFocus=view.findFocus();final FrameLayout fixture=prepare(view.getContext());((ViewGroup)view).addView(fixture);try{checkAttached(fixture);}finally{((ViewGroup)view).removeView(fixture);if(priorFocus!=null)priorFocus.requestFocus();}}public void onViewDetachedFromWindow(View view){}});
 }
}
