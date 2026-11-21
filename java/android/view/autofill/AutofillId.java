// SPDX-License-Identifier: GPL-3.0-only
package android.view.autofill;
import android.os.*;
/** Stable in-process view identity, optionally qualified by a virtual child. */
public final class AutofillId implements Parcelable {
 private final int viewId,childId;private final boolean virtual;
 public AutofillId(int viewId){this.viewId=viewId;childId=-1;virtual=false;}
 public AutofillId(AutofillId parent,int childId){this(parent.viewId,childId);}
 public AutofillId(int viewId,int childId){this.viewId=viewId;this.childId=childId;virtual=true;}
 public int getViewId(){return viewId;}public int getVirtualChildId(){return childId;}public boolean isVirtual(){return virtual;}
 @Override public boolean equals(Object other){if(!(other instanceof AutofillId))return false;AutofillId id=(AutofillId)other;return viewId==id.viewId&&childId==id.childId;}
 @Override public int hashCode(){return 31*(31+viewId)+childId;}
 @Override public String toString(){return virtual?viewId+":"+childId:Integer.toString(viewId);}
 public static AutofillId create(android.view.View host,int virtualId){if(host==null)throw new NullPointerException("host must not be null");return new AutofillId(host.getAutofillId(),virtualId);}
 public int describeContents(){return 0;}
 public void writeToParcel(Parcel out,int flags){out.writeInt(viewId);out.writeInt(virtual?1:0);if(virtual)out.writeInt(childId);}
 public static final Parcelable.Creator<AutofillId> CREATOR=new Parcelable.Creator<AutofillId>(){public AutofillId createFromParcel(Parcel in){int v=in.readInt();boolean virt=in.readInt()!=0;return virt?new AutofillId(v,in.readInt()):new AutofillId(v);}public AutofillId[] newArray(int count){return new AutofillId[count];}};
}
