// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
import android.widget.FrameLayout;
import org.shiraz.fixture.AttachFixture;
public final class AttachSmoke {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message+": "+AttachFixture.events);}
 static final class FocusReceiver extends FrameLayout {
  View notified;int notifications;
  FocusReceiver(android.content.Context c){super(c);}
  @Override public void focusableViewAvailable(View v){notified=v;notifications++;}
 }
 static final class DuringLayout extends FrameLayout {
  int requests,added;
  DuringLayout(android.content.Context c){super(c);}
  @Override public void requestLayout(){requests++;super.requestLayout();}
  @Override public void onViewAdded(View v){added++;}
  boolean insert(View v,int index,boolean prevent){return addViewInLayout(v,index,new FrameLayout.LayoutParams(20,30),prevent);}
 }
 public static void main(String[] args){
  android.os.Looper.prepareMainLooper();
  android.content.Context context=new android.content.ContextWrapper(null);
  FocusReceiver receiver=new FocusReceiver(context);FrameLayout branch=new FrameLayout(context);View candidate=new View(context);receiver.addView(branch);branch.addView(candidate);
  ViewParent parentApi=branch;parentApi.focusableViewAvailable(candidate);check(receiver.notifications==1&&receiver.notified==candidate,"Focusable notification identity through parent API");
  branch.setVisibility(View.INVISIBLE);branch.focusableViewAvailable(candidate);check(receiver.notifications==1,"Invisible branch suppresses notification");branch.setVisibility(View.VISIBLE);
  branch.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);branch.focusableViewAvailable(candidate);check(receiver.notifications==1,"Blocked descendants suppress notification");
  branch.setDescendantFocusability(android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS);branch.setFocusable(true);check(branch.requestFocus(),"Branch focus setup");branch.focusableViewAvailable(candidate);check(receiver.notifications==1,"Focused parent preference suppresses notification");
  branch.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);branch.focusableViewAvailable(candidate);check(receiver.notifications==2&&branch.isFocused(),"Descendant preference forwards without stealing focus");
  DuringLayout group=new DuringLayout(context);ViewTreeObserver window=ViewTreeObserver.shirazCreateWindowObserver();group.shirazAttachTree(window);
  View quiet=new View(context);quiet.layout(0,0,10,10);group.requests=0;
  check(group.insert(quiet,-1,true),"Insert during layout");
  check(group.requests==0&&!quiet.isLayoutRequested()&&quiet.isAttachedToWindow()&&quiet.getParent()==group&&group.added==1,"Quiet insertion preserves layout state and attaches");
  View normal=new View(context);normal.layout(0,0,10,10);check(group.insert(normal,0,false),"Normal insertion");
  check(normal.isLayoutRequested()&&group.requests==0&&group.getChildAt(0)==normal&&normal.getLayoutParams().height==30,"Child layout request without parent propagation");
  try{group.insert(quiet,-1,true);throw new AssertionError("Duplicate child accepted");}catch(IllegalStateException expected){}
  check(group.getChildCount()==2,"Rejected insertion leaves hierarchy intact");group.shirazDetachTree();window.shirazKill();
  final FrameLayout root=new FrameLayout(context);AttachFixture.Probe first=new AttachFixture.Probe(context,"first"),second=new AttachFixture.Probe(context,"second");root.addView(first);root.addView(second);
  check(root.getWindowToken()==null&&first.getWindowToken()==null,"Unattached views have no token");
  check(root.getHandler()==null&&first.getHandler()==null,"Detached views have no handler");
  final java.util.ArrayList<Boolean> focusEvents=new java.util.ArrayList<Boolean>();
  final ViewTreeObserver detachedObserver=first.getViewTreeObserver();
  detachedObserver.addOnWindowFocusChangeListener(new ViewTreeObserver.OnWindowFocusChangeListener(){public void onWindowFocusChanged(boolean value){focusEvents.add(value);check(first.hasWindowFocus()==value,"Focus state before listener");}});
  final View.OnAttachStateChangeListener[] listeners=new View.OnAttachStateChangeListener[2];
  listeners[1]=new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){AttachFixture.events.add("second.listener");}public void onViewDetachedFromWindow(View v){AttachFixture.events.add("removed.detach");}};
  listeners[0]=new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){check(v.getParent()==root&&root.indexOfChild(v)>=0,"hierarchy before callbacks");check(v.getHandler()!=null&&v.getHandler()==root.getHandler(),"Handler available during attach callback");v.removeOnAttachStateChangeListener(listeners[1]);AttachFixture.events.add("first.listener");}public void onViewDetachedFromWindow(View v){AttachFixture.events.add("first.listener.detach");}};
  first.addOnAttachStateChangeListener(listeners[0]);first.addOnAttachStateChangeListener(listeners[1]);ViewTreeObserver observer=ViewTreeObserver.shirazCreateWindowObserver();root.shirazAttachTree(observer);
  check(!detachedObserver.isAlive(),"Detached observer merged");root.shirazSetWindowFocus(true);root.shirazSetWindowFocus(true);
  check(first.hasWindowFocus()&&second.hasWindowFocus()&&focusEvents.size()==1,"Window focus shared and deduplicated");
  first.setPressed(true);root.shirazSetWindowFocus(false);check(!first.isPressed()&&!first.hasWindowFocus()&&focusEvents.size()==2,"Focus loss clears press and notifies");
  final java.util.ArrayList<Boolean> modes=new java.util.ArrayList<Boolean>();
  ViewTreeObserver.OnTouchModeChangeListener modeListener=new ViewTreeObserver.OnTouchModeChangeListener(){public void onTouchModeChanged(boolean value){check(first.isInTouchMode()==value,"Touch mode updated before listener");modes.add(value);}};
  observer.addOnTouchModeChangeListener(modeListener);first.setFocusable(true);first.requestFocus();root.shirazSetTouchMode(true);root.shirazSetTouchMode(true);
  check(!first.hasFocus()&&!first.requestFocus()&&second.isInTouchMode()&&modes.size()==1,"Touch mode clears keyboard-only focus and deduplicates");
  first.setFocusableInTouchMode(true);check(first.requestFocus(),"Touch-focusable view accepts focus");root.shirazSetTouchMode(false);check(first.hasFocus()&&modes.size()==2,"Keyboard mode preserves editor focus");observer.removeOnTouchModeChangeListener(modeListener);root.shirazSetTouchMode(true);check(modes.size()==2,"Removed touch listener");root.shirazSetTouchMode(false);
  final android.os.Handler handler=root.getHandler();check(handler==first.getHandler()&&handler==second.getHandler()&&handler.getLooper()==android.os.Looper.getMainLooper(),"Shared window UI handler");
  final int[] fired={0};Runnable callback=new Runnable(){public void run(){check(android.os.Looper.myLooper()==handler.getLooper(),"Callback UI thread");fired[0]++;}};
  handler.post(callback);android.os.Looper.myLooper().shirazDispatchDue(100);check(fired[0]==1,"Window handler executes posted callback");handler.post(callback);handler.removeCallbacks(callback);android.os.Looper.myLooper().shirazDispatchDue(100);check(fired[0]==1,"Window callback cancellation");
  first.post(callback);handler.removeCallbacks(callback);android.os.Looper.myLooper().shirazDispatchDue(100);check(fired[0]==1,"Handler cancels View.post callbacks");
  handler.post(callback);second.removeCallbacks(callback);android.os.Looper.myLooper().shirazDispatchDue(100);check(fired[0]==1,"View cancels window handler callbacks without prior posting");
  final android.os.IBinder token=root.getWindowToken();check(token!=null&&first.getWindowToken()==token&&second.getWindowToken()==token,"Window shares identity across subtree");
  check(AttachFixture.events.toString().equals("[first.attach, first.listener, second.listener, second.attach]"),"listener snapshot");root.shirazAttachTree(observer);check(AttachFixture.events.size()==4,"duplicate attach");
  root.shirazDetachTree();check(AttachFixture.events.toString().equals("[first.attach, first.listener, second.listener, second.attach, first.detach, first.listener.detach, second.detach]"),"detach order and removed listener");
  check(root.getWindowToken()==null&&first.getWindowToken()==null,"Detach clears token");check(root.getHandler()==null&&first.getHandler()==null,"Detach clears handler access");
  check(!root.isAttachedToWindow()&&!first.isAttachedToWindow(),"detached state");AttachFixture.events.clear();root.shirazAttachTree(observer);check(!AttachFixture.events.contains("second.listener"),"removed listener restored");
  check(root.getWindowToken()==token,"Reattach to same window preserves identity");check(root.getHandler()==handler,"Same window keeps handler");FrameLayout other=new FrameLayout(context);ViewTreeObserver otherObserver=ViewTreeObserver.shirazCreateWindowObserver();other.shirazAttachTree(otherObserver);check(other.getWindowToken()!=token,"Separate windows have distinct tokens");root.removeView(second);check(second.getWindowToken()==null,"Removed child token cleared");other.addView(second);check(second.getWindowToken()==other.getWindowToken(),"Reparent adopts new window identity");check(second.getHandler()==other.getHandler()&&second.getHandler()!=handler,"Reparent adopts destination handler");other.removeView(second);root.addView(second);other.shirazDetachTree();otherObserver.shirazKill();
  final AttachFixture.Probe removing=new AttachFixture.Probe(context,"remove");removing.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){root.removeView(v);}public void onViewDetachedFromWindow(View v){}});root.addView(removing);check(removing.getParent()==null&&!removing.isAttachedToWindow()&&root.getChildCount()==2,"self removal during attach");
  View bad=new View(context);bad.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){throw new IllegalStateException("expected detach failure");}});root.addView(bad);
  try{root.shirazDetachTree();throw new AssertionError("detach failure swallowed");}catch(IllegalStateException expected){check(expected.getMessage().equals("expected detach failure"),"exception identity");}
  check(!root.isAttachedToWindow()&&!bad.isAttachedToWindow()&&!first.isAttachedToWindow()&&bad.getWindowToken()==null&&first.getWindowToken()==null,"failure cleanup");root.removeView(bad);root.shirazAttachTree(observer);root.shirazDetachTree();observer.shirazKill();
  View bounded=new View(context);for(int i=0;i<1024;i++)bounded.addOnAttachStateChangeListener(listeners[0]);try{bounded.addOnAttachStateChangeListener(listeners[0]);throw new AssertionError("unbounded attach listeners");}catch(IllegalStateException expected){}
  System.out.println("ATTACH_LISTENER_LIFECYCLE_OK");
 }
}
