// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.*;
import android.os.*;
/** Local drag event carrier; host drag initiation and URI grants are separate. */
public class DragEvent implements Parcelable {
 public static final int ACTION_DRAG_STARTED=1,ACTION_DRAG_LOCATION=2,ACTION_DROP=3,ACTION_DRAG_ENDED=4,ACTION_DRAG_ENTERED=5,ACTION_DRAG_EXITED=6;
 private final int action;private final float x,y;private final Object local;private final ClipData data;private final ClipDescription description;private final boolean result;private boolean recycled;
 private DragEvent(int action,float x,float y,Object local,ClipDescription description,ClipData data,boolean result){if(action<1||action>6||!Float.isFinite(x)||!Float.isFinite(y))throw new IllegalArgumentException("Drag event fields");this.action=action;this.x=x;this.y=y;this.local=local;this.description=description;this.data=data;this.result=result;}
 public static DragEvent shirazObtain(int action,float x,float y,Object local,ClipDescription description,ClipData data,boolean result){return new DragEvent(action,x,y,local,description,data,result);}
 public static DragEvent obtain(DragEvent source){source.check();return new DragEvent(source.action,source.x,source.y,source.local,source.description,source.data,source.result);}
 private void check(){if(recycled)throw new IllegalStateException("Recycled drag event");}
 public int getAction(){check();return action;}public float getX(){check();return x;}public float getY(){check();return y;}public Object getLocalState(){check();return local;}public ClipData getClipData(){check();return data;}public ClipDescription getClipDescription(){check();return description;}public boolean getResult(){check();return result;}
 public void recycle(){check();recycled=true;}
 public int describeContents(){return 0;}public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("DragEvent parcel transport");}
 public static final Parcelable.Creator<DragEvent> CREATOR=new Parcelable.Creator<DragEvent>(){public DragEvent createFromParcel(Parcel in){throw new UnsupportedOperationException("DragEvent parcel transport");}public DragEvent[] newArray(int count){return new DragEvent[count];}};
}
