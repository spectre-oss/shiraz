// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.database.*;
public abstract class BaseExpandableListAdapter implements ExpandableListAdapter,HeterogeneousExpandableList {
 private final DataSetObservable observable=new DataSetObservable();
 public void registerDataSetObserver(DataSetObserver o){observable.registerObserver(o);}public void unregisterDataSetObserver(DataSetObserver o){observable.unregisterObserver(o);}
 public void notifyDataSetChanged(){observable.notifyChanged();}public void notifyDataSetInvalidated(){observable.notifyInvalidated();}
 public boolean areAllItemsEnabled(){return true;}public boolean isEmpty(){return getGroupCount()==0;}
 public void onGroupExpanded(int group){}public void onGroupCollapsed(int group){}
 public long getCombinedGroupId(long group){return (group&0x7fffffffL)<<32;}
 public long getCombinedChildId(long group,long child){return Long.MIN_VALUE|((group&0x7fffffffL)<<32)|(child&0xffffffffL);}
 public int getGroupType(int group){return 0;}public int getChildType(int group,int child){return 0;}public int getGroupTypeCount(){return 1;}public int getChildTypeCount(){return 1;}
}
