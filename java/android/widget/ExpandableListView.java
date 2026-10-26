// SPDX-License-Identifier: GPL-3.0-only
package android.widget;
import android.content.Context;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.*;
import java.util.*;
/** Grouped rows using the existing bounded ListView layout and input path. */
public class ExpandableListView extends ListView {
 public static final int PACKED_POSITION_TYPE_GROUP=0,PACKED_POSITION_TYPE_CHILD=1,PACKED_POSITION_TYPE_NULL=2;
 public static final long PACKED_POSITION_VALUE_NULL=0x00000000ffffffffL;
 public interface OnChildClickListener{boolean onChildClick(ExpandableListView parent,View view,int group,int child,long id);}
 public interface OnGroupClickListener{boolean onGroupClick(ExpandableListView parent,View view,int group,long id);}
 public interface OnGroupExpandListener{void onGroupExpand(int group);}public interface OnGroupCollapseListener{void onGroupCollapse(int group);}
 private OnChildClickListener childClick;private OnGroupClickListener groupClick;private OnGroupExpandListener expandListener;private OnGroupCollapseListener collapseListener;
 private ExpandableListAdapter source;private Flat flat;
 private final HashSet<Long> expanded=new HashSet<Long>();
 private ArrayList<int[]> rows=new ArrayList<int[]>();
 private final DataSetObserver observer=new DataSetObserver(){public void onChanged(){rebuild();flat.notifyDataSetChanged();}public void onInvalidated(){expanded.clear();rows.clear();flat.notifyDataSetInvalidated();}};
 public ExpandableListView(Context c){this(c,null);}public ExpandableListView(Context c,AttributeSet a){this(c,a,0x0101006f);}public ExpandableListView(Context c,AttributeSet a,int s){this(c,a,s,0);}public ExpandableListView(Context c,AttributeSet a,int s,int r){super(c,a,s,r);}
 private long key(int group){return source.hasStableIds()?source.getGroupId(group):group;}
 private void group(int group){if(source==null||group<0||group>=source.getGroupCount())throw new IllegalArgumentException("Group position");}
 private void rebuild(){
  ArrayList<int[]> next=new ArrayList<int[]>();HashSet<Long> live=new HashSet<Long>();int count=source.getGroupCount();if(count<0||count>65536)throw new IllegalArgumentException("Expandable group limit");
  for(int g=0;g<count;g++){long key=key(g);live.add(key);if(next.size()>=131072)throw new IllegalArgumentException("Expandable row limit");next.add(new int[]{g,-1});if(expanded.contains(key)){int children=source.getChildrenCount(g);if(children<0||children>65536||next.size()+children>131072)throw new IllegalArgumentException("Expandable row limit");for(int c=0;c<children;c++)next.add(new int[]{g,c});}}
  expanded.retainAll(live);rows=next;
 }
 public void setAdapter(ExpandableListAdapter adapter){if(source!=null)source.unregisterDataSetObserver(observer);source=adapter;expanded.clear();rows.clear();flat=null;if(adapter!=null){flat=new Flat();rebuild();adapter.registerDataSetObserver(observer);}super.setAdapter(flat);}
 @Override public void setAdapter(ListAdapter adapter){throw new UnsupportedOperationException("ExpandableListView requires ExpandableListAdapter");}
 public ExpandableListAdapter getExpandableListAdapter(){return source;}
 public void setOnChildClickListener(OnChildClickListener v){childClick=v;}public void setOnGroupClickListener(OnGroupClickListener v){groupClick=v;}public void setOnGroupExpandListener(OnGroupExpandListener v){expandListener=v;}public void setOnGroupCollapseListener(OnGroupCollapseListener v){collapseListener=v;}
 public boolean isGroupExpanded(int g){group(g);return expanded.contains(key(g));}
 public boolean expandGroup(int g){return expandGroup(g,false);}public boolean expandGroup(int g,boolean animate){group(g);long key=key(g);if(!expanded.add(key))return false;try{rebuild();}catch(RuntimeException failure){expanded.remove(key);throw failure;}flat.notifyDataSetChanged();source.onGroupExpanded(g);if(expandListener!=null)expandListener.onGroupExpand(g);return true;}
 public boolean collapseGroup(int g){group(g);if(!expanded.remove(key(g)))return false;rebuild();flat.notifyDataSetChanged();source.onGroupCollapsed(g);if(collapseListener!=null)collapseListener.onGroupCollapse(g);return true;}
 @Override public boolean performItemClick(View view,int position,long id){int p=position-getHeaderViewsCount();if(p<0||p>=rows.size())return super.performItemClick(view,position,id);int[] row=rows.get(p);int g=row[0],c=row[1];if(c>=0)return source.isChildSelectable(g,c)&&childClick!=null&&childClick.onChildClick(this,view,g,c,source.getChildId(g,c));if(groupClick!=null&&groupClick.onGroupClick(this,view,g,source.getGroupId(g)))return true;if(isGroupExpanded(g))collapseGroup(g);else expandGroup(g);return true;}
 public static int getPackedPositionType(long p){return p==PACKED_POSITION_VALUE_NULL?2:p<0?1:0;}public static int getPackedPositionGroup(long p){return p==PACKED_POSITION_VALUE_NULL?-1:(int)((p>>>32)&0x7fffffff);}
 public static int getPackedPositionChild(long p){return getPackedPositionType(p)==1?(int)p:-1;}public static long getPackedPositionForGroup(int g){return ((long)g&0x7fffffff)<<32;}public static long getPackedPositionForChild(int g,int c){return Long.MIN_VALUE|getPackedPositionForGroup(g)|(c&0xffffffffL);}
 public long getExpandableListPosition(int position){int p=position-getHeaderViewsCount();if(p<0||p>=rows.size())return PACKED_POSITION_VALUE_NULL;int[] r=rows.get(p);return r[1]<0?getPackedPositionForGroup(r[0]):getPackedPositionForChild(r[0],r[1]);}
 public int getFlatListPosition(long p){if(p==PACKED_POSITION_VALUE_NULL)return -1;int g=getPackedPositionGroup(p),c=getPackedPositionChild(p);for(int i=0;i<rows.size();i++){int[] r=rows.get(i);if(r[0]==g&&r[1]==c)return i+getHeaderViewsCount();}return -1;}
 public long getSelectedPosition(){return getExpandableListPosition(getSelectedItemPosition());}public long getSelectedId(){long p=getSelectedPosition();int g=getPackedPositionGroup(p),c=getPackedPositionChild(p);return g<0?-1:c<0?source.getGroupId(g):source.getChildId(g,c);}
 public void setSelectedGroup(int g){group(g);setSelection(getFlatListPosition(getPackedPositionForGroup(g)));}
 public boolean setSelectedChild(int g,int c,boolean expand){group(g);if(c<0||c>=source.getChildrenCount(g))return false;if(!isGroupExpanded(g)){if(!expand)return false;expandGroup(g);}setSelection(getFlatListPosition(getPackedPositionForChild(g,c)));return true;}
 public void setGroupIndicator(android.graphics.drawable.Drawable d){if(d!=null)throw new UnsupportedOperationException("Expandable group indicator drawable");}
 public void setChildIndicator(android.graphics.drawable.Drawable d){if(d!=null)throw new UnsupportedOperationException("Expandable child indicator drawable");}
 private final class Flat extends BaseAdapter {
  public int getCount(){return rows.size();}public boolean hasStableIds(){return source.hasStableIds();}public boolean areAllItemsEnabled(){return source.areAllItemsEnabled();}public boolean isEnabled(int p){int[] r=rows.get(p);return r[1]<0||source.isChildSelectable(r[0],r[1]);}
  public Object getItem(int p){int[] r=rows.get(p);return r[1]<0?source.getGroup(r[0]):source.getChild(r[0],r[1]);}
  public long getItemId(int p){int[] r=rows.get(p);long g=source.getGroupId(r[0]);return r[1]<0?source.getCombinedGroupId(g):source.getCombinedChildId(g,source.getChildId(r[0],r[1]));}
  public int getViewTypeCount(){if(!(source instanceof HeterogeneousExpandableList))return 2;HeterogeneousExpandableList h=(HeterogeneousExpandableList)source;int g=h.getGroupTypeCount(),c=h.getChildTypeCount();if(g<1||c<1||g>512||c>512)throw new IllegalArgumentException("Expandable view types");return g+c;}
  public int getItemViewType(int p){int[] r=rows.get(p);if(!(source instanceof HeterogeneousExpandableList))return r[1]<0?0:1;HeterogeneousExpandableList h=(HeterogeneousExpandableList)source;int type=r[1]<0?h.getGroupType(r[0]):h.getChildType(r[0],r[1]);if(type<0)return -1;if(type>=(r[1]<0?h.getGroupTypeCount():h.getChildTypeCount()))throw new IllegalArgumentException("Expandable row type");return r[1]<0?type:h.getGroupTypeCount()+type;}
  public View getView(int p,View recycled,ViewGroup parent){int[] r=rows.get(p);return r[1]<0?source.getGroupView(r[0],isGroupExpanded(r[0]),recycled,parent):source.getChildView(r[0],r[1],r[1]==source.getChildrenCount(r[0])-1,recycled,parent);}
 }
}
