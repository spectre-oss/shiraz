// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.database.DataSetObserver;
import android.view.*;
public interface ExpandableListAdapter {
 void registerDataSetObserver(DataSetObserver observer);void unregisterDataSetObserver(DataSetObserver observer);
 int getGroupCount();int getChildrenCount(int group);Object getGroup(int group);Object getChild(int group,int child);
 long getGroupId(int group);long getChildId(int group,int child);boolean hasStableIds();
 View getGroupView(int group,boolean expanded,View recycled,ViewGroup parent);
 View getChildView(int group,int child,boolean last,View recycled,ViewGroup parent);
 boolean isChildSelectable(int group,int child);boolean areAllItemsEnabled();boolean isEmpty();
 void onGroupExpanded(int group);void onGroupCollapsed(int group);
 long getCombinedChildId(long group,long child);long getCombinedGroupId(long group);
}
