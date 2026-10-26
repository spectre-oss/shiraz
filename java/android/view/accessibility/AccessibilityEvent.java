// SPDX-License-Identifier: GPL-3.0-only
package android.view.accessibility;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
public class AccessibilityEvent extends AccessibilityRecord implements Parcelable {
 public static final int TYPE_VIEW_CLICKED=1,TYPE_VIEW_LONG_CLICKED=2,TYPE_VIEW_SELECTED=4,TYPE_VIEW_FOCUSED=8,TYPE_VIEW_TEXT_CHANGED=16,TYPE_WINDOW_STATE_CHANGED=32,TYPE_NOTIFICATION_STATE_CHANGED=64,TYPE_VIEW_HOVER_ENTER=128,TYPE_VIEW_HOVER_EXIT=256,TYPE_TOUCH_EXPLORATION_GESTURE_START=512,TYPE_TOUCH_EXPLORATION_GESTURE_END=1024,TYPE_WINDOW_CONTENT_CHANGED=2048,TYPE_VIEW_SCROLLED=4096,TYPE_VIEW_TEXT_SELECTION_CHANGED=8192,TYPE_ANNOUNCEMENT=16384,TYPE_VIEW_ACCESSIBILITY_FOCUSED=32768,TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED=65536,TYPES_ALL_MASK=-1;
 private int eventType,contentChangeTypes,action,movementGranularity;private long eventTime;private CharSequence packageName;private final ArrayList<AccessibilityRecord> records=new ArrayList<AccessibilityRecord>();
 public static AccessibilityEvent obtain(){return new AccessibilityEvent();}
 public static AccessibilityEvent obtain(int type){AccessibilityEvent event=obtain();event.eventType=type;return event;}
 public static AccessibilityEvent obtain(AccessibilityEvent other){other.live();AccessibilityEvent e=obtain(other.eventType);e.copyFrom(other);e.contentChangeTypes=other.contentChangeTypes;e.action=other.action;e.movementGranularity=other.movementGranularity;e.eventTime=other.eventTime;e.packageName=other.packageName;for(AccessibilityRecord r:other.records)e.records.add(AccessibilityRecord.obtain(r));return e;}
 public int getEventType(){live();return eventType;}public void setEventType(int value){live();eventType=value;}
 public int getContentChangeTypes(){live();return contentChangeTypes;}public void setContentChangeTypes(int value){live();contentChangeTypes=value;}
 public int getAction(){live();return action;}public void setAction(int value){live();action=value;}
 public int getMovementGranularity(){live();return movementGranularity;}public void setMovementGranularity(int value){live();movementGranularity=value;}
 public long getEventTime(){live();return eventTime;}public void setEventTime(long value){live();eventTime=value;}
 public CharSequence getPackageName(){live();return packageName;}public void setPackageName(CharSequence value){live();packageName=value==null?null:value.toString();}
 public void appendRecord(AccessibilityRecord value){live();if(value==null)throw new NullPointerException("record");if(value==this||records.contains(value))throw new IllegalArgumentException("Event record ownership");if(records.size()>=1024)throw new IllegalStateException("Accessibility record limit");records.add(value);}
 public int getRecordCount(){live();return records.size();}public AccessibilityRecord getRecord(int index){live();return records.get(index);}
 public void recycle(){super.recycle();for(AccessibilityRecord r:records)r.recycle();records.clear();}
 public int describeContents(){live();return 0;}public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("Accessibility parcel transport is not implemented");}
 public static final Parcelable.Creator<AccessibilityEvent> CREATOR=new Parcelable.Creator<AccessibilityEvent>(){public AccessibilityEvent createFromParcel(Parcel in){throw new UnsupportedOperationException("Accessibility parcel decoding is not implemented");}public AccessibilityEvent[] newArray(int size){return new AccessibilityEvent[size];}};
}
