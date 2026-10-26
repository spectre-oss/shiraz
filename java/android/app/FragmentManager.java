// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.os.*;
import java.util.*;
/** Owner-thread platform fragment lifecycle with queued transactions and local saved state. */
public class FragmentManager {
 private final Activity host;private final Thread owner=Thread.currentThread();
 private final ArrayList<Fragment> fragments=new ArrayList<Fragment>();
 private final ArrayDeque<FragmentTransaction> pending=new ArrayDeque<FragmentTransaction>();
 private Handler handler;private int phase;private boolean executing,saved,destroyed;
 private final Runnable drain=new Runnable(){public void run(){executePendingTransactions();}};
 FragmentManager(Activity host,int phase){this.host=host;this.phase=phase;}
 void checkThread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Fragment manager owner thread required");}
 public FragmentTransaction beginTransaction(){checkThread();if(destroyed)throw new IllegalStateException("Fragment manager destroyed");return new FragmentTransaction(this);}
 public Fragment findFragmentByTag(String tag){checkThread();if(tag==null)return null;for(int i=fragments.size()-1;i>=0;i--)if(tag.equals(fragments.get(i).tag))return fragments.get(i);return null;}
 public List<Fragment> getFragments(){checkThread();return new ArrayList<Fragment>(fragments);}
 public boolean isDestroyed(){return destroyed;}
 public boolean isStateSaved(){return saved;}
 int enqueue(FragmentTransaction transaction,boolean loss,boolean now){
  checkThread();if(transaction.committed)throw new IllegalStateException("Transaction already committed");
  if(destroyed||saved&&!loss)throw new IllegalStateException("Fragment state is saved or destroyed");
  if(now&&executing)throw new IllegalStateException("Recursive fragment execution");
  if(!now&&pending.size()>=256)throw new IllegalStateException("Pending fragment transaction limit");
  if(!now&&handler==null)handler=new Handler(Looper.getMainLooper());
  transaction.committed=true;
  if(now){executing=true;try{apply(transaction);}finally{executing=false;}}
  else {pending.add(transaction);if(pending.size()==1&&!handler.post(drain)){pending.remove(transaction);throw new IllegalStateException("Fragment queue is closed");}}
  return -1;
 }
 private void apply(FragmentTransaction transaction){
  ArrayList<Fragment> projected=new ArrayList<Fragment>(fragments);
  for(FragmentTransaction.Op op:transaction.operations){Fragment f=op.fragment;
   if(op.add){if(projected.contains(f)||f.manager!=null&&f.manager!=this)throw new IllegalStateException("Fragment already owned");projected.add(f);if(projected.size()>64)throw new IllegalStateException("Fragment instance limit");}
   else if(!projected.remove(f))throw new IllegalStateException("Fragment is not added");
  }
  for(FragmentTransaction.Op op:transaction.operations){Fragment f=op.fragment;
   if(op.add){f.manager=this;f.tag=op.tag;f.removing=false;fragments.add(f);move(f,phase);}
   else{f.removing=true;try{move(f,0);}finally{fragments.remove(f);f.manager=null;}}
  }
 }
 public boolean executePendingTransactions(){checkThread();if(executing)throw new IllegalStateException("Recursive fragment execution");if(destroyed)return false;
  if(handler!=null)handler.removeCallbacks(drain);boolean changed=false;executing=true;
  try{int count=0;while(!pending.isEmpty()){if(++count>256)throw new IllegalStateException("Reentrant fragment transaction limit");apply(pending.remove());changed=true;}}finally{executing=false;}return changed;
 }
 private void move(Fragment f,int target){
  while(f.phase<target){int next=f.phase+1;f.phase=next;
   if(next==1){f.activity=host;f.onAttach((android.content.Context)host);host.onAttachFragment(f);f.onCreate(f.saved);}
   else if(next==2){if(f.onCreateView(host.getLayoutInflater(),null,f.saved)!=null)throw new UnsupportedOperationException("Platform fragment views are not implemented");f.onActivityCreated(f.saved);f.saved=null;}
   else if(next==3)f.onStart();else f.onResume();
  }
  while(f.phase>target){int old=f.phase--;if(old==4)f.onPause();else if(old==3)f.onStop();else if(old==2)f.onDestroyView();else{try{f.onDestroy();}finally{try{f.onDetach();}finally{f.activity=null;}}}}
 }
 void moveTo(int target){checkThread();if(destroyed)throw new IllegalStateException("Fragment manager destroyed");if(executing)throw new IllegalStateException("Recursive fragment lifecycle");
  executePendingTransactions();phase=target;if(target>=3)saved=false;executing=true;
  try{for(Fragment f:new ArrayList<Fragment>(fragments))move(f,target);}finally{executing=false;}
 }
 void configuration(android.content.res.Configuration config){checkThread();for(Fragment f:new ArrayList<Fragment>(fragments))f.onConfigurationChanged(config);}
 void lowMemory(){checkThread();for(Fragment f:new ArrayList<Fragment>(fragments))f.onLowMemory();}
 void trimMemory(int level){checkThread();for(Fragment f:new ArrayList<Fragment>(fragments))f.onTrimMemory(level);}
 Bundle save(){checkThread();executePendingTransactions();saved=true;Bundle out=new Bundle();out.putInt("count",fragments.size());int i=0;executing=true;
  try{for(Fragment f:fragments){Bundle record=new Bundle(),state=new Bundle();f.onSaveInstanceState(state);record.putString("class",f.getClass().getName());record.putString("tag",f.tag);record.putBundle("args",f.arguments);record.putBundle("state",state);out.putBundle("fragment."+i++,record);}return out;}finally{executing=false;}
 }
 void restore(Bundle in){checkThread();if(in==null)return;if(!fragments.isEmpty())throw new IllegalStateException("Fragments already initialized");int count=in.getInt("count",0);if(count<0||count>64)throw new IllegalArgumentException("Saved fragment limit");
  executing=true;try{for(int i=0;i<count;i++){Bundle record=in.getBundle("fragment."+i);if(record==null)throw new IllegalArgumentException("Missing saved fragment");
   try{Fragment f=Class.forName(record.getString("class"),true,host.getClassLoader()).asSubclass(Fragment.class).newInstance();f.arguments=record.getBundle("args");f.saved=record.getBundle("state");f.manager=this;f.tag=record.getString("tag");fragments.add(f);move(f,phase);}
   catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot restore fragment",error);}
  }}finally{executing=false;}
 }
 void destroy(){checkThread();if(destroyed)return;destroyed=true;if(handler!=null)handler.removeCallbacks(drain);pending.clear();phase=0;
  Throwable failure=null;
  for(Fragment f:new ArrayList<Fragment>(fragments)){
   try{while(f.phase>0){try{move(f,0);}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}}}finally{f.manager=null;f.activity=null;}
  }
  fragments.clear();if(failure instanceof RuntimeException)throw (RuntimeException)failure;if(failure instanceof Error)throw (Error)failure;
 }
}
