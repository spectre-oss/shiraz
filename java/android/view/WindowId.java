// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Identity of one attached host window; no cross-process window transport. */
public class WindowId implements Parcelable {
 private final IBinder token;
 private final WeakReference<ViewTreeObserver> observer;
 private final ArrayList<FocusObserver> focusObservers=new ArrayList<FocusObserver>();

 WindowId(ViewTreeObserver window){
  token=window.windowToken;
  observer=new WeakReference<ViewTreeObserver>(window);
 }

 public boolean isFocused(){
  ViewTreeObserver window=observer.get();
  return window!=null&&window.isAlive()&&window.windowFocused;
 }

 public void registerFocusObserver(FocusObserver observer){
  if(observer==null)throw new NullPointerException("observer must not be null");
  synchronized(focusObservers){
   if(focusObservers.contains(observer)){
    throw new IllegalStateException("FocusObserver already registered");
   }
   focusObservers.add(observer);
  }
 }

 public void unregisterFocusObserver(FocusObserver observer){
  if(observer==null)throw new NullPointerException("observer must not be null");
  synchronized(focusObservers){
   if(!focusObservers.remove(observer)){
    throw new IllegalStateException("FocusObserver not registered");
   }
  }
 }

 void dispatchFocusChanged(boolean hasFocus){
  ArrayList<FocusObserver> snapshot;
  synchronized(focusObservers){
   if(focusObservers.isEmpty())return;
   snapshot=new ArrayList<FocusObserver>(focusObservers);
  }
  for(int i=0;i<snapshot.size();i++){
   FocusObserver fo=snapshot.get(i);
   if(hasFocus)fo.onFocusGained(this);
   else fo.onFocusLost(this);
  }
 }

 @Override public boolean equals(Object other){
  return other instanceof WindowId&&token==((WindowId)other).token;
 }

 @Override public int hashCode(){
  return token.hashCode();
 }

 @Override public String toString(){
  return "IntentSender{"+Integer.toHexString(System.identityHashCode(this))+": "+token+"}";
 }

 @Override public int describeContents(){
  return 0;
 }

 @Override public void writeToParcel(Parcel destination,int flags){
  throw new UnsupportedOperationException("Window identity parcel transport");
 }

 public static final Parcelable.Creator<WindowId> CREATOR=new Parcelable.Creator<WindowId>(){
  @Override public WindowId createFromParcel(Parcel in){
   throw new UnsupportedOperationException("Window identity parcel transport");
  }
  @Override public WindowId[] newArray(int size){
   return new WindowId[size];
  }
 };

 public abstract static class FocusObserver {
  public FocusObserver(){}
  public abstract void onFocusGained(WindowId token);
  public abstract void onFocusLost(WindowId token);
 }
}
