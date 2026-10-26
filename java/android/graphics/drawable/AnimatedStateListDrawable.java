// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.HashMap;
/** State choices with timed, reversible frame transitions and animatable vector transitions. */
public class AnimatedStateListDrawable extends StateListDrawable {
 private static final class Item {final int[] states;final int id,index;Item(int[] s,int id,int i){states=s.clone();this.id=id;index=i;}}
 private static final class Edge {final int index;final boolean reversible,reverse;Edge(int i,boolean r,boolean back){index=i;reversible=r;reverse=back;}}
 private final ArrayList<Item> items=new ArrayList<Item>();
 private final HashMap<Long,Edge> edges=new HashMap<Long,Edge>();
 private int selected=-1,from=-1,to=-1;private Animatable animation;private Frames frames;
 private static long key(int a,int b){return ((long)a<<32)|(b&0xffffffffL);}
 public AnimatedStateListDrawable(){}
 @Override public void addState(int[] states,Drawable drawable){addState(states,drawable,0);}
 public void addState(int[] states,Drawable drawable,int id){if(states==null||drawable==null)throw new NullPointerException("State drawable");if(states.length>1024)throw new IllegalArgumentException("State limit");int index=addChild(drawable);items.add(new Item(states,id,index));onStateChange(getState());}
 public <T extends Drawable & Animatable> void addTransition(int fromId,int toId,T drawable,boolean reversible){if(drawable==null)throw new NullPointerException("Transition");int index=addChild(drawable);edges.put(key(fromId,toId),new Edge(index,reversible,false));if(reversible)edges.put(key(toId,fromId),new Edge(index,true,true));}
 private static boolean matches(int[] specification,int[] state){for(int value:specification){if(value==0)break;boolean found=false;for(int actual:state){if(actual==0)break;if(actual==Math.abs(value)){found=true;break;}}if(found!=(value>0))return false;}return true;}
 private int match(int[] states){for(int i=0;i<items.size();i++)if(matches(items.get(i).states,states))return i;for(int i=0;i<items.size();i++)if(matches(items.get(i).states,new int[0]))return i;return -1;}
 @Override public boolean hasFocusStateSpecified(){for(Item i:items)for(int s:i.states)if(s==16842908||s==-16842908)return true;return false;}
 @Override protected boolean onStateChange(int[] states){
  int target=match(states);boolean changed=false;
  if(animation!=null||frames!=null){if(target==to)return getCurrent()!=null&&getCurrent().setState(states);if(target==from&&frames!=null&&frames.reversible){frames.reverse();int old=from;from=to;to=old;selected=target;return true;}stopTransition();}
  if(target!=selected){int previous=selected;selected=target;Edge edge=previous<0||target<0?null:edges.get(key(items.get(previous).id,items.get(target).id));
   if(edge!=null){if(edge.reverse&&children.get(edge.index) instanceof AnimatedVectorDrawable)throw new UnsupportedOperationException("Animated vector state transition reversal is not implemented");from=previous;to=target;selectDrawable(edge.index);Drawable current=getCurrent();if(current instanceof AnimationDrawable){frames=new Frames((AnimationDrawable)current,edge.reverse,edge.reversible);frames.start();}else{animation=(Animatable)current;animation.start();}changed=true;}
   else changed=selectDrawable(target<0?-1:items.get(target).index);
  }
  return (getCurrent()!=null&&getCurrent().setState(states))||changed;
 }
 private void stopTransition(){if(frames!=null){frames.stop();frames=null;}if(animation!=null){animation.stop();animation=null;}from=to=-1;}
 @Override public void jumpToCurrentState(){stopTransition();selectDrawable(selected<0?-1:items.get(selected).index);super.jumpToCurrentState();}
 @Override public boolean setVisible(boolean visible,boolean restart){boolean changed=super.setVisible(visible,restart);if(!visible)jumpToCurrentState();else if(restart&&(animation!=null||frames!=null)){if(frames!=null)frames.start();else animation.start();}return changed;}
 private final class Frames implements Runnable {
  final AnimationDrawable drawable;final boolean reversible;final long total;boolean backward,running;long position,last;
  Frames(AnimationDrawable d,boolean reverse,boolean reversible){drawable=d;backward=reverse;this.reversible=reversible;long duration=0;for(int i=0;i<d.getNumberOfFrames();i++)duration+=Math.max(1,d.getDuration(i));total=duration;position=backward?total:0;}
  void start(){drawable.stop();running=true;last=SystemClock.uptimeMillis();paint();}
  void stop(){running=false;unscheduleSelf(this);drawable.stop();}
  void advance(){long now=SystemClock.uptimeMillis(),elapsed=Math.max(0,now-last);last=now;position=Math.max(0,Math.min(total,position+(backward?-elapsed:elapsed)));}
  void reverse(){advance();backward=!backward;running=true;paint();}
  void paint(){unscheduleSelf(this);int count=drawable.getNumberOfFrames();if(count==0){running=false;return;}long end=0;int frame=count-1;for(int i=0;i<count;i++){end+=Math.max(1,drawable.getDuration(i));if(position<end){frame=i;break;}}drawable.selectDrawable(frame);invalidateSelf();if(backward?position<=0:position>=total){running=false;return;}if(running)scheduleSelf(this,SystemClock.uptimeMillis()+Math.min(16,backward?position:total-position));}
  public void run(){if(!running)return;advance();paint();}
 }
}
