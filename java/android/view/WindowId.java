// SPDX-License-Identifier: GPL-3.0-only
package android.view;
/** Identity of one attached host window; no cross-process window transport. */
public final class WindowId implements android.os.Parcelable {
 private final android.os.IBinder token;
 private final java.lang.ref.WeakReference<ViewTreeObserver> observer;
 WindowId(ViewTreeObserver window){token=window.windowToken;observer=new java.lang.ref.WeakReference<ViewTreeObserver>(window);}
 public boolean isFocused(){ViewTreeObserver window=observer.get();return window!=null&&window.isAlive()&&window.windowFocused;}
 @Override public boolean equals(Object other){return other instanceof WindowId&&token==((WindowId)other).token;}
 @Override public int hashCode(){return token.hashCode();}
 public int describeContents(){return 0;}
 public void writeToParcel(android.os.Parcel destination,int flags){throw new UnsupportedOperationException("Window identity parcel transport");}
}
