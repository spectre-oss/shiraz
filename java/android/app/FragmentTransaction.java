// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import java.util.ArrayList;
/** Bounded add/remove transaction; back stack and visual containers are explicit gaps. */
public class FragmentTransaction {
 static final class Op {final Fragment fragment;final String tag;final boolean add;Op(Fragment f,String t,boolean a){fragment=f;tag=t;add=a;}}
 final FragmentManager manager;final ArrayList<Op> operations=new ArrayList<Op>();boolean committed;
 FragmentTransaction(FragmentManager manager){this.manager=manager;}
 private void writable(){manager.checkThread();if(committed)throw new IllegalStateException("Transaction already committed");if(operations.size()>=256)throw new IllegalStateException("Fragment operation limit");}
 public FragmentTransaction add(Fragment fragment,String tag){writable();if(fragment==null)throw new NullPointerException("fragment");if(tag!=null&&tag.length()>4096)throw new IllegalArgumentException("Fragment tag limit");operations.add(new Op(fragment,tag,true));return this;}
 public FragmentTransaction add(int container,Fragment fragment){return add(container,fragment,null);}
 public FragmentTransaction add(int container,Fragment fragment,String tag){if(container!=0)throw new UnsupportedOperationException("Platform fragment view containers are not implemented");return add(fragment,tag);}
 public FragmentTransaction remove(Fragment fragment){writable();if(fragment==null)throw new NullPointerException("fragment");operations.add(new Op(fragment,null,false));return this;}
 public FragmentTransaction replace(int container,Fragment fragment,String tag){throw new UnsupportedOperationException("Platform fragment replacement is not implemented");}
 public FragmentTransaction addToBackStack(String name){throw new UnsupportedOperationException("Platform fragment back stack is not implemented");}
 public int commit(){return manager.enqueue(this,false,false);}
 public int commitAllowingStateLoss(){return manager.enqueue(this,true,false);}
 public void commitNow(){manager.enqueue(this,false,true);}
 public void commitNowAllowingStateLoss(){manager.enqueue(this,true,true);}
}
