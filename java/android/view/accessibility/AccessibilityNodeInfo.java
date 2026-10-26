// SPDX-License-Identifier: GPL-3.0-only
package android.view.accessibility;
import android.view.View;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.*;
/** Mutable local node snapshot. Service IPC and virtual descendant transport are not implemented. */
public class AccessibilityNodeInfo implements Parcelable {
 public static final int ACTION_FOCUS=1,ACTION_CLEAR_FOCUS=2,ACTION_SELECT=4,ACTION_CLEAR_SELECTION=8,ACTION_CLICK=16,ACTION_LONG_CLICK=32,ACTION_ACCESSIBILITY_FOCUS=64,ACTION_CLEAR_ACCESSIBILITY_FOCUS=128,ACTION_NEXT_AT_MOVEMENT_GRANULARITY=256,ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY=512,ACTION_NEXT_HTML_ELEMENT=1024,ACTION_PREVIOUS_HTML_ELEMENT=2048,ACTION_SCROLL_FORWARD=4096,ACTION_SCROLL_BACKWARD=8192,ACTION_COPY=16384,ACTION_PASTE=32768,ACTION_CUT=65536,ACTION_SET_SELECTION=131072,ACTION_EXPAND=262144,ACTION_COLLAPSE=524288,ACTION_DISMISS=1048576,ACTION_SET_TEXT=2097152;
 public static final int FOCUS_INPUT=1,FOCUS_ACCESSIBILITY=2;
 private View source,parent;private boolean recycled;private final Rect parentBounds=new Rect(),screenBounds=new Rect();
 private final ArrayList<View> children=new ArrayList<View>();private final ArrayList<AccessibilityAction> actions=new ArrayList<AccessibilityAction>();private Bundle extras=new Bundle();
 public static AccessibilityNodeInfo obtain(){return new AccessibilityNodeInfo();}
 public static AccessibilityNodeInfo obtain(View view){AccessibilityNodeInfo info=obtain();info.setSource(view);return info;}
 public static AccessibilityNodeInfo obtain(View view,int id){virtual(id);return obtain(view);}
 private static void virtual(int id){if(id!=-1)throw new UnsupportedOperationException("Virtual accessibility nodes are not implemented");}
 private void live(){if(recycled)throw new IllegalStateException("Accessibility node recycled");}
 public void recycle(){live();recycled=true;source=parent=null;children.clear();actions.clear();extras.clear();}
 public void setSource(View view){live();source=view;}
 public void setSource(View view,int id){virtual(id);setSource(view);}
 public void setParent(View view){live();parent=view;}
 public void setParent(View view,int id){virtual(id);setParent(view);}
 public AccessibilityNodeInfo getParent(){live();return parent==null?null:parent.createAccessibilityNodeInfo();}
 public int getWindowId(){live();return -1;}
 public void addChild(View view){live();if(view==null)throw new NullPointerException("child");if(!children.contains(view)){if(children.size()>=4096)throw new IllegalStateException("Accessibility child limit");children.add(view);}}
 public void addChild(View view,int id){virtual(id);addChild(view);}
 public boolean removeChild(View view){live();return children.remove(view);}
 public boolean removeChild(View view,int id){virtual(id);return removeChild(view);}
 public int getChildCount(){live();return children.size();}
 public AccessibilityNodeInfo getChild(int index){live();return children.get(index).createAccessibilityNodeInfo();}
 public void getBoundsInParent(Rect out){live();out.set(parentBounds);}
 public void setBoundsInParent(Rect value){live();parentBounds.set(value);}
 public void getBoundsInScreen(Rect out){live();out.set(screenBounds);}
 public void setBoundsInScreen(Rect value){live();screenBounds.set(value);}
 public Bundle getExtras(){live();return extras;}
 public void addAction(int mask){live();if((mask&~0x3fffff)!=0)throw new IllegalArgumentException("Legacy accessibility action mask");for(int bit=1;bit<=2097152;bit<<=1)if((mask&bit)!=0)addAction(new AccessibilityAction(bit,null));}
 public void addAction(AccessibilityAction action){live();if(action==null)return;actions.remove(action);if(actions.size()>=64)throw new IllegalStateException("Accessibility action limit");actions.add(action);}
 public boolean removeAction(AccessibilityAction action){live();return actions.remove(action);}
 public int getActions(){live();int mask=0;for(AccessibilityAction action:actions)if(action.id>0&&action.id<=2097152&&(action.id&(action.id-1))==0)mask|=action.id;return mask;}
 public List<AccessibilityAction> getActionList(){live();return Collections.unmodifiableList(new ArrayList<AccessibilityAction>(actions));}
 public boolean performAction(int action){return performAction(action,null);}
 public boolean performAction(int action,Bundle arguments){live();return source!=null&&source.performAccessibilityAction(action,arguments);}
 public static final class AccessibilityAction {
  public static final AccessibilityAction ACTION_NEXT_AT_MOVEMENT_GRANULARITY=new AccessibilityAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,null);
  public static final AccessibilityAction ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY=new AccessibilityAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,null);
  public static final AccessibilityAction ACTION_NEXT_HTML_ELEMENT=new AccessibilityAction(AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT,null);
  public static final AccessibilityAction ACTION_PREVIOUS_HTML_ELEMENT=new AccessibilityAction(AccessibilityNodeInfo.ACTION_PREVIOUS_HTML_ELEMENT,null);
  public static final AccessibilityAction ACTION_COPY=new AccessibilityAction(AccessibilityNodeInfo.ACTION_COPY,null);
  public static final AccessibilityAction ACTION_PASTE=new AccessibilityAction(AccessibilityNodeInfo.ACTION_PASTE,null);
  public static final AccessibilityAction ACTION_CUT=new AccessibilityAction(AccessibilityNodeInfo.ACTION_CUT,null);
  public static final AccessibilityAction ACTION_SET_SELECTION=new AccessibilityAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,null);
  public static final AccessibilityAction ACTION_EXPAND=new AccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND,null);
  public static final AccessibilityAction ACTION_COLLAPSE=new AccessibilityAction(AccessibilityNodeInfo.ACTION_COLLAPSE,null);
  public static final AccessibilityAction ACTION_DISMISS=new AccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS,null);
  public static final AccessibilityAction ACTION_SET_TEXT=new AccessibilityAction(AccessibilityNodeInfo.ACTION_SET_TEXT,null);
  public static final AccessibilityAction ACTION_SHOW_ON_SCREEN=new AccessibilityAction(0x01020036,null);
  public static final AccessibilityAction ACTION_SCROLL_TO_POSITION=new AccessibilityAction(0x01020037,null);
  public static final AccessibilityAction ACTION_SCROLL_UP=new AccessibilityAction(0x01020038,null);
  public static final AccessibilityAction ACTION_SCROLL_LEFT=new AccessibilityAction(0x01020039,null);
  public static final AccessibilityAction ACTION_SCROLL_DOWN=new AccessibilityAction(0x0102003a,null);
  public static final AccessibilityAction ACTION_SCROLL_RIGHT=new AccessibilityAction(0x0102003b,null);
  public static final AccessibilityAction ACTION_CONTEXT_CLICK=new AccessibilityAction(0x0102003c,null);
  public static final AccessibilityAction ACTION_SET_PROGRESS=new AccessibilityAction(0x0102003d,null);
  public static final AccessibilityAction ACTION_MOVE_WINDOW=new AccessibilityAction(0x01020042,null);
  private final int id;private final CharSequence label;
  public AccessibilityAction(int id,CharSequence label){if(id==0)throw new IllegalArgumentException("Action ID");this.id=id;this.label=label==null?null:label.toString();}
  public int getId(){return id;}public CharSequence getLabel(){return label;}
  public boolean equals(Object other){return other instanceof AccessibilityAction&&((AccessibilityAction)other).id==id;}public int hashCode(){return id;}
  public static final AccessibilityAction ACTION_FOCUS=new AccessibilityAction(1,null),ACTION_CLEAR_FOCUS=new AccessibilityAction(2,null),ACTION_SELECT=new AccessibilityAction(4,null),ACTION_CLEAR_SELECTION=new AccessibilityAction(8,null),ACTION_CLICK=new AccessibilityAction(16,null),ACTION_LONG_CLICK=new AccessibilityAction(32,null),ACTION_ACCESSIBILITY_FOCUS=new AccessibilityAction(64,null),ACTION_CLEAR_ACCESSIBILITY_FOCUS=new AccessibilityAction(128,null),ACTION_SCROLL_FORWARD=new AccessibilityAction(4096,null),ACTION_SCROLL_BACKWARD=new AccessibilityAction(8192,null);
 }
 private CharSequence packageName;
 public CharSequence getPackageName(){live();return packageName;}
 public void setPackageName(CharSequence value){live();packageName=value==null?null:value.toString();}
 private CharSequence className;
 public CharSequence getClassName(){live();return className;}
 public void setClassName(CharSequence value){live();className=value==null?null:value.toString();}
 private CharSequence text;
 public CharSequence getText(){live();return text;}
 public void setText(CharSequence value){live();text=value==null?null:value.toString();}
 private CharSequence contentDescription;
 public CharSequence getContentDescription(){live();return contentDescription;}
 public void setContentDescription(CharSequence value){live();contentDescription=value==null?null:value.toString();}
 private CharSequence hintText;
 public CharSequence getHintText(){live();return hintText;}
 public void setHintText(CharSequence value){live();hintText=value==null?null:value.toString();}
 private CharSequence error;
 public CharSequence getError(){live();return error;}
 public void setError(CharSequence value){live();error=value==null?null:value.toString();}
 private int liveRegion;
 public int getLiveRegion(){live();return liveRegion;}
 public void setLiveRegion(int value){live();if(value<0||value>2)throw new IllegalArgumentException("Accessibility live region");liveRegion=value;}
 private String viewIdResourceName;
 public String getViewIdResourceName(){live();return viewIdResourceName;}
 public void setViewIdResourceName(String value){live();viewIdResourceName=value;}
 private boolean checkable;
 public boolean isCheckable(){live();return checkable;}
 public void setCheckable(boolean value){live();checkable=value;}
 private boolean checked;
 public boolean isChecked(){live();return checked;}
 public void setChecked(boolean value){live();checked=value;}
 private boolean focusable;
 public boolean isFocusable(){live();return focusable;}
 public void setFocusable(boolean value){live();focusable=value;}
 private boolean focused;
 public boolean isFocused(){live();return focused;}
 public void setFocused(boolean value){live();focused=value;}
 private boolean visibleToUser;
 public boolean isVisibleToUser(){live();return visibleToUser;}
 public void setVisibleToUser(boolean value){live();visibleToUser=value;}
 private boolean accessibilityFocused;
 public boolean isAccessibilityFocused(){live();return accessibilityFocused;}
 public void setAccessibilityFocused(boolean value){live();accessibilityFocused=value;}
 private boolean selected;
 public boolean isSelected(){live();return selected;}
 public void setSelected(boolean value){live();selected=value;}
 private boolean clickable;
 public boolean isClickable(){live();return clickable;}
 public void setClickable(boolean value){live();clickable=value;}
 private boolean longClickable;
 public boolean isLongClickable(){live();return longClickable;}
 public void setLongClickable(boolean value){live();longClickable=value;}
 private boolean enabled;
 public boolean isEnabled(){live();return enabled;}
 public void setEnabled(boolean value){live();enabled=value;}
 private boolean password;
 public boolean isPassword(){live();return password;}
 public void setPassword(boolean value){live();password=value;}
 private boolean scrollable;
 public boolean isScrollable(){live();return scrollable;}
 public void setScrollable(boolean value){live();scrollable=value;}
 private boolean editable;
 public boolean isEditable(){live();return editable;}
 public void setEditable(boolean value){live();editable=value;}
 private boolean multiLine;
 public boolean isMultiLine(){live();return multiLine;}
 public void setMultiLine(boolean value){live();multiLine=value;}
 private boolean contentInvalid;
 public boolean isContentInvalid(){live();return contentInvalid;}
 public void setContentInvalid(boolean value){live();contentInvalid=value;}
 private boolean dismissable;
 public boolean isDismissable(){live();return dismissable;}
 public void setDismissable(boolean value){live();dismissable=value;}
 private boolean showingHintText;
 public boolean isShowingHintText(){live();return showingHintText;}
 public void setShowingHintText(boolean value){live();showingHintText=value;}
 private boolean importantForAccessibility;
 public boolean isImportantForAccessibility(){live();return importantForAccessibility;}
 public void setImportantForAccessibility(boolean value){live();importantForAccessibility=value;}
 private int movementGranularities;
 public int getMovementGranularities(){live();return movementGranularities;}
 public void setMovementGranularities(int value){live();movementGranularities=value;}
 private int inputType;
 public int getInputType(){live();return inputType;}
 public void setInputType(int value){live();inputType=value;}
 private int maxTextLength=-1;
 public int getMaxTextLength(){live();return maxTextLength;}
 public void setMaxTextLength(int value){live();maxTextLength=value;}
 private int textSelectionStart=-1;
 public int getTextSelectionStart(){live();return textSelectionStart;}
 private int textSelectionEnd=-1;
 public int getTextSelectionEnd(){live();return textSelectionEnd;}
 public void setTextSelection(int start,int end){live();textSelectionStart=start;textSelectionEnd=end;}
 public static AccessibilityNodeInfo obtain(AccessibilityNodeInfo other){other.live();AccessibilityNodeInfo copy=obtain();copy.source=other.source;copy.liveRegion=other.liveRegion;copy.parent=other.parent;copy.parentBounds.set(other.parentBounds);copy.screenBounds.set(other.screenBounds);copy.children.addAll(other.children);copy.actions.addAll(other.actions);copy.extras=new Bundle(other.extras);copy.packageName=other.packageName;copy.className=other.className;copy.text=other.text;copy.contentDescription=other.contentDescription;copy.hintText=other.hintText;copy.error=other.error;copy.viewIdResourceName=other.viewIdResourceName;copy.checkable=other.checkable;copy.checked=other.checked;copy.focusable=other.focusable;copy.focused=other.focused;copy.visibleToUser=other.visibleToUser;copy.accessibilityFocused=other.accessibilityFocused;copy.selected=other.selected;copy.clickable=other.clickable;copy.longClickable=other.longClickable;copy.enabled=other.enabled;copy.password=other.password;copy.scrollable=other.scrollable;copy.editable=other.editable;copy.multiLine=other.multiLine;copy.contentInvalid=other.contentInvalid;copy.dismissable=other.dismissable;copy.showingHintText=other.showingHintText;copy.importantForAccessibility=other.importantForAccessibility;copy.movementGranularities=other.movementGranularities;copy.inputType=other.inputType;copy.maxTextLength=other.maxTextLength;copy.textSelectionStart=other.textSelectionStart;copy.textSelectionEnd=other.textSelectionEnd;return copy;}
 public int describeContents(){live();return 0;}
 public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("Accessibility parcel transport is not implemented");}
 public static final Parcelable.Creator<AccessibilityNodeInfo> CREATOR=new Parcelable.Creator<AccessibilityNodeInfo>(){public AccessibilityNodeInfo createFromParcel(Parcel in){throw new UnsupportedOperationException("Accessibility parcel decoding is not implemented");}public AccessibilityNodeInfo[] newArray(int size){return new AccessibilityNodeInfo[size];}};
}
