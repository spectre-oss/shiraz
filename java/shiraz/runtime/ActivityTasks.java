// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.Activity;
import android.content.Intent;
import java.util.ArrayList;
/** Session-local tasks: one normal app task and isolated single-instance component tasks. */
public final class ActivityTasks implements AutoCloseable {
    private static final class Task {
        final String component;
        final ActivityStack stack;
        Task(String component,ActivityStack stack){this.component=component;this.stack=stack;}
    }
    private final ActivityStack.Factory factory;
    private final ActivityStack.Activation activation;
    private final Thread owner=Thread.currentThread();
    private final ArrayList<Task> tasks=new ArrayList<Task>(); // Least to most recently foregrounded.
    private boolean closed,transition,visible=true;
    public ActivityTasks(ActivityStack.Factory factory,ActivityStack.Activation activation){this.factory=java.util.Objects.requireNonNull(factory);this.activation=java.util.Objects.requireNonNull(activation);}
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Task owner thread required");}
    private void enter(){thread();if(closed||transition)throw new IllegalStateException("Tasks closed or transitioning");transition=true;}
    private Task top(){return tasks.isEmpty()?null:tasks.get(tasks.size()-1);}
    public Activity current(){thread();return top()==null?null:top().stack.current();}
    public ActivityLifecycle currentLifecycle(){thread();if(top()==null)throw new IllegalStateException("No active task");return top().stack.currentLifecycle();}
    public int taskCount(){thread();return tasks.size();}
    public int size(){thread();return top()==null?0:top().stack.size();}
    public void setVisible(boolean value){enter();try{visible=value;if(top()!=null)top().stack.setVisible(value);}finally{transition=false;}}
    public void launch(Intent intent){launch(intent,0);}
    public void launch(Intent intent,int mode){
        java.util.Objects.requireNonNull(intent);enter();
        try{
            if(mode<0||mode>3)throw new UnsupportedOperationException("Activity task launch mode "+mode);
            if(mode==3&&intent.getComponent()==null)throw new IllegalArgumentException("Single-instance task requires a component");
            Intent snapshot=intent.shirazBroadcastCopy();
            String key=mode==3?snapshot.getComponent().flattenToString():null;
            Task previous=top(),target=null;
            for(Task candidate:tasks)if(java.util.Objects.equals(candidate.component,key)){target=candidate;break;}
            if(target==previous&&target!=null){target.stack.launch(snapshot,mode==3?2:mode);return;}
            if((snapshot.getFlags()&Intent.FLAG_ACTIVITY_FORWARD_RESULT)!=0)throw new UnsupportedOperationException("Cross-task result forwarding is not implemented");
            boolean fresh=target==null;
            if(fresh){if(tasks.size()>=16)throw new IllegalStateException("Activity task limit");target=new Task(key,new ActivityStack(factory,activation));}
            try{
                if(previous!=null)previous.stack.setVisible(false);
                // Start or deliver while hidden, then activate only the destination task.
                target.stack.setVisible(false);
                target.stack.launch(snapshot,mode==3?2:mode);
                target.stack.setVisible(visible);
            }catch(RuntimeException|Error failure){
                try{if(fresh)target.stack.close();else target.stack.setVisible(false);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
                if(previous!=null)try{previous.stack.setVisible(visible);}catch(RuntimeException|Error restore){if(restore!=failure)failure.addSuppressed(restore);}
                throw failure;
            }
            tasks.remove(target);tasks.add(target);
        }finally{transition=false;}
    }
    public boolean finishTop(){
        enter();try{
            Task current=top();if(current==null||!current.stack.finishTop())return false;
            if(current.stack.current()==null){
                tasks.remove(current);current.stack.close();
                // A task finishing while covered must not flash back onto the display.
                while(top()!=null&&top().stack.current()!=null&&top().stack.current().isFinishing()){
                    Task covered=top();covered.stack.finishTop();
                    if(covered.stack.current()==null){tasks.remove(covered);covered.stack.close();}else break;
                }
                if(top()!=null)top().stack.setVisible(visible);
            }
            return true;
        }finally{transition=false;}
    }
    public void close(){
        thread();if(closed)return;if(transition)throw new IllegalStateException("Tasks transitioning");closed=true;transition=true;Throwable failure=null;
        try{while(!tasks.isEmpty()){Task task=tasks.remove(tasks.size()-1);try{task.stack.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(error!=failure)failure.addSuppressed(error);}}}finally{transition=false;}
        if(failure instanceof Error)throw (Error)failure;if(failure!=null)throw (RuntimeException)failure;
    }
}
