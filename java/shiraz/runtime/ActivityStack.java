// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.Activity;
import android.content.Intent;
import java.util.ArrayList;
/** One app task. The caller resolves and authorizes components before supplying a lifecycle. */
public final class ActivityStack implements AutoCloseable {
 public interface Activation {void prepare(ActivityLifecycle lifecycle);}
 private final Activation activation;
 public interface Factory { ActivityLifecycle create(Intent intent); }
 private final Thread owner=Thread.currentThread();
 private final Factory factory;
 private final ArrayList<ActivityLifecycle> entries=new ArrayList<ActivityLifecycle>();
 private boolean closed,transition;
 public ActivityStack(Factory factory){this(factory,new Activation(){public void prepare(ActivityLifecycle lifecycle){}});}
 public ActivityStack(Factory factory,Activation activation){if(activation==null)throw new NullPointerException("activation");this.activation=activation;if(factory==null)throw new NullPointerException("factory");this.factory=factory;}
 private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Activity stack requires its owner thread");}
 private void enter(){thread();if(closed||transition)throw new IllegalStateException("Activity stack is closed or transitioning");transition=true;}
 public Activity current(){thread();return entries.isEmpty()?null:entries.get(entries.size()-1).current();}
 public ActivityLifecycle currentLifecycle(){thread();if(entries.isEmpty())throw new IllegalStateException("Empty activity stack");return entries.get(entries.size()-1);}
 public int size(){thread();return entries.size();}
 public void launch(Intent intent){launch(intent,android.content.pm.ActivityInfo.LAUNCH_MULTIPLE);}
 public void launch(Intent intent,int mode){
  if(intent==null)throw new NullPointerException("intent");enter();
  try{
   if(mode<0||mode>2)throw new UnsupportedOperationException("Separate activity task launch modes are not implemented");
   if(mode!=0&&intent.getComponent()==null)throw new IllegalArgumentException("Retained launch requires a resolved component");
   // Snapshot before any lifecycle callback; failed copying leaves the old activity untouched.
   Intent snapshot=intent.shirazBroadcastCopy();
   boolean reorder=(snapshot.getFlags()&Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)!=0&&mode!=2;
   if(reorder&&snapshot.getComponent()==null)throw new IllegalArgumentException("Reordering requires a resolved component");
   boolean clearTop=(snapshot.getFlags()&Intent.FLAG_ACTIVITY_CLEAR_TOP)!=0&&!reorder&&snapshot.getComponent()!=null;
   int reuse=-1;
   if(clearTop)for(int i=entries.size()-1;i>=0;i--){
    Activity candidate=entries.get(i).current();
    if(!candidate.isFinishing()&&snapshot.getComponent().equals(candidate.getComponentName())){reuse=i;break;}
   }
   else if(mode!=0||reorder)for(int i=entries.size()-1;i>=0;i--){
    Activity candidate=entries.get(i).current();
    if(!candidate.isFinishing()&&snapshot.getComponent().equals(candidate.getComponentName())){reuse=i;break;}
    if(mode==1&&!reorder)break;
   }
   if(reuse>=0){
    ActivityLifecycle target=entries.get(reuse),oldTop=currentLifecycle();oldTop.pauseForLaunch();
    if(reorder){entries.remove(reuse);entries.add(target);target.newIntent(snapshot);activation.prepare(target);target.resumeFromBackStack();if(oldTop!=target)oldTop.stopCovered();return;}
    while(entries.size()>reuse+1){ActivityLifecycle removed=entries.remove(entries.size()-1);removed.close();}
    target.newIntent(snapshot);activation.prepare(target);target.resumeFromBackStack();return;
   }
   if(entries.size()>=32)throw new IllegalStateException("Activity stack limit");
   ActivityLifecycle old=entries.isEmpty()?null:entries.get(entries.size()-1),next=null;
   if(old!=null)old.pauseForLaunch();
   try{
    next=factory.create(snapshot);if(next==null)throw new IllegalStateException("Activity factory returned null lifecycle");
    next.start();
   }catch(RuntimeException|Error failure){
    if(next!=null)try{next.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
    if(old!=null&&!old.current().isFinishing())try{old.resumeFromBackStack();}catch(RuntimeException|Error restore){if(restore!=failure)failure.addSuppressed(restore);}
    throw failure;
   }
   entries.add(next);
   if(old!=null)old.stopCovered();
  }finally{transition=false;}
 }
 /** Remove finished screens. The caller checks this between dispatches, never during callbacks. */
 public boolean finishTop(){
  enter();try{
   if(entries.isEmpty()||!current().isFinishing())return false;
   ActivityLifecycle top=entries.get(entries.size()-1);int resultCode=top.current().shirazResultCode();Intent resultData=top.current().shirazResultData();top.pauseForLaunch();entries.remove(entries.size()-1);
   try{
    // A covered activity may have finished itself while another screen was active.
    while(!entries.isEmpty()&&current().isFinishing()){
     ActivityLifecycle finished=entries.remove(entries.size()-1);finished.close();
    }
    if(!entries.isEmpty()){activation.prepare(currentLifecycle());currentLifecycle().current().shirazDeliverResult(resultCode,resultData);currentLifecycle().resumeFromBackStack();}
   }finally{top.close();}
   return true;
  }finally{transition=false;}
 }
 public void close(){
  thread();if(closed)return;if(transition)throw new IllegalStateException("Activity stack is transitioning");closed=true;transition=true;Throwable first=null;
  try{while(!entries.isEmpty()){ActivityLifecycle life=entries.remove(entries.size()-1);try{life.close();}catch(RuntimeException|Error failure){if(first==null)first=failure;else if(first!=failure)first.addSuppressed(failure);}}}
  finally{transition=false;}
  if(first instanceof Error)throw (Error)first;if(first!=null)throw (RuntimeException)first;
 }
}
