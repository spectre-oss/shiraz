// SPDX-License-Identifier: GPL-3.0-only
package android.os;
import java.util.ArrayList;
import java.util.IdentityHashMap;
/** In-process callback registry keyed by binder identity. */
public class RemoteCallbackList<E extends IInterface> {
 private static final class Entry<E> {final E callback;final Object cookie;Entry(E callback,Object cookie){this.callback=callback;this.cookie=cookie;}}
 private final IdentityHashMap<IBinder,Entry<E>> callbacks=new IdentityHashMap<IBinder,Entry<E>>();
 private ArrayList<Entry<E>> broadcast;
 private boolean killed;
 public boolean register(E callback){return register(callback,null);}
 public synchronized boolean register(E callback,Object cookie){
  if(killed||callback==null)return false;
  IBinder binder=callback.asBinder();if(binder==null)return false;
  callbacks.put(binder,new Entry<E>(callback,cookie));return true;
 }
 public synchronized boolean unregister(E callback){
  if(callback==null)return false;
  IBinder binder=callback.asBinder();return binder!=null&&callbacks.remove(binder)!=null;
 }
 public synchronized void kill(){killed=true;callbacks.clear();}
 public void onCallbackDied(E callback){}
 public void onCallbackDied(E callback,Object cookie){onCallbackDied(callback);}
 public synchronized int beginBroadcast(){
  if(broadcast!=null)throw new IllegalStateException("already in the process of making a broadcast");
  broadcast=new ArrayList<Entry<E>>(callbacks.values());return broadcast.size();
 }
 public synchronized E getBroadcastItem(int index){return broadcast.get(index).callback;}
 public synchronized Object getBroadcastCookie(int index){return broadcast.get(index).cookie;}
 public synchronized void finishBroadcast(){
  if(broadcast==null)throw new IllegalStateException("finishBroadcast() called outside of a broadcast");
  broadcast=null;
 }
 public synchronized int getRegisteredCallbackCount(){return killed?0:callbacks.size();}
 public synchronized E getRegisteredCallbackItem(int index){return new ArrayList<Entry<E>>(callbacks.values()).get(index).callback;}
 public synchronized Object getRegisteredCallbackCookie(int index){return new ArrayList<Entry<E>>(callbacks.values()).get(index).cookie;}
}
