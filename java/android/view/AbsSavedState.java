// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.os.Parcel;
import android.os.Parcelable;
/** In-process saved-state ownership. Parcel transport remains unsupported. */
public abstract class AbsSavedState implements Parcelable {
 public static final AbsSavedState EMPTY_STATE=new AbsSavedState(){};
 private final Parcelable superState;
 private AbsSavedState(){superState=null;}
 protected AbsSavedState(Parcelable state){if(state==null)throw new IllegalArgumentException("superState must not be null");superState=state==EMPTY_STATE?null:state;}
 protected AbsSavedState(Parcel source){throw new UnsupportedOperationException("Saved-state parcel transport is not implemented");}
 protected AbsSavedState(Parcel source,ClassLoader loader){throw new UnsupportedOperationException("Saved-state parcel transport is not implemented");}
 public final Parcelable getSuperState(){return superState;}
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("Saved-state parcel transport is not implemented");}
}
