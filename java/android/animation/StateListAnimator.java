// SPDX-License-Identifier: GPL-3.0-only
package android.animation;
import android.view.View;
import android.util.StateSet;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
/** Ordered drawable-state animation selection, with weak view ownership. */
public class StateListAnimator implements Cloneable {
 private static final class Entry {final int[] states;final Animator animation;Entry(int[] s,Animator a){states=s.clone();animation=a;}}
 private final ArrayList<Entry> entries=new ArrayList<Entry>();
 private WeakReference<View> target=new WeakReference<View>(null);
 private Entry selected;
 private Animator running;
 private int configurations,generation;
 private final AnimatorListenerAdapter listener=new AnimatorListenerAdapter(){public void onAnimationEnd(Animator animation){if(running==animation){running=null;bind(animation,null);}}};
 private static void bind(Animator animation,View view){if(animation instanceof ObjectAnimator||animation instanceof AnimatorSet)animation.setTarget(view);}
 public void addState(int[] states,Animator animation){if(states==null||animation==null)throw new NullPointerException();if(entries.size()>=256||states.length>256)throw new IllegalArgumentException("State animator bound");if(running!=null)throw new IllegalStateException("Cannot edit running state list");for(Entry entry:entries)if(entry.animation==animation)throw new IllegalArgumentException("Animator reused in state list");entries.add(new Entry(states,animation));animation.addListener(listener);}
 public View getTarget(){return target.get();}
 public void setTarget(View view){if(target.get()==view)return;int token=++generation;Animator old=running;running=null;selected=null;if(old!=null)old.cancel();if(token!=generation)return;for(Entry entry:entries)bind(entry.animation,null);target=new WeakReference<View>(view);}
 public void setState(int[] states){Entry next=null;for(Entry entry:entries)if(StateSet.stateSetMatches(entry.states,states)){next=entry;break;}if(next==selected)return;int token=++generation;Animator old=running;running=null;selected=next;if(old!=null){old.cancel();if(token!=generation)return;bind(old,null);}View view=target.get();if(next==null||view==null)return;running=next.animation;bind(running,view);try{running.start();}catch(RuntimeException|Error failure){if(token==generation){Animator failed=running;running=null;selected=null;if(failed!=null)bind(failed,null);}throw failure;}}
 public Animator getRunningAnimator(){return running;}
 public void jumpToCurrentState(){Animator current=running;if(current!=null)current.end();}
 public int getChangingConfigurations(){return configurations;}public void setChangingConfigurations(int value){configurations=value;}
 public StateListAnimator clone(){StateListAnimator copy=new StateListAnimator();copy.configurations=configurations;for(Entry entry:entries){Animator cloned=entry.animation.clone();cloned.removeListener(listener);bind(cloned,null);copy.addState(entry.states,cloned);}return copy;}
}
