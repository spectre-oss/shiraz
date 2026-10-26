// SPDX-License-Identifier: GPL-3.0-only
package android.view.accessibility;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
/** Local event metadata; no system accessibility connection is claimed. */
public class AccessibilityRecord {
 protected boolean recycled;private View source;private final ArrayList<CharSequence> text=new ArrayList<CharSequence>();
 protected void live(){if(recycled)throw new IllegalStateException("Accessibility record recycled");}
 public static AccessibilityRecord obtain(){return new AccessibilityRecord();}
 public static AccessibilityRecord obtain(AccessibilityRecord other){AccessibilityRecord copy=obtain();copy.copyFrom(other);return copy;}
 public void recycle(){live();recycled=true;source=null;text.clear();}
 public void setSource(View view){live();source=view;}
 public void setSource(View view,int id){if(id!=-1)throw new UnsupportedOperationException("Virtual accessibility source");setSource(view);}
 public AccessibilityNodeInfo getSource(){live();return source==null?null:source.createAccessibilityNodeInfo();}
 public int getWindowId(){live();return -1;}
 public List<CharSequence> getText(){live();return text;}
 private CharSequence className;
 public CharSequence getClassName(){live();return className;}
 public void setClassName(CharSequence value){live();className=value==null?null:value.toString();}
 private CharSequence contentDescription;
 public CharSequence getContentDescription(){live();return contentDescription;}
 public void setContentDescription(CharSequence value){live();contentDescription=value==null?null:value.toString();}
 private CharSequence beforeText;
 public CharSequence getBeforeText(){live();return beforeText;}
 public void setBeforeText(CharSequence value){live();beforeText=value==null?null:value.toString();}
 private boolean checked;
 public boolean isChecked(){live();return checked;}
 public void setChecked(boolean value){live();checked=value;}
 private boolean enabled;
 public boolean isEnabled(){live();return enabled;}
 public void setEnabled(boolean value){live();enabled=value;}
 private boolean password;
 public boolean isPassword(){live();return password;}
 public void setPassword(boolean value){live();password=value;}
 private boolean fullScreen;
 public boolean isFullScreen(){live();return fullScreen;}
 public void setFullScreen(boolean value){live();fullScreen=value;}
 private boolean scrollable;
 public boolean isScrollable(){live();return scrollable;}
 public void setScrollable(boolean value){live();scrollable=value;}
 private int itemCount=-1;
 public int getItemCount(){live();return itemCount;}
 public void setItemCount(int value){live();itemCount=value;}
 private int currentItemIndex=-1;
 public int getCurrentItemIndex(){live();return currentItemIndex;}
 public void setCurrentItemIndex(int value){live();currentItemIndex=value;}
 private int fromIndex=-1;
 public int getFromIndex(){live();return fromIndex;}
 public void setFromIndex(int value){live();fromIndex=value;}
 private int toIndex=-1;
 public int getToIndex(){live();return toIndex;}
 public void setToIndex(int value){live();toIndex=value;}
 private int scrollX=-1;
 public int getScrollX(){live();return scrollX;}
 public void setScrollX(int value){live();scrollX=value;}
 private int scrollY=-1;
 public int getScrollY(){live();return scrollY;}
 public void setScrollY(int value){live();scrollY=value;}
 private int maxScrollX=-1;
 public int getMaxScrollX(){live();return maxScrollX;}
 public void setMaxScrollX(int value){live();maxScrollX=value;}
 private int maxScrollY=-1;
 public int getMaxScrollY(){live();return maxScrollY;}
 public void setMaxScrollY(int value){live();maxScrollY=value;}
 private int addedCount=-1;
 public int getAddedCount(){live();return addedCount;}
 public void setAddedCount(int value){live();addedCount=value;}
 private int removedCount=-1;
 public int getRemovedCount(){live();return removedCount;}
 public void setRemovedCount(int value){live();removedCount=value;}
 protected void copyFrom(AccessibilityRecord other){other.live();source=other.source;text.clear();text.addAll(other.text);className=other.className;contentDescription=other.contentDescription;beforeText=other.beforeText;checked=other.checked;enabled=other.enabled;password=other.password;fullScreen=other.fullScreen;scrollable=other.scrollable;itemCount=other.itemCount;currentItemIndex=other.currentItemIndex;fromIndex=other.fromIndex;toIndex=other.toIndex;scrollX=other.scrollX;scrollY=other.scrollY;maxScrollX=other.maxScrollX;maxScrollY=other.maxScrollY;addedCount=other.addedCount;removedCount=other.removedCount;}
}
