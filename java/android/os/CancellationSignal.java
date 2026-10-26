// SPDX-License-Identifier: GPL-3.0-only
package android.os;
public final class CancellationSignal {
 public interface OnCancelListener{void onCancel();}
 private boolean cancelled;private OnCancelListener listener;
 public CancellationSignal(){}
 public synchronized boolean isCanceled(){return cancelled;}
 public void throwIfCanceled(){if(isCanceled())throw new OperationCanceledException();}
 public void cancel(){OnCancelListener target;synchronized(this){if(cancelled)return;cancelled=true;target=listener;}if(target!=null)target.onCancel();}
 public void setOnCancelListener(OnCancelListener value){boolean fire;synchronized(this){listener=value;fire=cancelled&&value!=null;}if(fire)value.onCancel();}
}
