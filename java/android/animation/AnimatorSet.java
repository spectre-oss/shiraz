// SPDX-License-Identifier: GPL-3.0-only
package android.animation;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
/** Looper-driven animation dependency groups, using the children's frame clocks. */
public final class AnimatorSet extends Animator {
 private final ArrayList<Animator> children=new ArrayList<Animator>();
 private final ArrayList<int[]> relations=new ArrayList<int[]>();
 private long duration=-1,delay;
 private TimeInterpolator interpolator;
 private boolean hasInterpolator,started,running,draining,terminating;
 private Looper owner;
 private int generation;
 private ArrayList<Group> groups;
 private final IdentityHashMap<Animator,Boolean> finished=new IdentityHashMap<Animator,Boolean>();
 private final IdentityHashMap<Animator,AnimatorListener> observers=new IdentityHashMap<Animator,AnimatorListener>();
 private ValueAnimator delayAnimator;
 private static final class Group {final ArrayList<Animator> items=new ArrayList<Animator>();final ArrayList<Animator> completed=new ArrayList<Animator>();final ArrayList<Group> parents=new ArrayList<Group>();final ArrayList<Animator> before=new ArrayList<Animator>();boolean launched,done;int remaining;}
 private void thread(){if(owner!=null&&owner!=Looper.myLooper())throw new IllegalStateException("AnimatorSet owning Looper required");}
 private void editable(){thread();if(started)throw new IllegalStateException("Cannot change running AnimatorSet graph");}
 private static boolean contains(Animator candidate,Animator target,int depth){if(candidate==target)return true;if(!(candidate instanceof AnimatorSet))return false;if(depth>=64)throw new IllegalArgumentException("AnimatorSet nesting limit");for(Animator child:((AnimatorSet)candidate).children)if(contains(child,target,depth+1))return true;return false;}
 private int node(Animator child){if(child==null)throw new NullPointerException("animation");if(contains(child,this,0))throw new IllegalArgumentException("AnimatorSet containment cycle");for(int i=0;i<children.size();i++)if(children.get(i)==child)return i;if(children.size()>=1024)throw new IllegalArgumentException("AnimatorSet child limit");children.add(child);return children.size()-1;}
 private void relation(int a,int b,int type){editable();if(relations.size()>=65536)throw new IllegalArgumentException("AnimatorSet edge limit");relations.add(new int[]{a,b,type});}
 public final class Builder {
  private final int index;private Builder(int value){index=value;}
  public Builder with(Animator child){editable();relation(index,node(child),0);return this;}
  public Builder before(Animator child){editable();relation(index,node(child),1);return this;}
  public Builder after(Animator child){editable();relation(node(child),index,1);return this;}
  public Builder after(long ms){if(ms<0)throw new IllegalArgumentException("Delay");ValueAnimator wait=ValueAnimator.ofFloat(0,1);wait.setDuration(ms);return after(wait);}
 }
 public Builder play(Animator child){editable();return child==null?null:new Builder(node(child));}
 public void playTogether(Animator... items){if(items==null)throw new NullPointerException("items");editable();if(items.length==0)return;Builder builder=play(items[0]);for(int i=1;i<items.length;i++)builder.with(items[i]);}
 public void playTogether(Collection<Animator> items){playTogether(items.toArray(new Animator[0]));}
 public void playSequentially(Animator... items){if(items==null)throw new NullPointerException("items");editable();for(int i=0;i<items.length;i++){Builder builder=play(items[i]);if(i+1<items.length)builder.before(items[i+1]);}}
 public void playSequentially(List<Animator> items){playSequentially(items.toArray(new Animator[0]));}
 public ArrayList<Animator> getChildAnimations(){return new ArrayList<Animator>(children);}
 public void setTarget(Object target){editable();for(Animator child:children)if(child instanceof ObjectAnimator||child instanceof AnimatorSet)child.setTarget(target);}
 public AnimatorSet setDuration(long value){editable();if(value<0)throw new IllegalArgumentException("Duration");duration=value;return this;}
 public long getDuration(){return duration;}
 public void setStartDelay(long value){editable();delay=Math.max(0,value);}public long getStartDelay(){return delay;}
 public void setInterpolator(TimeInterpolator value){editable();interpolator=value;hasInterpolator=true;}public TimeInterpolator getInterpolator(){return interpolator;}
 public boolean isStarted(){return started;}public boolean isRunning(){return running;}
 private static int root(int[] parents,int i){while(parents[i]!=i)i=parents[i];return i;}
 private ArrayList<Group> graph(){
  int[] parents=new int[children.size()];for(int i=0;i<parents.length;i++)parents[i]=i;
  for(int[] edge:relations)if(edge[2]==0)parents[root(parents,edge[1])]=root(parents,edge[0]);
  Group[] byRoot=new Group[children.size()];ArrayList<Group> all=new ArrayList<Group>();
  for(int i=0;i<children.size();i++){int id=root(parents,i);if(byRoot[id]==null){byRoot[id]=new Group();all.add(byRoot[id]);}byRoot[id].items.add(children.get(i));}
  for(int[] edge:relations)if(edge[2]==1){Group from=byRoot[root(parents,edge[0])],to=byRoot[root(parents,edge[1])];if(from==to)throw new IllegalArgumentException("Animation dependency cycle");if(!to.parents.contains(from))to.parents.add(from);if(!to.before.contains(children.get(edge[0])))to.before.add(children.get(edge[0]));}
  ArrayList<Group> sorted=new ArrayList<Group>();while(sorted.size()<all.size()){boolean changed=false;for(Group group:all)if(!sorted.contains(group)&&sorted.containsAll(group.parents)){sorted.add(group);changed=true;}if(!changed)throw new IllegalArgumentException("Animation dependency cycle");}return sorted;
 }
 private boolean event(int kind,int token){for(AnimatorListener listener:new ArrayList<AnimatorListener>(listeners)){if(kind==0)listener.onAnimationStart(this);else if(kind==1)listener.onAnimationEnd(this);else listener.onAnimationCancel(this);if(token!=generation)return false;}return true;}
 private void detach(){for(Animator child:new ArrayList<Animator>(observers.keySet()))child.removeListener(observers.get(child));observers.clear();if(delayAnimator!=null){delayAnimator.removeAllListeners();delayAnimator.cancel();delayAnimator=null;}}
 private void cleanup(){detach();started=running=false;}
 private void abort(){cleanup();for(Animator child:children)if(child.isStarted())try{child.cancel();}catch(RuntimeException ignored){}}
 private void drain(final int token){
  if(draining||!started||token!=generation)return;draining=true;
  try{boolean changed;do{changed=false;for(final Group group:groups){if(token!=generation||!started)return;if(group.launched)continue;boolean ready=true;for(Animator predecessor:group.before)if(!finished.containsKey(predecessor))ready=false;if(!ready)continue;
    group.launched=true;group.remaining=group.items.size();changed=true;
    for(final Animator child:group.items){if(token!=generation||!started)return;AnimatorListener observer=new AnimatorListenerAdapter(){public void onAnimationEnd(Animator animation){if(token!=generation||!started)return;AnimatorListener self=observers.remove(child);if(self==null)return;child.removeListener(self);group.completed.add(child);finished.put(child,Boolean.TRUE);if(--group.remaining==0)group.done=true;drain(token);}};observers.put(child,observer);child.addListener(observer);child.start();}
   }}while(changed&&started&&token==generation);
   for(Group group:groups)if(!group.done)return;
   cleanup();event(1,token);
  }catch(RuntimeException|Error failure){if(token==generation)abort();throw failure;}finally{draining=false;if(token!=generation&&started&&running)drain(generation);}
 }
 public void start(){
  thread();if(Looper.myLooper()==null)throw new IllegalStateException("AnimatorSet requires a Looper");if(started)cancel();editable();groups=graph();finished.clear();owner=Looper.myLooper();
  for(Animator child:children){if(child.isStarted())throw new IllegalStateException("AnimatorSet child already running");if(duration>=0)child.setDuration(duration);if(hasInterpolator)child.setInterpolator(interpolator);}
  final int token=++generation;started=true;running=false;terminating=false;
  try{if(!event(0,token)||!started)return;if(delay==0){running=true;drain(token);}else{delayAnimator=ValueAnimator.ofFloat(0,1);delayAnimator.setStartDelay(delay);delayAnimator.setDuration(0);delayAnimator.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator animation){if(token==generation&&started){delayAnimator=null;running=true;drain(token);}}});delayAnimator.start();}}
  catch(RuntimeException|Error failure){if(token==generation)abort();throw failure;}
 }
 public void cancel(){thread();if(!started||terminating)return;terminating=true;int token=generation;try{if(!event(2,token))return;detach();for(Animator child:children){if(token!=generation)return;if(child.isStarted())child.cancel();}if(token==generation){cleanup();event(1,++generation);}}catch(RuntimeException|Error failure){if(token==generation)abort();throw failure;}finally{if(token==generation||!started)terminating=false;}}
 public void end(){thread();if(terminating)return;if(!started)start();if(!started)return;terminating=true;int token=generation;try{detach();for(Group group:groups)for(Animator child:group.items){if(token!=generation)return;if(!group.completed.contains(child))child.end();}cleanup();event(1,++generation);}catch(RuntimeException|Error failure){if(token==generation)abort();throw failure;}finally{if(token==generation||!started)terminating=false;}}
 private static long sum(long a,long b){if(a<0||b<0)return DURATION_INFINITE;return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
 public long getTotalDuration(){
  IdentityHashMap<Animator,Long> ends=new IdentityHashMap<Animator,Long>();long maximum=0;
  for(Group group:graph()){
   long start=0;for(Animator previous:group.before){long value=ends.get(previous);if(value<0)return DURATION_INFINITE;start=Math.max(start,value);}
   for(Animator child:group.items){long value;
    if(duration>=0&&child instanceof ValueAnimator){ValueAnimator v=(ValueAnimator)child;value=v.getRepeatCount()<0?DURATION_INFINITE:sum(v.getStartDelay(),duration>Long.MAX_VALUE/(v.getRepeatCount()+1L)?Long.MAX_VALUE:duration*(v.getRepeatCount()+1L));}
    else if(duration>=0&&child instanceof AnimatorSet){AnimatorSet copy=((AnimatorSet)child).clone();copy.setDuration(duration);value=copy.getTotalDuration();}else value=child.getTotalDuration();
    if(value<0)return DURATION_INFINITE;long end=sum(start,value);ends.put(child,end);maximum=Math.max(maximum,end);
   }
  }return sum(delay,maximum);
 }
 public AnimatorSet clone(){AnimatorSet copy=new AnimatorSet();copy.duration=duration;copy.delay=delay;copy.interpolator=interpolator;copy.hasInterpolator=hasInterpolator;copy.listeners=new ArrayList<AnimatorListener>(listeners);for(Animator child:children){Animator next=child.clone();AnimatorListener internal=observers.get(child);if(internal!=null)next.removeListener(internal);copy.children.add(next);}for(int[] edge:relations)copy.relations.add(edge.clone());return copy;}
}
