// SPDX-License-Identifier: GPL-3.0-only
package android.os;
/** In-process Messenger; remote binders are not supported. */
public final class Messenger {
 private static final class Local extends Binder {
  final Handler handler;
  Local(Handler handler){this.handler=handler;}
 }
 private final Local binder;
 public Messenger(Handler handler){if(handler==null)throw new NullPointerException("handler");binder=new Local(handler);}
 public Messenger(IBinder target){if(!(target instanceof Local))throw new UnsupportedOperationException("Remote Messenger binders are not implemented");binder=(Local)target;}
 public void send(Message message)throws RemoteException{Message copy=Message.obtain(message);copy.replyTo=message.replyTo;if(!binder.handler.sendMessage(copy))throw new RemoteException();}
 public IBinder getBinder(){return binder;}
 @Override public boolean equals(Object other){return other instanceof Messenger&&((Messenger)other).binder==binder;}
 @Override public int hashCode(){return System.identityHashCode(binder);}
}
